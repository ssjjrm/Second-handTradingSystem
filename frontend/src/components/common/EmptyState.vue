<script setup>
/**
 * 空状态。
 *
 * 一个只有"暂无数据"四个字的空页面，用户会以为页面坏了。
 * 空状态要回答三个问题：发生了什么 / 为什么 / 我该干什么。
 */
defineProps({
  title: { type: String, default: '这里什么都没有' },
  desc: { type: String, default: '' },
  actionText: { type: String, default: '' },
  icon: { type: String, default: '🫥' }
})
defineEmits(['action'])
</script>

<template>
  <div class="empty">
    <div class="empty-icon">{{ icon }}</div>
    <p class="empty-title">{{ title }}</p>
    <p v-if="desc" class="empty-desc">{{ desc }}</p>
    <button v-if="actionText" class="btn btn-primary btn-sm" type="button" @click="$emit('action')">
      {{ actionText }}
    </button>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 64px 16px;
  background: var(--color-card);
  border-radius: var(--radius-lg);
}
.empty-icon {
  font-size: 44px;
  line-height: 1;
  opacity: 0.85;
}
.empty-title {
  margin: 0;
  font-size: var(--fs-md);
  color: var(--color-text);
}
.empty-desc {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
  text-align: center;
}
</style>
