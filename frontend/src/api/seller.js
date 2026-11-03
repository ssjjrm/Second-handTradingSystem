import request from '@/utils/request'

/** 6.1 卖家公开资料（不含联系方式） */
export const getSeller = (id) => request.get(`/sellers/${id}`)

/** 6.2 卖家在售的商品 */
export const getSellerProducts = (id, params) => request.get(`/sellers/${id}/products`, { params })
