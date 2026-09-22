package com.secondhand.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改资料入参。
 *
 * <p>所有字段都可选，**只传要改的**。
 *
 * <p>关于"清空"：传空字符串 {@code ""} 表示把这个字段置空；
 * 不传（null）表示不动它。两者的区别在 Service 里用
 * {@code dto.getXxx() != null} 判断。
 */
@Data
public class UpdateProfileDTO {

    @Size(max = 50, message = "昵称不能超过 50 个字符")
    private String nickname;

    @Size(max = 50, message = "微信号不能超过 50 个字符")
    private String wechat;

    @Pattern(regexp = "^$|^[1-9]\\d{4,11}$", message = "QQ号格式不正确")
    private String qq;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    private String avatar;
}
