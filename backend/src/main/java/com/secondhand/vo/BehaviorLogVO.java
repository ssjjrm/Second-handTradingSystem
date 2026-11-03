package com.secondhand.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台 · 行为流水的单行（接口文档 §10.6）。
 *
 * <p>把 {@code user_behavior} 的三列外键 id 全部"翻译成人话"：
 * {@code userNickname} / {@code productTitle} / {@code productPrice}，
 * 这样后台表格不用再对着 id 反查（也就不会出现 N+1）。
 *
 * <p>⚠️ 注意这里**不带任何联系方式**：后台看的是"平台上发生了什么"，
 * 而不是"这个人怎么联系" —— 那是用户列表里的事。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BehaviorLogVO {

    private Integer id;

    /** 1浏览 / 2收藏 / 3咨询 / 4取消收藏 */
    private Integer behaviorType;

    /** 行为中文：浏览 / 收藏 / 咨询 / 取消收藏 */
    private String behaviorTypeText;

    private LocalDateTime behaviorTime;

    private Integer userId;

    private String userNickname;

    private Integer productId;

    private String productTitle;

    /** 行为发生时的商品价格（商品后来调价也不影响这条历史流水） */
    private BigDecimal productPrice;
}
