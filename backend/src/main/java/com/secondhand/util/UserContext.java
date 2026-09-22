package com.secondhand.util;

import com.secondhand.common.BusinessException;
import com.secondhand.common.Result;
import jakarta.servlet.ServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前登录用户上下文。
 *
 * <p>拦截器验完 token 后，把用户信息塞进当前 request；
 * Service / Controller 随时用静态方法取回来，**不用一层层传参数**。
 *
 * <p>为什么不用 ThreadLocal？
 * 用 ThreadLocal 就必须记得在请求结束时 {@code remove()}，
 * 一旦某条路径忘了清理，下一个复用这个线程的请求就会读到上一个人的身份 ——
 * 这是很严重的安全事故。而 Spring 本来就为每个请求准备好了 request 作用域，
 * 存在 request 的 attribute 里，请求结束自动销毁，**不存在忘记清理的问题**。
 */
public final class UserContext {

    private static final String KEY_USER_ID = "currentUserId";
    private static final String KEY_USERNAME = "currentUsername";
    private static final String KEY_ROLE = "currentRole";

    private UserContext() {
    }

    /** 由 JwtInterceptor 调用，把身份写进本次请求 */
    public static void set(ServletRequest request, Integer userId, String username, String role) {
        request.setAttribute(KEY_USER_ID, userId);
        request.setAttribute(KEY_USERNAME, username);
        request.setAttribute(KEY_ROLE, role);
    }

    private static Object getAttribute(String key) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest().getAttribute(key);
    }

    /** 当前用户 id；未登录时返回 null */
    public static Integer getCurrentUserId() {
        return (Integer) getAttribute(KEY_USER_ID);
    }

    /** 当前用户名；未登录时返回 null */
    public static String getCurrentUsername() {
        return (String) getAttribute(KEY_USERNAME);
    }

    /** 当前角色 user / admin；未登录时返回 null */
    public static String getCurrentRole() {
        return (String) getAttribute(KEY_ROLE);
    }

    /** 是否管理员 */
    public static boolean isAdmin() {
        return "admin".equals(getCurrentRole());
    }

    /**
     * 取当前用户 id，未登录直接抛 401。
     *
     * <p>Service 里需要"必须登录"的场景用这个，比到处判空简洁。
     */
    public static Integer requireUserId() {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "请先登录");
        }
        return userId;
    }
}
