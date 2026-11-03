<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getSeller, getSellerProducts } from '@/api/seller'

/**
 * 卖家主页 —— 让买家在"联系卖家"之前先评估一下这个人。
 *
 * <p>平台没有订单和评价体系，所以能建立的信任信号只有这几个数字：
 * 注册多久了、在售几件、卖掉过几件、收到多少"想要"。
 *
 * <p>⚠️ 这一页**故意不显示微信/QQ**：联系方式只有一条获取路径
 * （在具体商品上点「联系卖家」），否则点进主页就能批量拿走卖家的联系方式。
 */
const route = useRoute()

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
    const data = await getSellerProducts(id, { size: 40 })
    products.value = data.records
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)
// 同一个组件在不同卖家之间切换时（/seller/2 → /seller/3），要重新加载
watch(() => route.params.id, load)
</script>

<template>
  <main class="content">
    <p v-if="error" class="msg-error">{{ error }}</p>
    <p v-if="loading" class="hint">加载中…</p>

    <template v-if="seller">
      <header class="profile">
        <img v-if="seller.avatar" :src="seller.avatar" class="avatar" alt="">
        <div v-else class="avatar placeholder">{{ (seller.nickname || '?').slice(0, 1) }}</div>

        <div class="info">
          <h1>{{ seller.nickname }}</h1>
          <p class="hint">加入于 {{ (seller.createTime || '').slice(0, 10) }}</p>
          <p v-if="seller.hasContact" class="hint ok">✔ 已填写联系方式（在商品页点「联系卖家」即可获取）</p>
          <p v-else class="hint warn">✘ 这位卖家还没填联系方式，暂时无法联系</p>
        </div>

        <div class="stats">
          <div><b>{{ seller.stats.onSaleCount }}</b><span>在售</span></div>
          <div><b>{{ seller.stats.soldCount }}</b><span>已卖出</span></div>
          <div><b>{{ seller.stats.favoriteReceivedCount }}</b><span>收到的想要</span></div>
        </div>
      </header>

      <h2>TA 在卖的东西（{{ products.length }}）</h2>
      <p v-if="!products.length" class="hint">暂时没有在售的商品</p>

      <div class="grid">
        <router-link
          v-for="p in products"
          :key="p.id"
          :to="`/product/${p.id}`"
          class="card"
        >
          <img v-if="p.images && p.images.length" :src="p.images[0]" alt="">
          <div v-else class="noimg">无图</div>
          <div class="card-body">
            <div class="card-title">{{ p.title }}</div>
            <div class="card-price">¥{{ p.price }}</div>
            <div class="card-meta">{{ p.favoriteCount }} 人想要 · {{ p.tradePlace }}</div>
          </div>
        </router-link>
      </div>
    </template>
  </main>
</template>

<style scoped>
.content { padding: 24px; max-width: 1100px; }
.profile { display: flex; align-items: center; gap: 20px; padding: 20px; background: #fff; border: 1px solid #eee; border-radius: 8px; }
.avatar { width: 72px; height: 72px; border-radius: 50%; object-fit: cover; }
.avatar.placeholder { display: flex; align-items: center; justify-content: center; background: #ffe9a8; font-size: 28px; color: #8a6d00; }
.info h1 { margin: 0 0 6px; font-size: 20px; }
.stats { margin-left: auto; display: flex; gap: 24px; text-align: center; }
.stats b { display: block; font-size: 20px; color: #ff5000; }
.stats span { font-size: 12px; color: #888; }

.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 14px; }
.card { background: #fff; border: 1px solid #eee; border-radius: 8px; overflow: hidden; color: inherit; }
.card img, .card .noimg { width: 100%; height: 150px; object-fit: cover; background: #f5f5f5; display: flex; align-items: center; justify-content: center; color: #bbb; }
.card-body { padding: 8px 10px; }
.card-title { font-size: 13px; height: 36px; overflow: hidden; }
.card-price { color: #ff5000; font-weight: bold; margin: 4px 0; }
.card-meta { font-size: 12px; color: #999; }

.ok { color: #2e7d32; }
.warn { color: #c62828; }
.hint { color: #888; font-size: 13px; }
.msg-error { color: #c62828; }
</style>
