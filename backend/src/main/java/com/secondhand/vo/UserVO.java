package com.secondhand.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

//VO = View Object，中文常叫视图对象，属于 POJO（简单 Java 对象）家族。
//
// 后缀		               方向
// DTO		         入参	前端 → 后端，装"用户提交了什么"
// VO		         出参	后端 → 前端，装"我让前端看到什么"
// Entit 实体 数据库	 表 ↔ 对象，装"数据库里存了什么"


/**
 * 用户信息出参。
 *
 * <p>这是**唯一**允许把 wechat / qq / phone 带出去的对象
 * （实体上的那几个字段都标了 {@code @JsonIgnore}）。
 *
 * <p>{@code @JsonInclude(NON_NULL)} 的作用：值为 null 的字段不出现在 JSON 里。
 * 所以登录接口只填 6 个字段时，响应里就只有那 6 个，不会冒出一堆 null。
 *
 *<p>为什么非要一个独立的 VO 类：
 *实体是"数据库的形状"，VO 是"接口的形状"，两者需求天然冲突
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)  //Jackson 提供的注解，用来控制 JSON 序列化时哪些字段会被输出。
public class UserVO {

    private Integer id;

    private String username;

    private String nickname;

    /** 微信号（仅本人可见） */
    private String wechat;

    /** QQ 号（仅本人可见） */
    private String qq;

    /** 手机号（仅本人可见，且仅用于账号找回） */
    private String phone;

    private String avatar;

    /** user / admin */
    private String role;

    /** 1 正常 / 0 禁用 */
    private Integer status;

    private LocalDateTime createTime;

    /** 是否已填微信号或 QQ —— 前端据此提前提示"发布商品前请先填联系方式" */
    private Boolean hasContact;

    /** 个人中心统计，只在 /api/auth/me 里返回 */
    private UserStatsVO stats;
}
