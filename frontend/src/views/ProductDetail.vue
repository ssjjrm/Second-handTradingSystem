<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  deleteProduct,
  getContact,
  getProductDetail,
  markSold,
  offShelfProduct
} from '@/api/product'
import { addFavorite, removeFavorite } from '@/api/favorite'
import PriceTag from '@/components/product/PriceTag.vue'
import StatusTag from '@/components/product/StatusTag.vue'
import ContactDialog from '@/components/common/ContactDialog.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useUser } from '@/composables/useUser'
import { toast } from '@/composables/useToast'
import { formatDate, initialOf, timeAgo } from '@/composables/useFormat'

/**
 * 商品详情页（P03）—— 本项目交互最重的一页。
 *
 * 三件事在这里同时发生：
 * 1. **身份决定按钮**：游客 / 买家 / 卖家本人看到的操作完全不同；
 * 2. **联系方式只有一个出口**：点「获取联系方式」弹窗，且这次请求会记一条咨询行为；
 * 3. **卖家自己的商品**能标记售出 / 下架 / 删除（都已验过，直接复用）。
 *
 * ⚠️ 接口已冻结，这里只负责把 `canContact` / `contactHint` / `isFavorited`
 * 这几个**后端已经算好的字段**翻译成界面 —— 前端不重新判断业务规则。
 */
const route = useRoute()
const router = useRouter()
const { isLoggedIn, user } = useUser()

const product = ref(null)
const loading = ref(false)
const error = ref('')
const notFound = ref(false)
const busy = ref(false)

// 图片画廊
const activeImage = ref(0)

// 描述展开（只有真的超过 4 行才显示「展开」，否则短描述下面挂个按钮很奇怪）
const descExpanded = ref(false)
const descOverflow = ref(false)
const descRef = ref(null)

function measureDesc() {
  const el = descRef.value
  if (!el) return
  descOverflow.value = el.scrollHeight > el.clientHeight + 2
}

// 联系弹窗
const dialogOpen = ref(false)
const dialogLoading = ref(false)
const dialogError = ref('')
const contact = ref(null)

// 收藏
const favorited = ref(false)

const isOwner = computed(
  () => !!product.value && !!user.value && product.value.sellerId === user.value.id
)
const isOnSale = computed(() => product.value?.status === 1)
const seller = computed(() => product.value?.seller || null)

const images = computed(() => {
  const list = product.value?.images
  return Array.isArray(list) && list.length ? list : []
})

const conditionText = computed(() => product.value?.conditionLevel || '-')

async function load() {
  const id = route.params.id
  loading.value = true
  error.value = ''
  notFound.value = false
  descExpanded.value = false
  activeImage.value = 0
  try {
    const data = await getProductDetail(id)
    product.value = data
    favorited.value = !!data.isFavorited
    // 等 DOM 渲染完再量高度，判断描述有没有超过 4 行
    requestAnimationFrame(() => {
      descExpanded.value = false
      measureDesc()
    })
  } catch (e) {
    product.value = null
    // 后端对"不存在"和"不该给你看"都返回 404，界面上统一按 404 处理
    if (e.message && (e.message.includes('不存在') || e.message.includes('404'))) {
      notFound.value = true
    } else {
      error.value = e.message
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.id, load)

// ==================== 收藏（想要） ====================
async function toggleFavorite() {
  if (!isLoggedIn.value) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  try {
    if (favorited.value) {
      await removeFavorite(product.value.id)
      favorited.value = false
      product.value.favoriteCount = Math.max(0, (product.value.favoriteCount || 1) - 1)
      toast('已取消想要')
    } else {
      await addFavorite(product.value.id)
      favorited.value = true
      product.value.favoriteCount = (product.value.favoriteCount || 0) + 1
      toast('已加入想要')
    }
  } catch (e) {
    toast(e.message, 'error')
  }
}

// ==================== 获取联系方式 ====================
async function openContact() {
  if (!isLoggedIn.value) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  // 后端已经算好"你能不能联系"，不能联系就把提示语直接显示出来
  if (!product.value.canContact) {
    toast(product.value.contactHint || '暂时无法联系卖家', 'error')
    return
  }

  dialogOpen.value = true
  dialogLoading.value = true
  dialogError.value = ''
  contact.value = null
  try {
    // ⚠️ 这一次请求会在后端记一条「咨询」行为，所以只在打开弹窗时调一次
    contact.value = await getContact(product.value.id)
  } catch (e) {
    dialogError.value = e.message
  } finally {
    dialogLoading.value = false
  }
}

// ==================== 卖家自己的操作 ====================
async function onMarkSold() {
  if (!confirm('确定把「' + product.value.title + '」标记为已售出吗？\n\n标记后不可恢复。')) return
  busy.value = true
  try {
    await markSold(product.value.id)
    toast('已标记售出')
    await load()
  } catch (e) {
    toast(e.message, 'error')
  } finally {
    busy.value = false
  }
}

async function onOffShelf() {
  if (!confirm('确定下架这件商品吗？下架后买家就搜不到了。')) return
  busy.value = true
  try {
    await offShelfProduct(product.value.id)
    toast('已下架')
    await load()
  } catch (e) {
    toast(e.message, 'error')
  } finally {
    busy.value = false
  }
}

async function onDelete() {
  if (!confirm('确定删除吗？相关的收藏和浏览记录会一起删掉。')) return
  busy.value = true
  try {
    await deleteProduct(product.value.id)
    toast('已删除')
    router.push('/profile?tab=published')
  } catch (e) {
    toast(e.message, 'error')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="container page">
    <!-- 加载中：左右两块的骨架 -->
    <div v-if="loading" class="detail card">
      <div class="sk-img"></div>
      <div class="sk-info">
        <div class="sk-line" style="width: 40%"></div>
        <div class="sk-line" style="width: 80%"></div>
        <div class="sk-line" style="width: 65%"></div>
        <div class="sk-line" style="width: 50%"></div>
      </div>
    </div>

    <p v-else-if="error" class="msg-error">{{ error }}</p>

    <EmptyState
      v-else-if="notFound || !product"
      title="商品不存在或已下架"
      desc="可能卖家删除了这件商品，或者它还在审核中"
      icon="📦"
      action-text="去逛逛别的"
      @action="router.push('/products')"
    />

    <template v-else>
      <!-- ============ 卖家条 ============ -->
      <section class="seller-bar card">
        <router-link :to="`/seller/${product.sellerId}`" class="seller-left">
          <img
            v-if="seller?.avatar"
            v-img-fallback
            :src="seller.avatar"
            class="avatar"
            alt=""
          />
          <span v-else class="avatar avatar-text">
            {{ initialOf(seller?.nickname || product.sellerNickname) }}
          </span>
          <div class="seller-info">
            <div class="seller-name">
              {{ seller?.nickname || product.sellerNickname }}
              <span v-if="seller && seller.onSaleCount !== undefined" class="tag tag-credit">
                在售 {{ seller.onSaleCount }}
              </span>
            </div>
            <div class="seller-meta">
              <span>已售 {{ seller?.soldCount ?? 0 }}</span>
              <span class="dot">·</span>
              <span>{{ product.favoriteCount || 0 }} 人想要</span>
              <span class="dot">·</span>
              <span>{{ product.viewCount || 0 }} 次浏览</span>
            </div>
          </div>
        </router-link>

        <router-link :to="`/seller/${product.sellerId}`" class="btn btn-ghost btn-sm">
          查看卖家主页
        </router-link>
      </section>

      <!-- ============ 主体：左图右信息 ============ -->
      <section class="detail">
        <!-- 左：缩略图列 + 主图 -->
        <div class="gallery card">
          <div v-if="images.length > 1" class="thumbs">
            <button
              v-for="(img, i) in images"
              :key="img"
              class="thumb"
              :class="{ on: i === activeImage }"
              type="button"
              @click="activeImage = i"
            >
              <img v-img-fallback :src="img" alt="" />
            </button>
          </div>

          <div class="main-img">
            <img
              v-if="images.length"
              v-img-fallback
              :src="images[activeImage] || images[0]"
              :alt="product.title"
              class="big"
            />
            <div v-else class="noimg">暂无图片</div>
          </div>
        </div>

        <!-- 右：价格 / 标题 / 参数 / 按钮 -->
        <div class="info card">
          <div class="info-head">
            <StatusTag :status="product.status" :text="product.statusText" />
            <span class="time">{{ timeAgo(product.createTime) }}发布</span>
          </div>

          <div class="price-block">
            <PriceTag :price="product.price" :original="product.originalPrice" large />
            <span class="want">{{ product.favoriteCount || 0 }} 人想要</span>
          </div>

          <h1 class="title">{{ product.title }}</h1>

          <div class="desc-wrap">
            <p ref="descRef" class="desc" :class="{ expanded: descExpanded }">
              {{ product.description || '卖家没有填写描述' }}
            </p>
            <button
              v-if="descOverflow || descExpanded"
              class="expand"
              type="button"
              @click="descExpanded = !descExpanded"
            >
              {{ descExpanded ? '收起' : '展开' }}
            </button>
          </div>

          <dl class="attrs">
            <div class="attr"><dt>品牌</dt><dd>{{ product.brand || '-' }}</dd></div>
            <div class="attr"><dt>分类</dt><dd>{{ product.categoryName || '-' }}</dd></div>
            <div class="attr"><dt>成色</dt><dd>{{ conditionText }}</dd></div>
            <div class="attr"><dt>交易地点</dt><dd>{{ product.tradePlace || '-' }}</dd></div>
          </dl>

          <!-- 提示条：待审核 / 已售出 / 自己发的 -->
          <p v-if="isOwner" class="banner banner-info">
            这是你发布的商品，买家看不到下面这些管理按钮。
          </p>
          <p v-else-if="product.status === 2" class="banner banner-grey">
            该商品已售出，不能再联系卖家。
          </p>
          <p v-else-if="product.status !== 1" class="banner banner-grey">
            该商品当前不可交易。
          </p>

          <!-- 操作区：身份决定按钮 -->
          <div class="actions">
            <template v-if="isOwner">
              <button v-if="isOnSale" class="btn btn-dark" :disabled="busy" @click="onMarkSold">
                标记售出
              </button>
              <button v-if="isOnSale" class="btn btn-ghost" :disabled="busy" @click="onOffShelf">
                下架
              </button>
              <button class="btn btn-ghost danger" :disabled="busy" @click="onDelete">删除</button>
            </template>

            <template v-else>
              <button class="btn btn-ghost fav" :class="{ on: favorited }" @click="toggleFavorite">
                {{ favorited ? '★ 已想要' : '☆ 想要' }}
              </button>
              <button
                class="btn btn-primary contact"
                :disabled="!product.canContact"
                @click="openContact"
              >
                获取联系方式
              </button>
            </template>
          </div>

          <!-- 不能联系时把原因说清楚（后端给的 contactHint） -->
          <p v-if="!isOwner && !product.canContact && product.contactHint" class="hint contact-hint">
            {{ product.contactHint }}
          </p>
        </div>
      </section>
    </template>

    <ContactDialog
      :open="dialogOpen"
      :contact="contact"
      :loading="dialogLoading"
      :error="dialogError"
      @close="dialogOpen = false"
    />
  </main>
</template>

<style scoped>
.page {
  padding-top: var(--gap-4);
  padding-bottom: var(--gap-6);
}

/* ---------- 卖家条 ---------- */
.seller-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gap-4);
  padding: var(--gap-3) var(--gap-4);
  margin-bottom: var(--gap-3);
}
.seller-left {
  display: flex;
  align-items: center;
  gap: var(--gap-3);
  min-width: 0;
}
.avatar {
  width: 44px;
  height: 44px;
  flex: none;
  border-radius: 50%;
  object-fit: cover;
  background: #fff5c2;
}
.avatar-text {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-md);
  font-weight: 600;
  color: #8a6100;
}
.seller-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-base);
  font-weight: 600;
}
.seller-meta {
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}
.dot {
  margin: 0 4px;
  opacity: 0.5;
}

/* ---------- 主体两栏 ---------- */
.detail {
  display: grid;
  grid-template-columns: minmax(0, 45fr) minmax(0, 55fr);
  gap: var(--gap-3);
  align-items: start;
}

/* 左：画廊 */
.gallery {
  display: flex;
  gap: var(--gap-2);
  padding: var(--gap-3);
}
.thumbs {
  display: flex;
  flex-direction: column;
  gap: var(--gap-2);
  width: 60px;
  flex: none;
  max-height: 480px;
  overflow-y: auto;
}
.thumb {
  width: 60px;
  height: 60px;
  padding: 0;
  overflow: hidden;
  background: #f5f5f5;
  border: 2px solid transparent;
  border-radius: var(--radius-md);
  transition: border-color var(--dur-fast);
}
.thumb.on {
  border-color: var(--color-text);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.main-img {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  background: #f7f7f7;
  border-radius: var(--radius-lg);
}
.big {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
}
.noimg {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1 / 1;
  color: var(--color-text-muted);
  font-size: var(--fs-sm);
}

/* 右：信息 */
.info {
  padding: var(--gap-4) var(--gap-5);
}
.info-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--gap-2);
}
.time {
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}
.price-block {
  display: flex;
  align-items: baseline;
  gap: var(--gap-3);
  padding-bottom: var(--gap-3);
}
.want {
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
}
.title {
  font-size: var(--fs-lg);
  font-weight: 600;
  line-height: 1.45;
}

.desc-wrap {
  position: relative;
  margin: var(--gap-3) 0;
  padding-top: var(--gap-3);
  border-top: 1px solid var(--color-border);
}
.desc {
  margin: 0;
  font-size: var(--fs-base);
  line-height: 1.75;
  color: var(--color-text-sub);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  line-clamp: 4;
  overflow: hidden;
}
.desc.expanded {
  display: block;
  -webkit-line-clamp: unset;
  line-clamp: unset;
}
.expand {
  margin-top: 4px;
  padding: 0;
  font-size: var(--fs-sm);
  color: var(--color-link);
  background: transparent;
  border: none;
}

.attrs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px var(--gap-4);
  margin: 0 0 var(--gap-4);
  padding: var(--gap-3);
  background: #fafbfc;
  border-radius: var(--radius-lg);
}
.attr {
  display: flex;
  gap: 6px;
  font-size: var(--fs-sm);
}
.attr dt {
  flex: none;
  width: 60px;
  color: var(--color-text-muted);
}
.attr dd {
  margin: 0;
  color: var(--color-text);
  overflow-wrap: anywhere;
}

.banner {
  margin: 0 0 var(--gap-3);
  padding: 8px 12px;
  font-size: var(--fs-sm);
  border-radius: var(--radius-md);
}
.banner-info {
  color: #8a6100;
  background: var(--color-tag-new-bg);
}
.banner-grey {
  color: var(--color-text-sub);
  background: #f5f5f5;
}

.actions {
  display: flex;
  gap: var(--gap-3);
}
.actions .btn {
  height: 44px;
  flex: 1;
  font-size: var(--fs-md);
}
.fav {
  flex: 0 0 140px !important;
}
.fav.on {
  color: var(--color-tag-credit-text);
  border-color: var(--color-tag-credit-text);
  background: var(--color-tag-credit-bg);
}
.danger {
  color: var(--color-tag-warn-text);
}
.contact-hint {
  margin: 8px 0 0;
}

/* ---------- 骨架 ---------- */
.detail.card {
  display: flex;
  gap: var(--gap-4);
  padding: var(--gap-4);
}
.sk-img {
  width: 45%;
  aspect-ratio: 1 / 1;
  border-radius: var(--radius-lg);
  background: #f2f3f5;
}
.sk-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sk-line {
  height: 14px;
  border-radius: 4px;
  background: #f2f3f5;
}

/* ---------- 响应式 ---------- */
@media (max-width: 991px) {
  .detail {
    grid-template-columns: minmax(0, 1fr);
  }
  .attrs {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
