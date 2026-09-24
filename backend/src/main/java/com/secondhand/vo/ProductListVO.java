package com.secondhand.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品列表项出参。
 *
 * <p>⚠️ <b>这里绝对不会有 wechat / qq / phone</b>：列表接口是公开的，
 * 任何人都能访问，一旦带上联系方式就等于把卖家的微信公开了（BR-C02）。
 * 需要联系方式的唯一入口是"获取联系方式"接口，它要求登录并且会记录行为。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductListVO {

    private Integer id;

    private String title;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String conditionLevel;

    /** 解析好的图片数组（数据库里存的是 JSON 字符串） */
    private List<String> images;

    private String brand;

    private Integer categoryId;

    /** 分类名 —— 由后端 JOIN 好，前端不用再查一次 */
    private String categoryName;

    private String tradePlace;

    private Integer status;

    /** 状态中文 —— 后端统一给，前端不用自己写一张映射表 */
    private String statusText;

    private Integer viewCount;

    private Integer favoriteCount;

    private Integer sellerId;

    private String sellerNickname;

    private String sellerAvatar;

    private LocalDateTime createTime;
}
