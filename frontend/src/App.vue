<script setup>
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/layout/AppHeader.vue'
import NoticeBar from '@/components/layout/NoticeBar.vue'
import FloatingBar from '@/components/layout/FloatingBar.vue'
import AppFooter from '@/components/layout/AppFooter.vue'
import AppToast from '@/components/common/AppToast.vue'
import { useUser } from '@/composables/useUser'

/**
 * 根组件 = 全站骨架。
 *
 * 结构：
 *   顶栏（黄）
 *   公告条（管理后台不显示）
 *   <router-view/>  ← 各页面自己写内容
 *   页脚（管理后台不显示）
 *
 * 悬浮条 + toast 是全局的，永远挂在最外层。
 *
 * ⚠️ localStorage 不是响应式的，所以监听路由变化时重新同步一次登录态 ——
 * 登录页写入 token 后，顶栏才会从"登录/注册"变成"头像 + 昵称"。
 */
const route = useRoute()
const { sync } = useUser()

watch(() => route.fullPath, sync)

/** 管理后台有自己的侧边栏，不需要公告条和页脚 */
const isAdminArea = computed(() => route.path.startsWith('/admin'))
</script>

<template>
  <div class="page">
    <AppHeader />

    <NoticeBar v-if="!isAdminArea" />

    <router-view />

    <AppFooter v-if="!isAdminArea" />

    <FloatingBar v-if="!isAdminArea" />
    <AppToast />
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}
</style>
