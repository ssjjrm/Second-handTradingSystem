<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMe, updatePassword, updateProfile } from '@/api/auth'
import { deleteProduct, getMyProducts, markSold, offShelfProduct } from '@/api/product'
import { getMyFavorites, removeFavorite } from '@/api/favorite'
import PriceTag from '@/components/product/PriceTag.vue'
import StatusTag from '@/components/product/StatusTag.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useUser } from '@/composables/useUser'
import { toast } from '@/composables/useToast'
import { formatDate, initialOf } from '@/composables/useFormat'

/**
 * 个人中心（P08）。
 *
 * S10 的改动：Tab 换成**左侧菜单** —— 更像闲鱼的个人主页，
 * 而且省掉了"上面一排 Tab + 下面一堆内容"的双层导航。
 * 三个菜单项：我发布的 / 我的收藏 / 联系方式设置。
 *
 * 这一页有两个不能丢的东西（S8 的成果）：
 * 1. `needAttention` 的橙色提醒 —— 平台没有订单，"成交后记得标记售出"只能靠提醒；
 * 2. 联系方式**逐条保存** —— 只有真的改了哪一项才提交哪一项。
 */
const route = useRoute()
const router = useRouter()
const { patchUser } = useUser()

const me = ref(null)
const loading = ref(false)
const error = ref('')

// 当前菜单（写进 URL，刷新后还在原来那一项）
const tab = computed(() => String(route.query.tab || 'published'))

const MENUS = [
  { key: 'published', label: '我发布的', icon: '📦' },
  { key: 'favorites', label: '我的收藏', icon: '⭐' },
  { key: 'contact', label: '联系方式设置', icon: '⚙️' }
]

// ---------------- 我发布的 ----------------
const myProducts = ref([])
const productsLoading = ref(false)
const statusFilter = ref('')

// ---------------- 我的收藏 ----------------
const favorites = ref([])
const favLoading = ref(false)

// ---------------- 联系方式 / 密码 ----------------
const form = ref({ nickname: '', wechat: '', qq: '', phone: '' })
const savingField = ref('')
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
  productsLoading.value = true
  try {
    const data = await getMyProducts({
      size: 100,
      status: statusFilter.value === '' ? undefined : Number(statusFilter.value)
    })
    myProducts.value = data.records || []
  } catch (e) {
    error.value = e.message
  } finally {
    productsLoading.value = false
  }
}

async function loadFavorites() {
  favLoading.value = true
  try {
    const data = await getMyFavorites({ size: 100 })
    favorites.value = data.records || []
  } catch (e) {
    error.value = e.message
  } finally {
    favLoading.value = false
  }
}

function switchTab(key) {
  router.push({ path: '/profile', query: key === 'published' ? {} : { tab: key } })
}

/** 按当前菜单拉数据（切菜单、改筛选都走这里） */
function loadCurrentTab() {
  if (tab.value === 'published') loadMyProducts()
  else if (tab.value === 'favorites') loadFavorites()
}

// ---------------- 我发布的：操作 ----------------
async function onMarkSold(item) {
  if (!confirm(`确定把「${item.title}」标记为已售出吗？\n\n标记后不可恢复。`)) return
  try {
    await markSold(item.id)
    toast('已标记售出')
    await loadMyProducts()
    await loadMe()
  } catch (e) {
    toast(e.message, 'error')
  }
}

async function onOffShelf(item) {
  if (!confirm(`确定把「${item.title}」下架吗？下架后买家就搜不到了。`)) return
  try {
    await offShelfProduct(item.id)
    toast('已下架')
    await loadMyProducts()
    await loadMe()
  } catch (e) {
    toast(e.message, 'error')
  }
}

async function onDelete(item) {
  if (!confirm(`确定删除「${item.title}」吗？\n\n相关的收藏和浏览记录也会一起删掉。`)) return
  try {
    await deleteProduct(item.id)
    toast('已删除')
    await loadMyProducts()
    await loadMe()
  } catch (e) {
    toast(e.message, 'error')
  }
}

async function onRemoveFavorite(item) {
  try {
    await removeFavorite(item.product.id)
    toast('已取消想要')
    await loadFavorites()
    await loadMe()
  } catch (e) {
    toast(e.message, 'error')
  }
}

// ---------------- 联系方式：逐条保存 ----------------
async function saveField(field) {
  savingField.value = field
  try {
    const payload = {}
    payload[field] = form.value[field]
    const updated = await updateProfile(payload)
    // 顶栏显示的是昵称，改完要同步一下
    patchUser({ nickname: updated?.nickname, avatar: updated?.avatar })
    await loadMe()
    toast(`「${labelOf(field)}」已保存`)
  } catch (e) {
    toast(e.message, 'error')
  } finally {
    savingField.value = ''
  }
}

function labelOf(field) {
  return { nickname: '昵称', wechat: '微信号', qq: 'QQ号', phone: '手机号' }[field] || field
}

async function onChangePassword() {
  pwdMsg.value = ''
  if (!pwd.value.oldPassword || !pwd.value.newPassword) {
    pwdMsg.value = '原密码和新密码都要填'
    return
  }
  try {
    await updatePassword(pwd.value)
    pwdMsg.value = '密码已修改，下次登录请用新密码'
    pwd.value = { oldPassword: '', newPassword: '' }
  } catch (e) {
    pwdMsg.value = e.message
  }
}

// ---------------- 生命周期 ----------------
onMounted(async () => {
  await loadMe()
  loadCurrentTab()
})

// 菜单或筛选变了就重新拉数据
watch(() => route.query.tab, loadCurrentTab)
watch(statusFilter, () => {
  if (tab.value === 'published') loadMyProducts()
})
</script>

<template>
  <main class="container page">
    <h1 class="page-title">个人中心</h1>

    <p v-if="error" class="msg-error">{{ error }}</p>

    <div class="layout">
      <!-- ==================== 左侧菜单 ==================== -->
      <aside class="side-menu card">
        <a
          v-for="m in MENUS"
          :key="m.key"
          href="#"
          class="menu-item"
          :class="{ on: tab === m.key }"
          @click.prevent="switchTab(m.key)"
        >
          <span class="m-icon">{{ m.icon }}</span>
          <span>{{ m.label }}</span>
        </a>
      </aside>

      <!-- ==================== 右侧内容 ==================== -->
      <section class="content">
        <!-- 资料卡 -->
        <div v-if="me" class="profile-card">
          <img v-if="me.avatar" v-img-fallback :src="me.avatar" class="avatar" alt="" />
          <span v-else class="avatar avatar-text">{{ initialOf(me.nickname || me.username) }}</span>

          <div class="who">
            <div class="name-row">
              <span class="name">{{ me.nickname || me.username }}</span>
              <span class="tag tag-credit">@{{ me.username }}</span>
              <span v-if="me.role === 'admin'" class="tag tag-new">管理员</span>
            </div>
            <div class="meta">
              <span v-if="me.wechat">微信 {{ me.wechat }}</span>
              <span v-else class="warn">未填微信号</span>
              <span class="dot">·</span>
              <span v-if="me.qq">QQ {{ me.qq }}</span>
              <span v-else class="warn">未填 QQ</span>
              <span class="dot">·</span>
              <span>加入于 {{ formatDate(me.createTime) }}</span>
            </div>
          </div>

          <button class="btn btn-ghost btn-sm edit" type="button" @click="switchTab('contact')">
            编辑资料
          </button>
        </div>

        <!-- 五个统计（接口给的，不是前端算的） -->
        <div v-if="me" class="stats">
          <div class="stat"><b>{{ me.stats.publishedCount }}</b><span>发布</span></div>
          <div class="stat"><b>{{ me.stats.onSaleCount }}</b><span>在售</span></div>
          <div class="stat"><b>{{ me.stats.soldCount }}</b><span>已售出</span></div>
          <div class="stat"><b>{{ me.stats.favoriteCount }}</b><span>我的收藏</span></div>
          <div class="stat highlight">
            <b>{{ me.stats.receivedContactCount }}</b><span>人想要我的东西</span>
          </div>
        </div>

        <!-- ==================== 我发布的 ==================== -->
        <section v-if="tab === 'published'" class="panel card">
          <div class="panel-head">
            <h2>我发布的</h2>
            <div class="head-tools">
              <select v-model="statusFilter" class="select">
                <option value="">全部状态</option>
                <option value="0">待审核</option>
                <option value="1">在售</option>
                <option value="2">已售出</option>
                <option value="3">已下架</option>
                <option value="4">已拒绝</option>
              </select>
              <router-link to="/publish" class="btn btn-primary btn-sm">发布闲置</router-link>
            </div>
          </div>

          <p v-if="productsLoading" class="hint">加载中…</p>

          <EmptyState
            v-else-if="!myProducts.length"
            title="还没有发布过商品"
            desc="把宿舍里闲置的东西挂上来，说不定就有人要"
            icon="📦"
            action-text="去发布"
            @action="router.push('/publish')"
          />

          <div v-else class="rows">
            <article v-for="item in myProducts" :key="item.id" class="row-card">
              <router-link :to="`/product/${item.id}`" class="row-main">
                <img
                  v-if="item.images && item.images.length"
                  v-img-fallback
                  :src="item.images[0]"
                  class="thumb"
                  alt=""
                />
                <div v-else class="thumb thumb-empty">无图</div>

                <div class="row-info">
                  <div class="row-title ellipsis">{{ item.title }}</div>
                  <div class="row-meta">
                    <StatusTag :status="item.status" :text="item.statusText" />
                    <span class="sep">·</span>
                    <PriceTag :price="item.price" />
                    <span class="sep">·</span>
                    <span>浏览 {{ item.viewCount }}</span>
                    <span class="sep">·</span>
                    <span>想要 {{ item.favoriteCount }}</span>
                    <span class="sep">·</span>
                    <b class="contact">咨询 {{ item.contactCount }}</b>
                  </div>
                  <!-- ⭐ 平台没有订单，成交只能靠卖家自己想起来标记 -->
                  <div v-if="item.needAttention" class="attention">
                    🔔 {{ item.contactCount }} 人想要，成交后请点「标记售出」
                  </div>
                </div>
              </router-link>

              <div class="row-actions">
                <button
                  v-if="item.status === 1"
                  class="btn btn-dark btn-sm"
                  type="button"
                  @click="onMarkSold(item)"
                >
                  标记售出
                </button>
                <button
                  v-if="item.status === 1"
                  class="btn btn-ghost btn-sm"
                  type="button"
                  @click="onOffShelf(item)"
                >
                  下架
                </button>
                <button
                  v-if="item.status !== 2"
                  class="btn btn-ghost btn-sm danger"
                  type="button"
                  @click="onDelete(item)"
                >
                  删除
                </button>
                <span v-if="item.status === 2" class="hint">已售出不可删改</span>
              </div>
            </article>
          </div>
        </section>

        <!-- ==================== 我的收藏 ==================== -->
        <section v-if="tab === 'favorites'" class="panel card">
          <div class="panel-head">
            <h2>我的收藏</h2>
            <span class="hint">已售出 / 已下架的商品也在这儿（灰色），让你知道它被买走了</span>
          </div>

          <p v-if="favLoading" class="hint">加载中…</p>

          <EmptyState
            v-else-if="!favorites.length"
            title="收藏夹还是空的"
            desc="看到想要的东西，点一下星标就收藏了"
            icon="⭐"
            action-text="去逛逛"
            @action="router.push('/products')"
          />

          <div v-else class="rows">
            <article
              v-for="item in favorites"
              :key="item.favoriteId"
              class="row-card"
              :class="{ faded: item.product.status !== 1 }"
            >
              <router-link :to="`/product/${item.product.id}`" class="row-main">
                <img
                  v-if="item.product.images && item.product.images.length"
                  v-img-fallback
                  :src="item.product.images[0]"
                  class="thumb"
                  alt=""
                />
                <div v-else class="thumb thumb-empty">无图</div>

                <div class="row-info">
                  <div class="row-title ellipsis">{{ item.product.title }}</div>
                  <div class="row-meta">
                    <StatusTag :status="item.product.status" :text="item.product.statusText" />
                    <span class="sep">·</span>
                    <PriceTag :price="item.product.price" />
                    <span class="sep">·</span>
                    <span>{{ item.product.tradePlace }}</span>
                  </div>
                  <div class="row-meta">
                    收藏于 {{ String(item.favoriteTime || '').replace('T', ' ').slice(0, 16) }}
                  </div>
                </div>
              </router-link>

              <div class="row-actions">
                <button class="btn btn-ghost btn-sm danger" type="button" @click="onRemoveFavorite(item)">
                  移除
                </button>
              </div>
            </article>
          </div>
        </section>

        <!-- ==================== 联系方式设置 ==================== -->
        <section v-if="tab === 'contact'" class="panel card">
          <div class="panel-head">
            <h2>联系方式设置</h2>
          </div>

          <p class="hint rules">
            每一项<b>单独保存</b> —— 只有你真的改了哪一项，才提交哪一项。
            联系方式只会在买家点「获取联系方式」时出现，<b>不会出现在任何列表页</b>。
          </p>

          <div class="field">
            <label>昵称</label>
            <input v-model="form.nickname" maxlength="50" />
            <button class="btn btn-ghost btn-sm" :disabled="savingField === 'nickname'" @click="saveField('nickname')">
              保存
            </button>
            <span class="hint">必填，会显示在商品卡片上</span>
          </div>

          <div class="field">
            <label>微信号</label>
            <input v-model="form.wechat" maxlength="50" placeholder="留空表示清空" />
            <button class="btn btn-ghost btn-sm" :disabled="savingField === 'wechat'" @click="saveField('wechat')">
              保存
            </button>
          </div>

          <div class="field">
            <label>QQ 号</label>
            <input v-model="form.qq" maxlength="12" placeholder="留空表示清空" />
            <button class="btn btn-ghost btn-sm" :disabled="savingField === 'qq'" @click="saveField('qq')">
              保存
            </button>
          </div>

          <p class="hint warn">⚠️ 微信和 QQ 必须至少留一个，否则买家点「获取联系方式」什么也拿不到。</p>

          <div class="field">
            <label>手机号</label>
            <input v-model="form.phone" maxlength="11" placeholder="仅用于账号找回，不会展示给别人" />
            <button class="btn btn-ghost btn-sm" :disabled="savingField === 'phone'" @click="saveField('phone')">
              保存
            </button>
          </div>

          <hr class="hr" />

          <h3 class="sub-title">修改密码</h3>
          <p v-if="pwdMsg" class="msg-ok">{{ pwdMsg }}</p>
          <div class="field">
            <label>原密码</label>
            <input v-model="pwd.oldPassword" type="password" />
          </div>
          <div class="field">
            <label>新密码</label>
            <input v-model="pwd.newPassword" type="password" placeholder="6~20 位" />
            <button class="btn btn-ghost btn-sm" @click="onChangePassword">修改密码</button>
          </div>
        </section>
      </section>
    </div>
  </main>
</template>

<style scoped>
.page {
  padding-top: var(--gap-4);
  padding-bottom: var(--gap-6);
}
.page-title {
  margin-bottom: var(--gap-3);
  font-size: var(--fs-lg);
}

.layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: var(--gap-4);
  align-items: start;
}

/* ---------- 左侧菜单 ---------- */
.side-menu {
  padding: var(--gap-2);
}
.menu-item {
  display: flex;
  align-items: center;
  gap: var(--gap-2);
  height: 44px;
  padding: 0 12px;
  font-size: var(--fs-base);
  color: var(--color-text-sub);
  border-radius: var(--radius-lg);
  transition: background var(--dur-fast), color var(--dur-fast);
}
.menu-item:hover {
  color: var(--color-text);
  background: #fafbfc;
}
.menu-item.on {
  font-weight: 600;
  color: #1a1a1a;
  background: var(--color-primary-soft);
  box-shadow: inset 3px 0 0 var(--color-primary);
}
.m-icon {
  font-size: 14px;
}

/* ---------- 资料卡 ---------- */
.content {
  display: flex;
  flex-direction: column;
  gap: var(--gap-3);
  min-width: 0;
}
.profile-card {
  display: flex;
  align-items: center;
  gap: var(--gap-4);
  padding: var(--gap-5);
  background: linear-gradient(135deg, #fff9db 0%, #ffffff 62%);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-card);
}
.avatar {
  width: 64px;
  height: 64px;
  flex: none;
  border-radius: 50%;
  object-fit: cover;
  background: #ffe9a8;
}
.avatar-text {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 600;
  color: #8a6100;
}
.who {
  min-width: 0;
}
.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.name {
  font-size: var(--fs-lg);
  font-weight: 600;
}
.meta {
  margin-top: 6px;
  font-size: var(--fs-sm);
  color: var(--color-text-sub);
}
.meta .dot {
  margin: 0 6px;
  opacity: 0.5;
}
.meta .warn {
  color: var(--color-tag-warn-text);
}
.edit {
  margin-left: auto;
  flex: none;
}

/* ---------- 统计 ---------- */
.stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--gap-3);
}
.stat {
  padding: var(--gap-3) var(--gap-4);
  text-align: center;
  background: var(--color-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}
.stat b {
  display: block;
  font-size: 22px;
  line-height: 1.3;
  color: var(--color-text);
}
.stat span {
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}
.stat.highlight b {
  color: var(--color-price);
}

/* ---------- 面板 ---------- */
.panel {
  padding: var(--gap-4);
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gap-3);
  flex-wrap: wrap;
  margin-bottom: var(--gap-3);
}
.panel-head h2 {
  font-size: var(--fs-md);
}
.head-tools {
  display: flex;
  align-items: center;
  gap: var(--gap-2);
}
.select {
  height: 30px;
  padding: 0 8px;
  font-size: var(--fs-sm);
  background: #fff;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  outline: none;
}

/* ---------- 行卡片 ---------- */
.rows {
  display: flex;
  flex-direction: column;
  gap: var(--gap-2);
}
.row-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gap-3);
  padding: var(--gap-3);
  background: #fcfcfd;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  transition: box-shadow var(--dur-fast), border-color var(--dur-fast);
}
.row-card:hover {
  border-color: #e6e6e6;
  box-shadow: var(--shadow-card);
}
.row-card.faded {
  opacity: 0.62;
}
.row-main {
  display: flex;
  align-items: center;
  gap: var(--gap-3);
  min-width: 0;
  flex: 1;
}
.thumb {
  width: 64px;
  height: 64px;
  flex: none;
  border-radius: var(--radius-md);
  object-fit: cover;
  background: #f2f3f5;
}
.thumb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}
.row-info {
  min-width: 0;
}
.row-title {
  font-size: var(--fs-base);
  font-weight: 500;
}
.row-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 4px;
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}
.sep {
  opacity: 0.5;
}
.contact {
  color: var(--color-price);
}
.attention {
  margin-top: 6px;
  padding: 4px 8px;
  font-size: var(--fs-xs);
  color: var(--color-tag-credit-text);
  background: var(--color-tag-credit-bg);
  border-left: 3px solid var(--color-tag-credit-text);
  border-radius: var(--radius-sm);
}
.row-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  flex: none;
}
.danger {
  color: var(--color-tag-warn-text);
}

/* ---------- 联系方式设置 ---------- */
.rules {
  margin: 0 0 var(--gap-4);
  padding: 10px 12px;
  background: #fafbfc;
  border-radius: var(--radius-md);
  line-height: 1.7;
}
.field {
  display: flex;
  align-items: center;
  gap: var(--gap-2);
  margin-bottom: var(--gap-3);
  flex-wrap: wrap;
}
.field label {
  width: 68px;
  flex: none;
  font-size: var(--fs-sm);
  color: var(--color-text-sub);
}
.field input {
  width: 240px;
  height: 34px;
  padding: 0 10px;
  font-size: var(--fs-base);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  outline: none;
  transition: border-color var(--dur-fast), box-shadow var(--dur-fast);
}
.field input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(255, 209, 0, 0.18);
}
.hr {
  margin: var(--gap-4) 0;
  border: none;
  border-top: 1px solid var(--color-border);
}
.sub-title {
  margin-bottom: var(--gap-3);
  font-size: var(--fs-base);
  font-weight: 600;
}

/* ---------- 响应式 ---------- */
@media (max-width: 991px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .side-menu {
    display: flex;
    overflow-x: auto;
  }
  .menu-item {
    flex: none;
    height: 38px;
  }
  .stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
