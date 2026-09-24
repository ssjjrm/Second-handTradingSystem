package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.secondhand.common.ProductStatus;
import com.secondhand.entity.Category;
import com.secondhand.entity.Product;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.service.CategoryService;
import com.secondhand.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类模块实现。
 *
 * <p><b>本类最重要的两个字是"避免 N+1"。</b>
 *
 * <p>分类树有 9 个一级 + 41 个二级。如果按最直白的写法：
 * 先查一级，再 for 循环给每个一级查一次子分类 —— 那就是 1 + 9 = 10 次数据库往返。
 * 这里改成：**一次把 50 条全查出来**，在内存里用 Map 按 parentId 分组拼装，只花 1 次查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    /** 热搜词（接口文档 §4.4：静态配置即可，后期可改成按搜索量统计） */
    private static final List<String> HOT_WORDS = List.of(
            "耳机", "平板", "自行车", "键盘", "考研", "吉他", "显示器", "收纳");

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    @Override
    public List<CategoryVO> getTree() {
        // ① 一次查出全部 50 个分类（按 sortOrder 排好）
        List<Category> all = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder));

        // ② 一次查出"每个分类有多少件在售商品"，在内存里统计
        Map<Integer, Long> countMap = countOnSaleByCategory();

        // ③ 全部转成 VO
        List<CategoryVO> voList = all.stream().map(c -> {
            CategoryVO vo = new CategoryVO();
            vo.setId(c.getId());
            vo.setName(c.getName());
            vo.setParentId(c.getParentId());
            vo.setSortOrder(c.getSortOrder());
            vo.setProductCount(countMap.getOrDefault(c.getId(), 0L));
            return vo;
        }).collect(Collectors.toList());

        // ④ 在内存里分组：key = 父分类 id，value = 它的子分类列表
        Map<Integer, List<CategoryVO>> childrenMap = voList.stream()
                .filter(vo -> vo.getParentId() != null)
                .collect(Collectors.groupingBy(CategoryVO::getParentId,
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            list.sort(Comparator.comparing(CategoryVO::getSortOrder,
                                    Comparator.nullsLast(Comparator.naturalOrder())));
                            return list;
                        })));

        // ⑤ 组装一级分类
        List<CategoryVO> roots = voList.stream()
                .filter(vo -> vo.getParentId() == null)
                .sorted(Comparator.comparing(CategoryVO::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .peek(root -> {
                    List<CategoryVO> children = childrenMap.getOrDefault(root.getId(), List.of());
                    root.setChildren(children.isEmpty() ? null : children);

                    // 商品挂在二级分类上，所以一级分类的数量要把子分类的加起来
                    long sum = root.getProductCount() == null ? 0L : root.getProductCount();
                    for (CategoryVO child : children) {
                        sum += child.getProductCount() == null ? 0L : child.getProductCount();
                    }
                    root.setProductCount(sum);
                })
                .collect(Collectors.toList());

        log.debug("分类树组装完成：一级 {} 个，共 {} 条", roots.size(), voList.size());
        return roots;
    }

    @Override
    public List<CategoryVO> getParents() {
        // 直接复用 getTree 的结果，把 children 去掉即可 —— 保证两处的排序和数量一致。
        // （用 map 而不是 peek：peek 是给调试用的，在流里改对象属于副作用，容易看漏。）
        return getTree().stream()
                .map(vo -> {
                    vo.setChildren(null);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryVO> getChildren(Integer parentId) {
        // 不传 parentId 时按接口文档的要求，返回全部一级分类
        if (parentId == null) {
            return getParents();
        }

        List<Category> children = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getParentId, parentId)
                        .orderByAsc(Category::getSortOrder));

        Map<Integer, Long> countMap = countOnSaleByCategory();

        return children.stream().map(c -> {
            CategoryVO vo = new CategoryVO();
            vo.setId(c.getId());
            vo.setName(c.getName());
            vo.setParentId(c.getParentId());
            vo.setSortOrder(c.getSortOrder());
            vo.setProductCount(countMap.getOrDefault(c.getId(), 0L));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<String> getHotWords() {
        return HOT_WORDS;
    }

    /**
     * 统计每个分类下的在售商品数。
     *
     * <p>只查 {@code category_id} 这一列（不查整行），然后在内存里分组计数。
     * 之所以不用 {@code GROUP BY} 的 SQL，是因为 {@code selectMaps} 返回的 Map
     * 里的 key 在某些数据库/驱动下会被大写，取值的代码很容易写出"看起来对但不生效"的 bug。
     * 商品总量不大，内存统计更稳。
     */
    private Map<Integer, Long> countOnSaleByCategory() {
        List<Product> onSaleList = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .select(Product::getCategoryId)
                        .eq(Product::getStatus, ProductStatus.ON_SALE.getCode()));

        return onSaleList.stream()
                .filter(p -> p.getCategoryId() != null)
                .collect(Collectors.groupingBy(Product::getCategoryId, Collectors.counting()));
    }
}
