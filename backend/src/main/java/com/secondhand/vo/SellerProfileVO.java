package com.secondhand.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 卖家公开资料（接口文档 §6.1）。
 *
 * <p>⚠️ <b>这里绝不返回 wechat / qq / phone。</b>
 * 想看联系方式只有一条路：登录 + 在具体商品上点「获取联系方式」。
 * 否则任何人点进卖家主页就能把 TA 的微信批量拿走 —— 那就等于没有隐私设计了。
 *
 * <p>{@code hasContact} 只表示"这位卖家填没填"，不透露填了什么。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SellerProfileVO {

    private Integer id;

    private String nickname;

    private String avatar;

    /** 注册时间 —— 老用户通常更可信 */
    private LocalDateTime createTime;

    /** 是否已填微信或 QQ（供买家判断"点联系能不能联系上"） */
    private Boolean hasContact;

    private SellerStatsVO stats;
}
