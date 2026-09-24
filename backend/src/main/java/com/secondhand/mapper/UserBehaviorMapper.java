package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.UserBehavior;

/**
 * 用户行为表 Mapper。
 *
 * <p>⚠️ 埋点写入的地方**必须包 try-catch**：记录行为失败绝不能影响主业务。
 * 用户想看商品详情，不能因为"写日志表失败"就给他返回 500。
 */
public interface UserBehaviorMapper extends BaseMapper<UserBehavior> {
}
