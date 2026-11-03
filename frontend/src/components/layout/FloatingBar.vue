<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUser } from '@/composables/useUser'

/**
 * 右侧悬浮工具条。
 *
 * 闲鱼有 6 个（发闲置/消息/商品码/APP/反馈/客服），我们只留 3 个 ——
 * 没有消息、没有 App、没有客服。**留不存在的按钮比不写更糟**：点了一点反应都没有。
 */
const router = useRouter()
const { isLoggedIn } = useUser()

const showTop = ref(false)

function onScroll() {
  showTop.value = window.scrollY > 400
}

function goPublish() {
  router.push(isLoggedIn.value ? '/publish' : { path: '/login', query: { redirect: '/publish' } })
}

function goMine() {
  router.push(isLoggedIn.value ? '/profile' : '/login')
}

function toTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <div class="floating-bar">
    <button class="item" type="button" title="发布闲置" @click="goPublish">
      <span class="ico">＋</span>
      <span class="txt">发闲置</span>
    </button>

    <button class="item" type="button" title="我的" @click="goMine">
      <span class="ico">👤</span>
      <span class="txt">我的</span>
    </button>

    <transition name="fade">
      <button v-if="showTop" class="item" type="button" title="回到顶部" @click="toTop">
        <span class="ico">↑</span>
        <span class="txt">顶部</span>
      </button>
    </transition>
  </div>
</template>

<style scoped>
.floating-bar {
  position: fixed;
  right: 20px;
  top: 42%;
  z-index: var(--z-floating);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 52px;
  height: 52px;
  color: var(--color-text-sub);
  background: var(--color-card);
  border: none;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-hover);
  transition: transform var(--dur-fast) var(--ease), color var(--dur-fast);
}
.item:hover {
  color: var(--color-price);
  transform: translateY(-2px);
}
.item:active {
  transform: scale(0.97);
}
.ico {
  font-size: 16px;
  line-height: 1;
}
.txt {
  font-size: 10px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--dur-fast);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 窄屏藏起来，别挡内容 */
@media (max-width: 767px) {
  .floating-bar { display: none; }
}
</style>
