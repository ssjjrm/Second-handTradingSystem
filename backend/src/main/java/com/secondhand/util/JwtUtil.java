package com.secondhand.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类。
 *
 * <p>JWT 由三段组成：{@code 头部.载荷.签名}。
 * 前两段是 Base64 编码的明文（**任何人都能解开看**，所以绝不能放密码、
 * 手机号这类敏感信息），第三段是用密钥算出来的签名，用来防篡改。
 *
 * <p>服务端不需要存 token —— 只要用户带着 token 来，
 * 用同一个密钥验签就能确认"这确实是我签发的、且没被改过"。
 */
@Slf4j
@Component
public class JwtUtil {

    /** 载荷里的自定义字段名 */
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    /** 密钥从配置读，绝不硬编码（硬编码的密钥一旦泄露，任何人都能伪造 token） */
    @Value("${jwt.secret}") //从 Spring 配置里找到 jwt.secret 这个配置项，把它的值赋给下面的字段。
    private String secret;

    /** 有效期，毫秒 */
    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey signingKey() {
        // HS256 要求密钥至少 256 位（32 字节）
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token。
     * 把用户 ID 放进 JWT 的标准字段 subject，再把用户名和角色放进自定义字段，并写入签发时间和过期时间。
     * 最后使用密钥签名，compact() 将这些内容组合成客户端要携带的 token 字符串。
     *
     * @param userId   用户 id（放在标准字段 subject 里）
     * @param username 用户名（自定义声明）
     * @param role     角色 user / admin（自定义声明，用来判断有没有管理权限）
     */
    public String generateToken(Integer userId, String username, String role) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(signingKey())
                .compact();
    }

    /**
     * 解析并验签。
     * 用同一个密钥验签并解析内容。签名不匹配、token 格式无效或已过期时会抛出异常。
     *
     * @throws JwtException 签名不对、格式不对、或已过期 —— 调用方负责捕获
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** token 是否有效（不抛异常版本） */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("token 校验失败：{}", e.getMessage());
            return false;
        }
    }

    /** 取用户 id；解析失败返回 null */
    public Integer getUserId(String token) {
        try {
            return Integer.valueOf(parseToken(token).getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    /** 取用户名；解析失败返回 null */
    public String getUsername(String token) {
        try {
            return parseToken(token).get(CLAIM_USERNAME, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 取角色；解析失败返回 null */
    public String getRole(String token) {
        try {
            return parseToken(token).get(CLAIM_ROLE, String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
