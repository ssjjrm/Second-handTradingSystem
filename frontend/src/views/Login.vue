<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '@/api/auth'
import { useUser } from '@/composables/useUser'
import { toast } from '@/composables/useToast'

/**
 * 登录页。
 *
 * S10 只做了视觉（黄顶栏 + 白卡片 + 黄色胶囊按钮），**逻辑一行没改**：
 * 校验 → 请求 → 存 token → 跳回原来想去的页面。
 *
 * ⚠️ 类名 `.form-card` / `.msg-error` / `button[type=submit]` 都被
 * `scripts/e2e-test.mjs` 断言，改样式时不要改类名。
 */
const router = useRouter()
const route = useRoute()
const { setAuth } = useUser()

// 从注册页跳过来时会带上 ?registered=用户名，自动填好省得再敲一遍
const username = ref(route.query.registered || '')
const password = ref('')
const loading = ref(false)
const error = ref('')
const okMsg = ref(route.query.registered ? `注册成功，请用「${route.query.registered}」登录` : '')

async function onSubmit() {
  error.value = ''

  if (!username.value || !password.value) {
    error.value = '请填写用户名和密码'
    return
  }

  loading.value = true
  try {
    // request.js 的响应拦截器已经把 data 剥出来了，这里拿到的是 { token, user }
    const data = await login({ username: username.value, password: password.value })
    setAuth(data)
    toast(`欢迎回来，${data.user?.nickname || data.user?.username}`)
    // 被拦截到登录页的，登录后跳回原来想去的页面
    router.push(route.query.redirect || '/')
  } catch (e) {
    // 后端返回的 message 原样显示，比如"用户名或密码错误" / "账号已被禁用"
    error.value = e.message
  } finally {
    loading.value = false
  }
}

/** 开发期便利：一键填入测试账号 */
function fill(u, p) {
  username.value = u
  password.value = p
  error.value = ''
}
</script>

<template>
  <main class="container">
    <div class="form-card">
      <h2>登录校园二手</h2>

      <p v-if="okMsg" class="msg-ok">{{ okMsg }}</p>
      <p v-if="error" class="msg-error">{{ error }}</p>

      <form @submit.prevent="onSubmit">
        <label for="username">用户名</label>
        <input id="username" v-model="username" autocomplete="username" placeholder="请输入用户名" />

        <label for="password">密码</label>
        <input
          id="password"
          v-model="password"
          type="password"
          autocomplete="current-password"
          placeholder="请输入密码"
        />

        <button type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登 录' }}
        </button>
      </form>

      <p class="foot">
        还没有账号？<router-link to="/register">去注册</router-link>
      </p>

      <p class="foot">
        测试账号：
        <a href="#" @click.prevent="fill('admin', 'admin123')">admin</a> ·
        <a href="#" @click.prevent="fill('student01', '123456')">student01</a> ·
        <a href="#" @click.prevent="fill('buyer01', '123456')">buyer01</a>
      </p>
    </div>
  </main>
</template>
