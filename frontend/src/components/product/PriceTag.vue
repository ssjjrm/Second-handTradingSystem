<script setup>
import { computed } from 'vue'
import { formatPrice, formatPriceGrouped } from '@/composables/useFormat'

/**
 * 价格（现价 + 原价）。
 *
 * 单独抽出来是因为"价格"在三个地方出现（卡片、详情、个人中心），
 * 而且规则不简单：整数不带小数、有小数保留两位、原价加删除线。
 * 规则只写一份，改一处全站生效。
 */
const props = defineProps({
  price: { type: [Number, String], default: 0 },
  original: { type: [Number, String], default: null },
  /** 详情页用大字 + 千分位 */
  large: { type: Boolean, default: false }
})

const text = computed(() =>
  props.large ? formatPriceGrouped(props.price) : formatPrice(props.price)
)

// 原价必须比现价高才有展示意义（后端也校验了原价 ≥ 现价）
const showOriginal = computed(() => {
  const o = Number(props.original)
  return Number.isFinite(o) && o > Number(props.price)
})
</script>

<template>
  <div class="price-tag" :class="{ large }">
    <span class="symbol">¥</span>
    <span class="now">{{ text }}</span>
    <span v-if="showOriginal" class="origin">¥{{ formatPriceGrouped(original) }}</span>
  </div>
</template>

<style scoped>
.price-tag {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  color: var(--color-price);
  font-weight: 700;
  white-space: nowrap;
}
.symbol {
  font-size: 0.75em;
  font-weight: 600;
}
.now {
  font-size: 20px;
  line-height: 1.1;
}
.origin {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-muted);
  text-decoration: line-through;
}

.price-tag.large .now {
  font-size: 32px;
}
.price-tag.large .symbol {
  font-size: 18px;
}
.price-tag.large .origin {
  font-size: 14px;
}
</style>
