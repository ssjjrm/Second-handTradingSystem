import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由表。
 *
 * 守卫逻辑在 S3 就写好了，后面加页面只要在路由上写
 * `meta: { requiresAuth: true }` / `requiresAdmin: true` 就自动生效。
 *
 * 页面清单见 `需求设计文档.md` §7：
 *   P01 / 首页        P02 /products    P03 /product/:id   P04 /publish
 *   P05 /seller/:id   P06 /login       P07 /register      P08 /profile
 *   A01~A05 /admin/**
 */
const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '' }
  },

  // ---------------- S10：商品列表 / 详情 / 发布 ----------------
  {
    path: '/products',
    name: 'product-list',
    component: () => import('@/views/ProductList.vue'),
    // 匿名可访问：游客也能逛
    meta: { title: '全部商品' }
  },
  {
    path: '/product/:id',
    name: 'product-detail',
    component: () => import('@/views/ProductDetail.vue'),
    meta: { title: '商品详情' }
  },
  {
    path: '/publish',
    name: 'publish',
    component: () => import('@/views/Publish.vue'),
    // 发布必须登录（后端也会挡，这里只是让他先去登录）
    meta: { requiresAuth: true, title: '发布闲置' }
  },

  // ---------------- 登录 / 注册 ----------------
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

  // ---------------- 个人中心 + 卖家主页 ----------------
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/Profile.vue'),
    meta: { requiresAuth: true, title: '个人中心' }
  },
  {
    path: '/seller/:id',
    name: 'seller',
    component: () => import('@/views/Seller.vue'),
    // 卖家主页公开，但不显示联系方式
    meta: { title: '卖家主页' }
  },

  // ---------------- 管理后台（嵌套路由） ----------------
  //
  // requiresAdmin 写在**父路由**上就够了：vue-router 的 to.meta 是
  // 父子合并的结果，5 个子页面自动都受保护。
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
  },

  // ---------------- 兜底 404 ----------------
  // 必须放最后：`/:pathMatch(.*)*` 会吃掉所有没匹配上的地址
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 切页面时回到顶部（否则从长列表点进详情，会停在中段）
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  }
})

/**
 * 全局前置守卫。
 *
 * 判断顺序很重要：
 *   1. 没登录 + 要登录才能看 → 跳登录页（并把原地址记下来，登录后跳回去）
 *   2. 登录了但要管理员 + 不是管理员 → 回首页
 *   3. 已登录还去登录页 → 回首页
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

  if (to.name === 'login' && token) {
    return next({ path: '/' })
  }

  next()
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 校园二手` : '校园二手交易平台'
})

export default router
