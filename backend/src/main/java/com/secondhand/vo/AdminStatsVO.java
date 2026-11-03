package com.secondhand.vo;

import lombok.Data;

/**
 * 管理后台 · 数据统计（接口文档 §10.9）。
 *
 * <p><b>⚠️ 本类故意没有"成交额"字段。</b>
 * 平台只做信息撮合、不参与交易，卖家实际卖了多少钱、有没有议价，
 * 后端根本无从知晓。硬造一个"成交额"就是编数据 ——
 * 所以这里用 {@code soldProducts}（卖家自己点的"已售出"数量）
 * 作为<b>成交量代理指标</b>，并在需求文档 §4.2.5 里记录为已知代价。
 */
@Data
public class AdminStatsVO {

    /** 用户总数 */
    private Long totalUsers;

    /** 商品总数（所有状态） */
    private Long totalProducts;

    /** 在售商品数 */
    private Long onSaleProducts;

    /** 已售出商品数（**成交量代理指标**） */
    private Long soldProducts;

    /** 待审核商品数（后台的待办量） */
    private Long pendingProducts;

    /** 收藏总记录数 */
    private Long totalFavoriteRecords;

    /** ⭐ 咨询总次数（获取联系方式） */
    private Long totalContactRecords;

    /** 今日新增用户 */
    private Long todayNewUsers;

    /** 今日新增商品 */
    private Long todayNewProducts;

    /** 今日咨询次数 */
    private Long todayContacts;
}
