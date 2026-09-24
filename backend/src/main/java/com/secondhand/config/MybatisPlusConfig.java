package com.secondhand.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>⚠️ 这个类看着简单，但**绝对不能省**。
 *
 * <p>参考项目就是没注册下面的分页插件，导致所有 {@code selectPage()} 悄悄失效 ——
 * 不报错、不抛异常，只是把**全表数据**当成第一页返回。
 * 数据少的时候完全看不出问题，数据一多才发现"分页怎么没生效"。
 * 这是最阴的一类 bug：**静默错误**。
 */
@Configuration
public class MybatisPlusConfig {

    /*
    * 为什么需要 @Bean？
    *你自己写的类，所以在类上加注解就行，如@Service
    *但是第三方提供的类 —— 你改不了它的源码，没法在上面加 @Service
    * */

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最多 100 条，防止前端传 size=99999 把数据库拖垮
        pagination.setMaxLimit(100L);

        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
