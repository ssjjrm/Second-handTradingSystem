package com.secondhand.service;

import com.secondhand.dto.ProductQueryDTO;
import com.secondhand.entity.Product;
import com.secondhand.vo.PageVO;
import com.secondhand.vo.ProductDetailVO;
import com.secondhand.vo.ProductListVO;

/** 商品模块业务接口（S4 只做"读"，发布/编辑/下架在 S5） */
public interface ProductService {

    /** 商品列表（公开，支持关键词/分类/价格/排序/分页） */
    PageVO<ProductListVO> listProducts(ProductQueryDTO query);

    /**
     * 商品详情（公开）。
     *
     * <p>有副作用：浏览量 +1、写浏览行为埋点 —— 但都只在这一个接口里发生。
     */
    ProductDetailVO getDetail(Integer id);

    /**
     * 纯查询：按 id 取商品，不存在返回 null。
     *
     * <p>⚠️ 给内部调用（S5 编辑、S6 标记售出）用的，**不会**改浏览量。
     * 参考项目就是把"查数据"和"浏览量+1"塞在同一个 getById 里，
     * 结果购物车列表循环调用它，刷新一次购物车浏览量就暴涨。
     */
    Product getProductById(Integer id);
}
