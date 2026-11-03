package com.secondhand.service.impl;

import com.secondhand.common.BehaviorType;
import com.secondhand.entity.Product;
import com.secondhand.entity.UserBehavior;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.mapper.UserBehaviorMapper;
import com.secondhand.service.BehaviorService;
import com.secondhand.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 行为埋点实现。
 *
 * <p>⚠️ <b>本类最重要的特性是：任何方法都不会向外抛异常。</b>
 * 埋点是"锦上添花"，用户要的是看商品、拿联系方式。
 * 如果因为埋点表写失败就给他返回 500，等于为了让日志完整牺牲了主业务 ——
 * 这个取舍在任何真实系统里都是不划算的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorServiceImpl implements BehaviorService {

    private final UserBehaviorMapper userBehaviorMapper;
    private final ProductMapper productMapper;

    @Override
    public void record(Integer userId, Integer productId, BehaviorType type) {
        // 未登录 / 参数不全 → 静默跳过，什么都不做也不报错
        if (userId == null || productId == null || type == null) {
            return;
        }

        try {
            UserBehavior behavior = new UserBehavior();
            behavior.setUserId(userId);
            behavior.setProductId(productId);
            behavior.setBehaviorType(type.getCode());
            userBehaviorMapper.insert(behavior);
        } catch (Exception e) {
            // 只记日志，绝不外抛
            log.warn("行为埋点写入失败（不影响主流程）：userId={}, productId={}, type={}",
                    userId, productId, type.getCode(), e);
        }
    }

    @Override
    public void recordView(Integer productId) {
        // BR-B01：未登录不记（没有 user_id 的浏览记录对推荐算法毫无用处）
        Integer userId = UserContext.getCurrentUserId();
        if (userId == null || productId == null) {
            return;
        }

        try {
            // BR-B02：自己看自己的商品不记 ——
            // 否则卖家反复刷新自己的商品，就会被当成"很热门"
            Product product = productMapper.selectById(productId);
            if (product == null || userId.equals(product.getSellerId())) {
                return;
            }
        } catch (Exception e) {
            log.warn("查询商品失败，跳过浏览埋点：productId={}", productId, e);
            return;
        }

        record(userId, productId, BehaviorType.VIEW);
    }
}
