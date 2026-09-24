package com.secondhand.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商品详情出参 = 列表项的全部字段 + 详情独有的字段。
 *
 * <p>用继承而不是重写一遍：列表和详情的公共字段有十几个，
 * 抄两遍很容易改了一处忘了另一处。
 *
 * <p>⚠️ {@code isFavorited} 的类型必须是包装类 {@code Boolean} 而不是 {@code boolean}：
 * <ul>
 *   <li>{@code Boolean} + Lombok 生成 {@code getIsFavorited()} → JSON 字段名是 {@code isFavorited} ✅</li>
 *   <li>{@code boolean} + Lombok 生成 {@code isFavorited()} → JSON 字段名会变成 {@code favorited} ❌</li>
 * </ul>
 * 前端按文档写的是 {@code isFavorited}，用错类型就取不到值，而且不报错。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDetailVO extends ProductListVO {

    /** 完整描述（列表里不带，太大） */
    private String description;

    private LocalDateTime updateTime;

    /** 当前登录用户是否已收藏；未登录恒为 false */
    private Boolean isFavorited;

    /** 是否可点「联系卖家」 */
    private Boolean canContact;

    /** 不可联系时的原因文案，可联系时为 null */
    private String contactHint;

    private SellerBriefVO seller;
}
