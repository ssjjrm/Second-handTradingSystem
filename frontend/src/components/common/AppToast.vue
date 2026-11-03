<script setup>
import { toasts } from '@/composables/useToast'

/**
 * 全局 toast 容器（挂在 App.vue 最外层）。
 *
 * 只负责渲染 `useToast` 里的数组，出现/消失都靠 CSS 过渡。
 * `pointer-events: none` —— 提示条不能挡住下面的按钮点击。
 */
const ICONS = { success: '✓', error: '!', info: 'i' }
</script>

<template>
  <div class="toast-host">
    <transition-group name="toast">
      <div v-for="t in toasts" :key="t.id" class="toast" :class="'toast-' + t.type">
        <span class="icon">{{ ICONS[t.type] || 'i' }}</span>
        <span>{{ t.message }}</span>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  left: 50%;
  top: 96px;
  z-index: var(--z-toast);
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 420px;
  padding: 9px 18px;
  font-size: var(--fs-sm);
  color: #fff;
  background: rgba(0, 0, 0, 0.82);
  border-radius: var(--radius-pill);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.18);
  backdrop-filter: blur(4px);
}

.icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  font-size: 11px;
  font-weight: 700;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
}

.toast-error .icon {
  background: #ff4d4f;
}

.toast-success .icon {
  background: #52c41a;
}

/* 进场：从上方滑下 + 淡入；离场反过来 */
.toast-enter-active,
.toast-leave-active {
  transition: opacity var(--dur-base) var(--ease), transform var(--dur-base) var(--ease);
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
