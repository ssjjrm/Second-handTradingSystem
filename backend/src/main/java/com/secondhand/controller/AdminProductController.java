package com.secondhand.controller;

import com.secondhand.common.Result;
import com.secondhand.service.AdminProductService;
import com.secondhand.vo.PageVO;
import com.secondhand.vo.ProductAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台 · 商品审核（接口文档 §10.1~10.3）。
 *
 * <p>⚠️ 这里的路径全是 {@code /api/admin/**}，会被 {@code AdminInterceptor} 拦住 ——
 * 非管理员访问返回 403。所以本类里**不需要**再写任何权限判断。
 *
 * <p>这也是一个设计取舍：把权限校验放在拦截器（接口层）而不是每个方法里，
 * 好处是不会漏写；代价是 Service 单独被调用时没有这层保护。
 * 对课程项目来说，接口层拦截 + Service 只做业务是最清晰的分工。
 */
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    /** 10.1 待审核商品列表 */
    @GetMapping("/pending")
    public Result<PageVO<ProductAdminVO>> pending(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(adminProductService.listPending(page, size));
    }

    /** 10.2 审核通过：status 0 → 1，商品立刻出现在前台列表 */
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Integer id) {
        adminProductService.approve(id);
        return Result.success("审核通过，商品已上架", null);
    }

    /**
     * 10.3 审核拒绝：status 0 → 4。
     *
     * <p>{@code reason} 是可选的（前端的拒绝弹窗可以留空）。
     * 用 {@code required = false} 是必须的 —— 否则前端不传 body 时会直接 400，
     * 而"不填原因也能拒绝"是产品上允许的。
     */
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Integer id,
                               @RequestBody(required = false) Map<String, String> body) {
        String reason = (body == null) ? null : body.get("reason");
        adminProductService.reject(id, reason);
        return Result.success("已拒绝该商品", null);
    }
}
