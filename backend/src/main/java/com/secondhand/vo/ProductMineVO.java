package com.secondhand.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 「我发布的商品」列表项（接口文档 §5.9）。
 *
 * <p>比普通列表项多两个字段，而这两个字段正是**代替订单系统的关键**：
 * <ul>
 *   <li>{@code contactCount} —— 多少人点了「获取联系方式」（去重用户数）。
 *       平台不产生订单，所以这是最接近"有人想买"的信号</li>
 *   <li>{@code needAttention} —— 是否该去处理了（在售 且 有人咨询过）。
 *       前端据此在卡片上加醒目角标，提醒卖家"有人想要，成交后记得标记售出"</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductMineVO extends ProductListVO {

    /** 多少人咨询过（按 user_id 去重，同一人点多次只算一次） */
    private Long contactCount;

    /** 是否需要卖家处理：status=1 且 contactCount>0 */
    private Boolean needAttention;
}
