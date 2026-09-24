package com.secondhand.service;

import com.secondhand.vo.CategoryVO;

import java.util.List;

/** 分类模块业务接口 */
public interface CategoryService {

    /** 两级分类树（一级带 children），带在售商品数 */
    List<CategoryVO> getTree();

    /** 只要一级分类（不带 children） */
    List<CategoryVO> getParents();

    /** 某个一级分类下的二级分类；parentId 为空时返回全部一级分类 */
    List<CategoryVO> getChildren(Integer parentId);

    /** 热搜词（静态配置） */
    List<String> getHotWords();
}
