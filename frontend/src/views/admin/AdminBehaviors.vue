<script setup>
import { onMounted, ref } from 'vue'
import { getBehaviors } from '@/api/admin'

/**
 * 咨询记录（§10.6）—— 这一栏**替代了参考项目的「订单管理」**。
 *
 * 本项目没有订单（平台不参与交易），所以后台不可能是"订单管理"。
 * 换来的是全站行为流水：浏览 / 收藏 / 咨询 / 取消收藏。
 * 其中「咨询」= 买家点了获取联系方式，是这个平台最接近成交的信号。
 *
 * ⚠️ 顶部四个汇总是**全站总量**，不随下面的筛选条件变化 ——
 * 它回答的是"这个平台有多活跃"，不是"这一页有多少条"。
 */
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const summary = ref(null)
const behaviorType = ref(3) // 默认只看咨询，因为它是后台最关心的
const productId = ref('')
const userId = ref('')
const error = ref('')
const loading = ref(false)

const TYPE_OPTIONS = [
  { value: 1, label: '浏览' },
  { value: 2, label: '收藏' },
  { value: 3, label: '咨询' },
  { value: 4, label: '取消收藏' }
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getBehaviors({
      page: page.value,
      size,
      behaviorType: behaviorType.value === '' ? undefined : Number(behaviorType.value),
      productId: productId.value ? Number(productId.value) : undefined,
      userId: userId.value ? Number(userId.value) : undefined
    })
    list.value = data.records
    total.value = data.total
    summary.value = data.summary
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

function search() {
  page.value = 1
  load()
}

function reset() {
  behaviorType.value = ''
  productId.value = ''
  userId.value = ''
  search()
}

function changePage(delta) {
  const pages = Math.max(1, Math.ceil(total.value / size))
  const next = page.value + delta
  if (next < 1 || next > pages) return
  page.value = next
  load()
}
</script>

<template>
  <div>
    <h2>咨询记录 <span class="count">共 {{ total }} 条</span></h2>

    <!-- 全站汇总：四个数不随筛选变化 -->
    <div v-if="summary" class="summary">
      <div class="sum">
        <b>{{ summary.viewCount }}</b><span>浏览</span>
      </div>
      <div class="sum">
        <b>{{ summary.favoriteCount }}</b><span>收藏</span>
      </div>
      <div class="sum highlight">
        <b>{{ summary.contactCount }}</b><span>咨询（≈成交意向）</span>
      </div>
      <div class="sum">
        <b>{{ summary.unfavoriteCount }}</b><span>取消收藏</span>
      </div>
      <p class="hint note">这四个是<b>全站总量</b>，不受下面的筛选条件影响。</p>
    </div>

    <div class="toolbar">
      类型
      <select v-model="behaviorType" @change="search">
        <option value="">全部</option>
        <option v-for="o in TYPE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
      商品 id <input v-model="productId" size="6" placeholder="可空">
      用户 id <input v-model="userId" size="6" placeholder="可空">
      <button @click="search">查询</button>
      <button @click="reset">重置</button>
    </div>

    <p v-if="error" class="msg-error">{{ error }}</p>
    <p v-if="loading" class="hint">加载中…</p>
    <p v-else-if="!list.length" class="hint">没有符合条件的记录</p>

    <table v-if="list.length">
      <thead>
        <tr><th>id</th><th>时间</th><th>类型</th><th>用户</th><th>商品</th><th>价格</th></tr>
      </thead>
      <tbody>
        <tr v-for="b in list" :key="b.id">
          <td>{{ b.id }}</td>
          <td class="time">{{ b.behaviorTime }}</td>
          <td>
            <span class="type" :class="'type' + b.behaviorType">{{ b.behaviorTypeText }}</span>
          </td>
          <td>{{ b.userNickname || b.userId }}</td>
          <td class="t">{{ b.productTitle }}</td>
          <td>¥{{ b.productPrice ?? '-' }}</td>
        </tr>
      </tbody>
    </table>

    <div v-if="total > size" class="pager">
      <button :disabled="page <= 1" @click="changePage(-1)">上一页</button>
      <span>第 {{ page }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span>
      <button :disabled="page >= Math.ceil(total / size)" @click="changePage(1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
h2 { font-size: 18px; margin: 0 0 16px; }
.count { font-size: 13px; font-weight: normal; color: #888; margin-left: 8px; }
.summary { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; padding: 12px 14px; margin-bottom: 12px; background: #fff; border: 1px solid #eee; border-radius: 8px; }
.sum { min-width: 110px; }
.sum b { display: block; font-size: 22px; color: #333; }
.sum span { font-size: 12px; color: #888; }
.sum.highlight b { color: #ff5000; }
.note { flex-basis: 100%; margin: 4px 0 0; }
.toolbar { margin-bottom: 12px; font-size: 13px; }
.toolbar select, .toolbar input { margin: 0 8px 0 4px; padding: 4px 8px; }
.toolbar button { padding: 4px 12px; margin-right: 6px; cursor: pointer; }
table { width: 100%; border-collapse: collapse; background: #fff; font-size: 13px; }
th, td { border: 1px solid #eee; padding: 6px 8px; text-align: left; }
th { background: #fafafa; font-weight: 500; color: #666; }
td.time { color: #999; white-space: nowrap; }
td.t { max-width: 300px; }
.type { padding: 1px 6px; border-radius: 3px; font-size: 12px; }
.type1 { background: #f0f5ff; color: #2f54eb; }
.type2 { background: #fff0e5; color: #ff6600; }
.type3 { background: #fff1f0; color: #cf1322; font-weight: bold; }
.type4 { background: #f0f0f0; color: #888; }
.pager { display: flex; gap: 10px; align-items: center; font-size: 13px; margin-top: 12px; }
.msg-error { color: #c62828; font-size: 13px; }
.hint { color: #888; font-size: 13px; }
</style>
