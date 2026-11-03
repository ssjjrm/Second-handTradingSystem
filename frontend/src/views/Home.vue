<script setup>
import { computed, ref } from 'vue'

/**
 * 首页（S8 版骨架）。
 *
 * ⚠️ 这一页到 S10 会被真正的商品列表首页替换掉。
 * 现在它的作用是"导航中心"：把已经做好的页面和验证页都列出来，方便人肉点一遍。
 */
const token = ref(localStorage.getItem('token') || '')
const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

const isLoggedIn = computed(() => !!token.value)
</script>

<template>
  <main class="content">
    <h1>校园二手交易平台</h1>

    <p v-if="isLoggedIn" class="ok">
      已登录：{{ user?.nickname || user?.username }}（{{ user?.role }}）
    </p>
    <p v-else class="warn">
      当前未登录 —— 去 <router-link to="/login">登录页</router-link> 登录
    </p>

    <h3>正式页面</h3>
    <ul>
      <li><router-link to="/login">登录</router-link> / <router-link to="/register">注册</router-link></li>
      <li><router-link to="/profile">个人中心</router-link>（我发布的 / 我的收藏 / 联系方式设置）</li>
      <li><router-link to="/seller/2">卖家主页示例</router-link>（游客也能看，但看不到联系方式）</li>
    </ul>

    <h3>接口验证页</h3>
    <p class="tip">这些是开发期的临时页面，S10 做完真实前端后会整个删掉。</p>
    <ul>
      <li><a href="/verify/s2-health.html">S2 后端骨架</a></li>
      <li><a href="/verify/s3-auth.html">S3 用户模块</a></li>
      <li><a href="/verify/s4-products.html">S4 分类 + 商品列表/详情</a></li>
      <li><a href="/verify/s5-publish.html">S5 发布 + 审核</a></li>
      <li><a href="/verify/s6-contact.html">S6 ⭐ 获取联系方式 + 标记售出</a></li>
      <li><a href="/verify/s7-favorite.html">S7 收藏 + 行为埋点</a></li>
    </ul>

    <h3>自动化测试</h3>
    <p class="tip">
      在项目根目录执行：<code>node scripts\e2e-test.mjs</code>（浏览器全链路）、
      <code>node scripts\s7-test.mjs</code>（收藏）、<code>s6-test.mjs</code>、<code>s5-test.mjs</code>。
    </p>
    <p class="tip">页面还很朴素是故意的 —— 视觉还原统一放在 S10 做。</p>
  </main>
</template>

<style scoped>
.content { padding: 24px; max-width: 900px; }
.ok { color: #2e7d32; }
.warn { color: #c62828; }
.tip { color: #888; font-size: 13px; }
code { background: #eee; padding: 1px 5px; }
</style>
