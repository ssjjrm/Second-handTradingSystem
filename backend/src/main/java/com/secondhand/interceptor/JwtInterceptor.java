package com.secondhand.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.common.Result;
import com.secondhand.entity.User;
import com.secondhand.mapper.UserMapper;
import com.secondhand.util.JwtUtil;
import com.secondhand.util.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/*① 登录
AuthController.login → UserServiceImpl.login
            ├─ BCrypt 校验密码（加密存储）
            ├─ JwtUtil.generateToken(id, username, role)
            └─ 返回 { token, user }

② 前端存 token

③ 之后每个请求
JwtInterceptor.preHandle
     ├─ OPTIONS → 放行
     ├─ 取 Authorization 头 → 剥掉 "Bearer "
     ├─ JwtUtil.parseToken() → 失败 → 返回 401 JSON
     ├─ 查库确认账号存在且没被禁用        ← tlias 没有这一步
     └─ UserContext.set(request, id, username, role)   ← 【关键：留下了】

④ Controller / Service 执行
   UserContext.requireUserId()   ← 【随时能问"当前是谁"】
*/

/**
 * 登录拦截器。
 *
 * <p>执行顺序：
 * <pre>
 * 请求 → JwtInterceptor
 *         ├─ OPTIONS 预检        → 直接放行
 *         ├─ 没有 Authorization  → 401 JSON
 *         ├─ token 无效 / 过期   → 401 JSON
 *         ├─ 账号不存在 / 被禁用  → 401 JSON
 *         └─ 都通过 → 写入 UserContext → 交给 Controller
 * </pre>
 *
 * <p>⚠️ 三个坑：
 * <ol>
 *   <li><b>CORS 预检必须放行</b>：浏览器发 OPTIONS 时不会带 Authorization 头，
 *       拦下来会导致所有跨域请求失败，而且报错信息完全看不出原因</li>
 *   <li><b>返回要用 response 直接写</b>，不要抛异常 —— 抛出去会被
 *       {@code GlobalExceptionHandler} 兜底成 500，前端就分不清"没登录"和"服务器炸了"</li>
 *   <li><b>要 setCharacterEncoding</b>，否则中文提示是乱码</li>
 * </ol>
 *
 * <p>这里多查了一次数据库（确认账号还在、没被禁用），
 * 好处是管理员禁用用户后**立即生效**，不用等 token 24 小时过期。
 */
@Slf4j // 编译时自动为类生成一个日志对象
@Component  // 把当前类标记为一个 Spring Bean，交给 Spring 容器管理。
@RequiredArgsConstructor  // 为类中所有 final 字段 和 @NonNull 字段 生成一个构造器。
public class JwtInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    /**
    *返回值 boolean：return true放行，return false拦住
    *preHandle 的意思：handle = 处理请求的那段代码（Controller 方法），pre = 在它之前。
    * */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        // 1. CORS 预检请求直接放行
        /*
        * 你的前端在 5174 端口，后端在 8081 端口。端口不同 = 跨域。新项目前后端分端口开发，一定会遇到。
        * 浏览器有个安全规矩：跨域发请求前，先发一个"试探请求"问后端"我能给你发请求吗？"
        * 那个 OPTIONS 预检请求，浏览器按规范不带 Authorization 头。
        * 如果拦截器不放过 OPTIONS：拦截器：没有 token → 401 ❌
         * */
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // 2. 取 Authorization 头
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return reject(response, "请先登录");
        }
        String token = header.substring(PREFIX.length()).trim();
        if (token.isEmpty()) {
            return reject(response, "请先登录");
        }

        // 3. 解析 parseToken(token)
        // JwtUtil.parseToken() → 失败 → 返回 401 JSON
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (ExpiredJwtException e) {
            return reject(response, "登录已过期，请重新登录");
        } catch (Exception e) {
            log.debug("token 解析失败：{}", e.getMessage());
            return reject(response, "登录状态无效，请重新登录");
        }

        Integer userId;
        try {
            userId = Integer.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            return reject(response, "登录状态无效，请重新登录");
        }

        // 4. 查库：确认账号仍然存在且没被禁用
        User user = userMapper.selectById(userId);
        if (user == null) {
            return reject(response, "账号不存在，请重新登录");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return reject(response, "账号已被禁用，请联系管理员");
        }

        // 5. 写入请求上下文，后续用 UserContext 取
        UserContext.set(request, user.getId(), user.getUsername(), user.getRole());
        return true;
    }

    /**
     * 把拒绝逻辑收进一个方法。
     * 统一输出 401 JSON */
    private boolean reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // ← 设 HTTP 状态码
        response.setCharacterEncoding("UTF-8"); // ← 防中文乱码
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(Result.error(Result.CODE_UNAUTHORIZED, message)));
        return false;  // ← 返回 false：拦住
    }
}
