package com.secondhand.common;

import lombok.Getter;

/**
 * 用户行为类型，对应 {@code user_behavior.behavior_type}。
 *
 * <p>这张表是"信息撮合"模式下**最有价值的数据**：
 * 平台不产生订单，所以"有多少人点了获取联系方式"就是最接近成交的转化指标。
 */
@Getter
public enum BehaviorType {

    /** 浏览商品详情 */
    VIEW(1, "浏览"),

    /** 点"想要"收藏 */
    FAVORITE(2, "收藏"),

    /** ⭐ 点"获取联系方式" —— 本平台最核心的转化指标 */
    CONTACT(3, "咨询"),

    /** 取消收藏 */
    UNFAVORITE(4, "取消收藏");

    private final int code;
    private final String text;

    BehaviorType(int code, String text) {
        this.code = code;
        this.text = text;
    }

    public static BehaviorType ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (BehaviorType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }

    public static String textOf(Integer code) {
        BehaviorType type = ofCode(code);
        return type == null ? "未知" : type.text;
    }
}
