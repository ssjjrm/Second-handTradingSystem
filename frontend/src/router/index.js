import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由表。
 *
 * S3 阶段只有三条路由，但**守卫逻辑现在就写好** ——
 * 后面 S5（发布商品）、S8（个人中心）、S9（管理后台）加页面时，
 * 只要在路由上写 `meta: { requiresAuth: true }` 就自动生效，不用再改这里。
 */
const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/Home.vue')
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册' }
  }
  // 后面阶段会陆续加上：
  // /products、/products/:id、/publish、/favorites、/profile、/admin/...
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局前置守卫。
 *
 * 判断顺序很重要：
 *   1. 没登录 + 要登录才能看 → 跳登录页（并把原地址记下来，登录后跳回去）
 *   2. 登录了但要管理员 + 不是管理员 → 回首页
 *   3. 其余放行
 */
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const user = JSON.parse(localStorage.getItem('user') || 'null')

  if (to.meta.requiresAuth && !token) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }

  if (to.meta.requiresAdmin && user?.role !== 'admin') {
    return next({ path: '/' })
  }

  // 已登录还去登录页 → 直接回首页
  if (to.name === 'login' && token) {
    return next({ path: '/' })
  }

  next()
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 校园二手` : '校园二手交易平台'
})

export default router
