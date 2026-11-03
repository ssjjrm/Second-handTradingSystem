package com.secondhand.vo;

import lombok.Data;

/**
 * 全站行为汇总（接口文档 §10.6 的 {@code summary}）。
 *
 * <p>后台首页最该看的四个数字。其中 {@code contactCount}（咨询量）是本项目
 * <b>没有订单</b>这个前提下，最接近"成交量"的指标 ——
 * 平台不参与交易，唯一能观测到的转化动作就是"买家点了获取联系方式"。
 *
 * <p>⚠️ 口径说明：这四个数字是**全站总量**，不受列表的筛选条件影响。
 * 列表可以只看"某件商品的咨询"，但汇总永远是"整个平台有多大活跃度"。
 */
@Data
public class BehaviorSummaryVO {

    /** 浏览总次数 */
    private Long viewCount;

    /** 收藏总次数 */
    private Long favoriteCount;

    /** ⭐ 咨询总次数（获取联系方式） */
    private Long contactCount;

    /** 取消收藏总次数 */
    private Long unfavoriteCount;
}
