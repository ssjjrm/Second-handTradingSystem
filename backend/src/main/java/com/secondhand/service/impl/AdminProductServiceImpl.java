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

        return PageVO.of(result, p -> {
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
                // （S9 做用户列表时再补真实统计。）
                vo.setSeller(brief);
            }
            return vo;
        });
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
