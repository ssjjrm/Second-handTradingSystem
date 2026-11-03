package com.secondhand.service;

import com.secondhand.vo.AdminStatsVO;

/**
 * 管理后台 · 数据统计（接口文档 §10.9）。
 *
 * <p>这个接口只有一个方法，但它是后台首页真正要看的东西 ——
 * 10 个数字从 5 张表里凑出来，回答"平台现在什么规模、今天有没有人在用"。
 */
public interface AdminStatsService {

    /** 10.9 平台数据统计 */
    AdminStatsVO getStats();
}
