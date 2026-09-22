package com.secondhand.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充。
 *
 * <p>实体上标了 {@code @TableField(fill = FieldFill.INSERT)} 的字段，
 * 在 insert / update 时会自动带上时间，不用每个 Service 都手写。
 *
 * <p>为什么还要配它？因为只写 {@code fill = INSERT} 注解、
 * 却没有 MetaObjectHandler 的话，注解是**不生效**的（字段保持 null）。
 * 数据库虽然有 {@code DEFAULT CURRENT_TIMESTAMP} 兜底，
 * 但那是"插入后再查"才有值，刚 new 出来的对象依然是 null。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // strictInsertFill 只在字段确实有 fill=INSERT 注解、且当前值为 null 时才填
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
