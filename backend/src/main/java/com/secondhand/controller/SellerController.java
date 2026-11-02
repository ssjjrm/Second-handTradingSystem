package com.secondhand.controller;

import com.secondhand.common.Result;
import com.secondhand.service.SellerService;
import com.secondhand.vo.PageVO;
import com.secondhand.vo.ProductListVO;
import com.secondhand.vo.SellerProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 卖家模块（接口文档 §6）—— 全部公开。
 *
 * <p>⚠️ 这两个接口都在拦截器白名单里（{@code /api/sellers/**}），
 * 所以游客也能访问。正因为公开，这里返回的字段必须格外小心：
 * <b>绝不能带联系方式</b>，否则等于把全站卖家的微信公开了。
 */
@RestController
@RequestMapping("/api/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    /** 6.1 卖家公开资料 */
    @GetMapping("/{id:\\d+}")
    public Result<SellerProfileVO> profile(@PathVariable Integer id) {
        return Result.success(sellerService.getProfile(id));
    }

    /** 6.2 卖家当前在售的商品 */
    @GetMapping("/{id:\\d+}/products")
    public Result<PageVO<ProductListVO>> products(
            @PathVariable Integer id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.success(sellerService.listOnSale(id, page, size));
    }
}
