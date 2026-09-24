package com.secondhand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

//DTO 是 Data Transfer Object 的缩写，中文叫 数据传输对象。
//一句话理解：
//DTO 就是一个专门用来“搬运数据”的 Java 对象，通常只包含字段和 getter/setter，不包含业务逻辑。

/**
 * 注册入参。
 *
 * <p>校验规则来自接口文档 §3.1。注意 {@code qq} / {@code phone} 是可选的，
 * 但前端如果传了空字符串 {@code ""}，{@code @Pattern} 会判定为不匹配 ——
 * 所以正则写成 {@code ^$|...}，显式允许空串。
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度需在 4~20 字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需在 6~20 字符之间")
    private String password;

    @Size(max = 50, message = "昵称不能超过 50 个字符")
    private String nickname;

    /** 微信号：注册时可以不填，发布商品前必须至少填一个联系方式 */
    @Size(max = 50, message = "微信号不能超过 50 个字符")
    private String wechat;

    @Pattern(regexp = "^$|^[1-9]\\d{4,11}$", message = "QQ号格式不正确")
    private String qq;

//    ^$          |   ^[1-9]\d{4,11}$
//    └┬┘             └──────┬──────┘
//    空字符串          真正的 QQ 号格式
//        └──── 或者 ────┘

//    为什么必须加这个 ^$| ？ 因为 qq 是选填的（可以不填）

    /** 手机号：仅账号找回用，不对外展示 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
