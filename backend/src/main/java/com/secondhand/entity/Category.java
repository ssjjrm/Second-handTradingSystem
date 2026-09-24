package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类（两级树，靠 {@code parentId} 自关联）。
 *
 * <p>一级分类 {@code parentId == null}，二级分类指向一级的 id。
 */
@Data
@TableName("category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    /** 父分类 id；null 表示这是一级分类 */
    private Integer parentId;

    /** 排序值，越小越靠前 */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
