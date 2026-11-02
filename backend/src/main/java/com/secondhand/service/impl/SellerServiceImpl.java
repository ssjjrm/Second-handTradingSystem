package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.common.BusinessException;
import com.secondhand.common.ProductStatus;
import com.secondhand.common.Result;
import com.secondhand.entity.Category;
import com.secondhand.entity.Product;
import com.secondhand.entity.User;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.SellerService;
import com.secondhand.service.assembler.ProductVoAssembler;
import com.secondhand.vo.PageVO;
import com.secondhand.vo.ProductListVO;
import com.secondhand.vo.SellerProfileVO;
import com.secondhand.vo.SellerStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 卖家模块实现（全部只读、全部公开）。
 *
 * <p>这个类存在的意义：让买家在"联系卖家"之前能先评估一下这个人 ——
 * 注册多久了、在卖几件、卖掉过几件、收到多少"想要"。
 * 这些数字是"信息撮合"模式下唯一能建立的信任信号（没有订单和评价体系）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerServiceImpl implements SellerService {

    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final ProductVoAssembler assembler;

    @Override
    public SellerProfileVO getProfile(Integer sellerId) {
        User seller = userMapper.selectById(sellerId);
        if (seller == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "卖家不存在");
        }

        SellerProfileVO vo = new SellerProfileVO();
        vo.setId(seller.getId());
        vo.setNickname(seller.getNickname());
        vo.setAvatar(seller.getAvatar());
        vo.setCreateTime(seller.getCreateTime());
        // 只告诉"填没填"，不透露填了什么
        vo.setHasContact(StringUtils.hasText(seller.getWechat()) || StringUtils.hasText(seller.getQq()));

        SellerStatsVO stats = new SellerStatsVO();
        stats.setOnSaleCount(countByStatus(sellerId, ProductStatus.ON_SALE.getCode()));
        stats.setSoldCount(countByStatus(sellerId, ProductStatus.SOLD.getCode()));
        stats.setFavoriteReceivedCount(sumFavoriteReceived(sellerId));
        vo.setStats(stats);

        return vo;
    }

    @Override
    public PageVO<ProductListVO> listOnSale(Integer sellerId, Integer page, Integer size) {
        // 卖家不存在时返回 404，而不是一个空列表 ——
        // 空列表会让前端以为"这个人一件商品都没在卖"，其实是 id 错了
        User seller = userMapper.selectById(sellerId);
        if (seller == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "卖家不存在");
        }

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, sellerId)
                // 注意：这里**只含在售**，和 C 端商品列表（含已售出）不同 ——
                // 卖家主页是"这个人现在有什么可买"，把已售出混进来会误导买家
                .eq(Product::getStatus, ProductStatus.ON_SALE.getCode())
                .orderByDesc(Product::getCreateTime)
                .orderByDesc(Product::getId);

        Page<Product> pageParam = new Page<>(
                (page == null || page < 1) ? 1 : page,
                (size == null || size < 1) ? 20 : Math.min(size, 100));
        IPage<Product> result = productMapper.selectPage(pageParam, wrapper);

        Map<Integer, Category> categoryMap = assembler.loadCategories(result.getRecords());
        Map<Integer, User> sellerMap = assembler.loadSellers(result.getRecords());

        return PageVO.of(result, p -> {
            ProductListVO vo = new ProductListVO();
            assembler.fillCommon(vo, p, categoryMap.get(p.getCategoryId()), sellerMap.get(p.getSellerId()));
            return vo;
        });
    }

    private long countByStatus(Integer sellerId, int status) {
        Long count = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, sellerId)
                .eq(Product::getStatus, status));
        return count == null ? 0L : count;
    }

    /**
     * 该卖家所有商品的「想要」数之和。
     *
     * <p>用 SQL 的 {@code SUM} 一次算完，而不是把商品全查出来再用 Java 求和 ——
     * 一个卖家几十件商品无所谓，但写法上从一开始就选对更省心。
     */
    private long sumFavoriteReceived(Integer sellerId) {
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(favorite_count), 0)")
                .eq("seller_id", sellerId);

        return productMapper.selectObjs(wrapper).stream()
                .findFirst()
                .map(v -> ((Number) v).longValue())
                .orElse(0L);
    }
}
