package com.secondhand.vo;

import lombok.Data;

/**
 * 个人中心的统计数字。
 *
 * <p>⚠️ S3 阶段全部返回 0 —— 因为 product / favorite / user_behavior
 * 三张表的 Mapper 要到 S4、S7 才建。等 S8 做个人中心时再补齐真实统计。
 */
@Data
public class UserStatsVO {

    /** 我发布的商品总数 */
    private Long publishedCount;

    /** 在售中 */
    private Long onSaleCount;

    /** 已售出 */
    private Long soldCount;

    /** 我收藏了多少 */
    private Long favoriteCount;

    /** 我的商品被咨询的总次数 */
    private Long receivedContactCount;

    public static UserStatsVO empty() {
        UserStatsVO vo = new UserStatsVO();
        vo.setPublishedCount(0L);
        vo.setOnSaleCount(0L);
        vo.setSoldCount(0L);
        vo.setFavoriteCount(0L);
        vo.setReceivedContactCount(0L);
        return vo;
    }
}
