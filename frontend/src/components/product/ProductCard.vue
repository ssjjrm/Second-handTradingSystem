<script setup>
import { computed, ref } from 'vue'
import PriceTag from './PriceTag.vue'

/**
 * 商品卡片 —— 全站最核心的组件（列表页、首页、卖家主页都用它）。
 *
 * 信息层级完全照闲鱼（需求文档 §8.2）：
 *   ① 主图 1:1    ② 标题 2 行    ③ 标签行    ④ 价格行    ⑤ 卖家/地点行
 *
 * 两个细节值得注意：
 * 1. **卡片用 flex column + 价格行 margin-top:auto**，同一行卡片底部才会对齐
 *    （标题两行和一行混在一起时，不这么做会参差不齐）。
 * 2. **整卡是 `<a>`**，键盘可以 Tab 到；里面的收藏按钮要
 *    `@click.prevent.stop`，否则点收藏会跳走。
 */
const props = defineProps({
  product: { type: Object, required: true },
  /** 是否显示右上角的收藏按钮 */
  showFavorite: { type: Boolean, default: false },
  favorited: { type: Boolean, default: false }
})

const emit = defineEmits(['favorite'])

const p = computed(() => props.product || {})

const image = computed(() => {
  const list = p.value.images
  return Array.isArray(list) && list.length ? list[0] : ''
})

/**
 * 没有图片时的色块：用 id 生成一个确定的浅色，
 * 比统一的灰色好看，而且同一个商品每次刷新颜色一样（不会闪）。
 */
const fallbackStyle = computed(() => {
  const id = Number(p.value.id) || 0
  const hue = (id * 37) % 360
  return { background: `hsl(${hue}, 62%, 94%)`, color: `hsl(${hue}, 40%, 45%)` }
})

/** 只有"在售"才允许点收藏；已售出/已下架的不给操作 */
const canFavorite = computed(() => props.showFavorite && p.value.status === 1)

const isSold = computed(() => p.value.status === 2)
</script>

<template>
  <router-link class="card" :to="`/product/${p.id}`">
    <!-- ① 主图 -->
    <div class="thumb">
      <img
        v-if="image"
        v-img-fallback
        :src="image"
        :alt="p.title"
        loading="lazy"
        class="cover"
      />
      <div v-else class="noimg" :style="fallbackStyle">暂无图片</div>

      <span v-if="isSold" class="sold-mask">已售出</span>

      <button
        v-if="canFavorite"
        class="fav-btn"
        :class="{ on: favorited }"
        type="button"
        :title="favorited ? '取消想要' : '想要'"
        @click.prevent.stop="emit('favorite', p)"
      >
        {{ favorited ? '★' : '☆' }}
      </button>
    </div>

    <!-- ②~⑤ 文字区 -->
    <div class="body">
      <h3 class="title ellipsis-2">{{ p.title }}</h3>

      <div class="tags">
        <span v-if="p.conditionLevel" class="tag">{{ p.conditionLevel }}</span>
        <span v-if="p.brand" class="tag">{{ p.brand }}</span>
        <span v-if="p.categoryName" class="tag">{{ p.categoryName }}</span>
      </div>

      <div class="price-row">
        <PriceTag :price="p.price" :original="p.originalPrice" />
        <span v-if="p.favoriteCount > 0" class="want">{{ p.favoriteCount }}人想要</span>
      </div>

      <div class="seller-row">
        <span class="loc ellipsis">{{ p.tradePlace || '校园自提' }}</span>
        <span class="seller ellipsis">{{ p.sellerNickname }}</span>
      </div>
    </div>
  </router-link>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: inherit;
  background: var(--color-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  transition: transform var(--dur-base) var(--ease), box-shadow var(--dur-base) var(--ease);
}
.card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-hover);
}

/* ---------- 主图 ---------- */
.thumb {
  position: relative;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  background: #f5f5f5;
}
.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--dur-slow) var(--ease);
}
.card:hover .cover {
  transform: scale(1.04);
}
.noimg {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: var(--fs-sm);
}
.sold-mask {
  position: absolute;
  right: 8px;
  top: 8px;
  padding: 2px 8px;
  font-size: var(--fs-xs);
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
  border-radius: var(--radius-pill);
}

/* ---------- 收藏按钮（悬停才显现，触屏常显） ---------- */
.fav-btn {
  position: absolute;
  right: 8px;
  bottom: 8px;
  width: 30px;
  height: 30px;
  font-size: 16px;
  line-height: 1;
  color: #fff;
  background: rgba(0, 0, 0, 0.35);
  border: none;
  border-radius: 50%;
  opacity: 0;
  transition: opacity var(--dur-fast), background var(--dur-fast), transform var(--dur-fast);
}
.card:hover .fav-btn,
.fav-btn.on {
  opacity: 1;
}
.fav-btn:hover {
  background: rgba(0, 0, 0, 0.55);
}
.fav-btn.on {
  color: var(--color-primary);
  background: rgba(0, 0, 0, 0.6);
}
.fav-btn:active {
  transform: scale(1.2);
}

/* ---------- 文字区 ---------- */
.body {
  display: flex;
  flex-direction: column;
  flex: 1;
  gap: 6px;
  padding: 10px 10px 12px;
}
.title {
  margin: 0;
  min-height: 40px; /* 固定两行高度：一行标题和两行标题的卡片也能对齐 */
  font-size: var(--fs-base);
  font-weight: 400;
  line-height: 20px;
  color: var(--color-text);
}
.tags {
  display: flex;
  gap: 4px;
  overflow: hidden;
  white-space: nowrap;
}
.price-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto; /* ← 关键：把价格/卖家行顶到卡片底部 */
}
.want {
  flex: none;
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}
.seller-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}
.loc {
  max-width: 60%;
}
.seller {
  max-width: 40%;
  text-align: right;
}
</style>
