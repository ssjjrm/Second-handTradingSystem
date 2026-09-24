package com.secondhand.controller;

import com.secondhand.common.Result;
import com.secondhand.dto.ProductQueryDTO;
import com.secondhand.service.ProductService;
import com.secondhand.vo.PageVO;
import com.secondhand.vo.ProductDetailVO;
import com.secondhand.vo.ProductListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口（S4 只做"读"：列表 + 详情）。
 *
 * <p>⚠️ 白名单的注意事项（见 {@code WebConfig}）：
 * 拦截器放行的是 {@code /api/products} 和 {@code /api/products/{id:[0-9]+}}，
 * 只匹配纯数字 id。后面 S6 的 {@code /api/products/{id}/contact}
 * 多了一段路径，**不会被放行**，会正常走登录校验。
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

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
    @GetMapping("/{id}")
    public Result<ProductDetailVO> detail(@PathVariable Integer id) {
        return Result.success(productService.getDetail(id));
    }
}
