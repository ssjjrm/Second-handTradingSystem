<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

/**
 * 管理后台的骨架页：左边导航 + 右边子路由。
 *
 * 用**嵌套路由**（父路由 + children）而不是五个平级页面各自贴一遍导航：
 * 导航只写一份，加模块时只改菜单数组和路由表两处，不会漏。
 *
 * ⚠️ 权限不在这里判断 —— 路由表上已经写了 requiresAdmin，
 * 全局守卫会在进入本组件**之前**就把非管理员挡回首页。
 */
const route = useRoute()

const menus = [
  { path: '/admin', label: '数据统计', hint: '平台规模' },
  { path: '/admin/review', label: '商品审核', hint: '待办' },
  { path: '/admin/products', label: '商品管理', hint: '全部商品' },
  { path: '/admin/behaviors', label: '咨询记录', hint: '活跃度' },
  { path: '/admin/users', label: '用户管理', hint: '禁用/启用' }
]

const current = computed(() => route.path)
</script>

<template>
  <div class="admin">
    <aside class="side">
      <div class="side-title">管理后台</div>
      <nav>
        <router-link v-for="m in menus" :key="m.path" :to="m.path"
                     :class="{ active: current === m.path }">
          {{ m.label }}
          <span class="hint">{{ m.hint }}</span>
        </router-link>
      </nav>
      <div class="side-foot">
        <router-link to="/">← 回前台</router-link>
      </div>
    </aside>

    <main class="body">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin { display: flex; min-height: calc(100vh - 48px); background: #f7f8fa; }
.side { width: 190px; flex: none; background: #2b2b2b; color: #ddd; padding: 16px 0; }
.side-title { padding: 0 18px 12px; font-size: 15px; font-weight: bold; color: #ffd100; }
.side nav { display: flex; flex-direction: column; }
.side nav a {
  display: flex; justify-content: space-between; align-items: baseline;
  padding: 10px 18px; color: #ddd; font-size: 14px;
}
.side nav a:hover { background: #3a3a3a; }
.side nav a.active { background: #ffd100; color: #2b2b2b; font-weight: bold; }
.side nav a.active .hint { color: #5a4a00; }
.side .hint { font-size: 11px; color: #888; }
.side-foot { padding: 16px 18px; font-size: 13px; }
.side-foot a { color: #ffd100; }
.body { flex: 1; padding: 20px 24px; min-width: 0; }
</style>
