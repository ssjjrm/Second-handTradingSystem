package com.secondhand.service;

import com.secondhand.vo.PageVO;
import com.secondhand.vo.ProductAdminVO;

/**
 * 管理后台的商品管理（S5 只做审核三件套，S9 会扩充）。
 *
 * <p>单独一个 Service 而不是塞进 {@code ProductService}：
 * 管理端的业务规则（审核流转）和 C 端的（发布、编辑）几乎不重叠，
 * 混在一起会让 ProductService 越来越胖，也不好找。
 *
 * <p>⚠️ 权限校验不在这里做 —— 它由 {@code AdminInterceptor} 在
 * {@code /api/admin/**} 这一层统一拦住。Service 里再查一遍角色属于重复劳动，
 * 但如果哪天有人绕过 Controller 直接调 Service，就少了这层保护。
 * 本项目的取舍是：**权限是接口层的事，Service 只管业务**。
 */
public interface AdminProductService {

    /** 10.1 待审核商品列表（status=0） */
    PageVO<ProductAdminVO> listPending(Integer page, Integer size);

    /** 10.2 审核通过：status 0 → 1 */
    void approve(Integer productId);

    /** 10.3 审核拒绝：status 0 → 4 */
    void reject(Integer productId, String reason);

    /**
     * 10.4 全部商品（S9 补）。
     *
     * @param status  为 null 表示不限状态；否则只看这一种
     * @param keyword 标题 / 描述 / 品牌模糊匹配，可为空
     */
    PageVO<ProductAdminVO> listAll(Integer page, Integer size, Integer status, String keyword);

    /**
     * 10.5 强制下架（S9 补）：status 1 → 3。
     *
     * <p>和 C 端的 {@code offShelf} 是两件事：
     * 那个只能下架自己的商品，这个**可以下架任何人的**（处理违规商品）。
     */
    void forceOffShelf(Integer productId);
}
