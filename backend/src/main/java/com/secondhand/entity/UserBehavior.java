package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户行为流水（浏览 / 收藏 / 咨询 / 取消收藏）。
 *
 * <p>和 {@link Favorite} 的关键区别：这张表**只追加、不修改、不删除**，
 * 同一个人可以浏览同一件商品 100 次（就是 100 行），所以**没有唯一键**。
 *
 * <p>⚠️ 参考项目建了这张表，却从头到尾没往里写过一行数据 ——
 * 表和实体都在，就是没有任何业务代码调用它。本项目从 S4 开始
 * 每个相关动作都要落一条记录。
 */
@Data
@TableName("user_behavior")
public class UserBehavior {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private Integer productId;

    /** 见 BehaviorType：1浏览 / 2收藏 / 3咨询 / 4取消收藏 */
    private Integer behaviorType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime behaviorTime;
}
