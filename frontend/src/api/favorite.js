import request from '@/utils/request'

/** 7.1 收藏（幂等：重复点不会报错，计数也不会重复加） */
export const addFavorite = (productId) => request.post(`/favorites/${productId}`)

/** 7.2 取消收藏（同样幂等） */
export const removeFavorite = (productId) => request.delete(`/favorites/${productId}`)

/** 7.3 我的收藏（包含已售出/已下架的商品） */
export const getMyFavorites = (params) => request.get('/favorites', { params })

/** 7.4 检查是否已收藏 */
export const checkFavorite = (productId) => request.get(`/favorites/check/${productId}`)
