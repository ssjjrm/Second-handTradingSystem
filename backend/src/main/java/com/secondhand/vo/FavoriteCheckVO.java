package com.secondhand.vo;

import lombok.Data;

/** 「是否已收藏」检查结果（接口文档 §7.4），用于详情页初始化「想要」按钮的选中态 */
@Data
public class FavoriteCheckVO {

    private Boolean favorited;

    /** 顺带带上当前商品的总想要数，省一次请求 */
    private Long favoriteCount;
}
