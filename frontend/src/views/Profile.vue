<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMe, updatePassword, updateProfile } from '@/api/auth'
import { deleteProduct, getMyProducts, markSold, offShelfProduct } from '@/api/product'
import { getMyFavorites, removeFavorite } from '@/api/favorite'

/**
 * 个人中心 —— 三个 Tab（注意：不是四个，因为本项目没有订单）。
 *
 * 「我发布的」是这一页的重点：{@code needAttention=true} 的商品要加醒目提醒，
 * 让卖家知道"有人想要，成交后记得标记售出"（BR-T09）。
 * 平台不产生订单，所以"标记售出"只能靠卖家自己想起来 —— 这个提醒就是为此存在的。
 */
const router = useRouter()

const tab = ref('published')
const me = ref(null)
const loading = ref(false)
const error = ref('')

// ---------------- 我发布的 ----------------
const myProducts = ref([])
const statusFilter = ref('')

// ---------------- 我的收藏 ----------------
const favorites = ref([])

// ---------------- 联系方式设置 ----------------
// 每一项**单独保存**（而不是整表单一起提交）。
// 这样"没改的字段"根本不会发出去，后端"null=不改 / ""=清空"的三态语义天然成立，
// 前端不需要去区分空输入框到底是"没改"还是"要清空"。
const form = ref({ nickname: '', wechat: '', qq: '', phone: '' })
const savingField = ref('')
const fieldMsg = ref('')
const pwd = ref({ oldPassword: '', newPassword: '' })
const pwdMsg = ref('')

async function loadMe() {
  loading.value = true
  error.value = ''
  try {
    me.value = await getMe()
    form.value = {
      nickname: me.value.nickname || '',
      wechat: me.value.wechat || '',
      qq: me.value.qq || '',
      phone: me.value.phone || ''
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function loadMyProducts() {
  try {
    const data = await getMyProducts({
      size: 50,
      status: statusFilter.value === '' ? undefined : Number(statusFilter.value)
    })
    myProducts.value = data.records
  } catch (e) {
    error.value = e.message
  }
}

async function loadFavorites() {
  try {
    const data = await getMyFavorites({ size: 50 })
    favorites.value = data.records
  } catch (e) {
    error.value = e.message
  }
}

async function switchTab(name) {
  tab.value = name
  error.value = ''
  if (name === 'published') await loadMyProducts()
  if (name === 'favorites') await loadFavorites()
}

// ---------------- 我发布的：操作 ----------------
async function onOffShelf(item) {
  if (!confirm(`确定把「${item.title}」下架吗？下架后买家就搜不到了。`)) return
  try {
    await offShelfProduct(item.id)
    await loadMyProducts()
  } catch (e) {
    alert(e.message)
  }
}

async function onMarkSold(item) {
  // 不可逆操作，必须二次确认
  if (!confirm(`确定把「${item.title}」标记为已售出吗？\n\n标记后将不可恢复，商品会从在售列表移除。`)) return
  try {
    await markSold(item.id)
    await loadMyProducts()
  } catch (e) {
    alert(e.message)
  }
}

async function onDelete(item) {
  if (!confirm(`确定删除「${item.title}」吗？\n\n相关的收藏和浏览记录也会一起删掉。`)) return
  try {
    await deleteProduct(item.id)
    await loadMyProducts()
  } catch (e) {
    alert(e.message)
  }
}

// ---------------- 我的收藏 ----------------
async function onRemoveFavorite(item) {
  try {
    await removeFavorite(item.product.id)
    await loadFavorites()
  } catch (e) {
    alert(e.message)
  }
}

// ---------------- 联系方式：逐条保存 ----------------
/**
 * 只提交被改的那一个字段。
 *
 * 传空字符串表示"清空这一项"（后端会存成 NULL）；
 * 后端还有一条规则：微信和 QQ 不能同时为空。
 */
async function saveField(field) {
  savingField.value = field
  fieldMsg.value = ''
  try {
    const payload = {}
    payload[field] = form.value[field]
    await updateProfile(payload)
    await loadMe()
    fieldMsg.value = `「${labelOf(field)}」已保存`
  } catch (e) {
    fieldMsg.value = e.message
  } finally {
    savingField.value = ''
  }
}

function labelOf(field) {
  return { nickname: '昵称', wechat: '微信号', qq: 'QQ号', phone: '手机号' }[field] || field
}

async function onChangePassword() {
  pwdMsg.value = ''
  try {
    await updatePassword(pwd.value)
    pwdMsg.value = '密码已修改，下次登录请用新密码'
    pwd.value = { oldPassword: '', newPassword: '' }
  } catch (e) {
    pwdMsg.value = e.message
  }
}

onMounted(async () => {
  await loadMe()
  await loadMyProducts()
})
</script>

<template>
  <main class="content">
    <h1>个人中心</h1>

    <p v-if="error" class="msg-error">{{ error }}</p>
    <p v-if="loading" class="hint">加载中…</p>

    <!-- 顶部统计 -->
    <div v-if="me" class="stats">
      <div class="stat"><b>{{ me.stats.publishedCount }}</b><span>发布</span></div>
      <div class="stat"><b>{{ me.stats.onSaleCount }}</b><span>在售</span></div>
      <div class="stat"><b>{{ me.stats.soldCount }}</b><span>已售出</span></div>
      <div class="stat"><b>{{ me.stats.favoriteCount }}</b><span>我的收藏</span></div>
      <div class="stat highlight">
        <b>{{ me.stats.receivedContactCount }}</b><span>人想要我的东西</span>
      </div>
    </div>

    <nav class="tabs">
      <button :class="{ active: tab === 'published' }" @click="switchTab('published')">我发布的</button>
      <button :class="{ active: tab === 'favorites' }" @click="switchTab('favorites')">我的收藏</button>
      <button :class="{ active: tab === 'contact' }" @click="switchTab('contact')">联系方式设置</button>
    </nav>

    <!-- ================= Tab 1：我发布的 ================= -->
    <section v-if="tab === 'published'">
      <div class="toolbar">
        状态筛选
        <select v-model="statusFilter" @change="loadMyProducts">
          <option value="">全部</option>
          <option value="0">待审核</option>
          <option value="1">在售</option>
          <option value="2">已售出</option>
          <option value="3">已下架</option>
          <option value="4">已拒绝</option>
        </select>
        <span class="hint">共 {{ myProducts.length }} 件</span>
      </div>

      <p v-if="!myProducts.length" class="hint">还没有发布过商品</p>

      <div v-for="item in myProducts" :key="item.id" class="row-card">
        <div class="row-main">
          <img v-if="item.images && item.images.length" :src="item.images[0]" class="thumb" alt="">
          <div class="row-info">
            <div class="row-title">
              {{ item.title }}
              <span class="tag">{{ item.statusText }}</span>
            </div>
            <div class="row-meta">
              ¥{{ item.price }}
              · 浏览 {{ item.viewCount }}
              · 想要 {{ item.favoriteCount }}
              · <b class="contact">咨询 {{ item.contactCount }}</b>
            </div>
            <!-- ⭐ 需要处理的提醒：在售 + 有人想要 -->
            <div v-if="item.needAttention" class="attention">
              🔔 {{ item.contactCount }} 人想要，成交后请点「标记售出」
            </div>
          </div>
        </div>

        <div class="row-actions">
          <button v-if="item.status === 1" @click="onMarkSold(item)">标记售出</button>
          <button v-if="item.status === 1" @click="onOffShelf(item)">下架</button>
          <button v-if="item.status !== 2" class="danger" @click="onDelete(item)">删除</button>
          <span v-if="item.status === 2" class="hint">已售出的商品不能删除或编辑</span>
        </div>
      </div>
    </section>

    <!-- ================= Tab 2：我的收藏 ================= -->
    <section v-if="tab === 'favorites'">
      <p v-if="!favorites.length" class="hint">收藏夹是空的</p>

      <div v-for="item in favorites" :key="item.favoriteId" class="row-card"
           :class="{ faded: item.product.status !== 1 }">
        <div class="row-main">
          <img v-if="item.product.images && item.product.images.length"
               :src="item.product.images[0]" class="thumb" alt="">
          <div class="row-info">
            <div class="row-title">
              {{ item.product.title }}
              <span class="tag">{{ item.product.statusText }}</span>
            </div>
            <div class="row-meta">
              ¥{{ item.product.price }}
              · {{ item.product.tradePlace }}
              · 卖家 {{ item.product.sellerNickname }}
            </div>
            <div class="row-meta">收藏于 {{ item.favoriteTime }}</div>
          </div>
        </div>
        <div class="row-actions">
          <button class="danger" @click="onRemoveFavorite(item)">移除</button>
        </div>
      </div>
    </section>

    <!-- ================= Tab 3：联系方式设置 ================= -->
    <section v-if="tab === 'contact'">
      <p class="hint">
        每一项<b>单独保存</b> —— 只有你真的改了哪一项，才提交哪一项。
        联系方式会出现在买家点「联系卖家」时的弹窗里，<b>不会出现在任何列表页</b>。
      </p>

      <p v-if="fieldMsg" class="msg-ok">{{ fieldMsg }}</p>

      <div class="field">
        <label>昵称</label>
        <input v-model="form.nickname" maxlength="50">
        <button :disabled="savingField === 'nickname'" @click="saveField('nickname')">保存</button>
        <span class="hint">必填，不能为空</span>
      </div>

      <div class="field">
        <label>微信号</label>
        <input v-model="form.wechat" maxlength="50" placeholder="留空表示清空">
        <button :disabled="savingField === 'wechat'" @click="saveField('wechat')">保存</button>
      </div>

      <div class="field">
        <label>QQ 号</label>
        <input v-model="form.qq" maxlength="12" placeholder="留空表示清空">
        <button :disabled="savingField === 'qq'" @click="saveField('qq')">保存</button>
      </div>

      <p class="hint">⚠️ 微信和 QQ 必须至少留一个，否则买家点「联系卖家」什么也拿不到。</p>

      <div class="field">
        <label>手机号</label>
        <input v-model="form.phone" maxlength="11" placeholder="仅用于账号找回，不会展示给别人">
        <button :disabled="savingField === 'phone'" @click="saveField('phone')">保存</button>
      </div>

      <hr>

      <h3>修改密码</h3>
      <p v-if="pwdMsg" class="msg-ok">{{ pwdMsg }}</p>
      <div class="field">
        <label>原密码</label>
        <input v-model="pwd.oldPassword" type="password">
      </div>
      <div class="field">
        <label>新密码</label>
        <input v-model="pwd.newPassword" type="password" placeholder="6~20 位">
      </div>
      <button @click="onChangePassword">修改密码</button>
    </section>
  </main>
</template>

<style scoped>
.content { padding: 24px; max-width: 960px; }
.stats { display: flex; gap: 24px; margin: 16px 0; padding: 16px; background: #fff; border: 1px solid #eee; border-radius: 8px; }
.stat { text-align: center; }
.stat b { display: block; font-size: 22px; color: #333; }
.stat span { font-size: 12px; color: #888; }
.stat.highlight b { color: #ff5000; }

.tabs { display: flex; gap: 8px; margin: 16px 0; }
.tabs button { padding: 8px 18px; border: 1px solid #ddd; background: #fff; cursor: pointer; border-radius: 4px; }
.tabs button.active { border-color: #ff5000; color: #ff5000; font-weight: bold; }

.toolbar { margin-bottom: 12px; font-size: 14px; }
.row-card { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 12px; margin-bottom: 10px; background: #fff; border: 1px solid #eee; border-radius: 6px; }
.row-card.faded { opacity: 0.55; }
.row-main { display: flex; gap: 12px; align-items: center; }
.thumb { width: 64px; height: 64px; object-fit: cover; border-radius: 4px; background: #f0f0f0; }
.row-title { font-size: 14px; font-weight: 500; }
.row-meta { font-size: 12px; color: #888; margin-top: 4px; }
.contact { color: #ff5000; }
.tag { margin-left: 6px; padding: 1px 6px; font-size: 12px; background: #fff0e5; color: #ff6600; border-radius: 3px; }
.attention { margin-top: 6px; padding: 4px 8px; font-size: 12px; color: #ff6600; background: #fff7e6; border-left: 3px solid #ff6600; }
.row-actions { display: flex; gap: 6px; flex-wrap: wrap; }
.row-actions button { padding: 5px 12px; font-size: 13px; cursor: pointer; }
button.danger { color: #c62828; }
.field { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.field label { width: 70px; font-size: 14px; color: #666; }
.field input { padding: 6px 10px; border: 1px solid #d9d9d9; border-radius: 4px; width: 240px; }
.msg-ok { color: #2e7d32; font-size: 13px; }
.msg-error { color: #c62828; font-size: 13px; }
.hint { color: #888; font-size: 13px; }
</style>
