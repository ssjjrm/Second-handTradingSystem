package com.secondhand.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台的商品列表项。
 *
 * <p>比前台列表多两样：
 * <ul>
 *   <li>{@code description} —— 审核要看描述有没有违规</li>
 *   <li>{@code seller} —— 审核要看是谁发的</li>
 * </ul>
 *
 * <p>⚠️ 即使是管理员，这里也**不带卖家的微信/QQ** ——
 * 联系方式只有"获取联系方式"那一个出口，管理员也不例外。
 * 管理员需要联系卖家时，应该用卖家 id 去查用户资料，那是 S9 的事。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductAdminVO extends ProductListVO {

    private String description;

    private SellerBriefVO seller;
}
