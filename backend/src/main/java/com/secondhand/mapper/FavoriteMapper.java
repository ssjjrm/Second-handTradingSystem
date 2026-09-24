package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.Favorite;

/**
 * 收藏表 Mapper。
 *
 * <p>这张表有唯一键 {@code uk_user_product}，所以"重复收藏"在数据库层面就被挡住了 ——
 * 不需要先 selectCount 再 insert（那种写法在并发下会漏）。
 */
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
