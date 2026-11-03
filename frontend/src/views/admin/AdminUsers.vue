<script setup>
import { computed, onMounted, ref } from 'vue'
import { getUsers, setUserStatus } from '@/api/admin'

/**
 * 用户管理（§10.7~10.8）。
 *
 * ⚠️ 这个页面显示微信 / QQ / 手机号 —— 整个项目里**只有这里**会把
 * 别人的联系方式批量展示出来（另一个出口是买家自己点「获取联系方式」）。
 * 理由：平台不参与交易，出现纠纷时管理员必须能联系上人。
 * 所以这页的权限完全依赖后端的 AdminInterceptor，前端藏菜单不算防护。
 *
 * 「禁用」的效果是**立即**的：登录返回 403，
 * 而且已经登录的旧 token 也会马上失效 —— 拦截器每个请求都查一次库确认状态。
 */
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const keyword = ref('')
const error = ref('')
const loading = ref(false)
const busyId = ref(null)

const me = computed(() => JSON.parse(localStorage.getItem('user') || 'null'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getUsers({
      page: page.value,
      size,
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

async function toggle(item) {
  const next = item.status === 1 ? 0 : 1
  const word = next === 1 ? '启用' : '禁用'
  if (!confirm(`确定${word}用户「${item.username}」吗？\n\n`
    + (next === 1 ? '启用后他可以重新登录。' : '禁用后他立刻无法登录，已登录的会话也会马上失效。'))) return

  busyId.value = item.id
  try {
    await setUserStatus(item.id, next)
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
    <h2>用户管理 <span class="count">共 {{ total }} 人</span></h2>

    <div class="toolbar">
      关键词 <input v-model="keyword" placeholder="用户名 / 昵称" @keyup.enter="search">
      <button @click="search">查询</button>
    </div>

    <p class="hint note">
      ⚠️ 本页显示的微信 / QQ / 手机号属于隐私数据，仅管理员可见（用于处理纠纷）。
      「禁用」是立即生效的：登录返回 403，已登录的旧 token 也会立刻失效。
    </p>

    <p v-if="error" class="msg-error">{{ error }}</p>
    <p v-if="loading" class="hint">加载中…</p>

    <table v-if="list.length">
      <thead>
        <tr>
          <th>id</th><th>用户名</th><th>昵称</th><th>微信</th><th>QQ</th><th>手机号</th>
          <th>角色</th><th>状态</th><th>发布/已售/收藏</th><th>注册时间</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="u in list" :key="u.id" :class="{ off: u.status === 0 }">
          <td>{{ u.id }}</td>
          <td>{{ u.username }}</td>
          <td>{{ u.nickname || '-' }}</td>
          <td>{{ u.wechat || '-' }}</td>
          <td>{{ u.qq || '-' }}</td>
          <td>{{ u.phone || '-' }}</td>
          <td>{{ u.role }}</td>
          <td><span class="st" :class="u.status === 1 ? 'st1' : 'st0'">{{ u.statusText }}</span></td>
          <td>{{ u.publishedCount }} / {{ u.soldCount }} / {{ u.favoriteCount }}</td>
          <td class="time">{{ u.createTime }}</td>
          <td>
            <button :disabled="busyId === u.id || u.id === me?.id"
                    :class="u.status === 1 ? 'danger' : 'primary'"
                    @click="toggle(u)">
              {{ u.status === 1 ? '禁用' : '启用' }}
            </button>
            <div v-if="u.id === me?.id" class="hint">当前登录账号</div>
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
.toolbar input { margin: 0 8px 0 4px; padding: 4px 8px; }
.toolbar button { padding: 4px 12px; cursor: pointer; }
.note { padding: 8px 12px; background: #fffbe6; border: 1px solid #ffe58f; border-radius: 6px; margin-bottom: 12px; line-height: 1.6; }
table { width: 100%; border-collapse: collapse; background: #fff; font-size: 13px; }
th, td { border: 1px solid #eee; padding: 6px 8px; text-align: left; }
th { background: #fafafa; font-weight: 500; color: #666; }
tr.off { background: #fafafa; color: #999; }
td.time { color: #999; white-space: nowrap; }
.st { padding: 1px 6px; border-radius: 3px; font-size: 12px; }
.st1 { background: #f6ffed; color: #389e0d; }
.st0 { background: #fff1f0; color: #cf1322; }
button { padding: 3px 10px; cursor: pointer; }
button.danger { border: 1px solid #c62828; color: #c62828; background: #fff1f0; }
button.primary { border: 1px solid #389e0d; color: #389e0d; background: #f6ffed; }
button:disabled { cursor: not-allowed; opacity: 0.5; }
.pager { display: flex; gap: 10px; align-items: center; font-size: 13px; margin-top: 12px; }
.msg-error { color: #c62828; font-size: 13px; }
.hint { color: #888; font-size: 13px; }
</style>
