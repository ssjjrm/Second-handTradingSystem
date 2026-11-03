package com.secondhand.service;

import com.secondhand.vo.FavoriteCheckVO;
import com.secondhand.vo.FavoriteItemVO;
import com.secondhand.vo.PageVO;

/**
 * 收藏（页面上的「想要」）模块，接口文档 §7。
 *
 * <p>两个关键设计：
 * <ol>
 *   <li><b>幂等</b>：重复收藏不报错也不重复 +1；没收藏过就取消同样不报错。
 *       用户快速连点两下不该看到"操作失败"</li>
 *   <li><b>计数用 SQL 原子更新</b>：{@code favorite_count} 是冗余字段，
 *       必须和 {@code favorite} 表保持同步，且并发下不能丢更新</li>
 * </ol>
 */
public interface FavoriteService {

    /**
     * 7.1 收藏（幂等）。
     *
     * @return 操作后的商品总「想要」数（前端直接拿去更新按钮上的数字）
     */
    long addFavorite(Integer productId);

    /**
     * 7.2 取消收藏（幂等）。
     *
     * @return 操作后的商品总「想要」数
     */
    long removeFavorite(Integer productId);

    /** 7.3 我的收藏（**包含已售出/已下架的商品**） */
    PageVO<FavoriteItemVO> listMine(Integer page, Integer size);

    /** 7.4 检查某商品是否已收藏 */
    FavoriteCheckVO check(Integer productId);
}
