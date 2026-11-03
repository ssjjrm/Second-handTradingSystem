<script setup>
import { onMounted, ref } from 'vue'
import { forceOffShelf, getAllProducts } from '@/api/admin'

/**
 * 商品管理（§10.4~10.5）。
 *
 * 和前台列表最大的区别：**所有状态都看得到**。
 * 管理员最常干的事是排查"这件商品为什么搜不到" —— 十有八九是还在待审核，
 * 或者被谁下架/拒绝了。前台列表永远看不到这些，所以后台必须能看。
 */
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 12
const status = ref('')
const keyword = ref('')
const error = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getAllProducts({
      page: page.value,
      size,
      status: status.value === '' ? undefined : Number(status.value),
      keyword: keyword.value.trim() || undefined
    })
    list.value = data.records
    total.value = data.total
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
  status.value = ''
  keyword.value = ''
  search()
}

async function onOffShelf(item) {
  if (!confirm(`强制下架「${item.title}」吗？\n\n管理员可以下架任何人的商品（用于处理违规），下架后买家就搜不到了。`)) return
  try {
    await forceOffShelf(item.id)
    await load()
  } catch (e) {
    alert(e.message)
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
    <h2>商品管理 <span class="count">共 {{ total }} 件（所有状态）</span></h2>

    <div class="toolbar">
      状态
      <select v-model="status" @change="search">
        <option value="">全部</option>
        <option value="0">待审核</option>
        <option value="1">在售</option>
        <option value="2">已售出</option>
        <option value="3">已下架</option>
        <option value="4">已拒绝</option>
      </select>
      关键词 <input v-model="keyword" placeholder="标题 / 描述 / 品牌" @keyup.enter="search">
      <button @click="search">查询</button>
      <button @click="reset">重置</button>
    </div>

    <p v-if="error" class="msg-error">{{ error }}</p>
    <p v-if="loading" class="hint">加载中…</p>
    <p v-else-if="!list.length" class="hint">没有符合条件的商品</p>

    <table v-if="list.length">
      <thead>
        <tr>
          <th>id</th><th>标题</th><th>价格</th><th>分类</th>
          <th>状态</th><th>卖家</th><th>浏览/想要</th><th>发布时间</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in list" :key="item.id">
          <td>{{ item.id }}</td>
          <td class="t">{{ item.title }}</td>
          <td>¥{{ item.price }}</td>
          <td>{{ item.categoryName }}</td>
          <td><span class="st" :class="'st' + item.status">{{ item.statusText }}</span></td>
          <td>{{ item.seller?.nickname || item.sellerId }}</td>
          <td>{{ item.viewCount }} / {{ item.favoriteCount }}</td>
          <td class="time">{{ item.createTime }}</td>
          <td>
            <button v-if="item.status === 1" class="danger" @click="onOffShelf(item)">强制下架</button>
            <span v-else class="hint">-</span>
          </td>
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
.toolbar { margin-bottom: 12px; font-size: 13px; }
.toolbar select, .toolbar input { margin: 0 8px 0 4px; padding: 4px 8px; }
.toolbar button { padding: 4px 12px; margin-right: 6px; cursor: pointer; }
table { width: 100%; border-collapse: collapse; background: #fff; font-size: 13px; }
th, td { border: 1px solid #eee; padding: 6px 8px; text-align: left; }
th { background: #fafafa; font-weight: 500; color: #666; }
td.t { max-width: 260px; }
td.time { color: #999; white-space: nowrap; }
.st { padding: 1px 6px; border-radius: 3px; font-size: 12px; }
.st0 { background: #fff7e6; color: #d46b08; }
.st1 { background: #f6ffed; color: #389e0d; }
.st2 { background: #f0f0f0; color: #888; }
.st3 { background: #f0f0f0; color: #888; }
.st4 { background: #fff1f0; color: #cf1322; }
button.danger { border: 1px solid #c62828; color: #c62828; background: #fff1f0; padding: 2px 8px; cursor: pointer; }
.pager { display: flex; gap: 10px; align-items: center; font-size: 13px; margin-top: 12px; }
.msg-error { color: #c62828; font-size: 13px; }
.hint { color: #888; font-size: 13px; }
</style>
