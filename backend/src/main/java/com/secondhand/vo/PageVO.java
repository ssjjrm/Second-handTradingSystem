package com.secondhand.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页出参（通用）。
 *
 * <p>不直接把 MyBatis-Plus 的 {@code IPage} 返回给前端，有两个原因：
 * <ol>
 *   <li>{@code IPage} 里有一堆前端用不到的字段（orders、optimizeCountSql、searchCount…）</li>
 *   <li>返回实体会把实体里所有字段都暴露出去，换成 VO 才能控制粒度</li>
 * </ol>
 *
 * <p>字段名和接口文档 §1.3 一致：records / total / current / size / pages。
 */
@Data
@AllArgsConstructor
public class PageVO<T> {

    /** 当前页数据 */
    private List<T> records;

    /** 总条数 */
    private Long total;

    /** 当前页码 */
    private Long current;

    /** 每页条数 */
    private Long size;

    /** 总页数 */
    private Long pages;

    /**
     * 把 MyBatis-Plus 的分页结果转成出参，同时用 {@code converter} 把实体逐个转成 VO。
     *
     * <p>这样 Service 里写一行就能完成"分页 + 转换"，
     * 不用每处都重复 {@code page.getTotal()} / {@code page.getCurrent()} 那一串。
     */
    public static <E, V> PageVO<V> of(IPage<E> page, Function<E, V> converter) {
        List<V> records = page.getRecords().stream().map(converter).collect(Collectors.toList());
        return new PageVO<>(records, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }
}
