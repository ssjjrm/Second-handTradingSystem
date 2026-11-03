package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.common.BusinessException;
import com.secondhand.common.ProductStatus;
import com.secondhand.common.Result;
import com.secondhand.entity.Category;
import com.secondhand.entity.Product;
import com.secondhand.entity.User;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.service.AdminProductService;
import com.secondhand.service.assembler.ProductVoAssembler;
import com.secondhand.util.UserContext;
import com.secondhand.vo.PageVO;
import com.secondhand.vo.ProductAdminVO;
import com.secondhand.vo.SellerBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;

/**
 * 管理后台商品管理实现。
 *
 * <p><b>本类的关键点是"状态流转要用乐观锁"。</b>
 *
 * <p>审核接口最容易写错的地方是：
 * <pre>
 *   Product p = mapper.selectById(id);
 *   if (p.getStatus() == 0) { p.setStatus(1); mapper.updateById(p); }   // ❌
 * </pre>
 * 这中间隔着一次数据库往返，两个管理员同时点"通过"就会都通过
 * （虽然结果看起来一样，但如果一个是"通过"一个是"拒绝"，
 *  最终状态就取决于谁后写入，而且两边都以为自己成功了）。
 *
 * <p>正确写法是把状态判断塞进 WHERE：
 * <pre>
 *   UPDATE product SET status=1 WHERE id=? AND status=0
 * </pre>
 * 影响行数为 0 就说明"这条已经被别人处理过了"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductServiceImpl implements AdminProductService {

    private final ProductMapper productMapper;
    private final ProductVoAssembler assembler;

    @Override
    public PageVO<ProductAdminVO> listPending(Integer page, Integer size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductStatus.PENDING.getCode())
                // 待审列表按提交时间**正序**：先提交的先审，排队才公平
                .orderByAsc(Product::getCreateTime)
                .orderByAsc(Product::getId);

        Page<Product> pageParam = new Page<>(
                (page == null || page < 1) ? 1 : page,
                (size == null || size < 1) ? 10 : Math.min(size, 100));
        IPage<Product> result = productMapper.selectPage(pageParam, wrapper);

        Map<Integer, Category> categoryMap = assembler.loadCategories(result.getRecords());
        Map<Integer, User> sellerMap = assembler.loadSellers(result.getRecords());

        return PageVO.of(result, p -> toAdminVO(p, categoryMap, sellerMap));
    }

    /**
     * 10.4 全部商品：**所有状态**都查得出来（包括待审核、已拒绝、已下架）。
     *
     * <p>和 C 端列表的关键区别：C 端只返回"在售 + 已售出"，
     * 后台必须能看到全部 —— 否则管理员无法排查"某件商品为什么搜不到"
     * （十有八九是还在待审核，或者被拒了）。
     */
    @Override
    public PageVO<ProductAdminVO> listAll(Integer page, Integer size, Integer status, String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                // 条件为 false 时这一句不会拼进 SQL —— 也就是"不传 status 就是全状态"
                .eq(status != null, Product::getStatus, status);

        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // ⚠️ 和 C 端列表同样的坑：三个 or 必须被 and(...) 包起来，
            //    否则 or 会和 status 条件平级，把状态筛选冲掉。
            wrapper.and(w -> w.like(Product::getTitle, kw)
                    .or().like(Product::getDescription, kw)
                    .or().like(Product::getBrand, kw));
        }

        // 后台按"最新发布在前"：管理员最常处理的是刚提交的那批
        wrapper.orderByDesc(Product::getCreateTime).orderByDesc(Product::getId);

        Page<Product> pageParam = new Page<>(
                (page == null || page < 1) ? 1 : page,
                (size == null || size < 1) ? 10 : Math.min(size, 100));
        IPage<Product> result = productMapper.selectPage(pageParam, wrapper);

        Map<Integer, Category> categoryMap = assembler.loadCategories(result.getRecords());
        Map<Integer, User> sellerMap = assembler.loadSellers(result.getRecords());

        return PageVO.of(result, p -> toAdminVO(p, categoryMap, sellerMap));
    }

    /**
     * 10.5 强制下架：status 1 → 3。
     *
     * <p><b>和 C 端 {@code ProductServiceImpl.offShelf} 的区别只有"归属校验"这一处</b>：
     * 那个要先确认"这件商品是你发的"，这个**故意不做**归属校验 ——
     * 管理员的职责就是能下架任何人的违规商品。
     *
     * <p>但"状态必须是 1（在售）"这条校验仍然要做，理由和审核一样：
     * <ul>
     *   <li>已售出的不能改成"已下架" —— 那是篡改历史</li>
     *   <li>已下架的再下架一次没有意义，应该给管理员一个明确的 400</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceOffShelf(Integer productId) {
        // 先确认存在 → 不存在要给 404，而不是笼统的"状态不对"
        requireExists(productId);

        // 乐观锁：把"在售"这个前提写进 WHERE，避免和"卖家刚标记售出"打架
        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .eq(Product::getStatus, ProductStatus.ON_SALE.getCode())
                .set(Product::getStatus, ProductStatus.OFF_SHELF.getCode()));

        if (rows == 0) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "只有在售中的商品才能强制下架");
        }
        log.info("管理员 {} 强制下架商品 id={}",
                UserContext.getCurrentUsername(), productId);
    }

    /**
     * 实体 → 后台列表项。
     *
     * <p>待审列表和全部商品列表用的是同一套字段，所以抽出来共用 ——
     * 否则以后加一个字段（比如"拒绝原因"）要改两处，迟早漏一处。
     */
    private ProductAdminVO toAdminVO(Product p,
                                     Map<Integer, Category> categoryMap,
                                     Map<Integer, User> sellerMap) {
        ProductAdminVO vo = new ProductAdminVO();
        assembler.fillCommon(vo, p, categoryMap.get(p.getCategoryId()), sellerMap.get(p.getSellerId()));
        // 审核要看描述有没有违规，所以列表里也带上
        vo.setDescription(p.getDescription());

        User seller = sellerMap.get(p.getSellerId());
        if (seller != null) {
            SellerBriefVO brief = new SellerBriefVO();
            brief.setId(seller.getId());
            brief.setNickname(seller.getNickname());
            brief.setAvatar(seller.getAvatar());
            // 这里不带"在售数 / 已售数"两个统计 —— 审核员关心的是
            // "这条内容合不合规"，不是这个卖家卖了多少东西。
            // （要看统计请用后台的用户列表 /api/admin/users。）
            vo.setSeller(brief);
        }
        return vo;
    }

    /** 商品必须存在（不存在 → 404） */
    private Product requireExists(Integer productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Integer productId) {
        requirePending(productId);

        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .eq(Product::getStatus, ProductStatus.PENDING.getCode())
                .set(Product::getStatus, ProductStatus.ON_SALE.getCode()));

        if (rows == 0) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "该商品已被其他管理员处理，请刷新后重试");
        }
        log.info("管理员 {} 审核通过商品 id={}", UserContext.getCurrentUsername(), productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Integer productId, String reason) {
        requirePending(productId);

        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .eq(Product::getStatus, ProductStatus.PENDING.getCode())
                .set(Product::getStatus, ProductStatus.REJECTED.getCode()));

        if (rows == 0) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "该商品已被其他管理员处理，请刷新后重试");
        }

        // ⚠️ 拒绝原因只记在日志里，**没有存进数据库**。
        //    因为 5 张表里没有"审核备注"字段，为了不给课程项目增加表，
        //    这里的取舍是：管理员能在日志里追溯，卖家只看到"已拒绝"状态。
        //    如果以后要展示给卖家，需要给 product 表加一列 reject_reason。
        log.info("管理员 {} 拒绝商品 id={}，原因：{}",
                UserContext.getCurrentUsername(), productId,
                StringUtils.hasText(reason) ? reason : "（未填写）");
    }

    /** 确认商品存在且处于待审核状态 */
    private Product requirePending(Integer productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }
        if (!Objects.equals(product.getStatus(), ProductStatus.PENDING.getCode())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "该商品不处于待审核状态");
        }
        return product;
    }
}
