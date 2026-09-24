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

    /** 由 JwtInterceptor 调用，把身份写进本次请求
     *  <p>UserContext 到底怎么"留下"身份的：
     * 每个 HTTP 请求，在服务器内部都有一个对应的"请求对象"（request）。它是这次请求专属的、用完就销毁的。
     * <p>它内部就是一个 key-value 的容器（像一个 Map），你可以往上面存东西：
     * UserContext.set(request, ...) 就是往这个请求对象上贴了张便签，写着"这次请求是 6 号用户发的"。
     * 后面的任何代码（Controller、Service），只要拿到这次请求，就能读这张便签。*/
    public static void set(ServletRequest request, Integer userId, String username, String role) {
        request.setAttribute(KEY_USER_ID, userId);
        request.setAttribute(KEY_USERNAME, username);
        request.setAttribute(KEY_ROLE, role);
    }

    /**
     * getCurrentUser() 的括号里没有 request 参数，那 getAttribute() 是从哪个 request 上取的？
     * <p>关键在 RequestContextHolder.getRequestAttributes() 这一行 —— 它"凭空"找到了当前请求。因为：
     * <li>Tomcat 处理一个请求时，是"一个线程从头干到尾"的。
     * <li>请求进来 → 分配一个线程 → 这个线程一路执行 Filter/Interceptor/Controller/Service → 返回响应 → 线程归还
     * <li>中间全程都是同一个线程。 Spring 就在这个线程上记了一笔："当前这个线程正在处理 3 号请求"。
     * <li>RequestContextHolder 就是去读这个记录。
     *
     * */
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
