<script setup>
import { computed, onMounted, ref } from 'vue'
import { getStats } from '@/api/admin'

/**
 * 数据统计（§10.9）—— 进后台第一眼看到的页面。
 *
 * 这里**刻意没有"成交额"**：平台不参与交易，卖家实际卖了多少钱、
 * 有没有议价，后端根本不知道。硬造一个数字就是编数据，
 * 所以用「已售出商品数」作为成交量代理指标（需求文档 §4.2.5 记录的已知代价）。
 */
const stats = ref(null)
const error = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    stats.value = await getStats()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

/**
 * 卡片分组：把 10 个数字按"规模 / 商品 / 活跃度"分三组，
 * 比一股脑平铺 10 个格子更容易看懂。
 */
const groups = computed(() => {
  const s = stats.value
  if (!s) return []
  return [
    {
      title: '用户',
      items: [
        { label: '用户总数', value: s.totalUsers },
        { label: '今日新增', value: s.todayNewUsers }
      ]
    },
    {
      title: '商品',
      items: [
        { label: '商品总数（所有状态）', value: s.totalProducts },
        { label: '在售', value: s.onSaleProducts },
        { label: '已售出（成交量代理）', value: s.soldProducts, highlight: true },
        { label: '待审核（待办）', value: s.pendingProducts, warn: s.pendingProducts > 0 },
        { label: '今日新增', value: s.todayNewProducts }
      ]
    },
    {
      title: '活跃度',
      items: [
        { label: '收藏总记录', value: s.totalFavoriteRecords },
        { label: '咨询总次数', value: s.totalContactRecords, highlight: true },
        { label: '今日咨询', value: s.todayContacts }
      ]
    }
  ]
})
</script>

<template>
  <div>
    <h2>数据统计</h2>

    <p v-if="error" class="msg-error">{{ error }}</p>
    <p v-if="loading" class="hint">加载中…</p>

    <div v-for="g in groups" :key="g.title" class="group">
      <h3>{{ g.title }}</h3>
      <div class="cards">
        <div v-for="it in g.items" :key="it.label" class="card"
             :class="{ highlight: it.highlight, warn: it.warn }">
          <b>{{ it.value }}</b>
          <span>{{ it.label }}</span>
        </div>
      </div>
    </div>

    <p class="hint note">
      ⚠️ 没有「成交额」—— 平台只做信息撮合，买卖双方线下交易，
      后端无法得知真实成交价。所以用「已售出」数量作为成交量代理指标。
      <br>
      另外：「商品总数」包含待审核 / 已下架 / 已拒绝，所以在售 + 已售出
      <b>不等于</b> 商品总数，这是对的。
    </p>

    <button @click="load">刷新</button>
  </div>
</template>

<style scoped>
h2 { font-size: 18px; margin: 0 0 16px; }
.group { margin-bottom: 20px; }
h3 { font-size: 14px; color: #666; margin: 0 0 8px; }
.cards { display: flex; gap: 12px; flex-wrap: wrap; }
.card {
  min-width: 160px; padding: 14px 16px; background: #fff;
  border: 1px solid #eee; border-radius: 8px;
}
.card b { display: block; font-size: 24px; color: #333; line-height: 1.2; }
.card span { font-size: 12px; color: #888; }
.card.highlight b { color: #ff5000; }
.card.warn { border-color: #ffd100; background: #fffbe6; }
.note { margin: 16px 0; padding: 12px; background: #fff; border: 1px solid #eee; border-radius: 6px; line-height: 1.7; }
.msg-error { color: #c62828; font-size: 13px; }
.hint { color: #888; font-size: 13px; }
button { padding: 6px 16px; cursor: pointer; }
</style>
