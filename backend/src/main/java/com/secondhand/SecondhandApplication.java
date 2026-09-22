package com.secondhand;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校园二手交易平台 · 后端启动类
 *
 * <p>端口 8081（避开参考项目的 8080），库 secondhand_shop。
 *
 * <p>{@code @MapperScan} 让 MyBatis 自动扫描 mapper 包，
 * 这样每个 Mapper 接口上就不用重复写 {@code @Mapper} 了。
 */
@SpringBootApplication
@MapperScan("com.secondhand.mapper")
public class SecondhandApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecondhandApplication.class, args);
        System.out.println("""

                ==================================================
                  Second-hand Trading System  (backend)
                  Health : http://localhost:8081/api/health
                  DB     : http://localhost:8081/api/health/db
                ==================================================
                """);
    }
}
