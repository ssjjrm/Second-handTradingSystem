package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.secondhand.common.BehaviorType;
import com.secondhand.common.ProductStatus;
import com.secondhand.entity.Favorite;
import com.secondhand.entity.Product;
import com.secondhand.entity.User;
import com.secondhand.entity.UserBehavior;
import com.secondhand.mapper.FavoriteMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.mapper.UserBehaviorMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.AdminStatsService;
import com.secondhand.vo.AdminStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 · 数据统计实现。
 *
 * <p><b>口径说明（很重要，答辩会被问）</b>：
 * <ul>
 *   <li>{@code totalProducts} 是**所有状态**的商品数（含待审核、已下架、已拒绝），
 *       它回答"平台上一共存过多少件"；而 {@code onSaleProducts} + {@code soldProducts}
 *       才是"用户能看到的"。所以这两个数加起来**不等于** {@code totalProducts}，这是对的。</li>
 *   <li>{@code soldProducts} 是卖家自己点的"已售出"数量。
 *       平台不参与交易，无法知道真实成交价，所以**没有"成交额"** ——
 *       这是需求文档 §4.2.5 记录的已知代价，而不是漏做。</li>
 *   <li>"今日"用服务器本地时区的 00:00:00 作为分界。</li>
 * </ul>
 *
 * <p>实现上没有耍花招：10 个数字 = 10 条 {@code COUNT(*)}，
 * 这是后台首页的一次性请求，不是高频接口，没必要为了省几次查询
 * 把 SQL 拼成一条看不懂的大语句。真到了要优化的规模，
 * 再给这些 count 加 Redis 缓存（S12 的思路）才有意义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final FavoriteMapper favoriteMapper;
    private final UserBehaviorMapper userBehaviorMapper;

    @Override
    public AdminStatsVO getStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        AdminStatsVO vo = new AdminStatsVO();

        // ---------- 用户 ----------
        vo.setTotalUsers(countUsers(null, null));
        vo.setTodayNewUsers(countUsers(todayStart, null));

        // ---------- 商品 ----------
        vo.setTotalProducts(countProducts(null, null));
        vo.setOnSaleProducts(countProducts(ProductStatus.ON_SALE.getCode(), null));
        vo.setSoldProducts(countProducts(ProductStatus.SOLD.getCode(), null));
        vo.setPendingProducts(countProducts(ProductStatus.PENDING.getCode(), null));
        vo.setTodayNewProducts(countProducts(null, todayStart));

        // ---------- 收藏 ----------
        Long favorites = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>());
        vo.setTotalFavoriteRecords(favorites == null ? 0L : favorites);

        // ---------- 行为（咨询是本项目的核心转化指标） ----------
        vo.setTotalContactRecords(countContacts(null));
        vo.setTodayContacts(countContacts(todayStart));

        return vo;
    }

    /** 统计用户数；status / since 传 null 表示不限 */
    private Long countUsers(LocalDateTime since, Integer status) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(status != null, User::getStatus, status)
                .ge(since != null, User::getCreateTime, since));
        return count == null ? 0L : count;
    }

    /** 统计商品数；status / since 传 null 表示不限 */
    private Long countProducts(Integer status, LocalDateTime since) {
        Long count = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(status != null, Product::getStatus, status)
                .ge(since != null, Product::getCreateTime, since));
        return count == null ? 0L : count;
    }

    /** 统计咨询次数（behavior_type = 3）；since 传 null 表示不限时间 */
    private Long countContacts(LocalDateTime since) {
        Long count = userBehaviorMapper.selectCount(new LambdaQueryWrapper<UserBehavior>()
                .eq(UserBehavior::getBehaviorType, BehaviorType.CONTACT.getCode())
                .ge(since != null, UserBehavior::getBehaviorTime, since));
        return count == null ? 0L : count;
    }
}
