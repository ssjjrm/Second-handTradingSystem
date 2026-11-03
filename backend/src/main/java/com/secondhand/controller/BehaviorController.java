package com.secondhand.controller;

import com.secondhand.common.Result;
import com.secondhand.service.BehaviorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 行为埋点模块（接口文档 §8）。
 *
 * <p>商品详情接口已经会自动记录浏览，这个接口是"补充埋点"用的 ——
 * 给那些没有走详情页但也要算一次浏览的场景（比如首页瀑布流的快速预览）。
 *
 * <p>⚠️ 它**永远返回成功**：埋点方法的内部已经吞掉了所有异常，
 * 而且未登录、浏览自己的商品都会被静默跳过。
 * 前端不需要为它做任何错误处理，也不用等它的结果。
 */
@RestController
@RequestMapping("/api/behaviors")
@RequiredArgsConstructor
public class BehaviorController {

    private final BehaviorService behaviorService;

    /** 8.1 记录一次浏览 */
    @PostMapping("/view/{productId:\\d+}")
    public Result<Void> view(@PathVariable Integer productId) {
        behaviorService.recordView(productId);
        return Result.success("已记录", null);
    }
}
