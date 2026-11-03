<script setup>
import { useRouter } from 'vue-router'

/**
 * 404 页。
 *
 * 单独一个页面，而不是"弹个提示"：
 * 用户输错地址 / 商品被下架时，至少还知道自己在哪、能点回首页。
 */
const router = useRouter()
</script>

<template>
  <main class="container notfound">
    <div class="card panel">
      <div class="code">404</div>
      <h1>页面不见了</h1>
      <p class="hint">
        可能地址输错了，或者这件商品已经下架 / 被删除了。
      </p>
      <div class="actions">
        <button class="btn btn-primary" type="button" @click="router.push('/')">回首页</button>
        <button class="btn btn-ghost" type="button" @click="router.back()">返回上一页</button>
      </div>
    </div>
  </main>
</template>

<style scoped>
.notfound {
  padding-top: 64px;
  padding-bottom: 64px;
}
.panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 56px 24px;
  text-align: center;
}
.code {
  font-size: 64px;
  font-weight: 800;
  line-height: 1;
  color: var(--color-primary);
  text-shadow: 0 2px 0 #e6bc00;
}
h1 {
  font-size: var(--fs-lg);
}
.actions {
  display: flex;
  gap: var(--gap-2);
  margin-top: var(--gap-2);
}
</style>
