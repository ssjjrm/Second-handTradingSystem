package com.secondhand.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * Jackson 时间格式配置。
 *
 * <p>⚠️ 为什么必须有这个类：
 * {@code application.yml} 里的 {@code spring.jackson.date-format} 只对
 * 老的 {@code java.util.Date} 生效，对 {@code LocalDateTime} **完全无效** ——
 * 不配这个类的话，接口返回的时间会是 {@code 2026-09-22T19:43:10} 这种 ISO 格式，
 * 而前端期望的是 {@code 2026-09-22 19:43:10}。
 *
 * <p>这里统一给 {@code LocalDateTime} 装上序列化器（转 JSON）和反序列化器（收 JSON），
 * 两边格式保持一致。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.serializers(new LocalDateTimeSerializer(DATE_TIME));
            builder.deserializers(new LocalDateTimeDeserializer(DATE_TIME));
        };
    }
}
