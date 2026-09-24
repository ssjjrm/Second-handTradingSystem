package com.secondhand.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.common.Result;
import com.secondhand.util.UserContext;
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

③ 之后每个请求 —— 现在由【两个】拦截器接力完成（原来是合成一个，见下方说明）

OptionalAuthInterceptor.preHandle      （拦所有 /api/**，永不拒绝）
     ├─ OPTIONS → 放行
     ├─ 取 Authorization 头 → 剥掉 "Bearer "
     ├─ JwtUtil.parseToken() → 失败 → 记为游客，照样放行
     ├─ 查库确认账号存在且没被禁用        ← tlias 没有这一步
     └─ UserContext.set(request, id, username, role)   ← 【关键：留下了身份】

JwtInterceptor.preHandle                （只拦需要登录的路径）
     └─ UserContext 里没身份 → 401 JSON

④ Controller / Service 执行
   UserContext.requireUserId()   ← 【随时能问"当前是谁"】
*/

/**
 * 「必须登录」拦截器。
 *
 * <p><b>为什么从"一个拦截器"拆成了"两个"</b>（这是一次真实的踩坑）：
 *
 * <p>原来只有这一个拦截器，白名单里放行了 {@code /api/products/{id}}。
 * 结果发现：登录用户访问商品详情时，{@code canContact} 永远返回
 * "请先登录后再联系卖家" —— 因为白名单里的路径**拦截器根本不执行**，
 * {@code UserContext} 是空的，登录的人也被当成了游客。
 *
 * <p>矛盾的根源：一个拦截器没法同时干两件相反的事 ——
 * "这条路径不需要登录" 和 "但登录了的话我要知道你是谁"。
 *
 * <p>拆成两个之后各自单纯：{@link OptionalAuthInterceptor} 负责**认出身份**
 * （认不出也不拦），本类负责**检查有没有身份**。顺序由 {@code WebConfig}
 * 的 {@code .order(1)} / {@code .order(2)} 固定。
 *
 * <p>⚠️ 两个坑：
 * <ol>
 *   <li><b>CORS 预检必须放行</b>：浏览器发 OPTIONS 时不会带 Authorization 头，
 *       拦下来会导致所有跨域请求失败，而且报错信息完全看不出原因</li>
 *   <li><b>返回要用 response 直接写</b>，不要抛异常 —— 抛出去会被
 *       {@code GlobalExceptionHandler} 兜底成 500，前端就分不清"没登录"和"服务器炸了"</li>
 * </ol>
 */
@Slf4j // 编译时自动为类生成一个日志对象
@Component  // 把当前类标记为一个 Spring Bean，交给 Spring 容器管理。
@RequiredArgsConstructor  // 为类中所有 final 字段 和 @NonNull 字段 生成一个构造器。
public class JwtInterceptor implements HandlerInterceptor {

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

        // 2. 身份是谁放进去的？
        //    OptionalAuthInterceptor 排在前面（order 1），它拦所有 /api/**，
        //    带了有效 token 就把身份写进 UserContext。
        //    所以这里只要判断"认出来了没有"就够了 ——
        //    不用再解析 token、也不用再查一次库（那些活儿前面已经干完了）。
        if (UserContext.getCurrentUserId() != null) {
            return true;
        }

        // 3. 没认出来 → 401。
        //    如果前置拦截器记下了具体原因（token 过期 / 账号被禁用 / 账号不存在），
        //    这里就用那个更准确的提示；否则说明压根没带 token。
        String reason = (String) request.getAttribute(OptionalAuthInterceptor.ATTR_AUTH_ERROR);
        return reject(response, reason != null ? reason : "请先登录");
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
