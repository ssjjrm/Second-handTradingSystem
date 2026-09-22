<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '@/api/auth'

/**
 * 登录页 —— S3 唯一提前做成"正式版"的前端页面。
 *
 * 为什么要提前做正式版：它产出的 token 会被 S4~S9 所有验证页复用；
 * 而且到 S10 之后它仍然是正式登录页，不用重写，不算浪费。
 * 但样式依然留到 S10 一起做视觉还原。
 */
const router = useRouter()
const route = useRoute()

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
    // request.js 的响应拦截器已经把后端返回的 data 剥出来了，
    // 所以这里的 data 就是 { token, user }
    const data = await login({ username: username.value, password: password.value })

    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data.user))

    // 被拦截到登录页的，登录后跳回原来想去的页面
    router.push(route.query.redirect || '/')
  } catch (e) {
    // 后端返回的 message 会原样显示，比如"用户名或密码错误"
    error.value = e.message
  } finally {
    loading.value = false
  }
}

/** 开发期便利：一键填入测试账号（S10 重写登录页时会去掉） */
function fill(u, p) {
  username.value = u
  password.value = p
  error.value = ''
}
</script>

<template>
  <div class="form-card">
    <h2>校园二手 · 登录</h2>

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
</template>
