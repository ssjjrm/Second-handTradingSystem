package com.secondhand.controller;

import com.secondhand.common.Result;
import com.secondhand.service.AdminStatsService;
import com.secondhand.vo.AdminStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 · 数据统计（接口文档 §10.9）。
 *
 * <p>只有一条只读接口，参数一个都没有 —— 统计口径固定，不做"按时间段查询"。
 * （真要做时间区间统计，应该走另一条 {@code /stats/trend?days=7}，而不是把这条改复杂。）
 */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    /** 10.9 平台数据统计 */
    @GetMapping
    public Result<AdminStatsVO> stats() {
        return Result.success(adminStatsService.getStats());
    }
}
