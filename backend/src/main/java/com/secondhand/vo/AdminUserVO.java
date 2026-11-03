package com.secondhand.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 · 用户列表项（接口文档 §10.7）。
 *
 * <p><b>这个类里没有 password 字段</b> —— 不是"填了 null"，而是**类型上就不存在**。
 * 这是比"记得别填"更可靠的保证：即使以后有人往这里加方法，也漏不出密码。
 *
 * <p>⚠️ 和 C 端 VO 的最大区别：这里**包含** wechat / qq / phone。
 * 管理员需要联系方式来处理纠纷（比如"卖家收了钱不发货，买家来投诉"），
 * 所以这是本项目里**唯一一处**"别人能看到的联系方式"由管理员接口输出的地方。
 * 权限由 {@code AdminInterceptor} 把守（非 admin 一律 403）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminUserVO {

    private Integer id;

    private String username;

    private String nickname;

    /** 微信号（管理员可见，用于处理纠纷） */
    private String wechat;

    /** QQ 号（管理员可见） */
    private String qq;

    /** 手机号（管理员可见） */
    private String phone;

    private String avatar;

    /** user / admin */
    private String role;

    /** 1 正常 / 0 禁用 */
    private Integer status;

    /** 状态中文：正常 / 已禁用（后端统一给，前端不用自己维护映射表） */
    private String statusText;

    private LocalDateTime createTime;

    /** 发布过的商品总数（所有状态） */
    private Long publishedCount;

    /** 已售出数量 */
    private Long soldCount;

    /** 该用户收藏了多少件 */
    private Long favoriteCount;
}
