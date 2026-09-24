package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏（页面上的"想要"）—— 记录的是【当前状态】。
 *
 * <p>有行 = 还想要；没行 = 不想要了。
 * 表上有唯一键 {@code uk_user_product}，从数据库层面杜绝重复收藏。
 *
 * <p>注意它和 {@link UserBehavior} 的区别：这张表可以删行，那张表只追加。
 */
@Data
@TableName("favorite")
public class Favorite {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private Integer productId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
