<script setup>
import { computed } from 'vue'

/**
 * 分页器。
 *
 * 不引组件库，自己算页码：最多显示 7 个格子，
 * 多了就在两端放省略号。规则虽然简单，但"当前页在中间时两边都要露出来"
 * 这个细节不做的话，翻到第 20 页会看到一排孤零零的数字。
 */
const props = defineProps({
  total: { type: Number, default: 0 },
  current: { type: Number, default: 1 },
  size: { type: Number, default: 20 }
})
const emit = defineEmits(['change'])

const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))

/** 生成页码数组，含 '...' 占位 */
const items = computed(() => {
  const total = pages.value
  const cur = props.current
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)

  const list = [1]
  const start = Math.max(2, cur - 2)
  const end = Math.min(total - 1, cur + 2)

  if (start > 2) list.push('...')
  for (let i = start; i <= end; i++) list.push(i)
  if (end < total - 1) list.push('...')
  list.push(total)
  return list
})

function go(p) {
  if (typeof p !== 'number' || p === props.current || p < 1 || p > pages.value) return
  emit('change', p)
  // 翻页后回到顶部，否则用户会落在新一页的中段
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <nav v-if="pages > 1" class="pager" aria-label="分页">
    <button class="pg" :disabled="current <= 1" @click="go(current - 1)">上一页</button>

    <button
      v-for="(it, i) in items"
      :key="i"
      class="pg num"
      :class="{ active: it === current, dots: it === '...' }"
      :disabled="it === '...'"
      @click="go(it)"
    >
      {{ it }}
    </button>

    <button class="pg" :disabled="current >= pages" @click="go(current + 1)">下一页</button>
  </nav>
</template>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: var(--gap-6) 0 var(--gap-2);
}
.pg {
  min-width: 34px;
  height: 34px;
  padding: 0 10px;
  font-size: var(--fs-sm);
  color: var(--color-text-sub);
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition: border-color var(--dur-fast), color var(--dur-fast), background var(--dur-fast);
}
.pg:hover:not(:disabled):not(.active) {
  color: var(--color-link);
  border-color: var(--color-primary);
}
.pg.active {
  font-weight: 700;
  color: #1a1a1a;
  background: var(--color-primary);
  border-color: var(--color-primary);
}
.pg:disabled {
  color: var(--color-text-muted);
  cursor: not-allowed;
  opacity: 0.7;
}
.pg.dots {
  background: transparent;
  border-color: transparent;
}
</style>
