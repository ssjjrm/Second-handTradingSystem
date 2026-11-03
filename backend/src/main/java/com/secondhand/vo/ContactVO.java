package com.secondhand.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 卖家联系方式出参 —— <b>全项目唯一会返回「别人的」微信/QQ 的对象</b>。
 *
 * <p>能走到这里必须同时满足 5 个条件（Service 里逐条校验）：
 * 已登录、商品存在、不是自己发的、商品在售、卖家填了联系方式。
 *
 * <p>⚠️ 记得它是 GET 但有副作用（写一条 type=3 咨询行为）。
 * 这是有意为之的权衡，已在接口文档 §5.3 标注为已知的设计不规范。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContactVO {

    private Integer productId;

    /** 冗余带上商品标题，前端弹窗里可以直接显示，不用再查一次 */
    private String productTitle;

    private Integer sellerId;

    private String sellerNickname;

    private String sellerAvatar;

    /** 微信号 —— 可能为 null（卖家只填了 QQ） */
    private String wechat;

    /** QQ 号 —— 可能为 null */
    private String qq;

    /** 建议的线下交易地点 */
    private String tradePlace;
}
