package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.common.BehaviorType;
import com.secondhand.common.BusinessException;
import com.secondhand.common.ProductStatus;
import com.secondhand.common.Result;
import com.secondhand.dto.ProductQueryDTO;
import com.secondhand.entity.Category;
import com.secondhand.entity.Favorite;
import com.secondhand.entity.Product;
import com.secondhand.entity.User;
import com.secondhand.entity.UserBehavior;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.FavoriteMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.mapper.UserBehaviorMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.ProductService;
import com.secondhand.util.UserContext;
import com.secondhand.vo.PageVO;
import com.secondhand.vo.ProductDetailVO;
import com.secondhand.vo.ProductListVO;
import com.secondhand.vo.SellerBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品模块实现（读）。
 *
 * <p>本类反复出现的两条原则：
 *
 * <p><b>1. 不把实体直接返回。</b>出参一律是 VO，
 * 这样"列表里带不带联系方式"这种隐私问题在类型层面就被挡住了 ——
 * {@link ProductListVO} 里压根没有 wechat / qq 字段，想漏也漏不出去。
 *
 * <p><b>2. 不在循环里查库。</b>拿到 20 条商品后，分类名和卖家昵称
 * 都是"一次批量查出来，再在内存里对上号"，而不是每条商品查两次
 * （那样 20 条就是 40 次额外查询，叫 N+1 问题）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final FavoriteMapper favoriteMapper;
    private final UserBehaviorMapper userBehaviorMapper;
    private final ObjectMapper objectMapper;

    // ==================================================================
    //  列表
    // ==================================================================
    @Override
    public PageVO<ProductListVO> listProducts(ProductQueryDTO query) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        // ① 状态：游客只看得到「在售」和「已售出」。
        //    待审核 / 已下架 / 已拒绝三种状态任何人都不能从列表里看到。
        wrapper.in(Product::getStatus,
                ProductStatus.ON_SALE.getCode(),
                ProductStatus.SOLD.getCode());

        // ② 关键词：标题 / 描述 / 品牌任意一处包含即可。
        //    注意必须用 and(...) 把这三个 or 包起来 ——
        //    否则 or 会和前面的 status 条件平级，变成"只要包含关键词就返回"，
        //    连待审核的商品都会被搜出来（这是极常见的 SQL 拼接事故）。
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Product::getTitle, keyword)
                    .or().like(Product::getDescription, keyword)
                    .or().like(Product::getBrand, keyword));
        }

        // ③ 分类：传一级分类时要把它下面的所有二级分类一起算上，
        //    因为商品都挂在二级分类上。
        if (query.getCategoryId() != null) {
            wrapper.in(Product::getCategoryId, expandCategoryIds(query.getCategoryId()));
        }

        // ④ 价格区间
        if (query.getMinPrice() != null) {
            wrapper.ge(Product::getPrice, query.getMinPrice());
        }
        if (query.getMaxPrice() != null) {
            wrapper.le(Product::getPrice, query.getMaxPrice());
        }

        // ⑤ 交易地点（模糊）与成色（精确）
        if (StringUtils.hasText(query.getTradePlace())) {
            wrapper.like(Product::getTradePlace, query.getTradePlace().trim());
        }
        if (StringUtils.hasText(query.getConditionLevel())) {
            wrapper.eq(Product::getConditionLevel, query.getConditionLevel().trim());
        }

        // ⑥ 排序
        applySort(wrapper, query.getSort());

        // ⑦ 分页查询 —— 这里能生效的前提是 S2 注册了分页插件，
        //    否则 selectPage 会静默返回全表。
        Page<Product> page = new Page<>(query.getPage(), query.getSize());
        IPage<Product> result = productMapper.selectPage(page, wrapper);

        // ⑧ 批量补齐分类名和卖家昵称（两次查询，不是 2N 次）
        Map<Integer, Category> categoryMap = batchLoadCategories(result.getRecords());
        Map<Integer, User> sellerMap = batchLoadSellers(result.getRecords());

        return PageVO.of(result, p -> toListVO(p, categoryMap, sellerMap));
    }

    // ==================================================================
    //  详情
    // ==================================================================
    @Override
    public ProductDetailVO getDetail(Integer id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            // 参考项目的 bug：这里没有 null 判断，商品不存在直接 NPE → 前端收到 500
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }

        Integer currentUserId = UserContext.getCurrentUserId();
        boolean isAdmin = UserContext.isAdmin();
        boolean isOwner = currentUserId != null && currentUserId.equals(product.getSellerId());

        // 待审核 / 已下架 / 已拒绝的商品：只有所有者本人和管理员能看。
        // 对外一律返回 404（而不是 403）—— 不告诉陌生人"这个 id 确实存在商品"。
        if (!ProductStatus.isVisibleInList(product.getStatus()) && !isOwner && !isAdmin) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }

        // 浏览量 +1（SQL 原子自增）。
        // ⚠️ 这是本接口独有的副作用，别的接口调用 getProductById 不会触发。
        productMapper.incrementViewCount(id);

        // 浏览行为埋点：登录了、而且不是自己的商品才记。
        // 埋点失败不影响主流程（BR-B08），所以这里是"吞掉异常 + 打日志"。
        if (currentUserId != null && !isOwner) {
            recordBehavior(currentUserId, id, BehaviorType.VIEW);
        }

        // 卖家和分类各查一次，后面几处复用（不要在同一个方法里反复查同一个对象）
        User seller = userMapper.selectById(product.getSellerId());

        ProductDetailVO vo = new ProductDetailVO();
        fillCommonFields(vo, product, categoryMapper.selectById(product.getCategoryId()), seller);

        vo.setDescription(product.getDescription());
        vo.setUpdateTime(product.getUpdateTime());

        // 查询结果里的 viewCount 是自增【前】的值，这里 +1 才是用户实际看到的这次访问
        vo.setViewCount((product.getViewCount() == null ? 0 : product.getViewCount()) + 1);

        // 是否已收藏（未登录恒 false）
        vo.setIsFavorited(currentUserId != null && isFavorited(currentUserId, id));

        // 卖家信息（只有昵称和头像，没有联系方式）
        vo.setSeller(buildSellerBrief(seller));

        // 能否联系卖家
        applyContactRule(vo, product, currentUserId, isOwner, seller);

        return vo;
    }

    @Override
    public Product getProductById(Integer id) {
        return productMapper.selectById(id);
    }

    // ==================================================================
    //  canContact 规则（接口文档 §5.2 的五种情况）
    // ==================================================================

    /**
     * 计算 {@code canContact} 与 {@code contactHint}。
     *
     * <p>判断顺序有讲究，和接口文档的表格严格对应：
     * <pre>
     *   未登录            → 请先登录后再联系卖家
     *   自己发布的        → 这是你发布的商品
     *   不是"在售"状态    → 该商品已不可交易
     *   卖家没填联系方式  → 卖家尚未填写联系方式
     *   以上都不是        → 可以联系 ✅
     * </pre>
     *
     * <p>把规则放在后端而不是前端：前端只负责"按钮亮不亮"，
     * 真正的拦截在 S6 的接口里还会再做一遍 —— 界面上的禁用永远不算安全措施。
     */
    private void applyContactRule(ProductDetailVO vo, Product product,
                                  Integer currentUserId, boolean isOwner, User seller) {
        if (currentUserId == null) {
            noContact(vo, "请先登录后再联系卖家");
            return;
        }
        if (isOwner) {
            noContact(vo, "这是你发布的商品");
            return;
        }
        if (!Objects.equals(product.getStatus(), ProductStatus.ON_SALE.getCode())) {
            noContact(vo, "该商品已不可交易");
            return;
        }

        boolean sellerHasContact = seller != null
                && (StringUtils.hasText(seller.getWechat()) || StringUtils.hasText(seller.getQq()));
        if (!sellerHasContact) {
            noContact(vo, "卖家尚未填写联系方式");
            return;
        }

        vo.setCanContact(true);
        vo.setContactHint(null);
    }

    private void noContact(ProductDetailVO vo, String hint) {
        vo.setCanContact(false);
        vo.setContactHint(hint);
    }

    // ==================================================================
    //  内部工具
    // ==================================================================

    /** 把一级分类展开成"它自己 + 它的所有子分类"的 id 列表 */
    private List<Integer> expandCategoryIds(Integer categoryId) {
        List<Integer> ids = new ArrayList<>();
        ids.add(categoryId);

        Category category = categoryMapper.selectById(categoryId);
        // 只有一级分类（parentId 为空）才需要展开；二级分类直接用它自己
        if (category != null && category.getParentId() == null) {
            List<Category> children = categoryMapper.selectList(
                    new LambdaQueryWrapper<Category>().eq(Category::getParentId, categoryId));
            children.forEach(child -> ids.add(child.getId()));
        }
        return ids;
    }

    /** 排序规则 */
    private void applySort(LambdaQueryWrapper<Product> wrapper, String sort) {
        String key = (sort == null) ? "newest" : sort;
        switch (key) {
            case "priceAsc" -> wrapper.orderByAsc(Product::getPrice);
            case "priceDesc" -> wrapper.orderByDesc(Product::getPrice);
            // "最多人想要"直接用冗余字段 favorite_count，省一次 JOIN
            case "hot" -> wrapper.orderByDesc(Product::getFavoriteCount);
            default -> wrapper.orderByDesc(Product::getCreateTime).orderByDesc(Product::getId);
        }
    }

    /** 一次查出列表里涉及的所有分类 */
    private Map<Integer, Category> batchLoadCategories(List<Product> products) {
        Set<Integer> ids = products.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    /** 一次查出列表里涉及的所有卖家 */
    private Map<Integer, User> batchLoadSellers(List<Product> products) {
        Set<Integer> ids = products.stream()
                .map(Product::getSellerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private ProductListVO toListVO(Product p, Map<Integer, Category> categoryMap, Map<Integer, User> sellerMap) {
        ProductListVO vo = new ProductListVO();
        fillCommonFields(vo, p, categoryMap.get(p.getCategoryId()), sellerMap.get(p.getSellerId()));
        return vo;
    }

    /** 列表项和详情共用的字段 */
    private void fillCommonFields(ProductListVO vo, Product p, Category category, User seller) {
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setPrice(p.getPrice());
        vo.setOriginalPrice(p.getOriginalPrice());
        vo.setConditionLevel(p.getConditionLevel());
        vo.setImages(parseImages(p.getImages()));
        vo.setBrand(p.getBrand());
        vo.setCategoryId(p.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getName());
        vo.setTradePlace(p.getTradePlace());
        vo.setStatus(p.getStatus());
        vo.setStatusText(ProductStatus.textOf(p.getStatus()));
        vo.setViewCount(p.getViewCount());
        vo.setFavoriteCount(p.getFavoriteCount());
        vo.setSellerId(p.getSellerId());
        vo.setSellerNickname(seller == null ? null : seller.getNickname());
        vo.setSellerAvatar(seller == null ? null : seller.getAvatar());
        vo.setCreateTime(p.getCreateTime());
    }

    /**
     * 把数据库里的 JSON 字符串解析成数组。
     *
     * <p>解析失败不能让接口挂掉 —— 返回空数组即可，
     * 前端顶多显示一个占位图，总比整页 500 好。
     */
    private List<String> parseImages(String imagesJson) {
        if (!StringUtils.hasText(imagesJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("商品图片 JSON 解析失败，已按空数组处理：{}", imagesJson);
            return List.of();
        }
    }

    private boolean isFavorited(Integer userId, Integer productId) {
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        return count != null && count > 0;
    }

    private SellerBriefVO buildSellerBrief(User seller) {
        if (seller == null) {
            return null;
        }
        SellerBriefVO vo = new SellerBriefVO();
        vo.setId(seller.getId());
        vo.setNickname(seller.getNickname());
        vo.setAvatar(seller.getAvatar());
        vo.setOnSaleCount(countBySeller(seller.getId(), ProductStatus.ON_SALE.getCode()));
        vo.setSoldCount(countBySeller(seller.getId(), ProductStatus.SOLD.getCode()));
        return vo;
    }

    private long countBySeller(Integer sellerId, int status) {
        Long count = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, sellerId)
                .eq(Product::getStatus, status));
        return count == null ? 0L : count;
    }

    /**
     * 写一条行为流水。
     *
     * <p><b>整个方法被 try-catch 包住是有意的</b>：埋点是"锦上添花"，
     * 用户要的是看商品详情。如果因为埋点表写失败就返回 500，
     * 等于为了让日志完整牺牲了主业务 —— 这个取舍不划算。
     */
    private void recordBehavior(Integer userId, Integer productId, BehaviorType type) {
        try {
            UserBehavior behavior = new UserBehavior();
            behavior.setUserId(userId);
            behavior.setProductId(productId);
            behavior.setBehaviorType(type.getCode());
            userBehaviorMapper.insert(behavior);
        } catch (Exception e) {
            log.warn("行为埋点写入失败（不影响主流程）：userId={}, productId={}, type={}",
                    userId, productId, type.getCode(), e);
        }
    }
}
