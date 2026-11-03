package com.secondhand.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 行为记录的分页出参 = 普通分页 + 一个 {@code summary}。
 *
 * <p><b>为什么要单独一个类，而不是把 summary 塞进 PageVO？</b>
 * 因为 PageVO 是全站通用的分页结构（商品列表、我发布的、用户列表都在用），
 * 给它加一个只有后台行为页才有的字段，等于让所有分页接口都多一个永远是 null 的键。
 *
 * <p>做法是<b>继承 + 追加</b>：records / total / current / size / pages 五个字段
 * 从父类继承下来，JSON 序列化出来仍然是**扁平的**结构，和接口文档 §10.6 完全一致：
 * <pre>
 * { "records": [...], "total": 1240, "current": 1, "size": 20, "pages": 62,
 *   "summary": { "viewCount": 980, ... } }
 * </pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BehaviorLogPageVO extends PageVO<BehaviorLogVO> {

    /** 全站各类型行为汇总（不受列表筛选条件影响） */
    private BehaviorSummaryVO summary;

    /**
     * 用一个已经转好的分页结果 + 汇总数组装出参。
     *
     * <p>这样 {@code AdminBehaviorServiceImpl} 里只需要写
     * {@code PageVO.of(page, converter)} 那套通用逻辑，再套一层即可。
     */
    public static BehaviorLogPageVO of(PageVO<BehaviorLogVO> page, BehaviorSummaryVO summary) {
        BehaviorLogPageVO vo = new BehaviorLogPageVO();
        vo.setRecords(page.getRecords());
        vo.setTotal(page.getTotal());
        vo.setCurrent(page.getCurrent());
        vo.setSize(page.getSize());
        vo.setPages(page.getPages());
        vo.setSummary(summary);
        return vo;
    }
}
