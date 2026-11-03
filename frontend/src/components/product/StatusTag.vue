<script setup>
import { computed } from 'vue'

/**
 * 商品状态标签。
 *
 * 五种状态的配色集中在**这一个组件**里 ——
 * 后端已经给了 `statusText` 中文，前端只负责"什么状态用什么颜色"。
 * 这样在列表、详情、个人中心里，同一个状态的标签长得完全一样。
 */
const props = defineProps({
  status: { type: Number, default: 1 },
  /** 优先用后端给的中文；没有就退回本地映射 */
  text: { type: String, default: '' }
})

const TEXT = { 0: '待审核', 1: '在售', 2: '已售出', 3: '已下架', 4: '已拒绝' }

const label = computed(() => props.text || TEXT[props.status] || '未知')
const tone = computed(
  () =>
    ({
      0: 'warn', // 待审核：橙
      1: 'on',   // 在售：黄
      2: 'grey', // 已售出：灰
      3: 'grey', // 已下架：灰
      4: 'red'   // 已拒绝：红
    })[props.status] || 'grey'
)
</script>

<template>
  <span class="status-tag" :class="'tone-' + tone">{{ label }}</span>
</template>

<style scoped>
.status-tag {
  display: inline-block;
  padding: 1px 8px;
  font-size: var(--fs-xs);
  line-height: 18px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
}
.tone-on {
  color: var(--color-tag-new-text);
  background: var(--color-tag-new-bg);
}
.tone-warn {
  color: var(--color-tag-credit-text);
  background: var(--color-tag-credit-bg);
}
.tone-grey {
  color: var(--color-tag-grey-text);
  background: var(--color-tag-grey-bg);
}
.tone-red {
  color: var(--color-tag-warn-text);
  background: var(--color-tag-warn-bg);
}
</style>
