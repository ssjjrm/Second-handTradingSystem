<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/auth'

/**
 * 注册页。
 *
 * 后端注册接口不返回 token（要单独登录一次），
 * 所以注册成功后回到登录页，并把用户名带过去自动填好。
 */
const router = useRouter()

const form = ref({
  username: '',
  password: '',
  nickname: '',
  wechat: '',
  qq: '',
  phone: ''
})

const loading = ref(false)
const error = ref('')

async function onSubmit() {
  error.value = ''

  if (!form.value.username || !form.value.password) {
    error.value = '用户名和密码不能为空'
    return
  }

  loading.value = true
  try {
    await register(form.value)
    // 用 query 把用户名带到登录页，省得再敲一遍
    router.push({ path: '/login', query: { registered: form.value.username } })
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="form-card">
    <h2>校园二手 · 注册</h2>

    <p v-if="error" class="msg-error">{{ error }}</p>

    <form @submit.prevent="onSubmit">
      <label for="username">用户名 <span class="req">*</span></label>
      <input id="username" v-model="form.username" placeholder="4~20 个字符" />

      <label for="password">密码 <span class="req">*</span></label>
      <input id="password" v-model="form.password" type="password" placeholder="6~20 个字符" />

      <label for="nickname">昵称</label>
      <input id="nickname" v-model="form.nickname" placeholder="不填就用用户名" />

      <label for="wechat">微信号</label>
      <input id="wechat" v-model="form.wechat" placeholder="发布商品前至少填微信或 QQ 之一" />

      <label for="qq">QQ 号</label>
      <input id="qq" v-model="form.qq" placeholder="5~12 位数字" />

      <label for="phone">手机号</label>
      <input id="phone" v-model="form.phone" placeholder="仅用于账号找回，不会展示给别人" />

      <button type="submit" :disabled="loading">
        {{ loading ? '提交中…' : '注 册' }}
      </button>
    </form>

    <p class="foot">
      已有账号？<router-link to="/login">去登录</router-link>
    </p>
  </div>
</template>

<style scoped>
.req { color: #ff5000; }
</style>
