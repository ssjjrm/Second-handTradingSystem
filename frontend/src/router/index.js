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
  },

  // ---------------- S8：个人中心 + 卖家主页 ----------------
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/Profile.vue'),
    // 个人中心全是"我的"数据，必须登录
    meta: { requiresAuth: true, title: '个人中心' }
  },
  {
    path: '/seller/:id',
    name: 'seller',
    component: () => import('@/views/Seller.vue'),
    // 卖家主页是公开的（游客也能看卖家在卖什么），但页面里不会显示联系方式
    meta: { title: '卖家主页' }
  },

  // ---------------- S9：管理后台（嵌套路由） ----------------
  //
  // 用"父路由 + children"而不是 5 个平级路由，好处是导航栏只写一份。
  // ⚠️ requiresAdmin 写在**父路由**上就够了：vue-router 的 to.meta 是
  //    父路由和子路由 meta 合并后的结果，所以 5 个子页面自动都受保护。
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true, title: '管理后台' },
    children: [
      {
        path: '',
        name: 'admin-stats',
        component: () => import('@/views/admin/AdminStats.vue'),
        meta: { title: '数据统计' }
      },
      {
        path: 'review',
        name: 'admin-review',
        component: () => import('@/views/admin/AdminReview.vue'),
        meta: { title: '商品审核' }
      },
      {
        path: 'products',
        name: 'admin-products',
        component: () => import('@/views/admin/AdminProducts.vue'),
        meta: { title: '商品管理' }
      },
      {
        path: 'behaviors',
        name: 'admin-behaviors',
        component: () => import('@/views/admin/AdminBehaviors.vue'),
        meta: { title: '咨询记录' }
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/views/admin/AdminUsers.vue'),
        meta: { title: '用户管理' }
      }
    ]
  }

  // 后面阶段会陆续加上：
  // /products（S10）、/product/:id（S10）、/publish（S10）
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
