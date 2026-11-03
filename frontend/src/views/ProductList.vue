<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProducts } from '@/api/product'
import { getCategoryTree } from '@/api/category'
import { addFavorite, getMyFavorites, removeFavorite } from '@/api/favorite'
import ProductGrid from '@/components/product/ProductGrid.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import { useUser } from '@/composables/useUser'
import { toast } from '@/composables/useToast'

/**
 * 商品列表 / 搜索结果页（P02）。
 *
 * 设计要点：**地址栏是唯一的数据源**。
 * 排序、筛选、页码全部写进 URL，然后 watch(route.query) 重新请求。
 * 这样刷新、后退、复制链接发给同学，看到的都是同一批结果 ——
 * 如果把条件只存在组件的 ref 里，刷新一下就"回到最初"了。
 *
 * ⚠️ 接口已冻结（S2~S9），这里只是调用方，不改任何后端。
 */
const route = useRoute()
const router = useRouter()
const { isLoggedIn } = useUser()

const PAGE_SIZE = 20

const list = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')

// ---------- 从 URL 读条件（单一数据源） ----------
const page = computed(() => Number(route.query.page) || 1)
const keyword = computed(() => (route.query.keyword ? String(route.query.keyword) : ''))
const categoryId = computed(() =>
  route.query.categoryId ? Number(route.query.categoryId) : null
)
const sort = computed(() => (route.query.sort ? String(route.query.sort) : 'newest'))
const conditionLevel = computed(() =>
  route.query.conditionLevel ? String(route.query.conditionLevel) : ''
)

// 这几个用输入框录入，先放本地 state，点"确定"才写进 URL
const priceMin = ref(route.query.minPrice ? String(route.query.minPrice) : '')
const priceMax = ref(route.query.maxPrice ? String(route.query.maxPrice) : '')
const placeInput = ref(route.query.tradePlace ? String(route.query.tradePlace) : '')
const filterError = ref('')

// ---------- 分类名（面包屑） ----------
const categoryMap = ref({})

async function loadCategories() {
  try {
    const roots = await getCategoryTree()
    const map = {}
    roots.forEach((r) => {
      map[r.id] = { name: r.name, parent: null }
      ;(r.children || []).forEach((c) => {
        map[c.id] = { name: c.name, parent: r.name }
      })
    })
    categoryMap.value = map
  } catch {
    /* 分类名只是展示，拿不到不影响列表 */
  }
}

const categoryCrumb = computed(() => {
  const c = categoryId.value ? categoryMap.value[categoryId.value] : null
  if (!c) return ''
  return c.parent ? `${c.parent} / ${c.name}` : c.name
})

// ---------- 我的收藏（用来点亮卡片上的星标） ----------
const favSet = ref(new Set())

async function loadMyFavorites() {
  if (!isLoggedIn.value) return
  try {
    const data = await getMyFavorites({ size: 200 })
    favSet.value = new Set((data.records || []).map((f) => f.product?.id))
  } catch {
    /* 收藏状态拿不到，卡片显示空心星，不影响浏览 */
  }
}

// ---------- 请求 ----------
async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: page.value, size: PAGE_SIZE, sort: sort.value }
    if (keyword.value) params.keyword = keyword.value
    if (categoryId.value) params.categoryId = categoryId.value
    if (conditionLevel.value) params.conditionLevel = conditionLevel.value
    if (route.query.tradePlace) params.tradePlace = String(route.query.tradePlace)
    if (route.query.minPrice) params.minPrice = Number(route.query.minPrice)
    if (route.query.maxPrice) params.maxPrice = Number(route.query.maxPrice)

    const data = await getProducts(params)
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    error.value = e.message
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 改条件 = 改 URL；page 一律回到 1（除非显式翻页） */
function pushQuery(patch, resetPage = true) {
  const next = { ...route.query, ...patch }
  if (resetPage) delete next.page
  // 去掉空值，地址栏才干净（?keyword=&sort= 这种很难看）
  Object.keys(next).forEach((k) => {
    if (next[k] === '' || next[k] === null || next[k] === undefined) delete next[k]
  })
  router.push({ path: '/products', query: next })
}

function selectSort(value) {
  pushQuery({ sort: value === 'newest' ? undefined : value })
}

function onPriceSort() {
  // 点同一个"价格"在升/降之间切换
  pushQuery({ sort: sort.value === 'priceAsc' ? 'priceDesc' : 'priceAsc' })
}

function applyPrice() {
  filterError.value = ''
  const min = priceMin.value === '' ? null : Number(priceMin.value)
  const max = priceMax.value === '' ? null : Number(priceMax.value)
  if (min !== null && (Number.isNaN(min) || min < 0)) {
    filterError.value = '最低价要填一个非负数字'
    return
  }
  if (max !== null && (Number.isNaN(max) || max < 0)) {
    filterError.value = '最高价要填一个非负数字'
    return
  }
  if (min !== null && max !== null && min > max) {
    filterError.value = '最低价不能高于最高价'
    return
  }
  pushQuery({
    minPrice: min === null ? undefined : String(min),
    maxPrice: max === null ? undefined : String(max)
  })
}

function applyPlace() {
  pushQuery({ tradePlace: placeInput.value.trim() || undefined })
}

function applyCondition(e) {
  pushQuery({ conditionLevel: e.target.value || undefined })
}

function clearAll() {
  priceMin.value = ''
  priceMax.value = ''
  placeInput.value = ''
  filterError.value = ''
  router.push({ path: '/products' })
}

function changePage(p) {
  pushQuery({ page: String(p) }, false)
}

// ---------- 收藏 ----------
async function onFavorite(p) {
  if (!isLoggedIn.value) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
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

// ---------- 生命周期 ----------
onMounted(() => {
  loadCategories()
  loadMyFavorites()
})

// 条件变了就重新请求（URL 是唯一数据源）
watch(() => route.fullPath, load, { immediate: true })

// 换关键词/分类时，把本地的价格、地点输入框同步回 URL 里的值
watch(
  () => [route.query.minPrice, route.query.maxPrice, route.query.tradePlace],
  () => {
    priceMin.value = route.query.minPrice ? String(route.query.minPrice) : ''
    priceMax.value = route.query.maxPrice ? String(route.query.maxPrice) : ''
    placeInput.value = route.query.tradePlace ? String(route.query.tradePlace) : ''
  }
)

const CONDITION_OPTIONS = ['全新', '几乎全新', '轻微使用痕迹', '明显使用痕迹']
</script>

<template>
  <main class="container page">
    <!-- 筛选卡 -->
    <section class="filter card">
      <div class="row row-main">
        <div class="sorts">
          <button class="sort" :class="{ on: sort === 'newest' }" @click="selectSort('newest')">
            最新发布
          </button>
          <button
            class="sort"
            :class="{ on: sort === 'priceAsc' || sort === 'priceDesc' }"
            @click="onPriceSort"
          >
            价格 <span class="arrow">{{ sort === 'priceDesc' ? '↓' : '↑' }}</span>
          </button>
        </div>

        <span class="divider"></span>

        <label class="field">
          成色
          <select :value="conditionLevel" class="select" @change="applyCondition">
            <option value="">不限</option>
            <option v-for="c in CONDITION_OPTIONS" :key="c" :value="c">{{ c }}</option>
          </select>
        </label>

        <label class="field">
          交易地点
          <input
            v-model="placeInput"
            class="input"
            placeholder="如：东区食堂"
            @keyup.enter="applyPlace"
          />
        </label>
      </div>

      <div class="row">
        <label class="field">
          价格区间
          <input v-model="priceMin" class="input input-price" placeholder="最低" />
          <span class="tilde">-</span>
          <input v-model="priceMax" class="input input-price" placeholder="最高" />
          <button class="btn btn-ghost btn-sm" type="button" @click="applyPrice">确定</button>
        </label>

        <button class="btn btn-sm clear" type="button" @click="clearAll">清空条件</button>
      </div>

      <p v-if="filterError" class="msg-error filter-error">{{ filterError }}</p>

      <!-- 当前生效条件 -->
      <div v-if="keyword || categoryCrumb" class="chips">
        <span class="chips-label">当前筛选：</span>
        <span v-if="keyword" class="chip">
          关键词「{{ keyword }}」
          <a href="#" @click.prevent="pushQuery({ keyword: undefined })">×</a>
        </span>
        <span v-if="categoryCrumb" class="chip">
          {{ categoryCrumb }}
          <a href="#" @click.prevent="pushQuery({ categoryId: undefined })">×</a>
        </span>
      </div>
    </section>

    <!-- 结果头 -->
    <div class="result-head">
      <h1 class="result-title">
        <template v-if="keyword">「{{ keyword }}」的搜索结果</template>
        <template v-else-if="categoryCrumb">{{ categoryCrumb }}</template>
        <template v-else>全部商品</template>
      </h1>
      <span class="result-count">共 {{ total }} 件</span>
    </div>

    <p v-if="error" class="msg-error">{{ error }}</p>

    <ProductGrid
      :products="list"
      :loading="loading"
      :columns="5"
      empty-title="没找到相关商品"
      empty-desc="换个关键词试试，或者回首页看看大家都在卖什么"
      @favorite="onFavorite"
    />

    <AppPagination
      v-if="!loading && total > 0"
      :total="total"
      :current="page"
      :size="PAGE_SIZE"
      @change="changePage"
    />
  </main>
</template>

<style scoped>
.page {
  padding-top: var(--gap-4);
  padding-bottom: var(--gap-6);
}

/* ---------- 筛选卡 ---------- */
.filter {
  padding: var(--gap-3) var(--gap-4);
  margin-bottom: var(--gap-4);
}
.row {
  display: flex;
  align-items: center;
  gap: var(--gap-4);
  flex-wrap: wrap;
  min-height: 34px;
}
.row + .row {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--color-border);
}

.sorts {
  display: flex;
  gap: var(--gap-1);
}
.sort {
  padding: 4px 12px;
  font-size: var(--fs-sm);
  color: var(--color-text-sub);
  background: transparent;
  border: none;
  border-radius: var(--radius-pill);
  transition: background var(--dur-fast), color var(--dur-fast);
}
.sort:hover {
  color: var(--color-link);
}
.sort.on {
  font-weight: 600;
  color: #1a1a1a;
  background: var(--color-primary);
}
.arrow {
  font-size: 11px;
}

.divider {
  width: 1px;
  height: 16px;
  background: var(--color-border);
}

.field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-sm);
  color: var(--color-text-sub);
}
.select,
.input {
  height: 30px;
  padding: 0 8px;
  font-size: var(--fs-sm);
  background: #fff;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  outline: none;
  transition: border-color var(--dur-fast), box-shadow var(--dur-fast);
}
.select:focus,
.input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(255, 209, 0, 0.18);
}
.input {
  width: 132px;
}
.input-price {
  width: 74px;
}
.tilde {
  color: var(--color-text-muted);
}

.clear {
  margin-left: auto;
  color: var(--color-text-muted);
  border-color: transparent;
}
.clear:hover {
  color: var(--color-link);
}

.filter-error {
  margin: 6px 0 0;
}

/* ---------- 生效条件 chips ---------- */
.chips {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: var(--gap-2);
  padding-top: var(--gap-2);
  border-top: 1px dashed var(--color-border);
  font-size: var(--fs-sm);
}
.chips-label {
  color: var(--color-text-muted);
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  color: var(--color-tag-credit-text);
  background: var(--color-tag-credit-bg);
  border-radius: var(--radius-pill);
}
.chip a {
  color: inherit;
  opacity: 0.7;
}
.chip a:hover {
  opacity: 1;
}

/* ---------- 结果头 ---------- */
.result-head {
  display: flex;
  align-items: baseline;
  gap: var(--gap-3);
  margin: 0 0 var(--gap-3);
}
.result-title {
  font-size: var(--fs-md);
  font-weight: 600;
}
.result-count {
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
}
</style>
