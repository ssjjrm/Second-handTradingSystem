<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getHotWords } from '@/api/category'
import { useUser } from '@/composables/useUser'
import { initialOf } from '@/composables/useFormat'

/**
 * 顶部导航（黄底两行）—— 所有 C 端页面的骨架。
 *
 * 第一行：logo + 搜索框 + 右侧操作区
 * 第二行：热搜词（点击即搜索）
 *
 * ⚠️ 类名 `.topbar` 必须保留：`scripts/e2e-test.mjs` 靠它判断登录态。
 *    （原来它在 App.vue 里，S10 抽成独立组件，类名不变。）
 */
const route = useRoute()
const router = useRouter()
const { user, isLoggedIn, isAdmin, logout } = useUser()

const keyword = ref('')

/** 热搜词：优先用接口给的（来自分类/品牌），失败就用静态兜底 */
const hotWords = ref(['耳机', '自行车', '教材', '显示器', '吉他', '台灯', '篮球', '行李箱'])

async function loadHotWords() {
  try {
    const words = await getHotWords()
    if (Array.isArray(words) && words.length) {
      hotWords.value = words.slice(0, 8)
    }
  } catch {
    /* 热搜词只是装饰，拿不到就算了，用静态兜底 */
  }
}

function onSearch() {
  const kw = keyword.value.trim()
  router.push({ path: '/products', query: kw ? { keyword: kw } : {} })
}

function searchHot(word) {
  keyword.value = word
  onSearch()
}

function onLogout() {
  logout()
  router.push('/login')
}

// 地址栏里的关键词变了（比如从列表页点分类），搜索框跟着变
watch(
  () => route.query.keyword,
  (kw) => {
    keyword.value = kw ? String(kw) : ''
  },
  { immediate: true }
)

onMounted(loadHotWords)
</script>

<template>
  <header class="topbar">
    <div class="container topbar-main">
      <!-- 左：logo -->
      <router-link to="/" class="logo">
        <span class="logo-mark">校</span>
        <span class="logo-text">校园二手</span>
      </router-link>

      <!-- 中：搜索 -->
      <form class="search" @submit.prevent="onSearch">
        <input
          v-model="keyword"
          class="search-input"
          type="text"
          placeholder="搜一搜：耳机、自行车、教材…"
          aria-label="搜索商品"
        />
        <button class="search-btn" type="submit">搜索</button>
      </form>

      <!-- 右：操作区 -->
      <div class="actions">
        <template v-if="isLoggedIn">
          <router-link to="/publish" class="post-btn">发布闲置</router-link>

          <div class="user-menu">
            <router-link to="/profile" class="user-trigger">
              <img v-if="user?.avatar" :src="user.avatar" class="avatar" alt="" />
              <span v-else class="avatar avatar-text">{{ initialOf(user?.nickname || user?.username) }}</span>
              <span class="nickname">{{ user?.nickname || user?.username }}</span>
              <span class="caret">▾</span>
            </router-link>

            <div class="dropdown">
              <router-link to="/profile">个人中心</router-link>
              <router-link to="/profile?tab=favorites">我的收藏</router-link>
              <router-link v-if="isAdmin" to="/admin">管理后台</router-link>
              <a href="#" @click.prevent="onLogout">退出登录</a>
            </div>
          </div>
        </template>

        <template v-else>
          <router-link to="/login" class="login-btn">登录</router-link>
          <router-link to="/register" class="register-btn">注册</router-link>
        </template>
      </div>
    </div>

    <!-- 第二行：热搜词 -->
    <div class="container hot-row">
      <span class="hot-label">热搜：</span>
      <a
        v-for="w in hotWords"
        :key="w"
        href="#"
        class="hot-word"
        @click.prevent="searchHot(w)"
      >{{ w }}</a>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: var(--z-header);
  background: var(--color-primary);
}

/* ---------- 第一行 ---------- */
.topbar-main {
  display: flex;
  align-items: center;
  gap: var(--gap-6);
  height: 58px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}
.logo-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 17px;
  font-weight: 800;
  color: var(--color-primary);
  background: #1a1a1a;
  border-radius: 8px;
}
.logo-text {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 1px;
  color: #1a1a1a;
}

/* ---------- 搜索框 ---------- */
.search {
  display: flex;
  align-items: center;
  flex: 1;
  max-width: 460px;
  height: 36px;
  padding-right: 4px;
  background: #fff;
  border-radius: var(--radius-pill);
  transition: box-shadow var(--dur-fast);
}
.search:focus-within {
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.55);
}
.search-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0 16px;
  font-size: var(--fs-base);
  background: transparent;
  border: none;
  outline: none;
  border-radius: var(--radius-pill);
}
.search-btn {
  flex: none;
  height: 28px;
  padding: 0 18px;
  font-size: var(--fs-sm);
  font-weight: 600;
  color: #fff;
  background: #1a1a1a;
  border: none;
  border-radius: var(--radius-pill);
  transition: opacity var(--dur-fast);
}
.search-btn:hover {
  opacity: 0.85;
}

/* ---------- 右侧 ---------- */
.actions {
  display: flex;
  align-items: center;
  gap: var(--gap-3);
  margin-left: auto;
  flex: none;
}

/* ⚠️ 类名不能叫 .publish-btn —— 发布页的主按钮用的就是那个名字，
   两处撞名会让测试和以后的调试脚本选中错误的元素 */
.post-btn {
  padding: 6px 16px;
  font-size: var(--fs-sm);
  font-weight: 600;
  color: #fff;
  background: var(--color-dark-btn);
  border-radius: var(--radius-pill);
  transition: background var(--dur-fast);
}
.post-btn:hover {
  background: var(--color-dark-btn-hover);
}

.login-btn {
  padding: 6px 16px;
  font-size: var(--fs-sm);
  border: 1px solid rgba(0, 0, 0, 0.22);
  border-radius: var(--radius-pill);
}
.register-btn {
  padding: 6px 16px;
  font-size: var(--fs-sm);
  font-weight: 600;
  color: #fff;
  background: var(--color-dark-btn);
  border-radius: var(--radius-pill);
}

/* ---------- 用户下拉（纯 CSS，不写 JS 点击外部关闭） ---------- */
.user-menu {
  position: relative;
  padding: 6px 0;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-sm);
}
.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  background: #fff;
}
.avatar-text {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-sm);
  font-weight: 600;
  color: #1a1a1a;
  background: #fff5c2;
}
.nickname {
  max-width: 110px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.caret {
  font-size: 10px;
  opacity: 0.6;
}

.dropdown {
  position: absolute;
  right: 0;
  top: 100%;
  min-width: 132px;
  padding: 6px 0;
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-hover);
  opacity: 0;
  visibility: hidden;
  transform: translateY(-4px);
  transition: opacity var(--dur-fast), transform var(--dur-fast), visibility var(--dur-fast);
}
.user-menu:hover .dropdown,
.user-menu:focus-within .dropdown {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}
.dropdown a {
  display: block;
  padding: 8px 16px;
  font-size: var(--fs-sm);
  color: var(--color-text);
}
.dropdown a:hover {
  background: var(--color-primary-soft);
  color: var(--color-link);
}

/* ---------- 第二行：热搜 ---------- */
.hot-row {
  display: flex;
  align-items: center;
  gap: var(--gap-4);
  height: 26px;
  overflow: hidden;
  font-size: var(--fs-sm);
  color: rgba(0, 0, 0, 0.62);
}
.hot-label {
  flex: none;
  opacity: 0.7;
}
.hot-word {
  flex: none;
  transition: color var(--dur-fast);
}
.hot-word:hover {
  color: #1a1a1a;
  text-decoration: underline;
}

/* ---------- 响应式 ---------- */
@media (max-width: 991px) {
  .search { max-width: 300px; }
  .hot-row { display: none; }
  .topbar-main { height: 58px; }
  .logo-text { font-size: 18px; }
}
@media (max-width: 767px) {
  .search { display: none; }
  .nickname, .caret { display: none; }
}
</style>
