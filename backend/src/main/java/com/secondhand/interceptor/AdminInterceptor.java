package com.secondhand.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.common.Result;
import com.secondhand.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 管理员拦截器。
 *
 * <p>只拦 {@code /api/admin/**}，而且是在 {@link JwtInterceptor} 之后执行 ——
 * 所以能确定"已经登录了"，这里只需要判断角色。
 *
 * <p>普通用户访问后台接口，返回 <b>403</b>（已登录但没权限），
 * 而不是 401（没登录）—— 前端据此决定是"跳登录页"还是"提示无权限"。
 *
 * <p>⚠️ 权限校验必须放在后端。前端把"管理后台"入口藏起来只是界面上的方便，
 * 任何人手工敲 URL 都能绕过 —— 那种做法等于没有权限控制。
 */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        if (UserContext.isAdmin()) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(Result.error(Result.CODE_FORBIDDEN, "需要管理员权限")));
        return false;
    }
}
