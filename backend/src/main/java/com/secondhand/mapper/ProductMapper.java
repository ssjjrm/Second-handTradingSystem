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
}
