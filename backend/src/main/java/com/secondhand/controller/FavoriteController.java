package com.secondhand.controller;

import com.secondhand.common.Result;
import com.secondhand.service.FavoriteService;
import com.secondhand.vo.FavoriteCheckVO;
import com.secondhand.vo.FavoriteItemVO;
import com.secondhand.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 收藏（想要）模块，接口文档 §7。
 *
 * <p>四个接口都在 {@code /api/favorites/**} 下，**不在拦截器白名单里**，
 * 所以全部要求登录 —— 收藏本来就是"我的"数据，游客没有收藏夹。
 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * 7.1 收藏。
     *
     * <p>幂等：重复点返回成功，但计数不会重复增加。
     */
    @PostMapping("/{productId:\\d+}")
    public Result<Map<String, Object>> add(@PathVariable Integer productId) {
        long count = favoriteService.addFavorite(productId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("favoriteCount", count);
        return Result.success("已加入想要", data);
    }

    /** 7.2 取消收藏（同样幂等：没收藏过也不报错） */
    @DeleteMapping("/{productId:\\d+}")
    public Result<Map<String, Object>> remove(@PathVariable Integer productId) {
        long count = favoriteService.removeFavorite(productId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("favoriteCount", count);
        return Result.success("已取消想要", data);
    }

    /**
     * 7.3 我的收藏。
     *
     * <p>⚠️ 包含已售出 / 已下架的商品 —— 用户需要知道"我想要的东西被买走了"，
     * 由他自己决定要不要移除。前端根据 {@code product.status} 显示灰色蒙层。
     */
    @GetMapping
    public Result<PageVO<FavoriteItemVO>> mine(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.success(favoriteService.listMine(page, size));
    }

    /** 7.4 检查是否已收藏（详情页初始化「想要」按钮用） */
    @GetMapping("/check/{productId:\\d+}")
    public Result<FavoriteCheckVO> check(@PathVariable Integer productId) {
        return Result.success(favoriteService.check(productId));
    }
}
