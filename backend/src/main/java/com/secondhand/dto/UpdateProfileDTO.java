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
 *
 * <p>⚠️ 但 <b>昵称是例外</b>：它必填、不允许清空，见下面字段上的注释。
 */
@Data
public class UpdateProfileDTO {

    /**
     * 昵称。
     *
     * <p>⚠️ 注意 {@code min = 1}：昵称**必填，不允许被清空**。
     * 传 {@code null} 表示"不改这个字段"（{@code @Size} 对 null 放行），
     * 传空串会被拒："昵称不能为空"。
     *
     * <p>为什么它和微信/QQ 的规则不一样：昵称是要展示给别人的名字，
     * 一旦为空，商品列表和卖家主页上都会显示空白；
     * 而 {@code @JsonInclude(NON_NULL)} 还会让这个键直接从 JSON 里消失，
     * 前端连"这里本该有个昵称"都看不出来，排查起来很费劲。
     */
    @Size(min = 1, max = 50, message = "昵称不能为空")
    private String nickname;

    @Size(max = 50, message = "微信号不能超过 50 个字符")
    private String wechat;

    @Pattern(regexp = "^$|^[1-9]\\d{4,11}$", message = "QQ号格式不正确")
    private String qq;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    private String avatar;
}
