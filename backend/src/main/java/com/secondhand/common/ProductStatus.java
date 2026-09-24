package com.secondhand.common;

import lombok.Getter;

/**
 * 商品状态。
 *
 * <p>对应 {@code product.status} 列（tinyint），取值与建表 SQL 的 CHECK 约束一致。
 *
 * <p>⚠️ <b>为什么必须有这个枚举</b>：
 * 参考项目里到处是裸数字 —— {@code if (book.getStatus() == 1)}、
 * {@code book.setStatus(2)}、前端再写一张 {@code {0:'待审',1:'在售'}} 的映射表。
 * 结果是同一套状态定义散落在后端 Service、后端 Controller、前端组件三处，
 * 改一个值要翻遍全项目，而且很容易出现"后端改了前端没改"。
 *
 * <p>用枚举之后：
 * <ul>
 *   <li>后端写 {@code ProductStatus.ON_SALE.getCode()}，IDE 能自动补全、能全局搜索</li>
 *   <li>中文文案由 {@link #getText()} 统一提供，前端直接用后端返回的 {@code statusText}</li>
 * </ul>
 */
@Getter
public enum ProductStatus {

    /** 用户刚发布，等管理员审核。列表页不展示 */
    PENDING(0, "待审核"),

    /** 审核通过，正常展示，可以联系卖家 */
    ON_SALE(1, "在售"),

    /** 卖家手动标记已售出。仍会展示（让买家知道这个价能成交），但不能联系 */
    SOLD(2, "已售出"),

    /** 卖家主动下架。列表页不展示 */
    OFF_SHELF(3, "已下架"),

    /** 管理员审核不通过。只有卖家本人在"我的发布"里能看到 */
    REJECTED(4, "已拒绝");

    private final int code;
    private final String text;

    ProductStatus(int code, String text) {
        this.code = code;
        this.text = text;
    }

    /** 按 code 找枚举，找不到返回 null */
    public static ProductStatus ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ProductStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * 按 code 取中文文案。
     *
     * <p>找不到时返回"未知"，而不是抛异常 —— 状态文案是展示用的，
     * 因为一个陌生的状态值就让整个接口 500 太不值得了。
     */
    public static String textOf(Integer code) {
        ProductStatus status = ofCode(code);
        return status == null ? "未知" : status.text;
    }

    /** 列表页可见的状态：在售 + 已售出（其余对游客不可见） */
    public static boolean isVisibleInList(Integer code) {
        return code != null && (code == ON_SALE.code || code == SOLD.code);
    }
}
