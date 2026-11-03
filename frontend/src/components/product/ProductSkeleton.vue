<script setup>
/**
 * 商品卡片骨架屏。
 *
 * 为什么不用"加载中…"三个字：
 * 骨架屏能提前把版面占好，内容出来时不会整页跳动（这叫 CLS，是体验评分项）。
 */
defineProps({
  count: { type: Number, default: 10 }
})
</script>

<template>
  <div class="skeleton" v-for="i in count" :key="i">
    <div class="sk-img shimmer"></div>
    <div class="sk-body">
      <div class="sk-line shimmer" style="width: 92%"></div>
      <div class="sk-line shimmer" style="width: 64%"></div>
      <div class="sk-line shimmer" style="width: 40%; height: 18px"></div>
      <div class="sk-line shimmer" style="width: 52%"></div>
    </div>
  </div>
</template>

<style scoped>
.skeleton {
  overflow: hidden;
  background: var(--color-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}
.sk-img {
  width: 100%;
  aspect-ratio: 1 / 1;
}
.sk-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
}
.sk-line {
  height: 12px;
  border-radius: 4px;
}

/* 扫光：一条更亮的渐变从左扫到右 */
.shimmer {
  background: linear-gradient(90deg, #f2f3f5 25%, #e9eaec 37%, #f2f3f5 63%);
  background-size: 400% 100%;
  animation: shimmer 1.2s ease-in-out infinite;
}
@keyframes shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}
</style>
