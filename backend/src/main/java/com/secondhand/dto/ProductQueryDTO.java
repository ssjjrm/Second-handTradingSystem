package com.secondhand.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品列表查询参数（接口文档 §5.1）。
 *
 * <p>GET 请求的参数由 Spring 自动按名字绑定到这个对象上，
 * 所以 Controller 里写一个 {@code ProductQueryDTO query} 就够了，
 * 不用写七个 {@code @RequestParam}。
 *
 * <p>默认值直接写在字段上：{@code page=1}、{@code size=20}、{@code sort=newest}，
 * 这样前端不传也有合理行为。
 */
@Data
public class ProductQueryDTO {

    /** 页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数，最大 100（超过会被分页插件的 maxLimit 截断） */
    private Integer size = 20;

    /** 关键词：匹配标题 / 描述 / 品牌 */
    private String keyword;

    /** 分类 id：传一级分类时自动包含它的所有子分类 */
    private Integer categoryId;

    /** 排序：newest 最新 / priceAsc 价格升 / priceDesc 价格降 / hot 最多人想要 */
    private String sort = "newest";

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    /** 交易地点模糊匹配，如"东区食堂" */
    private String tradePlace;

    /** 成色精确匹配 */
    private String conditionLevel;

    // ---- 下面是"兜底"，防止前端传 page=0 或 size=-1 把数据库搞崩 ----

    public Integer getPage() {
        return (page == null || page < 1) ? 1 : page;
    }

    public Integer getSize() {
        if (size == null || size < 1) {
            return 20;
        }
        return size > 100 ? 100 : size;
    }
}
