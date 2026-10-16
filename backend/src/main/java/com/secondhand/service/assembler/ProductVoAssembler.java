package com.secondhand.service.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.common.ProductStatus;
import com.secondhand.entity.Category;
import com.secondhand.entity.Product;
import com.secondhand.entity.User;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.vo.ProductListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品 VO 组装器。
 *
 * <p><b>为什么单独抽出来：</b>C 端列表、我发布的、管理后台待审列表
 * 这三处都要把 {@code Product} 实体拼成 VO（补分类名、卖家昵称、解析图片 JSON…）。
 * 如果每个 Service 各写一份，迟早会出现"某处补了 categoryName、另一处忘了"这种不一致，
 * 而且改一次要改三处。
 *
 * <p>这里遵循两条原则：
 * <ol>
 *   <li><b>批量加载，不在循环里查库</b> —— {@link #loadCategories} /
 *       {@link #loadSellers} 都是一次查出所有涉及的行，再在内存里对上号</li>
 *   <li><b>不返回联系方式</b> —— {@link ProductListVO} 类型上就没有
 *       wechat / qq / phone 字段，想漏也漏不出去</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductVoAssembler {

    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    /** 一次查出这批商品涉及的所有分类 */
    public Map<Integer, Category> loadCategories(List<Product> products) {
        Set<Integer> ids = products.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    /** 一次查出这批商品涉及的所有卖家 */
    public Map<Integer, User> loadSellers(List<Product> products) {
        Set<Integer> ids = products.stream()
                .map(Product::getSellerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /**
     * 把商品的公共字段填进 VO（列表项和详情共用的部分）。
     *
     * @param category 可以为 null（分类被删掉了，外键是 SET NULL）
     * @param seller   可以为 null（理论上不会，seller_id 是 NOT NULL）
     */
    public void fillCommon(ProductListVO vo, Product p, Category category, User seller) {
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setPrice(p.getPrice());
        vo.setOriginalPrice(p.getOriginalPrice());
        vo.setConditionLevel(p.getConditionLevel());
        vo.setImages(parseImages(p.getImages()));
        vo.setBrand(p.getBrand());
        vo.setCategoryId(p.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getName());
        vo.setTradePlace(p.getTradePlace());
        vo.setStatus(p.getStatus());
        // 状态中文由后端统一给，前端不用自己维护一张映射表
        vo.setStatusText(ProductStatus.textOf(p.getStatus()));
        vo.setViewCount(p.getViewCount());
        vo.setFavoriteCount(p.getFavoriteCount());
        vo.setSellerId(p.getSellerId());
        vo.setSellerNickname(seller == null ? null : seller.getNickname());
        vo.setSellerAvatar(seller == null ? null : seller.getAvatar());
        vo.setCreateTime(p.getCreateTime());
    }

    /**
     * 把数据库里的 JSON 字符串解析成数组。
     *
     * <p>解析失败不能让接口挂掉 —— 返回空数组即可。
     * 前端顶多显示一个占位图，总比整页 500 好。
     */
    public List<String> parseImages(String imagesJson) {
        if (!StringUtils.hasText(imagesJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("商品图片 JSON 解析失败，已按空数组处理：{}", imagesJson);
            return List.of();
        }
    }
}
