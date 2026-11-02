package com.secondhand.service;

import com.secondhand.vo.PageVO;
import com.secondhand.vo.ProductListVO;
import com.secondhand.vo.SellerProfileVO;

/** 卖家模块（接口文档 §6）—— 全部公开，游客也能看 */
public interface SellerService {

    /** 6.1 卖家公开资料（**不含联系方式**） */
    SellerProfileVO getProfile(Integer sellerId);

    /** 6.2 卖家当前在售的商品（**只含 status=1**） */
    PageVO<ProductListVO> listOnSale(Integer sellerId, Integer page, Integer size);
}
