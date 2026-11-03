<script setup>
import { onMounted, ref } from 'vue'

/**
 * 公告条 —— 一行文案，成本极低，但"页面有运营位"的观感提升明显。
 *
 * 关闭状态存 localStorage：用户关过一次，就别每次刷新都再来烦他。
 */
const KEY = 'notice-closed'
const closed = ref(false)

onMounted(() => {
  closed.value = localStorage.getItem(KEY) === '1'
})

function close() {
  closed.value = true
  localStorage.setItem(KEY, '1')
}
</script>

<template>
  <div v-if="!closed" class="container notice-wrap">
    <div class="notice">
      <span class="icon">📢</span>
      <span class="text">
        校园内当面交易、平台不涉及收款 —— 请务必确认实物后再付款，不要提前转账。
      </span>
      <button class="close" type="button" aria-label="关闭公告" @click="close">×</button>
    </div>
  </div>
</template>

<style scoped>
.notice-wrap {
  margin-top: var(--gap-4);
}
.notice {
  display: flex;
  align-items: center;
  gap: var(--gap-2);
  height: 40px;
  padding: 0 var(--gap-4);
  font-size: var(--fs-sm);
  color: var(--color-text-sub);
  background: var(--color-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}
.icon {
  flex: none;
  font-size: 14px;
}
.text {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.close {
  flex: none;
  width: 24px;
  height: 24px;
  font-size: 16px;
  line-height: 1;
  color: var(--color-text-muted);
  background: transparent;
  border: none;
  border-radius: 50%;
  transition: background var(--dur-fast), color var(--dur-fast);
}
.close:hover {
  color: var(--color-text);
  background: #f2f3f5;
}
</style>
