import request from '@/utils/request'

/**
 * 管理后台接口（接口文档 §10）。
 *
 * ⚠️ 这一组接口**全部**受两重保护：
 *   1. 后端 AdminInterceptor —— 非 admin 一律 403（真正的门）
 *   2. 前端路由 requiresAdmin —— 没权限的人连页面都进不去（只是体验）
 *
 * 只有第 1 条是安全措施，第 2 条谁都能绕过（改一下 localStorage 就行）。
 */

// ---------------- §10.1~10.3 商品审核 ----------------
export const getPendingProducts = (params) => request.get('/admin/products/pending', { params })

export const approveProduct = (id) => request.put(`/admin/products/${id}/approve`)

export const rejectProduct = (id, reason) => request.put(`/admin/products/${id}/reject`, { reason })

// ---------------- §10.4~10.5 商品管理 ----------------
export const getAllProducts = (params) => request.get('/admin/products', { params })

export const forceOffShelf = (id) => request.put(`/admin/products/${id}/off-shelf`)

// ---------------- §10.6 咨询记录（替代"订单管理"） ----------------
export const getBehaviors = (params) => request.get('/admin/behaviors', { params })

// ---------------- §10.7~10.8 用户管理 ----------------
export const getUsers = (params) => request.get('/admin/users', { params })

/** status: 1 启用 / 0 禁用（放在 query 参数里，不是请求体） */
export const setUserStatus = (id, status) =>
  request.put(`/admin/users/${id}/status`, null, { params: { status } })

// ---------------- §10.9 数据统计 ----------------
export const getStats = () => request.get('/admin/stats')
