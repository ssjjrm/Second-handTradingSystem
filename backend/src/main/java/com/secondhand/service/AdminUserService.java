package com.secondhand.service;

import com.secondhand.vo.AdminUserVO;
import com.secondhand.vo.PageVO;

/**
 * 管理后台 · 用户管理（接口文档 §10.7~10.8）。
 *
 * <p><b>本接口是唯一会输出"别人的联系方式"的地方之一</b>
 * （另一个是买家主动点"获取联系方式"）。
 * 理由很实在：平台不参与交易，买卖双方自己加微信谈，
 * 一旦出现纠纷（收了钱不发货、货不对板），管理员手里**必须有**联系方式才能介入。
 * 所以这里的 {@link AdminUserVO} 带 wechat / qq / phone，靠 {@code AdminInterceptor} 兜住权限。
 */
public interface AdminUserService {

    /** 10.7 用户列表（可按用户名 / 昵称搜索），带发布数、已售数、收藏数 */
    PageVO<AdminUserVO> list(Integer page, Integer size, String keyword);

    /**
     * 10.8 禁用 / 启用用户。
     *
     * @param status 1 启用 / 0 禁用
     */
    void updateStatus(Integer userId, Integer status);
}
