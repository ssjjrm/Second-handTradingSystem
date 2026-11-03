<script setup>
/**
 * 页脚 —— 一行字，唯一作用是把页面"收住"。
 * 没有它，内容少的时候（比如空状态）页面会显得悬空。
 */
</script>

<template>
  <footer class="footer">
    <div class="container inner">
      <span>校园二手交易平台</span>
      <span class="dot">·</span>
      <span>课程项目 · 仅供学习演示</span>
      <span class="dot">·</span>
      <span>平台不参与交易，请当面验货后付款</span>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  margin-top: var(--gap-8);
  padding: var(--gap-5) 0 var(--gap-6);
  border-top: 1px solid var(--color-border);
}
.inner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}
.dot {
  opacity: 0.6;
}
</style>
