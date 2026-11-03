package com.secondhand.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「我的收藏」列表项（接口文档 §7.3）。
 *
 * <p>结构是"收藏记录 + 嵌套的商品"，而不是把商品字段拍平 ——
 * 因为这里同时需要 {@code favoriteId}（取消收藏时用）和
 * {@code favoriteTime}（按收藏时间排序显示），拍平会分不清哪个字段属于谁。
 *
 * <p>⚠️ 即使商品已经售出或下架，这里**仍然会返回**（BR-F07）——
 * 用户需要看到"我想要的东西已经被别人买走了"，然后自己决定要不要移除。
 * 前端根据 {@code product.status} 显示灰色蒙层。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FavoriteItemVO {

    /** 收藏记录 id（取消收藏时前端可以用它，也可以用 productId） */
    private Integer favoriteId;

    /** 什么时候收藏的 */
    private LocalDateTime favoriteTime;

    /** 被收藏的商品（含 status / statusText，前端据此显示"已售出"蒙层） */
    private ProductListVO product;
}
