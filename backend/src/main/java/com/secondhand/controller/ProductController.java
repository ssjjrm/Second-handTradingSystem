package com.secondhand.controller;

import com.secondhand.common.Result;
import com.secondhand.dto.ProductCreateDTO;
import com.secondhand.dto.ProductQueryDTO;
import com.secondhand.dto.ProductUpdateDTO;
import com.secondhand.service.ProductService;
import com.secondhand.vo.PageVO;
import com.secondhand.vo.ProductDetailVO;
import com.secondhand.vo.ProductListVO;
import com.secondhand.vo.ProductMineVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商品接口（S4 读 + S5 写）。
 *
 * <p>⚠️ 两条路径规则值得注意：
 *
 * <p><b>1. 白名单里的路径有多精确</b>（见 {@code WebConfig}）：
 * 拦截器放行的是 {@code /api/products} 和 {@code /api/products/{id:[0-9]+}}，
 * 只匹配纯数字 id。所以 {@code /api/products/{id}/contact}（S6）
 * 多了一段路径，**不会被放行**，会正常走登录校验。
 *
 * <p><b>2. 为什么 {@code /mine} 要写成 {@code \d+} 的数字约束</b>：
 * {@code GET /api/products/mine} 和 {@code GET /api/products/{id}} 长得很像。
 * 虽然 Spring 会优先匹配更具体的字面量路径，但把 {@code {id}} 限制成
 * {@code \d+} 之后，这条歧义就彻底没了 —— 写清楚规则，比依赖框架的匹配优先级更可靠。
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ==================================================================
    //  读（公开）
    // ==================================================================

    /**
     * 5.1 商品列表（公开）。
     *
     * <p>参数直接绑定到 {@link ProductQueryDTO}，不用写一堆 {@code @RequestParam}。
     * 它是 GET 请求的查询串，所以这里不加 {@code @RequestBody}。
     */
    @GetMapping
    public Result<PageVO<ProductListVO>> list(ProductQueryDTO query) {
        return Result.success(productService.listProducts(query));
    }

    /**
     * 5.2 商品详情（公开）。
     *
     * <p>有副作用：浏览量 +1、写浏览行为 —— 只在登录且不是自己商品时才写行为。
     */
    @GetMapping("/{id:\\d+}")
    public Result<ProductDetailVO> detail(@PathVariable Integer id) {
        return Result.success(productService.getDetail(id));
    }

    // ==================================================================
    //  写（需登录）
    // ==================================================================

    /**
     * 5.4 发布商品。
     *
     * <p>返回新商品的 id；状态是「待审核」，**不会立刻出现在列表里**。
     */
    @PostMapping
    public Result<Map<String, Object>> publish(@Valid @RequestBody ProductCreateDTO dto) {
        Integer id = productService.publish(dto);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        return Result.success("发布成功，等待管理员审核", data);
    }

    /** 5.5 编辑商品（只能改在售 / 已下架的，且不会重置审核状态） */
    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Integer id, @Valid @RequestBody ProductUpdateDTO dto) {
        productService.updateProduct(id, dto);
        return Result.success("修改成功", null);
    }

    /** 5.7 下架：status 1 → 3 */
    @PutMapping("/{id:\\d+}/off-shelf")
    public Result<Void> offShelf(@PathVariable Integer id) {
        productService.offShelf(id);
        return Result.success("下架成功", null);
    }

    /** 5.8 删除（已售出的不能删；相关收藏和行为记录由外键级联清理） */
    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Integer id) {
        productService.deleteProduct(id);
        return Result.success("删除成功", null);
    }

    /**
     * 5.9 我发布的商品。
     *
     * <p>比普通列表多两个字段：{@code contactCount}（多少人咨询过）
     * 和 {@code needAttention}（在售且有人咨询）。
     */
    @GetMapping("/mine")
    public Result<PageVO<ProductMineVO>> mine(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer status) {
        return Result.success(productService.listMine(page, size, status));
    }
}
