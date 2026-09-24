package com.secondhand.config;

import com.secondhand.interceptor.AdminInterceptor;
import com.secondhand.interceptor.JwtInterceptor;
import com.secondhand.interceptor.OptionalAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web 层配置：拦截器 + 跨域 + 静态资源映射。
 * 实现 WebMvcConfigurer 接口，重写其中的方法。Spring 会在启动时调用这些方法，从而应用你的配置。
 *
 * <p>WebMvcConfigurer 就是 Spring MVC 提供的"设置面板"。 你想改哪部分行为，就重写对应的方法，其他不重写的保持默认。
 * 这种设计叫"回调"：你不用主动调用它，你只要告诉它"当需要配置时执行我这段代码"。
 */
@Configuration  //声明"这是一个配置类"
@RequiredArgsConstructor  //Lombok 为所有 final 字段生成一个构造器
public class WebConfig implements WebMvcConfigurer {

    private final OptionalAuthInterceptor optionalAuthInterceptor;
    private final JwtInterceptor jwtInterceptor;
    private final AdminInterceptor adminInterceptor;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    /**
     * 注册拦截器（三个，按 order 依次执行）。
     *
     * <p><b>为什么是三个而不是一个？</b>
     * 「公开接口」和「必须登录的接口」本身就是两类东西，
     * 但公开接口里又有一类（如商品详情）"登录了的话还得知道你是谁"。
     * 用一个拦截器 + 白名单做不到这件事 —— 白名单里的路径拦截器根本不执行，
     * 身份就丢了。拆开之后各管一段：
     *
     * <pre>
     *   order 1  OptionalAuthInterceptor  拦所有 /api/**，认出身份就记下，认不出当游客（永不拒绝）
     *   order 2  JwtInterceptor           只跑非白名单路径，检查有没有身份，没有就 401
     *   order 3  AdminInterceptor         只跑 /api/admin/**，检查是不是管理员，不是就 403
     * </pre>
     *
     * <p><b>白名单是这里最容易出错的地方。</b>参考项目的坑：它把
     * {@code /api/products/**} 整体放行，结果连 {@code /api/products/{id}/contact}
     * （获取卖家联系方式）也一起放行了 —— 游客不用登录就能拿到卖家的微信号。
     *
     * <p>所以这里的白名单**必须精确到接口**：
     * <ul>
     *   <li>{@code /api/products} —— 列表，游客可看</li>
     *   <li>{@code /api/products/{id:[0-9]+}} —— 详情，游客可看。
     *       注意正则 {@code [0-9]+}：只匹配纯数字 id，
     *       而 {@code /api/products/5/contact} 多了一段，
     *       <b>不会被放行</b>，它会正常落到 JwtInterceptor 上做登录校验 ✅</li>
     * </ul>
     *
     * <p>原则：白名单只放"确定不需要登录"的接口。
     * 宁可漏放（多要求一次登录），也不能错放（把隐私接口暴露出去）。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ① 可选身份：拦所有 /api/**，永不拒绝。
        //    带了有效 token 就把身份写进 UserContext，没带就当游客。
        registry.addInterceptor(optionalAuthInterceptor)
                .addPathPatterns("/api/**")
                .order(1);

        // ② 必须登录：只跑不在白名单里的路径
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        // 健康检查（开发调试用）
                        "/api/health",
                        "/api/health/**",
                        // 注册登录
                        "/api/auth/register",
                        "/api/auth/login",
                        // 分类：游客也能看到分类树
                        "/api/categories",
                        "/api/categories/**",
                        // 商品：列表和详情游客可看
                        "/api/products",
                        "/api/products/{id:[0-9]+}",
                        // 卖家主页：游客可看
                        "/api/sellers/**"
                )
                .order(2);

        // ③ 管理后台：必须是管理员。它在前面两个之后执行，所以此刻已确认登录
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**")
                .order(3);
    }

    /**
     * 跨域配置。
     *
     * <p>两处要注意：
     * <ol>
     *   <li>用 {@code allowedOriginPatterns} 而不是 {@code allowedOrigins} ——
     *       后者填 {@code "*"} 时不能和 {@code allowCredentials(true)} 共存，启动会直接报错</li>
     *   <li>S2 阶段验证页是用 {@code file://} 打开的，它的 Origin 是 {@code null}，
     *       所以这里必须放宽；到 S10 前端做完后可以收窄成只有 5174</li>
     * </ol>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 静态资源：把 URL {@code /uploads/xxx.jpg} 映射到本地磁盘的 uploads 目录。
     *
     * <p>上传的头像 / 商品图存本地，数据库里只存 URL 路径。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get(uploadPath).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
