package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.common.BehaviorType;
import com.secondhand.entity.Product;
import com.secondhand.entity.User;
import com.secondhand.entity.UserBehavior;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.mapper.UserBehaviorMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.AdminBehaviorService;
import com.secondhand.vo.BehaviorLogPageVO;
import com.secondhand.vo.BehaviorLogVO;
import com.secondhand.vo.BehaviorSummaryVO;
import com.secondhand.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理后台 · 行为流水实现。
 *
 * <p>两条要注意的地方：
 *
 * <p><b>1. 昵称和商品标题是"批量补"出来的，不是每行查一次。</b>
 * 一页 20 条流水涉及最多 20 个用户 + 20 件商品，
 * 如果写在循环里就是 40 次查询（经典的 N+1）。这里改成两条
 * {@code selectBatchIds}，再在内存里对上号。
 *
 * <p><b>2. 汇总和列表用的是两套条件。</b>
 * 列表可以只看"某件商品的咨询"，但 {@code summary} 永远是**全站总量** ——
 * 它回答的问题不是"这一页有多少咨询"，而是"这个平台有多活跃"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBehaviorServiceImpl implements AdminBehaviorService {

    private final UserBehaviorMapper userBehaviorMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;

    @Override
    public BehaviorLogPageVO list(Integer page, Integer size,
                                  Integer behaviorType, Integer productId, Integer userId) {

        LambdaQueryWrapper<UserBehavior> wrapper = new LambdaQueryWrapper<UserBehavior>()
                // 三个条件都是"传了才生效"（第一个参数为 false 时该条件不拼进 SQL）
                .eq(behaviorType != null, UserBehavior::getBehaviorType, behaviorType)
                .eq(productId != null, UserBehavior::getProductId, productId)
                .eq(userId != null, UserBehavior::getUserId, userId)
                // 最新的流水在最前面；再按 id 兜底，保证同一秒内的多条顺序稳定
                .orderByDesc(UserBehavior::getBehaviorTime)
                .orderByDesc(UserBehavior::getId);

        Page<UserBehavior> pageParam = new Page<>(
                (page == null || page < 1) ? 1 : page,
                (size == null || size < 1) ? 20 : Math.min(size, 100));
        IPage<UserBehavior> result = userBehaviorMapper.selectPage(pageParam, wrapper);

        List<UserBehavior> records = result.getRecords();

        // 批量补昵称 / 标题 / 价格（两条 SQL，不是 2N 条）
        Map<Integer, User> userMap = loadUsers(records);
        Map<Integer, Product> productMap = loadProducts(records);

        PageVO<BehaviorLogVO> pageVO = PageVO.of(result, b -> {
            BehaviorLogVO vo = new BehaviorLogVO();
            vo.setId(b.getId());
            vo.setBehaviorType(b.getBehaviorType());
            // 中文由后端统一给，前端不维护映射表（同 ProductStatus 的做法）
            vo.setBehaviorTypeText(BehaviorType.textOf(b.getBehaviorType()));
            vo.setBehaviorTime(b.getBehaviorTime());
            vo.setUserId(b.getUserId());

            User user = userMap.get(b.getUserId());
            // 昵称可能为空（脏数据），兜底显示用户名，实在没有就给个占位
            vo.setUserNickname(user == null ? null
                    : (user.getNickname() == null ? user.getUsername() : user.getNickname()));

            vo.setProductId(b.getProductId());
            Product product = productMap.get(b.getProductId());
            // 商品被删除时行为记录会被外键级联清掉，正常不会走到 null 分支；
            // 这里仍然判空，因为"展示层不能因为一条脏数据就整页 500"
            vo.setProductTitle(product == null ? "（商品已删除）" : product.getTitle());
            vo.setProductPrice(product == null ? null : product.getPrice());
            return vo;
        });

        return BehaviorLogPageVO.of(pageVO, loadSummary());
    }

    /** 一次查出这一页涉及的所有用户 */
    private Map<Integer, User> loadUsers(List<UserBehavior> records) {
        Set<Integer> ids = records.stream()
                .map(UserBehavior::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 一次查出这一页涉及的所有商品 */
    private Map<Integer, Product> loadProducts(List<UserBehavior> records) {
        Set<Integer> ids = records.stream()
                .map(UserBehavior::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return productMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    /**
     * 全站各类型行为的计数 —— <b>用一条 GROUP BY 查完，不是四次 count</b>。
     *
     * <pre>
     *   SELECT behavior_type, COUNT(*) AS cnt FROM user_behavior GROUP BY behavior_type
     * </pre>
     *
     * <p>某种行为一条都没有时，GROUP BY 里根本不会有那一行，
     * 所以返回的 VO 里**必须显式补 0**，否则前端拿到 null 会显示成空白。
     */
    private BehaviorSummaryVO loadSummary() {
        QueryWrapper<UserBehavior> wrapper = new QueryWrapper<>();
        wrapper.select("behavior_type", "COUNT(*) AS cnt").groupBy("behavior_type");

        Map<Integer, Long> counts = new HashMap<>();
        for (Map<String, Object> row : userBehaviorMapper.selectMaps(wrapper)) {
            // selectMaps 的 key 大小写由驱动决定（MySQL 通常原样返回），两种都试
            Object type = pickIgnoreCase(row, "behavior_type");
            Object cnt = pickIgnoreCase(row, "cnt");
            if (type != null && cnt != null) {
                counts.put(((Number) type).intValue(), ((Number) cnt).longValue());
            }
        }

        BehaviorSummaryVO summary = new BehaviorSummaryVO();
        summary.setViewCount(counts.getOrDefault(BehaviorType.VIEW.getCode(), 0L));
        summary.setFavoriteCount(counts.getOrDefault(BehaviorType.FAVORITE.getCode(), 0L));
        summary.setContactCount(counts.getOrDefault(BehaviorType.CONTACT.getCode(), 0L));
        summary.setUnfavoriteCount(counts.getOrDefault(BehaviorType.UNFAVORITE.getCode(), 0L));
        return summary;
    }

    private Object pickIgnoreCase(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(key.toUpperCase());
    }
}
