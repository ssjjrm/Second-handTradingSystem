<script setup>
import { computed, ref } from 'vue'

/**
 * 首页骨架（S3 临时版）。
 *
 * 现在只用来确认"登录态在页面之间是通的"。
 * S4 做完商品列表后，这个文件会被真正的首页替换掉。
 */
const token = ref(localStorage.getItem('token') || '')
const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

const isLoggedIn = computed(() => !!token.value)
</script>

<template>
  <main class="content">
    <h1>S3 用户模块已完成 ✅</h1>

    <p v-if="isLoggedIn" class="ok">
      当前登录：{{ user?.username }}（{{ user?.role }}），token 长度 {{ token.length }}
    </p>
    <p v-else class="warn">
      当前未登录 —— 去 <router-link to="/login">登录页</router-link> 登录试试
    </p>

    <h3>可以点的地方</h3>
    <ul>
      <li><router-link to="/login">登录页</router-link>（正式版，S10 之后仍然用它）</li>
      <li><router-link to="/register">注册页</router-link></li>
      <li><a href="/verify/s3-auth.html">S3 接口验证页</a>（能自动复用这里的登录态）</li>
      <li><a href="/verify/s2-health.html">S2 接口验证页</a></li>
    </ul>

    <h3>验证一下登录态是通的</h3>
    <p class="tip">
      在登录页登录后回到这里，顶栏应该显示昵称；再打开 S3 验证页，它应该显示「已登录」。
      这说明三处共享的是同一份 localStorage。
    </p>
    <p class="tip">当前页面还很丑是故意的 —— 视觉还原统一放在 S10 阶段做。</p>
  </main>
</template>

<style scoped>
.content { padding: 24px; max-width: 900px; }
.ok { color: #2e7d32; }
.warn { color: #c62828; }
.tip { color: #888; font-size: 13px; }
</style>
