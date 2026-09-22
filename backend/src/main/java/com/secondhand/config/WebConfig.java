package com.secondhand.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web 层配置：跨域 + 静态资源映射。
 *
 * <p>说明：拦截器（JwtInterceptor / AdminInterceptor）在 S3 阶段才注册，
 * 因为那时才有 JwtInterceptor 这个类。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${upload.path:uploads}")
    private String uploadPath;

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
