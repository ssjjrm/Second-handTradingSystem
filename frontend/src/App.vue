<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/**
 * 根组件：只负责顶栏 + 路由出口。
 *
 * 顶栏要显示登录状态，但 localStorage 不是响应式的 ——
 * 登录页写入 token 后，这里不会自己变。所以用 watch 监听路由变化时重新同步一次。
 * （更正规的做法是引入 Pinia，等 S8 个人中心再上也不迟。）
 */
const route = useRoute()
const router = useRouter()

const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
const token = ref(localStorage.getItem('token') || '')

const isLoggedIn = computed(() => !!token.value)

function sync() {
  user.value = JSON.parse(localStorage.getItem('user') || 'null')
  token.value = localStorage.getItem('token') || ''
}

watch(() => route.fullPath, sync)

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  sync()
  router.push('/login')
}
</script>

<template>
  <div class="page">
    <header class="topbar">
      <router-link to="/" class="brand">校园二手</router-link>
      <span class="spacer"></span>

      <template v-if="isLoggedIn">
        <router-link to="/profile">个人中心</router-link>
        <span class="who">你好，{{ user?.nickname || user?.username }}</span>
        <a href="#" @click.prevent="logout">退出登录</a>
      </template>
      <template v-else>
        <router-link to="/login">登录</router-link>
        <router-link to="/register">注册</router-link>
      </template>
    </header>

    <router-view />
  </div>
</template>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 20px;
  background: #2b2b2b;
  color: #fff;
}
.brand { color: #ffd100; font-weight: bold; }
.spacer { flex: 1; }
.who { color: #ddd; font-size: 14px; }
.topbar a { color: #ffd100; }
</style>
