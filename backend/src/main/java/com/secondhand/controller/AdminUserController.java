package com.secondhand.controller;

import com.secondhand.common.Result;
import com.secondhand.service.AdminUserService;
import com.secondhand.vo.AdminUserVO;
import com.secondhand.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 · 用户管理（接口文档 §10.7~10.8）。
 *
 * <p>权限仍然由 {@code AdminInterceptor} 统一把守，本类不写任何角色判断。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 10.7 用户列表 */
    @GetMapping
    public Result<PageVO<AdminUserVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminUserService.list(page, size, keyword));
    }

    /**
     * 10.8 禁用 / 启用：{@code ?status=0} 禁用，{@code ?status=1} 启用。
     *
     * <p>为什么状态放在 query 参数而不是路径里（如 {@code /status/0}）：
     * "设置成什么"是<b>参数</b>，"对谁操作"才是<b>资源</b>。
     * 路径保持 {@code /users/{id}/status} 这个固定形状，前端点两个不同按钮只改参数，不用改 URL 结构。
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Integer id,
                                     @RequestParam Integer status) {
        adminUserService.updateStatus(id, status);
        return Result.success(status == 1 ? "已启用该用户" : "已禁用该用户", null);
    }
}
