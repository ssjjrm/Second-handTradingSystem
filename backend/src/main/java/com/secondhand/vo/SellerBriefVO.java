package com.secondhand.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 商品详情页里的卖家信息。
 *
 * <p>⚠️ 这里**只有昵称和头像**，没有微信 / QQ / 手机号。
 * 想拿联系方式必须走专门接口，那是唯一会返回联系方式的地方。
 */
@Data
public class SellerBriefVO {

    private Integer id;

    private String nickname;

    private String avatar;

    /** 该卖家当前在售商品数 */
    private Long onSaleCount;

    /** 该卖家已售出商品数 —— 侧面反映"这人靠不靠谱" */
    private Long soldCount;
}
