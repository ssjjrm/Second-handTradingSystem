<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getCategoryTree } from '@/api/category'
import { getProducts } from '@/api/product'
import { addFavorite, getMyFavorites, removeFavorite } from '@/api/favorite'
import ProductGrid from '@/components/product/ProductGrid.vue'
import { useUser } from '@/composables/useUser'
import { toast } from '@/composables/useToast'

/**
 * 首页（P01）。
 *
 * 结构照闲鱼：**左边分类竖栏 + 右边商品流**。
 * 但闲鱼中间那块"促销位 + 2×2 彩色频道块"我们不做（用户早就定了）——
 * 那些位置放的是广告，我们没有运营内容可放，硬做就是几个空洞的色块。
 *
 * 所以首页的信息密度是：分类（导航）+ 商品（内容），一眼就知道这是个二手市场。
 *
 * ⚠️ 商品卡片复用列表页的 `ProductGrid` —— 这也是为什么首页要**最后做**：
 * 卡片先做好，首页就是"把组件摆一下"。
 */
const router = useRouter()
const { isLoggedIn } = useUser()

const PAGE_SIZE = 20

const categories = ref([])
const activeCategoryId = ref(null)

const products = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref('')

// 分类图标：emoji 便宜好用，真的换成 SVG 图标集反而更花时间
const ICONS = {
  数码: '📱',
  服饰: '👕',
  技能: '🎫',
  母婴: '🍼',
  家具: '🛋',
  文玩: '💍',
  食品: '🐱',
  图书: '📚',
  汽车: '🚗',
  五金: '🔧'
}

function iconOf(name) {
  const hit = Object.keys(ICONS).find((k) => (name || '').includes(k))
  return hit ? ICONS[hit] : '🔹'
}

const activeName = computed(() => {
  if (!activeCategoryId.value) return '猜你喜欢'
  return categories.value.find((c) => c.id === activeCategoryId.value)?.name || '猜你喜欢'
})

const hasMore = computed(() => products.value.length < total.value)

async function loadCategories() {
  try {
    categories.value = await getCategoryTree()
  } catch {
    categories.value = []
  }
}

async function load(reset = true) {
  if (loading.value) return
  loading.value = true
  error.value = ''
  if (reset) {
    page.value = 1
    products.value = []
  }
  try {
    const params = { page: page.value, size: PAGE_SIZE, sort: 'newest' }
    if (activeCategoryId.value) params.categoryId = activeCategoryId.value
    const data = await getProducts(params)
    const records = data.records || []
    products.value = reset ? records : products.value.concat(records)
    total.value = data.total || 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function selectCategory(id) {
  if (activeCategoryId.value === id) return
  activeCategoryId.value = id
  load(true)
}

function loadMore() {
  page.value += 1
  load(false)
}

function goList() {
  router.push({ path: '/products' })
}

// ---------- 收藏 ----------
const favSet = ref(new Set())

async function loadMyFavorites() {
  if (!isLoggedIn.value) return
  try {
    const data = await getMyFavorites({ size: 200 })
    favSet.value = new Set((data.records || []).map((f) => f.product?.id))
  } catch {
    /* 拿不到就显示空心星 */
  }
}

async function onFavorite(p) {
  if (!isLoggedIn.value) {
    router.push({ path: '/login', query: { redirect: '/' } })
    return
  }
  const set = new Set(favSet.value)
  try {
    if (set.has(p.id)) {
      await removeFavorite(p.id)
      set.delete(p.id)
      toast('已取消想要')
    } else {
      await addFavorite(p.id)
      set.add(p.id)
      p.favoriteCount = (p.favoriteCount || 0) + 1
      toast('已加入想要')
    }
    favSet.value = set
  } catch (e) {
    toast(e.message, 'error')
  }
}

onMounted(async () => {
  await loadCategories()
  loadMyFavorites()
  load(true)
})
</script>

<template>
  <main class="container page">
    <div class="layout">
      <!-- ==================== 左侧分类 ==================== -->
      <aside class="sidebar card">
        <a
          href="#"
          class="cat-item"
          :class="{ on: activeCategoryId === null }"
          @click.prevent="selectCategory(null)"
        >
          <span class="c-icon">🏠</span>
          <span class="c-name">全部闲置</span>
        </a>
        <a
          v-for="c in categories"
          :key="c.id"
          href="#"
          class="cat-item"
          :class="{ on: activeCategoryId === c.id }"
          @click.prevent="selectCategory(c.id)"
        >
          <span class="c-icon">{{ iconOf(c.name) }}</span>
          <span class="c-name">{{ c.name }}</span>
          <span v-if="c.productCount" class="c-count">{{ c.productCount }}</span>
        </a>

        <div class="sidebar-foot">
          <button class="btn btn-primary btn-sm" type="button" @click="router.push('/publish')">
            发布闲置
          </button>
        </div>
      </aside>

      <!-- ==================== 右侧内容 ==================== -->
      <section class="main">
        <!-- 分类 Tab（横向，窄屏可滚动） -->
        <nav class="tabs">
          <button
            class="tab"
            :class="{ on: activeCategoryId === null }"
            type="button"
            @click="selectCategory(null)"
          >
            猜你喜欢
          </button>
          <button
            v-for="c in categories"
            :key="c.id"
            class="tab"
            :class="{ on: activeCategoryId === c.id }"
            type="button"
            @click="selectCategory(c.id)"
          >
            {{ c.name }}
          </button>
        </nav>

        <div class="section-head">
          <h1 class="section-title">{{ activeName }}</h1>
          <span class="hint">共 {{ total }} 件在售</span>
          <button class="btn btn-ghost btn-sm more-link" type="button" @click="goList">
            去列表页筛选 →
          </button>
        </div>

        <p v-if="error" class="msg-error">{{ error }}</p>

        <ProductGrid
          :products="products"
          :loading="loading && !products.length"
          :columns="4"
          empty-title="这个分类还没有商品"
          empty-desc="换个分类看看，或者自己发一件？"
          @favorite="onFavorite"
        />

        <!-- 加载更多：首页不用分页器，滚动式的更自然 -->
        <div v-if="products.length && hasMore" class="load-more">
          <button class="btn btn-ghost" type="button" :disabled="loading" @click="loadMore">
            {{ loading ? '加载中…' : `加载更多（已显示 ${products.length}/${total}）` }}
          </button>
        </div>
        <p v-else-if="products.length" class="hint no-more">— 没有更多了 —</p>
      </section>
    </div>
  </main>
</template>

<style scoped>
.page {
  padding-top: var(--gap-4);
  padding-bottom: var(--gap-6);
}

.layout {
  display: grid;
  grid-template-columns: var(--sidebar-w) minmax(0, 1fr);
  gap: var(--gap-4);
  align-items: start;
}

/* ==================== 左侧分类 ==================== */
.sidebar {
  padding: var(--gap-2);
  position: sticky;
  top: calc(var(--header-h) + var(--gap-3));
}
.cat-item {
  display: flex;
  align-items: center;
  gap: var(--gap-2);
  height: 40px;
  padding: 0 10px;
  font-size: var(--fs-base);
  color: var(--color-text-sub);
  border-radius: var(--radius-lg);
  transition: background var(--dur-fast), color var(--dur-fast);
}
.cat-item:hover {
  color: var(--color-price);
  background: #fafbfc;
}
.cat-item.on {
  font-weight: 600;
  color: #1a1a1a;
  background: var(--color-primary-soft);
  box-shadow: inset 3px 0 0 var(--color-primary);
}
.c-icon {
  flex: none;
  width: 18px;
  font-size: 14px;
  text-align: center;
}
.c-name {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.c-count {
  flex: none;
  font-size: 11px;
  color: var(--color-text-muted);
}
.sidebar-foot {
  margin-top: var(--gap-2);
  padding: var(--gap-2) 6px 4px;
  border-top: 1px dashed var(--color-border);
  text-align: center;
}

/* ==================== 右侧 ==================== */
.main {
  min-width: 0;
}

.tabs {
  display: flex;
  gap: var(--gap-2);
  padding: 4px 0 var(--gap-3);
  overflow-x: auto;
  scrollbar-width: none;
}
.tabs::-webkit-scrollbar {
  display: none;
}
.tab {
  flex: none;
  height: 34px;
  padding: 0 18px;
  font-size: var(--fs-sm);
  color: var(--color-text-sub);
  background: var(--color-card);
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  transition: background var(--dur-fast), color var(--dur-fast), border-color var(--dur-fast);
}
.tab:hover {
  color: var(--color-link);
}
.tab.on {
  font-weight: 600;
  color: #1a1a1a;
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.section-head {
  display: flex;
  align-items: baseline;
  gap: var(--gap-3);
  margin-bottom: var(--gap-3);
}
.section-head .section-title {
  margin: 0;
}
.more-link {
  margin-left: auto;
  color: var(--color-text-muted);
  border-color: transparent;
}
.more-link:hover {
  color: var(--color-link);
}

.load-more {
  display: flex;
  justify-content: center;
  margin-top: var(--gap-4);
}
.no-more {
  margin: var(--gap-4) 0 0;
  text-align: center;
}

/* ==================== 响应式 ==================== */
@media (max-width: 1199px) {
  .layout {
    grid-template-columns: 200px minmax(0, 1fr);
  }
}
@media (max-width: 991px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .sidebar {
    position: static;
    display: none; /* 窄屏用上面的分类 Tab 就够了 */
  }
}
</style>
