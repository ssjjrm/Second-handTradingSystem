import request from '@/utils/request'

/** 5.1 商品列表（公开） */
export const getProducts = (params) => request.get('/products', { params })

/** 5.2 商品详情（公开） */
export const getProductDetail = (id) => request.get(`/products/${id}`)

/** 5.9 我发布的（带 contactCount / needAttention） */
export const getMyProducts = (params) => request.get('/products/mine', { params })

/** 5.4 发布商品 */
export const publishProduct = (data) => request.post('/products', data)

/** 5.5 编辑商品 */
export const updateProduct = (id, data) => request.put(`/products/${id}`, data)

/** 5.7 下架 */
export const offShelfProduct = (id) => request.put(`/products/${id}/off-shelf`)

/** 5.8 删除 */
export const deleteProduct = (id) => request.delete(`/products/${id}`)

/** 5.3 ⭐ 获取卖家联系方式（唯一能拿到微信/QQ 的接口） */
export const getContact = (id) => request.get(`/products/${id}/contact`)

/** 5.6 ⭐ 标记已售出（不可逆） */
export const markSold = (id) => request.put(`/products/${id}/mark-sold`)
