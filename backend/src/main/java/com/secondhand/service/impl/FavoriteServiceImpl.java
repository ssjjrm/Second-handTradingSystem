package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.common.BehaviorType;
import com.secondhand.common.BusinessException;
import com.secondhand.common.ProductStatus;
import com.secondhand.common.Result;
import com.secondhand.entity.Category;
import com.secondhand.entity.Favorite;
import com.secondhand.entity.Product;
import com.secondhand.entity.User;
import com.secondhand.mapper.FavoriteMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.service.BehaviorService;
import com.secondhand.service.FavoriteService;
import com.secondhand.service.assembler.ProductVoAssembler;
import com.secondhand.util.UserContext;
import com.secondhand.vo.FavoriteCheckVO;
import com.secondhand.vo.FavoriteItemVO;
import com.secondhand.vo.PageVO;
import com.secondhand.vo.ProductListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收藏模块实现。
 *
 * <p><b>本类的核心难点是"幂等 + 计数一致"。</b>
 *
 * <p>收藏涉及两处写入：{@code favorite} 表插一行、{@code product.favorite_count} 加一。
 * 这两步必须在**同一个事务**里，否则会出现"收藏记录有了但计数没加"的诡异状态。
 *
 * <p>重复收藏的处理有三层防线：
 * <ol>
 *   <li>先查一次是否存在（挡住 99% 的重复请求）</li>
 *   <li>真的并发撞上了，靠数据库唯一键 {@code uk_user_product} 拒绝</li>
 *   <li>捕获 {@link DuplicateKeyException}，当成"已收藏"处理，不报错也不重复 +1</li>
 * </ol>
 * 只有"先查"这一层是不够的 —— 两个请求可能同时查到"不存在"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;
    private final BehaviorService behaviorService;
    private final ProductVoAssembler assembler;

    // ==================================================================
    //  收藏 / 取消收藏
    // ==================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addFavorite(Integer productId) {
        Integer userId = UserContext.requireUserId();

        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }
        if (userId.equals(product.getSellerId())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "不能收藏自己的商品");
        }
        if (!Objects.equals(product.getStatus(), ProductStatus.ON_SALE.getCode())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "该商品已不可交易");
        }

        // 第 1 层：先查一次（绝大多数重复请求在这里就被挡住了）
        Long exists = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        if (exists != null && exists > 0) {
            // 已经收藏过了 → 幂等返回，**不重复 +1**
            log.debug("用户 {} 重复收藏商品 {}，已忽略", userId, productId);
            return currentFavoriteCount(productId);
        }

        // 第 2、3 层：插入，撞唯一键就当"已收藏"
        try {
            Favorite favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setProductId(productId);
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            //DuplicateKeyException 是Spring对数据库唯一约束冲突的标准化封装
            //当尝试插入或更新数据，违反了主键或唯一索引约束时就会抛出。
            // 并发下另一个请求刚插进去了 —— 这不是错误，用户想要的结果已经达成了
            log.debug("并发重复收藏，按已收藏处理：userId={}, productId={}", userId, productId);
            return currentFavoriteCount(productId);
        }

        // 计数 +1（SQL 原子自增）
        productMapper.incrementFavoriteCount(productId);

        // 埋点：type=2 收藏
        behaviorService.record(userId, productId, BehaviorType.FAVORITE);

        return currentFavoriteCount(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long removeFavorite(Integer productId) {
        Integer userId = UserContext.requireUserId();

        // 只有真的删掉了记录，才减计数 —— 这样"没收藏过就取消"也是幂等的
        int rows = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));

        if (rows > 0) {
            // 减计数时 SQL 里有 GREATEST(..., 0) 兜底，永远不会变成负数
            productMapper.decrementFavoriteCount(productId);
            // 埋点：type=4 取消收藏
            behaviorService.record(userId, productId, BehaviorType.UNFAVORITE);
            log.debug("用户 {} 取消收藏商品 {}", userId, productId);
        }

        return currentFavoriteCount(productId);
    }

    // ==================================================================
    //  我的收藏
    // ==================================================================
    @Override
    public PageVO<FavoriteItemVO> listMine(Integer page, Integer size) {
        Integer userId = UserContext.requireUserId();

        Page<Favorite> pageParam = new Page<>(
                (page == null || page < 1) ? 1 : page,
                (size == null || size < 1) ? 20 : Math.min(size, 100));

        // ⚠️ 这里**不过滤商品状态** —— 已售出/已下架的商品也要显示（BR-F07），
        //    让用户知道"我想要的东西已经被别人买走了"，由他自己决定要不要移除。
        IPage<Favorite> result = favoriteMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreateTime)
                        .orderByDesc(Favorite::getId));

        List<Favorite> favorites = result.getRecords();
        if (favorites.isEmpty()) {
            return PageVO.of(result, f -> new FavoriteItemVO());
        }

        // 一次查出这批收藏涉及的所有商品（不是循环里一条条查）
        List<Integer> productIds = favorites.stream()
                .map(Favorite::getProductId)
                .collect(Collectors.toList());
        List<Product> products = productMapper.selectBatchIds(productIds);

        Map<Integer, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Integer, Category> categoryMap = assembler.loadCategories(products);
        Map<Integer, User> sellerMap = assembler.loadSellers(products);

        return PageVO.of(result, favorite -> {
            FavoriteItemVO vo = new FavoriteItemVO();
            vo.setFavoriteId(favorite.getId());
            vo.setFavoriteTime(favorite.getCreateTime());

            Product product = productMap.get(favorite.getProductId());
            if (product != null) {
                ProductListVO productVO = new ProductListVO();
                assembler.fillCommon(productVO, product,
                        categoryMap.get(product.getCategoryId()),
                        sellerMap.get(product.getSellerId()));
                vo.setProduct(productVO);
            }
            // product 为 null 理论上不会发生：商品被删时外键 CASCADE 会连收藏一起删掉
            return vo;
        });
    }

    // ==================================================================
    //  检查是否已收藏
    // ==================================================================
    @Override
    public FavoriteCheckVO check(Integer productId) {
        Integer userId = UserContext.requireUserId();

        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));

        FavoriteCheckVO vo = new FavoriteCheckVO();
        vo.setFavorited(count != null && count > 0);
        vo.setFavoriteCount(currentFavoriteCount(productId));
        return vo;
    }

    /**
     * 从数据库读当前的「想要」数。
     *
     * <p>为什么操作完要重新读一次，而不是"进方法时的值 + 1"？
     * 因为这个数字是**所有人**共同累加的结果，不是当前用户视角的增量。
     * 重新读才能拿到别人刚刚加上的那部分。
     */
    private long currentFavoriteCount(Integer productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || product.getFavoriteCount() == null) {
            return 0L;
        }
        return product.getFavoriteCount();
    }
}
