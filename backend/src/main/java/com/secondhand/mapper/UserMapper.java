package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.User;

/**
 * 用户表 Mapper。
 *
 * <p>继承 {@code BaseMapper<User>} 就自动拥有单表增删改查（insert / selectById /
 * selectPage / updateById ...），不用自己写 SQL。
 *
 * <p>接口上不用写 {@code @Mapper} —— 启动类的 {@code @MapperScan("com.secondhand.mapper")}
 * 已经统一扫描了。
 */
public interface UserMapper extends BaseMapper<User> {
}
