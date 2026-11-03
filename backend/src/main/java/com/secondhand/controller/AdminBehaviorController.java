package com.secondhand.controller;

import com.secondhand.common.Result;
import com.secondhand.service.AdminBehaviorService;
import com.secondhand.vo.BehaviorLogPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 · 咨询记录（接口文档 §10.6）。
 *
 * <p>⚠️ 这是后台里<b>唯一一条"只读"的列表</b>：行为流水只追加、不修改、不删除，
 * 所以这里没有 POST / PUT / DELETE。真要清理只能靠 SQL 或定时任务，
 * 而且那属于运维操作，不该做成按钮放在管理界面上（一点就毁掉统计口径）。
 */
@RestController
@RequestMapping("/api/admin/behaviors")
@RequiredArgsConstructor
public class AdminBehaviorController {

    private final AdminBehaviorService adminBehaviorService;

    /** 10.6 行为/咨询记录（可按类型、商品、用户筛选） */
    @GetMapping
    public Result<BehaviorLogPageVO> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Integer behaviorType,
            @RequestParam(required = false) Integer productId,
            @RequestParam(required = false) Integer userId) {
        return Result.success(
                adminBehaviorService.list(page, size, behaviorType, productId, userId));
    }
}
