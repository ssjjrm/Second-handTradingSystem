package com.secondhand.service;

import com.secondhand.common.BehaviorType;

/**
 * 用户行为埋点（S7.2 统一封装）。
 *
 * <p><b>为什么要有这个 Service：</b>埋点散落在各个业务方法里，每个地方都写一遍
 * {@code try { insert } catch { log }} 是非常糟糕的 —— 复制粘贴多了，
 * 总有一处忘了 try-catch，然后某天埋点表出问题就把主业务带崩。
 *
 * <p>统一到一个入口后，只在这里保证三件事：
 * <ol>
 *   <li><b>绝不抛异常</b> —— 记录行为失败不影响用户真正想做的事（BR-B08）</li>
 *   <li><b>未登录直接跳过</b> —— 没有 user_id 的行为数据没有意义（BR-B01）</li>
 *   <li><b>浏览自己的商品不记</b> —— 否则"多少人看过"会被自己的刷新刷爆（BR-B02）</li>
 * </ol>
 *
 * <p>以后想加开关（比如压测时关掉埋点）、想换成异步写入、想批量落库，
 * 都只改这一个类。
 */
public interface BehaviorService {

    /**
     * 记录一条行为。
     *
     * <p><b>永远不会抛异常</b>，也永远不会因为埋点失败而影响调用方。
     *
     * @param userId    可以为 null（未登录则直接跳过）
     * @param productId 商品 id
     * @param type      行为类型
     */
    void record(Integer userId, Integer productId, BehaviorType type);

    /**
     * 记录「浏览」。
     *
     * <p>比 {@link #record} 多做两件事：自动判断当前用户是否登录、
     * 自动跳过"浏览自己发布的商品"。
     *
     * <p>这样调用方（商品详情接口）只写一行 {@code recordView(id)} 就够了。
     */
    void recordView(Integer productId);
}
