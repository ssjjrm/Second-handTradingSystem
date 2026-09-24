package com.secondhand.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 分类出参（两级树）。
 *
 * <p>{@code children} 只在一级分类上有值；二级分类这个字段是 null，
 * 配合 {@code @JsonInclude(NON_NULL)} 就不会出现在 JSON 里。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryVO {

    private Integer id;

    private String name;

    /** null = 一级分类 */
    private Integer parentId;

    private Integer sortOrder;

    /**
     * 该分类下的**在售**商品数（首页分类菜单的数量角标）。
     *
     * <p>注意：商品挂在二级分类上，所以一级分类的数字是它所有子分类之和。
     */
    private Long productCount;

    /** 二级分类列表，一级分类才有 */
    private List<CategoryVO> children;
}
