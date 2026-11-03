<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSeller, getSellerProducts } from '@/api/seller'
import ProductGrid from '@/components/product/ProductGrid.vue'
import { formatDate, initialOf } from '@/composables/useFormat'

/**
 * 卖家主页（P05）—— 买家在「联系卖家」前的评估页。
 *
 * 平台没有订单和评价体系，能建立的信任信号只有三个数字：
 * 在售几件、卖掉过几件、被多少人想要过。
 *
 * ⚠️ 这一页**故意不显示微信/QQ**：联系方式只有一条路径（在具体商品上点获取），
 * 否则点开主页就能批量拿走卖家的联系方式。后端本来也不返回。
 */
const route = useRoute()
const router = useRouter()

const seller = ref(null)
const products = ref([])
const loading = ref(false)
const error = ref('')

async function load() {
  const id = route.params.id
  loading.value = true
  error.value = ''
  try {
    seller.value = await getSeller(id)
    const data = await getSellerProducts(id, { size: 60 })
    products.value = data.records || []
  } catch (e) {
    error.value = e.message
    seller.value = null
    products.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
// /seller/2 → /seller/3 在同一个组件内切换，要重新加载
watch(() => route.params.id, load)
</script>

<template>
  <main class="container page">
    <p v-if="error" class="msg-error">{{ error }}</p>

    <template v-if="seller">
      <!-- 卖家卡 -->
      <section class="seller-card">
        <img v-if="seller.avatar" v-img-fallback :src="seller.avatar" class="avatar" alt="" />
        <span v-else class="avatar avatar-text">{{ initialOf(seller.nickname) }}</span>

        <div class="info">
          <h1 class="name">
            {{ seller.nickname }}
            <span v-if="seller.hasContact" class="tag tag-green">已填写联系方式</span>
            <span v-else class="tag">未填写联系方式</span>
          </h1>
          <div class="meta">
            <span>加入于 {{ formatDate(seller.createTime) }}</span>
            <span class="dot">·</span>
            <span>在售 {{ seller.stats.onSaleCount }} 件</span>
            <span class="dot">·</span>
            <span>已售出 {{ seller.stats.soldCount }} 件</span>
          </div>
          <p class="trust">
            累计被 <b>{{ seller.stats.favoriteReceivedCount }}</b> 人次想要
            <span class="hint">（平台不参与交易，这是最接近"受欢迎程度"的指标）</span>
          </p>
        </div>
      </section>

      <!-- 在售商品 -->
      <div class="list-head">
        <h2>TA 在卖的东西</h2>
        <span class="hint">共 {{ products.length }} 件</span>
      </div>

      <ProductGrid
        :products="products"
        :loading="loading"
        :columns="5"
        empty-title="TA 暂时没有在售商品"
        empty-desc="可以去首页看看别人在卖什么"
      />

      <div v-if="!products.length && !loading" class="back">
        <button class="btn btn-primary" type="button" @click="router.push('/products')">
          去逛逛
        </button>
      </div>
    </template>

    <p v-else-if="loading" class="hint">加载中…</p>
  </main>
</template>

<style scoped>
.page {
  padding-top: var(--gap-4);
  padding-bottom: var(--gap-6);
}

.seller-card {
  display: flex;
  align-items: center;
  gap: var(--gap-4);
  padding: var(--gap-5);
  margin-bottom: var(--gap-4);
  background: linear-gradient(135deg, #fff9db 0%, #ffffff 62%);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-card);
}
.avatar {
  width: 72px;
  height: 72px;
  flex: none;
  border-radius: 50%;
  object-fit: cover;
  background: #ffe9a8;
}
.avatar-text {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 600;
  color: #8a6100;
}
.info {
  min-width: 0;
}
.name {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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
.trust {
  margin: 6px 0 0;
  font-size: var(--fs-sm);
  color: var(--color-text-sub);
}
.trust b {
  font-size: var(--fs-md);
  color: var(--color-price);
}

.list-head {
  display: flex;
  align-items: baseline;
  gap: var(--gap-3);
  margin-bottom: var(--gap-3);
}
.list-head h2 {
  font-size: var(--fs-md);
  font-weight: 600;
}

.back {
  display: flex;
  justify-content: center;
  margin-top: var(--gap-4);
}
</style>
