package com.secondhand.service;

import com.secondhand.dto.ProductCreateDTO;
import com.secondhand.dto.ProductQueryDTO;
import com.secondhand.dto.ProductUpdateDTO;
import com.secondhand.entity.Product;
import com.secondhand.vo.PageVO;
import com.secondhand.vo.ProductDetailVO;
import com.secondhand.vo.ProductListVO;
import com.secondhand.vo.ProductMineVO;

/** 商品模块业务接口（S4 做"读"，S5 补"写"） */
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

    // ==================================================================
    //  S5 新增：卖家侧的写操作
    // ==================================================================

    /**
     * 5.4 发布商品。
     *
     * <p>新商品一律 {@code status=0}（待审核），要等管理员审核通过才会出现在前台列表。
     *
     * @return 新商品的 id
     */
    Integer publish(ProductCreateDTO dto);

    /**
     * 5.5 编辑商品。
     *
     * <p>只能改"在售"或"已下架"的商品，且**不重置审核状态**
     * —— 编辑一个在售商品不会让它变回待审。
     */
    void updateProduct(Integer id, ProductUpdateDTO dto);

    /** 5.7 下架：status 1 → 3 */
    void offShelf(Integer id);

    /** 5.8 删除（已售出的不能删） */
    void deleteProduct(Integer id);

    /** 5.9 我发布的商品（带 contactCount / needAttention） */
    PageVO<ProductMineVO> listMine(Integer page, Integer size, Integer status);
}
