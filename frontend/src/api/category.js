import request from '@/utils/request'

/** 4.1 分类树（一级带 children） */
export const getCategoryTree = () => request.get('/categories')

/** 4.2 仅一级分类 */
export const getParentCategories = () => request.get('/categories/parents')

/** 4.3 二级分类 */
export const getChildCategories = (parentId) =>
  request.get('/categories/children', { params: { parentId } })

/** 4.4 热搜词 */
export const getHotWords = () => request.get('/categories/hot-words')
