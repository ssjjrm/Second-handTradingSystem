package com.secondhand.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发布商品入参（接口文档 §5.4）。
 *
 * <p>校验分两层：
 * <ol>
 *   <li><b>格式校验</b>在这一层（@NotBlank / @Size / @DecimalMin…），
 *       由 {@code @Valid} 自动完成，失败就是 400 + 第一条提示</li>
 *   <li><b>业务校验</b>在 Service 层（分类必须是二级、原价≥现价、
 *       卖家必须已填联系方式），因为它需要查数据库</li>
 * </ol>
 * 不要把所有校验都塞进注解里 —— 注解查不了数据库。
 */
@Data
public class ProductCreateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(min = 5, max = 100, message = "标题长度需在 5~100 字符之间")
    private String title;

    @Size(max = 1500, message = "描述不能超过 1500 个字符")
    private String description;

    @NotNull(message = "请选择分类")
    private Integer categoryId;

    @NotBlank(message = "请选择成色")
    private String conditionLevel;

    @NotNull(message = "请填写价格")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "价格最多两位小数")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "原价必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "原价最多两位小数")
    private BigDecimal originalPrice;

    /** 图片 URL 数组（前端先调上传接口拿到 URL，再一起提交） */
    @Size(max = 9, message = "最多上传 9 张图片")
    private List<String> images;

    @Size(max = 50, message = "品牌不能超过 50 个字符")
    private String brand;

    @NotBlank(message = "请填写交易地点")
    @Size(max = 100, message = "交易地点不能超过 100 个字符")
    private String tradePlace;
}
