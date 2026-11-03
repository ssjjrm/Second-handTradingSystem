package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.common.BusinessException;
import com.secondhand.common.ProductStatus;
import com.secondhand.common.Result;
import com.secondhand.entity.Favorite;
import com.secondhand.entity.Product;
import com.secondhand.entity.User;
import com.secondhand.mapper.FavoriteMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.AdminUserService;
import com.secondhand.util.UserContext;
import com.secondhand.vo.AdminUserVO;
import com.secondhand.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理后台 · 用户管理实现。
 *
 * <p>三个统计数字（发布数 / 已售数 / 收藏数）用 <b>两条 GROUP BY 查询</b>批量算出来，
 * 而不是"一页 10 个用户 × 3 次 count = 30 次查询"。
 * 这是本项目里最容易写出 N+1 的地方之一，因为它在"列表里放统计"上。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final FavoriteMapper favoriteMapper;

    @Override
    public PageVO<AdminUserVO> list(Integer page, Integer size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 用户名或昵称命中即可。and(...) 包住 or 是必须的 ——
            // 以后这里再加别的条件（比如 status），裸 or 会把那个条件冲掉。
            wrapper.and(w -> w.like(User::getUsername, kw)
                    .or().like(User::getNickname, kw));
        }
        // 按注册顺序（id）升序：数据是"一个个人攒起来的"，
        // 升序看起来就是一部注册史，也方便和数据库里的行一一对照。
        wrapper.orderByAsc(User::getId);

        Page<User> pageParam = new Page<>(
                (page == null || page < 1) ? 1 : page,
                (size == null || size < 1) ? 10 : Math.min(size, 100));
        IPage<User> result = userMapper.selectPage(pageParam, wrapper);

        List<User> records = result.getRecords();
        List<Integer> userIds = records.stream().map(User::getId).collect(Collectors.toList());

        // 两个批量统计：① 商品（总数 / 已售） ② 收藏
        ProductStat productStat = statProducts(userIds);
        Map<Integer, Long> favoriteStat = statFavorites(userIds);

        return PageVO.of(result, u -> {
            AdminUserVO vo = new AdminUserVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setNickname(u.getNickname());
            // ★ 管理员可见联系方式（本类是整个项目里少数几个"能带出去"的地方）
            vo.setWechat(u.getWechat());
            vo.setQq(u.getQq());
            vo.setPhone(u.getPhone());
            vo.setAvatar(u.getAvatar());
            vo.setRole(u.getRole());
            vo.setStatus(u.getStatus());
            vo.setStatusText(statusText(u.getStatus()));
            vo.setCreateTime(u.getCreateTime());

            vo.setPublishedCount(productStat.published().getOrDefault(u.getId(), 0L));
            vo.setSoldCount(productStat.sold().getOrDefault(u.getId(), 0L));
            vo.setFavoriteCount(favoriteStat.getOrDefault(u.getId(), 0L));
            return vo;
        });
    }

    /**
     * 10.8 禁用 / 启用。
     *
     * <p>三条校验，顺序有讲究：
     * <ol>
     *   <li>{@code status} 只能是 0 或 1 —— 传个 9 进来不能悄悄当"启用"处理</li>
     *   <li>用户必须存在</li>
     *   <li><b>不能禁用自己</b>：否则管理员手一抖把自己关了，
     *       而又没有任何人能再启用他 —— 后台就永久锁死了</li>
     * </ol>
     *
     * <p>⚠️ 禁用之后的效果：
     * 登录接口会返回 403「账号已被禁用」；
     * <b>已经登录的旧 token 也立即失效</b> —— 因为 {@code OptionalAuthInterceptor}
     * 每个请求都会查一次库确认 status，不是靠 token 里的角色字段说了算。
     * 代价是每个请求多一次主键查询，换来的是"禁用马上生效"，这个交换是值得的。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Integer userId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "status 只能是 0（禁用）或 1（启用）");
        }

        User target = userMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "用户不存在");
        }

        if (status == 0 && Objects.equals(userId, UserContext.requireUserId())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "不能禁用当前登录账号");
        }

        // 状态没变就不用写库（幂等：连点两次"禁用"不会报错，也不会产生无意义的事务）
        if (Objects.equals(target.getStatus(), status)) {
            log.info("用户 id={} 状态已经是 {}，跳过更新", userId, status);
            return;
        }

        // 用 updateById + 只带 status 一个字段：不会误改其他列
        User update = new User();
        update.setId(userId);
        update.setStatus(status);
        userMapper.updateById(update);

        log.info("管理员 {} 将用户 id={}（{}）状态改为 {}",
                UserContext.getCurrentUsername(), userId, target.getUsername(), status);
    }

    // ==================================================================
    //  批量统计
    // ==================================================================

    /** 商品的"总数 / 已售数"统计结果 */
    private record ProductStat(Map<Integer, Long> published, Map<Integer, Long> sold) {
    }

    /**
     * 一次查出这批用户各自的「发布总数」和「已售出数」。
     *
     * <pre>
     *   SELECT seller_id, COUNT(*) AS total,
     *          SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS sold
     *   FROM product WHERE seller_id IN (...) GROUP BY seller_id
     * </pre>
     *
     * <p>为什么用 {@code SUM(CASE WHEN ...)} 而不是"再写一条 GROUP BY 查询"：
     * 一次扫表就能同时得到两个口径，比查两遍省一半。
     * 注意 {@code status = 2} 这里用的是枚举的 {@code getCode()}，不是裸数字。
     */
    private ProductStat statProducts(List<Integer> userIds) {
        if (userIds.isEmpty()) {
            return new ProductStat(Map.of(), Map.of());
        }
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        wrapper.select("seller_id",
                        "COUNT(*) AS total",
                        "SUM(CASE WHEN status = " + ProductStatus.SOLD.getCode()
                                + " THEN 1 ELSE 0 END) AS sold")
                .in("seller_id", userIds)
                .groupBy("seller_id");

        Map<Integer, Long> published = new HashMap<>();
        Map<Integer, Long> sold = new HashMap<>();
        for (Map<String, Object> row : productMapper.selectMaps(wrapper)) {
            Object sellerId = pickIgnoreCase(row, "seller_id");
            if (sellerId == null) {
                continue;
            }
            int id = ((Number) sellerId).intValue();
            published.put(id, toLong(pickIgnoreCase(row, "total")));
            sold.put(id, toLong(pickIgnoreCase(row, "sold")));
        }
        return new ProductStat(published, sold);
    }

    /** 一次查出这批用户各自收藏了多少件 */
    private Map<Integer, Long> statFavorites(List<Integer> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<Favorite> wrapper = new QueryWrapper<>();
        wrapper.select("user_id", "COUNT(*) AS cnt")
                .in("user_id", userIds)
                .groupBy("user_id");

        Map<Integer, Long> map = new HashMap<>();
        for (Map<String, Object> row : favoriteMapper.selectMaps(wrapper)) {
            Object userId = pickIgnoreCase(row, "user_id");
            if (userId != null) {
                map.put(((Number) userId).intValue(), toLong(pickIgnoreCase(row, "cnt")));
            }
        }
        return map;
    }

    /** 账号状态中文。只有两个取值，用 if 比建枚举更省事 */
    private String statusText(Integer status) {
        return (status != null && status == 1) ? "正常" : "已禁用";
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private Object pickIgnoreCase(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(key.toUpperCase());
    }
}
