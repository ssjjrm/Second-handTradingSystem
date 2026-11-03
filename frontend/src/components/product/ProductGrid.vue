<script setup>
import ProductCard from './ProductCard.vue'
import ProductSkeleton from './ProductSkeleton.vue'
import EmptyState from '@/components/common/EmptyState.vue'

/**
 * 商品网格 + 三态（加载 / 空 / 有数据）。
 *
 * 把"网格列数"和"状态切换"收在一个组件里，
 * 列表页、首页、卖家主页就都不用各写一遍 v-if。
 */
defineProps({
  products: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  /** 首页左侧有分类栏，列数要少一点 */
  columns: { type: Number, default: 5 },
  emptyTitle: { type: String, default: '没有找到商品' },
  emptyDesc: { type: String, default: '' }
})
defineEmits(['favorite'])
</script>

<template>
  <div>
    <!-- 加载中：骨架屏占位，版面不会跳 -->
    <div v-if="loading" class="grid" :style="{ '--cols': columns }">
      <ProductSkeleton :count="columns * 2" />
    </div>

    <!-- 空 -->
    <EmptyState
      v-else-if="!products.length"
      :title="emptyTitle"
      :desc="emptyDesc"
      icon="🔍"
    />

    <!-- 有数据 -->
    <div v-else class="grid" :style="{ '--cols': columns }">
      <ProductCard
        v-for="p in products"
        :key="p.id"
        :product="p"
        @favorite="$emit('favorite', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(var(--cols), minmax(0, 1fr));
  gap: var(--gap-3);
}

/* 窄一点就自动减列，不做横向滚动 —— 滚动条会让卡片被切一半 */
@media (max-width: 991px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 639px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
