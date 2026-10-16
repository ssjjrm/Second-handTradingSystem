package com.secondhand.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 编辑商品入参（接口文档 §5.5）。
 *
 * <p>和 {@link ProductCreateDTO} 的唯一区别：<b>所有字段都可选</b>。
 * 所以这里没有 {@code @NotBlank} / {@code @NotNull}，
 * 但保留了格式类校验（长度、金额精度）—— 传了就得合法，
 * 只是"不传"也允许。
 *
 * <p>注意 {@code @Size(min = 5, ...)} 对 {@code null} 是放行的，
 * 所以"不传 title"不会被拦，而"传了 3 个字的 title"会被拦。
 */
@Data
public class ProductUpdateDTO {

    @Size(min = 5, max = 100, message = "标题长度需在 5~100 字符之间")
    private String title;

    @Size(max = 1500, message = "描述不能超过 1500 个字符")
    private String description;

    private Integer categoryId;

    private String conditionLevel;

    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "价格最多两位小数")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "原价必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "原价最多两位小数")
    private BigDecimal originalPrice;

    @Size(max = 9, message = "最多上传 9 张图片")
    private List<String> images;

    @Size(max = 50, message = "品牌不能超过 50 个字符")
    private String brand;

    @Size(max = 100, message = "交易地点不能超过 100 个字符")
    private String tradePlace;
}
