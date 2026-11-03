package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 商品表 Mapper */
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 浏览量 +1。
     *
     * <p>两个细节都是有意的：
     *
     * <p><b>1. 用 SQL 自增，而不是"查出来 +1 再写回"。</b>
     * 后者在并发下会丢更新：两个请求同时读到 231，各自算出 232 写回，
     * 结果只涨了 1。写成 {@code view_count = view_count + 1} 是让数据库
     * 在一个原子操作里完成，天然不会丢。
     *
     * <p><b>2. 显式写上 {@code update_time = update_time}。</b>
     * 因为列定义是 {@code ON UPDATE CURRENT_TIMESTAMP}，只要这行数据被改动，
     * 更新时间就会自动刷新 —— 那样一来"看一看商品"也会把它变成"最近更新"，
     * 排序和缓存都会受影响。MySQL 的规则是：**只要 UPDATE 语句里显式写了这一列，
     * 就不再使用自动值**，写成等于自己就能保持原样。
     *
     * @return 影响的行数；0 表示这条商品不存在
     */
    @Update("UPDATE product SET view_count = view_count + 1, update_time = update_time WHERE id = #{id}")
    int incrementViewCount(@Param("id") Integer id);

    /**
     * 「想要」数 +1。
     *
     * <p>和浏览量同理：用 SQL 原子自增，避免并发下"读出来 +1 再写回"丢失更新。
     * {@code favorite_count} 是冗余字段（等于 favorite 表里该商品的行数），
     * 好处是列表页不用 JOIN 就能显示"多少人想要"。
     *
     * <p>⚠️ 冗余字段的代价：每次收藏/取消都必须同步维护它，否则会和对不上。
     * 项目里专门有一致性检查 SQL（见数据库脚本和验证页）。
     */
    @Update("UPDATE product SET favorite_count = favorite_count + 1, update_time = update_time WHERE id = #{id}")
    int incrementFavoriteCount(@Param("id") Integer id);

    /**
     * 「想要」数 -1，<b>但绝不低于 0</b>。
     *
     * <p>{@code GREATEST(favorite_count - 1, 0)} 是为了兜住脏数据：
     * 万一某次操作没配平（比如手工改过库），计数也不会变成负数 ——
     * 负的"想要数"显示在页面上会非常刺眼，而且很难解释。
     * 加了这个兜底，最坏情况是数字不动，而不是变成 -1。
     */
    @Update("UPDATE product SET favorite_count = GREATEST(favorite_count - 1, 0), update_time = update_time WHERE id = #{id}")
    int decrementFavoriteCount(@Param("id") Integer id);
}
