package com.secondhand.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 登录出参：token + 用户基本信息 */
@Data
@AllArgsConstructor
public class LoginVO {

    /** JWT，前端存进 localStorage，之后每个请求放在 Authorization 头里 */
    private String token;

    private UserVO user;
}
