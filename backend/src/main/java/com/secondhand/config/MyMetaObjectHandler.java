package com.secondhand.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/*
* @Configuration 和 @Component 的关系
你可能注意到了：它们看起来作用一样（都是"交给 Spring 管"）。

它们确实是"亲戚"：

复制
@Component                 ← 最基础："这是个 Bean"
   ├─ @Service             ← 语义：业务层（你用得多）
   ├─ @Repository          ← 语义：数据访问层
   ├─ @Controller          ← 语义：接口层
   └─ @Configuration       ← 语义：配置类 ⭐
功能上几乎一样（都能被扫描注册），区别是**"给人看的语义"** —— 以及 @Configuration 有个额外能力：它里面的 @Bean 方法之间互相调用时，Spring 会保证拿到同一个对象（这叫"代理"机制，你现在不用管）。

所以你可以这样理解：

@Configuration 就是"配置版的 @Component" —— 一样的东西，只是名字告诉你"这里是配置，别放业务代码"。
* */

/**
 * MyBatis-Plus 字段自动填充。
 *
 * 它解决的是你在 tlias 里手写的那两行：

 * <p>tlias你每个新增/修改都要写这两行：
 * <li>dept.setCreateTime(LocalDateTime.now());
 * <li>dept.setUpdateTime(LocalDateTime.now());
 * <p>
 * <p>它为什么用 @Component 而不是 @Configuration？
 * 因为它不提供新对象、也不改配置，它只是实现 MyBatis-Plus 要求的一个接口（MetaObjectHandler）。
 * MyBatis-Plus 说："你在 Spring 里放一个实现了这个接口的 Bean，我会在插入/更新时调它。"
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // strictInsertFill 只在字段确实有 fill=INSERT 注解、且当前值为 null 时才填；
        // 实体上没这个字段会自动跳过，所以可以放心把"所有时间列"都列出来。
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        // ⚠️ user_behavior 表的时间列叫 behavior_time，不是 create_time
        strictInsertFill(metaObject, "behaviorTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
