package com.secondhand.controller;

import com.secondhand.common.Result;
import com.secondhand.service.CategoryService;
import com.secondhand.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类接口（接口文档 §4）。
 *
 * <p>四个接口全部公开 —— 游客也要能看到分类去浏览商品。
 * 所以它们在 {@code WebConfig} 的拦截器白名单里。
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** 4.1 分类树（一级带 children，附在售商品数） */
    @GetMapping
    public Result<List<CategoryVO>> tree() {
        return Result.success(categoryService.getTree());
    }

    /** 4.2 仅一级分类 */
    @GetMapping("/parents")
    public Result<List<CategoryVO>> parents() {
        return Result.success(categoryService.getParents());
    }

    /** 4.3 二级分类（不传 parentId 则返回全部一级分类） */
    @GetMapping("/children")
    public Result<List<CategoryVO>> children(
            @RequestParam(required = false) Integer parentId) {
        return Result.success(categoryService.getChildren(parentId));
    }

    /** 4.4 热搜词 */
    @GetMapping("/hot-words")
    public Result<List<String>> hotWords() {
        return Result.success(categoryService.getHotWords());
    }
}
