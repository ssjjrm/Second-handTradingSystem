import axios from 'axios'

/**
 * axios 实例 + 两个拦截器。
 *
 * 后端约定（见接口文档 §1.1）：HTTP 状态码和响应体里的 code 保持一致。
 * 也就是说：
 *   - 成功 → HTTP 200，body = { code:200, message:'success', data:{...} }
 *   - 失败 → HTTP 400/401/403/404/500，body = { code:xxx, message:'...', data:null }
 *
 * 所以失败会走 axios 的 **catch 分支**，我们要在那里取 message。
 */
const request = axios.create({
  baseURL: '/api',       // 走 vite 代理，不用写 http://localhost:8081
  timeout: 10000
})

// ---------- 请求拦截器：自动带 token ----------
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = 'Bearer ' + token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ---------- 响应拦截器 ----------
request.interceptors.response.use(
  // 成功：直接把 data 剥出来，业务代码拿到的就是 { id, username, ... }
  (response) => {
    const body = response.data
    if (body && body.code === 200) {
      return body.data
    }
    // 理论上不会走到这里（后端非 200 都会带非 2xx 状态码），兜底提示一下
    return Promise.reject(new Error(body?.message || '请求失败'))
  },

  // 失败
  (error) => {
    const response = error.response

    // 后端没响应（服务没起来 / 网络断了）
    if (!response) {
      return Promise.reject(new Error('无法连接服务器，请确认后端已在 8081 启动'))
    }

    const message = response.data?.message || `请求失败（HTTP ${response.status}）`

    // 401：登录态没了 → 清掉本地 token 并跳登录页
    if (response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      // 已经在登录页就不要重复跳，免得死循环
      if (!location.hash.includes('/login') && !location.pathname.includes('/login')) {
        location.href = '/login'
      }
    }

    return Promise.reject(new Error(message))
  }
)

export default request
