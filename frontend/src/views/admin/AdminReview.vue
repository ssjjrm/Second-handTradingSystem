<script setup>
import { onMounted, ref } from 'vue'
import { approveProduct, getPendingProducts, rejectProduct } from '@/api/admin'

/**
 * 商品审核（§10.1~10.3）。
 *
 * 列表里的商品都是 status=0（待审核），审核后只有两条出路：
 *   通过 → status=1（立刻出现在前台）
 *   拒绝 → status=4（只有卖家自己能在"我的发布"里看到）
 *
 * ⚠️ 拒绝原因目前**只写日志、不入库**（表里没有这一列），
 * 所以弹窗填的原因是给管理员自己/日志看的，卖家只能看到"已拒绝"。
 */
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 12
const error = ref('')
const loading = ref(false)
const busyId = ref(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getPendingProducts({ page: page.value, size })
    list.value = data.records
    total.value = data.total
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function onApprove(item) {
  if (!confirm(`审核通过「${item.title}」吗？\n\n通过后它会立刻出现在前台列表里。`)) return
  busyId.value = item.id
  try {
    await approveProduct(item.id)
    await load()
  } catch (e) {
    alert(e.message)
  } finally {
    busyId.value = null
  }
}

async function onReject(item) {
  const reason = prompt(`拒绝「${item.title}」。\n\n请填写原因（可以留空，原因只记后台日志）：`, '')
  if (reason === null) return // 点了取消
  busyId.value = item.id
  try {
    await rejectProduct(item.id, reason)
    await load()
  } catch (e) {
    alert(e.message)
  } finally {
    busyId.value = null
  }
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
    <h2>商品审核 <span class="count">待审 {{ total }} 件</span></h2>

    <p v-if="error" class="msg-error">{{ error }}</p>
    <p v-if="loading" class="hint">加载中…</p>
    <p v-else-if="!list.length" class="hint">没有待审核商品 🎉</p>

    <div v-for="item in list" :key="item.id" class="card">
      <div class="main">
        <img v-if="item.images && item.images.length" :src="item.images[0]" class="thumb" alt="">
        <div class="info">
          <div class="title">
            {{ item.title }}
            <span class="tag">{{ item.statusText }}</span>
          </div>
          <div class="meta">
            ¥{{ item.price }}
            <span v-if="item.originalPrice">（原价 ¥{{ item.originalPrice }}）</span>
            · {{ item.categoryName }} · {{ item.conditionLevel }} · {{ item.tradePlace }}
          </div>
          <div class="meta">卖家：{{ item.seller?.nickname || item.sellerId }} · {{ item.createTime }}</div>
          <div v-if="item.description" class="desc">{{ item.description }}</div>
        </div>
      </div>
      <div class="actions">
        <button class="primary" :disabled="busyId === item.id" @click="onApprove(item)">通过</button>
        <button class="danger" :disabled="busyId === item.id" @click="onReject(item)">拒绝</button>
      </div>
    </div>

    <div v-if="total > size" class="pager">
      <button :disabled="page <= 1" @click="changePage(-1)">上一页</button>
      <span>第 {{ page }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span>
      <button :disabled="page >= Math.ceil(total / size)" @click="changePage(1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
h2 { font-size: 18px; margin: 0 0 16px; }
.count { font-size: 13px; font-weight: normal; color: #ff6600; margin-left: 8px; }
.card { display: flex; justify-content: space-between; align-items: center; gap: 14px; padding: 12px; margin-bottom: 10px; background: #fff; border: 1px solid #eee; border-radius: 6px; }
.main { display: flex; gap: 12px; align-items: flex-start; }
.thumb { width: 72px; height: 72px; object-fit: cover; border-radius: 4px; background: #f0f0f0; }
.title { font-size: 14px; font-weight: 500; }
.tag { margin-left: 6px; padding: 1px 6px; font-size: 12px; background: #fff7e6; color: #d46b08; border-radius: 3px; }
.meta { font-size: 12px; color: #888; margin-top: 4px; }
.desc { font-size: 12px; color: #666; margin-top: 6px; max-width: 640px; }
.actions { display: flex; gap: 8px; flex: none; }
.actions button { padding: 6px 14px; cursor: pointer; }
button.primary { border: 1px solid #389e0d; color: #389e0d; background: #f6ffed; }
button.danger { border: 1px solid #c62828; color: #c62828; background: #fff1f0; }
.pager { display: flex; gap: 10px; align-items: center; font-size: 13px; margin-top: 12px; }
.msg-error { color: #c62828; font-size: 13px; }
.hint { color: #888; font-size: 13px; }
</style>
