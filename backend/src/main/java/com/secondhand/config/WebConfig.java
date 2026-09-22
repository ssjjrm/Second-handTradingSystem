package com.secondhand.config;

import com.secondhand.interceptor.AdminInterceptor;
import com.secondhand.interceptor.JwtInterceptor;
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
 */
@Configuration  //声明"这是一个配置类"
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AdminInterceptor adminInterceptor;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    /**
     * 注册拦截器。
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
                );

        // 管理后台：必须是管理员。它在 JwtInterceptor 之后执行，所以此刻已确认登录
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**");
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
