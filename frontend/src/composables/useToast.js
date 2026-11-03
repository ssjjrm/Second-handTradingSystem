import { reactive } from 'vue'

/**
 * 极简 toast（轻提示）。
 *
 * 为什么要它：视觉还原后不该再用 `alert()` —— 系统弹窗会把页面风格彻底破坏。
 * 这个只有二十来行：一个响应式数组 + 定时移除，由 `AppToast.vue` 渲染。
 *
 * 用法：`toast('已加入想要')`、`toast('保存失败', 'error')`
 */

export const toasts = reactive([])

let seq = 0

/**
 * @param {string} message 文案
 * @param {'success'|'error'|'info'} type 类型（决定颜色和小图标）
 * @param {number} duration 停留毫秒
 */
export function toast(message, type = 'success', duration = 2400) {
  if (!message) return
  const id = ++seq
  toasts.push({ id, message, type })

  setTimeout(() => {
    const i = toasts.findIndex((t) => t.id === id)
    if (i > -1) toasts.splice(i, 1)
  }, duration)
}

export function useToast() {
  return { toasts, toast }
}
