package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.Category;

/** 分类表 Mapper。分类数据基本不变，用 BaseMapper 自带方法就够了 */
public interface CategoryMapper extends BaseMapper<Category> {
}
