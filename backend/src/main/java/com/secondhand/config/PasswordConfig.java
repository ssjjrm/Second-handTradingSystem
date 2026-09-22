package com.secondhand.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密配置。
 *
 * <p>用 BCrypt，是因为它有两个别的算法比不了的特性：
 * <ol>
 *   <li><b>自带随机 salt</b> —— 同一个密码每次加密结果都不同，防彩虹表</li>
 *   <li><b>故意算得慢</b> —— 暴力破解的成本极高（默认强度 10，约 2^10 次迭代）</li>
 * </ol>
 *
 * <p>⚠️ 参考项目的致命缺陷：注册时直接把明文密码存进数据库
 * （{@code user.setPassword(dto.getPassword())}）。任何拿到数据库的人
 * 立刻就能登录所有人的账号。本项目的密码字段**只存 BCrypt 密文**。
 *
 * <p>注意 {@code matches(明文, 密文)} 是唯一的校验方式，
 * **不能用 {@code equals} 比较** —— 因为每次 encode 结果都不一样。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
