package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.common.BehaviorType;
import com.secondhand.common.BusinessException;
import com.secondhand.common.ProductStatus;
import com.secondhand.common.Result;
import com.secondhand.dto.ProductCreateDTO;
import com.secondhand.dto.ProductQueryDTO;
import com.secondhand.dto.ProductUpdateDTO;
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
import com.secondhand.service.BehaviorService;
import com.secondhand.service.assembler.ProductVoAssembler;
import com.secondhand.util.UserContext;
import com.secondhand.vo.ContactVO;
import com.secondhand.vo.PageVO;
import com.secondhand.vo.ProductDetailVO;
import com.secondhand.vo.ProductListVO;
import com.secondhand.vo.ProductMineVO;
import com.secondhand.vo.SellerBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** VO 组装（补分类名、卖家昵称、解析图片 JSON）—— 和后台共用同一份逻辑 */
    private final ProductVoAssembler assembler;

    /** 行为埋点统一入口（内部已保证：未登录跳过、浏览自己商品跳过、失败不抛异常） */
    private final BehaviorService behaviorService;

    // ==================================================================
    //  列表
    // ==================================================================
    @Override
    public PageVO<ProductListVO> listProducts(ProductQueryDTO query) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        // ① 状态：游客只看得到「在售」和「已售出」。
        //    待审核 / 已下架 / 已拒绝三种状态任何人都不能从列表里看到。
        // in 方法是 MyBatis-Plus 中用于构建查询条件的基本方法之一，它用于设置单个字段的 IN 条件，即字段的值在给定的集合中。
        wrapper.in(Product::getStatus,
                ProductStatus.ON_SALE.getCode(),
                ProductStatus.SOLD.getCode());

        // ② 关键词：标题 / 描述 / 品牌任意一处包含即可。
        //    注意必须用 and(...) 把这三个 or 包起来 ——
        //    否则 or 会和前面的 status 条件平级，变成"只要包含关键词就返回"，
        //    连待审核的商品都会被搜出来（这是极常见的 SQL 拼接事故）。
        // and 方法用于在查询条件中添加 AND 逻辑。
        // or 方法用于在查询条件中添加 OR 逻辑。
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            //like 设置单个字段的LIKE条件。
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
            wrapper.ge(Product::getPrice, query.getMinPrice());//ge 设置单个字段的大于等于条件。
        }
        if (query.getMaxPrice() != null) {
            wrapper.le(Product::getPrice, query.getMaxPrice());
        }

        // ⑤ 交易地点（模糊）与成色（精确）
        if (StringUtils.hasText(query.getTradePlace())) {
            //like 设置单个字段的LIKE条件。
            wrapper.like(Product::getTradePlace, query.getTradePlace().trim());
        }
        if (StringUtils.hasText(query.getConditionLevel())) {
            // eq 设置单个字段的相等条件。
            wrapper.eq(Product::getConditionLevel, query.getConditionLevel().trim());
        }

        // ⑥ 排序
        applySort(wrapper, query.getSort());

        // ⑦ 分页查询 —— 这里能生效的前提是 S2 注册了分页插件，
        //    否则 selectPage 会静默返回全表。
        Page<Product> page = new Page<>(query.getPage(), query.getSize());
        IPage<Product> result = productMapper.selectPage(page, wrapper);

        // ⑧ 批量补齐分类名和卖家昵称（两次查询，不是 2N 次）
        Map<Integer, Category> categoryMap = assembler.loadCategories(result.getRecords());
        Map<Integer, User> sellerMap = assembler.loadSellers(result.getRecords());

        return PageVO.of(result, p -> {
            ProductListVO vo = new ProductListVO();
            assembler.fillCommon(vo, p, categoryMap.get(p.getCategoryId()), sellerMap.get(p.getSellerId()));
            return vo;
        });
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

        // 浏览行为埋点：交给 BehaviorService 统一处理 ——
        // 它内部会自己判断"未登录跳过"和"浏览自己的商品跳过"，
        // 而且写入失败只打日志、不抛异常（BR-B08），不会影响这次详情请求。
        behaviorService.recordView(id);

        // 卖家和分类各查一次，后面几处复用（不要在同一个方法里反复查同一个对象）
        User seller = userMapper.selectById(product.getSellerId());

        ProductDetailVO vo = new ProductDetailVO();
        assembler.fillCommon(vo, product, categoryMapper.selectById(product.getCategoryId()), seller);

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
    //  发布 / 编辑 / 下架 / 删除 / 我发布的（S5）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer publish(ProductCreateDTO dto) {
        Integer userId = UserContext.requireUserId();
        User seller = userMapper.selectById(userId);
        if (seller == null) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "用户不存在");
        }

        // ★ 业务规则 BR-C05：卖家必须先填联系方式。
        //   平台只做信息撮合，买家拿不到微信/QQ 就等于这件商品根本没法成交 ——
        //   与其让他发完再被买家投诉"联系不上"，不如在发布这一步就拦住。
        if (!StringUtils.hasText(seller.getWechat()) && !StringUtils.hasText(seller.getQq())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "请先完善微信号或QQ号，便于买家联系你");
        }

        // 必须挂在二级分类上（一级分类太笼统，买家筛不出来）
        checkCategoryIsSecondary(dto.getCategoryId());

        // 原价是"参考价"，低于现价没有意义
        if (dto.getOriginalPrice() != null && dto.getOriginalPrice().compareTo(dto.getPrice()) < 0) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "原价不能低于现价");
        }

        Product product = new Product();
        product.setTitle(dto.getTitle().trim());
        product.setDescription(dto.getDescription());
        product.setCategoryId(dto.getCategoryId());
        product.setConditionLevel(dto.getConditionLevel());
        product.setPrice(dto.getPrice());
        product.setOriginalPrice(dto.getOriginalPrice());
        product.setImages(writeImages(dto.getImages()));
        product.setBrand(dto.getBrand());
        product.setTradePlace(dto.getTradePlace());
        product.setSellerId(userId);
        // 新发布的商品一律「待审核」，绝不会直接出现在前台列表里
        product.setStatus(ProductStatus.PENDING.getCode());
        product.setViewCount(0);
        product.setFavoriteCount(0);

        productMapper.insert(product);
        log.info("用户 {} 发布商品 id={}，等待管理员审核", seller.getUsername(), product.getId());
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(Integer id, ProductUpdateDTO dto) {
        Product product = requireOwnedProduct(id);

        // 只有「在售」和「已下架」能编辑。
        // 待审核的等审核结果，已拒绝的应该重新发布，已售出的是历史记录不能改。
        Integer status = product.getStatus();
        if (Objects.equals(status, ProductStatus.SOLD.getCode())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "已售出的商品不能编辑");
        }
        if (!Objects.equals(status, ProductStatus.ON_SALE.getCode())
                && !Objects.equals(status, ProductStatus.OFF_SHELF.getCode())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "当前状态的商品不能编辑");
        }

        if (dto.getCategoryId() != null) {
            checkCategoryIsSecondary(dto.getCategoryId());
        }

        // 价格校验要用"改完之后的最终值"：可能只改了原价，也可能只改了现价
        BigDecimal finalPrice = dto.getPrice() != null ? dto.getPrice() : product.getPrice();
        BigDecimal finalOriginalPrice = dto.getOriginalPrice() != null
                ? dto.getOriginalPrice() : product.getOriginalPrice();
        if (finalOriginalPrice != null && finalOriginalPrice.compareTo(finalPrice) < 0) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "原价不能低于现价");
        }

        // ⚠️ 全部字段都没传的话，下面拼出来的 SQL 会是 "UPDATE product SET WHERE id=?"，
        //    直接语法错误。所以先挡一道。
        if (dto.getTitle() == null && dto.getDescription() == null && dto.getCategoryId() == null
                && dto.getConditionLevel() == null && dto.getPrice() == null
                && dto.getOriginalPrice() == null && dto.getImages() == null
                && dto.getBrand() == null && dto.getTradePlace() == null) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "没有需要修改的内容");
        }

        // ⚠️ 用 LambdaUpdateWrapper 的「条件 set」而不是 updateById：
        //    updateById 默认跳过 null 字段，就没办法把品牌、原价这类字段**清空**。
        //    set(condition, column, value) 的第一参数为 false 时，该列不会出现在 SET 里。
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .set(dto.getTitle() != null, Product::getTitle,
                        dto.getTitle() == null ? null : dto.getTitle().trim())
                .set(dto.getDescription() != null, Product::getDescription, dto.getDescription())
                .set(dto.getCategoryId() != null, Product::getCategoryId, dto.getCategoryId())
                .set(dto.getConditionLevel() != null, Product::getConditionLevel, dto.getConditionLevel())
                .set(dto.getPrice() != null, Product::getPrice, dto.getPrice())
                .set(dto.getOriginalPrice() != null, Product::getOriginalPrice, dto.getOriginalPrice())
                .set(dto.getImages() != null, Product::getImages,
                        dto.getImages() == null ? null : writeImages(dto.getImages()))
                .set(dto.getBrand() != null, Product::getBrand, dto.getBrand())
                .set(dto.getTradePlace() != null, Product::getTradePlace, dto.getTradePlace()));

        // 注意：这里**不动 status** —— 编辑一个在售商品不会让它退回待审核。
        log.info("商品 id={} 已被所有者编辑", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offShelf(Integer id) {
        Product product = requireOwnedProduct(id);

        if (!Objects.equals(product.getStatus(), ProductStatus.ON_SALE.getCode())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "只有在售中的商品才能下架");
        }

        // 和「标记售出」同一套乐观锁写法：把状态判断写进 WHERE，
        // 让"检查"和"修改"在一条 SQL 里原子完成，并发下也不会两个请求都成功。
        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .eq(Product::getStatus, ProductStatus.ON_SALE.getCode())
                .set(Product::getStatus, ProductStatus.OFF_SHELF.getCode()));

        if (rows == 0) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "商品状态已变化，请刷新后重试");
        }
        log.info("商品 id={} 已下架", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Integer id) {
        Product product = requireOwnedProduct(id);

        if (Objects.equals(product.getStatus(), ProductStatus.SOLD.getCode())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "已售出的商品不能删除");
        }

        // 只删 product 一行就够了 —— favorite 和 user_behavior 上的外键是
        // ON DELETE CASCADE，数据库会自动把相关的收藏和行为记录一起清掉。
        productMapper.deleteById(id);
        log.info("商品 id={} 已删除（相关收藏与行为记录由外键级联清理）", id);
    }

    @Override
    public PageVO<ProductMineVO> listMine(Integer page, Integer size, Integer status) {
        Integer userId = UserContext.requireUserId();

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, userId)
                .eq(status != null, Product::getStatus, status)
                .orderByDesc(Product::getCreateTime)
                .orderByDesc(Product::getId);

        Page<Product> pageParam = new Page<>(
                (page == null || page < 1) ? 1 : page,
                (size == null || size < 1) ? 20 : Math.min(size, 100));
        IPage<Product> result = productMapper.selectPage(pageParam, wrapper);

        // 咨询数一次算完（不是每条商品查一次）
        Map<Integer, Long> contactMap = countContacts(result.getRecords());
        Map<Integer, Category> categoryMap = assembler.loadCategories(result.getRecords());
        Map<Integer, User> sellerMap = assembler.loadSellers(result.getRecords());

        return PageVO.of(result, p -> {
            ProductMineVO vo = new ProductMineVO();
            assembler.fillCommon(vo, p, categoryMap.get(p.getCategoryId()), sellerMap.get(p.getSellerId()));

            long contacts = contactMap.getOrDefault(p.getId(), 0L);
            vo.setContactCount(contacts);
            // ⭐ needAttention：在售 且 有人咨询过 → 提醒卖家"有人想要，成交后记得标记售出"
            vo.setNeedAttention(
                    Objects.equals(p.getStatus(), ProductStatus.ON_SALE.getCode()) && contacts > 0);
            return vo;
        });
    }

    // ==================================================================
    //  交易功能（S6）—— 本项目最核心的两个方法
    // ==================================================================

    @Override
    public ContactVO getContact(Integer productId) {
        // ① 登录校验。
        //    正常情况下 JwtInterceptor 已经挡过一道（contact 不在白名单里），
        //    这里再查一次是为了"Service 被直接调用时也安全"，
        //    顺带能给出比拦截器更贴切的提示文案。
        Integer currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "请先登录后再联系卖家");
        }

        // ② 商品必须存在
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }

        // ③ 不能联系自己发布的商品
        if (currentUserId.equals(product.getSellerId())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "不能联系自己发布的商品");
        }

        // ④ 商品必须在售。已售出 / 已下架 / 待审核的都不能联系 ——
        //    否则会出现"买家联系了卖家，才发现东西早就卖了"的糟糕体验。
        if (!Objects.equals(product.getStatus(), ProductStatus.ON_SALE.getCode())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "该商品已不可交易");
        }

        // ⑤ 卖家必须填了联系方式
        User seller = userMapper.selectById(product.getSellerId());
        if (seller == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "卖家不存在");
        }
        if (!StringUtils.hasText(seller.getWechat()) && !StringUtils.hasText(seller.getQq())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "卖家尚未填写联系方式");
        }

        // ⑥ 记录咨询行为。
        //    ⚠️ 埋点失败绝不能影响主流程（BR-B08）——
        //    用户要的是卖家的微信，不是"日志写成功了没有"。
        //    record() 内部已经 try-catch 吞掉异常，这里不用再包一层。
        behaviorService.record(currentUserId, productId, BehaviorType.CONTACT);

        // ⑦ 组装返回
        ContactVO vo = new ContactVO();
        vo.setProductId(product.getId());
        vo.setProductTitle(product.getTitle());
        vo.setSellerId(seller.getId());
        vo.setSellerNickname(seller.getNickname());
        vo.setSellerAvatar(seller.getAvatar());
        vo.setWechat(seller.getWechat());
        vo.setQq(seller.getQq());
        vo.setTradePlace(product.getTradePlace());

        log.info("用户 id={} 获取了商品 id={} 的联系方式", currentUserId, productId);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSold(Integer productId) {
        // 先确认是本人发布的（不是本人 → 403「无权操作此商品」）
        requireOwnedProduct(productId);

        // ⚠️⚠️ 这是本项目**唯一的并发点**，写法必须小心。
        //
        // ❌ 错误写法（参考项目就这么干的）：
        //      Product p = selectById(id);
        //      if (p.getStatus() == 1) { p.setStatus(2); updateById(p); }
        //    检查和修改之间隔着一次数据库往返，两个并发请求会都通过检查、
        //    都执行更新 —— 虽然最终 status 都是 2 看不出问题，
        //    但同样的模式用在"审核"上（一个通过、一个拒绝）就会真出乱子。
        //
        // ✅ 正确写法：把状态判断写进 WHERE，让数据库在一个原子操作里
        //    完成「检查 + 修改」。影响行数为 0 就说明状态已经不是在售了。
        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .eq(Product::getStatus, ProductStatus.ON_SALE.getCode())
                .set(Product::getStatus, ProductStatus.SOLD.getCode()));

        if (rows == 0) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "商品已非在售状态");
        }

        // 不可逆（BR-T06）：本项目没有任何"改回在售"的接口。
        // 想重新卖就重新发布一件新商品 —— 这样历史数据才不会被来回篡改。
        log.info("商品 id={} 已标记为售出（不可逆）", productId);
    }

    /**
     * 一次查出"这些商品各有多少人咨询过"。
     *
     * <p>用 SQL 的 {@code GROUP BY product_id} + {@code COUNT(DISTINCT user_id)} 一次算完，
     * 而不是在循环里每条商品查一次（N+1：20 条商品就是 20 次额外查询）。
     *
     * <p>去重是必须的：同一个人点两次"获取联系方式"只应该算 1 个人想要。
     */
    private Map<Integer, Long> countContacts(List<Product> products) {
        if (products.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Integer> productIds = products.stream().map(Product::getId).collect(Collectors.toList());

        QueryWrapper<UserBehavior> wrapper = new QueryWrapper<>();
        wrapper.select("product_id", "COUNT(DISTINCT user_id) AS contact_count")
                .eq("behavior_type", BehaviorType.CONTACT.getCode())
                .in("product_id", productIds)
                .groupBy("product_id");

        List<Map<String, Object>> rows = userBehaviorMapper.selectMaps(wrapper);
        Map<Integer, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            // ⚠️ selectMaps 返回的 Map，key 的大小写由数据库驱动决定
            //    （MySQL 通常保持原样，某些库会全大写），所以两种都试一下。
            Object productId = pickIgnoreCase(row, "product_id");
            Object count = pickIgnoreCase(row, "contact_count");
            if (productId != null && count != null) {
                map.put(((Number) productId).intValue(), ((Number) count).longValue());
            }
        }
        return map;
    }

    private Object pickIgnoreCase(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(key.toUpperCase());
    }

    /** 取商品并校验"是当前登录用户发布的"，否则抛异常 */
    private Product requireOwnedProduct(Integer id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }
        Integer userId = UserContext.requireUserId();
        if (!userId.equals(product.getSellerId())) {
            // 这里用 403 而不是 404：用户是登录的，商品也确实存在，
            // 只是不归他 —— 属于"没权限"，不是"资源不存在"。
            throw new BusinessException(Result.CODE_FORBIDDEN, "无权操作此商品");
        }
        return product;
    }

    /** 校验分类必须是二级分类（商品挂到一级分类上，买家就筛不出来了） */
    private void checkCategoryIsSecondary(Integer categoryId) {
        if (categoryId == null) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "请选择分类");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "分类不存在");
        }
        if (category.getParentId() == null) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "请选择具体的二级分类");
        }
    }

    /**
     * 图片数组 → JSON 字符串。
     *
     * <p>空数组存 {@code null} 而不是 {@code "[]"}：
     * 让"没有图片"在数据库里只有一种表示，省得前端要判断两种空。
     */
    private String writeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException e) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "图片地址格式不正确");
        }
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
}
