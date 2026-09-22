package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 {@code user}。
 *
 * <p>字段与列名的对应关系靠 {@code map-underscore-to-camel-case: true} 自动完成
 * （{@code create_time} ↔ {@code createTime}），所以不需要逐个写 {@code @TableField}。
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;

    /**
     * 密码密文（BCrypt）。
     *
     * <p>{@code @JsonIgnore} 是关键保险：任何把 User 对象直接当响应返回的地方，
     * 这个字段都不会被序列化出去。即使后面哪次忘了用 VO，密码也不会泄漏。
     */
    @JsonIgnore
    private String password;

    private String nickname;

    /**
     * 微信号。
     *
     * <p>wechat / qq / phone 三个都加了 {@code @JsonIgnore} —— 默认**永不序列化**。
     * 需要展示时（"获取联系方式"接口、个人中心"我的资料"）用专门的 VO 显式带出。
     * 这样即使哪次忘了用 VO，联系方式也不会漏出去 —— 默认拒绝，比默认放行安全。
     */
    @JsonIgnore
    private String wechat;

    /** QQ 号。理由同 wechat */
    @JsonIgnore
    private String qq;

    /** 手机号：仅账号找回用，永不对外展示。理由同 wechat */
    @JsonIgnore
    private String phone;

    private String avatar;

    /** 角色：user / admin */
    private String role;

    /** 账号状态：1 正常 / 0 禁用 */
    private Integer status;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;
}
