package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.UserBehavior;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户行为表 Mapper。
 *
 * <p>⚠️ 埋点写入的地方**必须包 try-catch**：记录行为失败绝不能影响主业务。
 * 用户想看商品详情，不能因为"写日志表失败"就给他返回 500。
 * （这条由 {@code BehaviorService} 统一保证，业务代码不用自己写 try-catch。）
 */
public interface UserBehaviorMapper extends BaseMapper<UserBehavior> {

    /**
     * 我这个卖家一共被多少人咨询过（跨所有商品，按人去重）。
     *
     * <p>这是「信息撮合」模式下最接近"成交"的指标 ——
     * 平台不产生订单，所以"有多少人点过获取联系方式"就是卖家最该关心的数字。
     *
     * <p>⚠️ 必须 {@code COUNT(DISTINCT user_id)}：
     * 同一个人对同一件商品点两次，只能算 1 个人想要；
     * 同一个人对你的两件商品都感兴趣，也只算 1 个人（他毕竟只是一个潜在买家）。
     *
     * <p>这里用 JOIN 而不是"先查出我的所有商品 id 再 IN"，是因为商品一多
     * {@code IN} 列表会变得很长，而 JOIN 交给数据库优化器处理更稳。
     */
    @Select("""
            SELECT COUNT(DISTINCT b.user_id)
            FROM user_behavior b
            JOIN product p ON p.id = b.product_id
            WHERE b.behavior_type = 3
              AND p.seller_id = #{sellerId}
            """)
    Long countReceivedContacts(@Param("sellerId") Integer sellerId);
}
