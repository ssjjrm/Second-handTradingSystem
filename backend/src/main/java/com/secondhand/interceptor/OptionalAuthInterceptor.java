package com.secondhand.interceptor;

import com.secondhand.entity.User;
import com.secondhand.mapper.UserMapper;
import com.secondhand.util.JwtUtil;
import com.secondhand.util.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 「可选身份」拦截器 —— 本项目的身份识别入口。
 *
 * <h3>为什么需要它</h3>
 *
 * <p>商品列表和商品详情是**公开接口**（游客也能看），所以在拦截器白名单里。
 * 但详情接口还要回答两个只有登录后才知道的问题：
 * <ul>
 *   <li>{@code isFavorited} —— 你有没有收藏过它</li>
 *   <li>{@code canContact} —— 你能不能联系卖家</li>
 * </ul>
 *
 * <p>如果把它们放进白名单让拦截器**完全不执行**，那 Controller 里永远
 * 拿不到当前用户，登录的人也会被当成游客 —— 这正是第一版实现踩的坑。
 *
 * <h3>它的做法</h3>
 *
 * <p>它拦截**所有** {@code /api/**}（包括公开路径），只做一件事：
 * 带了有效 token 就把身份写进 {@link UserContext}，否则什么都不做直接放行。
 *
 * <p>于是两个拦截器分工变得很清晰：
 * <pre>
 *   OptionalAuthInterceptor  → 能认出你是谁就认，认不出当游客（永不拦截）
 *   JwtInterceptor           → 只跑需要登录的路径，检查"认出来了吗"，没认出来就 401
 * </pre>
 *
 * <p>注意这里**故意不报错**：token 过期的人浏览商品列表时，应该继续当游客用，
 * 而不是被弹一个 401 打断。等他真要"联系卖家"时，才由 JwtInterceptor 拦下来。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OptionalAuthInterceptor implements HandlerInterceptor {

    /** token 有问题时，把原因放在这个 attribute 里，让 JwtInterceptor 给出更准确的提示 */
    public static final String ATTR_AUTH_ERROR = "authError";

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 预检不带 Authorization 头，直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            // 游客访问，正常现象，什么都不做
            return true;
        }

        String token = header.substring(PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            return true;
        }

        // 验签 + 解析。任何异常都当"没登录"处理，不打扰用户浏览
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            log.debug("token 无效，按游客处理：{}", e.getMessage());
            request.setAttribute(ATTR_AUTH_ERROR, "登录已过期，请重新登录");
            return true;
        }

        Integer userId;
        try {
            userId = Integer.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            request.setAttribute(ATTR_AUTH_ERROR, "登录状态无效，请重新登录");
            return true;
        }

        // 查库确认账号还在、没被禁用 —— 这样管理员一禁用就立即生效，不用等 token 过期
        User user = userMapper.selectById(userId);
        if (user == null) {
            request.setAttribute(ATTR_AUTH_ERROR, "账号不存在，请重新登录");
            return true;
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            request.setAttribute(ATTR_AUTH_ERROR, "账号已被禁用，请联系管理员");
            return true;
        }

        // ✅ 认出来了，写入请求上下文
        UserContext.set(request, user.getId(), user.getUsername(), user.getRole());
        return true;
    }
}
