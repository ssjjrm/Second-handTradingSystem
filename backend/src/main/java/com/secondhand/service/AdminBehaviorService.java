package com.secondhand.service;

import com.secondhand.vo.BehaviorLogPageVO;

/**
 * 管理后台 · 咨询/行为记录（接口文档 §10.6）。
 *
 * <p><b>为什么后台没有"订单管理"？</b>
 * 本项目是纯信息撮合：用户线下交易，平台不产生订单、不收款、不托管。
 * 需求文档 §12 里记录过：参考项目的"订单管理"拿到这里无从实现，
 * 硬造一张 order 表只能产生一堆假数据。
 *
 * <p>取而代之的是<b>全站行为流水</b> —— 这张表本来就存在（{@code user_behavior}），
 * 而且它比订单更能说明平台有没有人用：
 * 浏览说明有人逛，收藏说明有人犹豫，**咨询说明有人真想买**。
 */
public interface AdminBehaviorService {

    /**
     * 分页查询行为流水，附带全站汇总。
     *
     * @param behaviorType 1浏览 / 2收藏 / 3咨询 / 4取消收藏；null 表示不限
     * @param productId    只看某件商品；null 表示不限
     * @param userId       只看某个用户；null 表示不限
     */
    BehaviorLogPageVO list(Integer page, Integer size,
                           Integer behaviorType, Integer productId, Integer userId);
}
