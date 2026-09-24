package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体。
 *
 * <p>三个需要特别注意的字段：
 * <ol>
 *   <li>{@code images} —— 数据库里存的是 **JSON 字符串** {@code ["url1","url2"]}，
 *       不是数组。Service 层负责解析成 {@code List<String>} 后再放进 VO，
 *       <b>绝不把 {@code String images} 直接返回给前端</b>（否则前端会收到一个字符串）</li>
 *   <li>{@code viewCount} —— 只在**详情接口**里 +1，别的接口调用都不能碰它
 *       （参考项目把它写在通用的 getById 里，购物车一刷新浏览量就暴涨）</li>
 *   <li>{@code favoriteCount} —— 冗余字段，必须和 favorite 表的行数保持一致</li>
 * </ol>
 *
 * <p>金额一律用 {@link BigDecimal}，**绝不用 double/float** ——
 * 浮点数存不下 0.1 这种十进制小数，累加会出现 1280.0000000000002 这种结果。
 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String title;

    private String description;

    private Integer categoryId;

    /** 成色：全新 / 几乎全新 / 轻微使用痕迹 / 明显使用痕迹 */
    private String conditionLevel;

    private BigDecimal price;

    private BigDecimal originalPrice;

    /** 图片 URL 数组的 JSON 字符串，如 ["https://...","https://..."] */
    private String images;

    private String brand;

    private String tradePlace;

    private Integer sellerId;

    /** 0待审核 / 1在售 / 2已售出 / 3已下架 / 4已拒绝，见 ProductStatus */
    private Integer status;

    private Integer viewCount;

    private Integer favoriteCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
