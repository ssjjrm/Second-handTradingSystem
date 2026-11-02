package com.secondhand.vo;

import lombok.Data;

/** 卖家主页上的几个数字 */
@Data
public class SellerStatsVO {

    /** 正在卖的商品数 */
    private Long onSaleCount;

    /** 已经卖掉的商品数 —— 侧面反映"这人靠不靠谱" */
    private Long soldCount;

    /** 收到的「想要」总数（所有商品的 favorite_count 之和） */
    private Long favoriteReceivedCount;
}
