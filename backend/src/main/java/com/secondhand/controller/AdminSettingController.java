package com.secondhand.controller;

import com.secondhand.common.Result;
import com.secondhand.config.ReviewSwitch;
import com.secondhand.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理后台 · 业务开关（**开发调试用，不属于那 35 个业务接口**）。
 *
 * <p>目前只有一个开关：<b>发布商品是否需要审核</b>。
 * 有了它，测收藏 / 咨询 / 卖家主页这些功能时不用每次都
 * "先发布 → 再切管理员账号审核一次"。
 *
 * <p>放在 {@code /api/admin/**} 下面，所以自动享受 {@code AdminInterceptor} 的保护 ——
 * 普通用户改不了。这一点很重要：如果这个开关挂在公开路径上，
 * 任何人都能把审核关掉，那就等于审核功能不存在。
 *
 * <p>⚠️ 运行时切换的值**只存在内存**里，重启后端就回到
 * {@code application.yml} 的 {@code app.review.enabled}。这是刻意的设计：
 * 免得某次调试忘了关，第二天启动还以为自己在正式模式。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingController {

    private final ReviewSwitch reviewSwitch;

    /**
     * 查当前审核开关状态。
     *
     * <p>顺带返回 {@code configValue}（配置文件里的值），
     * 前端据此提示"重启后会回到哪个值"，避免线上改完忘了改配置。
     */
    @GetMapping("/review")
    public Result<Map<String, Object>> getReviewSwitch() {
        return Result.success(buildPayload());
    }

    /**
     * 切换审核开关。
     *
     * @param enabled true = 发布后待审核（正式）；false = 发布即上架（测试）
     */
    @PutMapping("/review")
    public Result<Map<String, Object>> setReviewSwitch(@RequestParam Boolean enabled) {
        boolean old = reviewSwitch.setEnabled(Boolean.TRUE.equals(enabled));
        log.warn("管理员 {} 把商品审核开关从 {} 改为 {}",
                UserContext.getCurrentUsername(),
                old ? "开启" : "关闭", reviewSwitch.isEnabled() ? "开启" : "关闭");

        return Result.success(
                reviewSwitch.isEnabled()
                        ? "审核已开启：新发布的商品需要管理员审核后才上架"
                        : "审核已关闭（测试模式）：新发布的商品直接上架",
                buildPayload());
    }

    private Map<String, Object> buildPayload() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", reviewSwitch.isEnabled());
        data.put("configValue", reviewSwitch.getConfigValue());
        data.put("configKey", "app.review.enabled");
        return data;
    }
}
