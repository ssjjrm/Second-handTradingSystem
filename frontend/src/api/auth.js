import request from '@/utils/request'

/** 3.1 注册 */
export const register = (data) => request.post('/auth/register', data)

/** 3.2 登录 —— 返回 { token, user } */
export const login = (data) => request.post('/auth/login', data)

/** 3.3 当前用户信息（含统计） */
export const getMe = () => request.get('/auth/me')

/** 3.4 修改资料 */
export const updateProfile = (data) => request.put('/auth/profile', data)

/** 3.5 修改密码 */
export const updatePassword = (data) => request.put('/auth/password', data)
