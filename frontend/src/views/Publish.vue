<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMe } from '@/api/auth'
import { publishProduct } from '@/api/product'
import ImageUploader from '@/components/publish/ImageUploader.vue'
import CategorySelect from '@/components/publish/CategorySelect.vue'
import { toast } from '@/composables/useToast'

/**
 * 发布商品（P04）。
 *
 * 和闲鱼最大的区别：闲鱼只有"图片 + 描述 + 价格"，
 * 因为它的分类和成色是 AI 从图片里识别出来的。
 * 我们不做识别，所以**补了分类下拉和成色单选** —— 这是功能刚需，不是设计退化。
 *
 * 另一个细节：**没填联系方式的人不让填整张表**。
 * 后端在发布时会拦（BR-C05），但让他辛苦填完一屏再报错，体验很差，
 * 所以进页面就先检查一次，顶部直接给出「去设置」。
 */
const router = useRouter()

const form = ref({
  title: '',
  description: '',
  categoryId: '',
  conditionLevel: '',
  price: '',
  originalPrice: '',
  brand: '',
  tradePlace: '',
  images: []
})
const parentId = ref('')

const loadingMe = ref(true)
const hasContact = ref(true)
const submitting = ref(false)
const error = ref('')

const CONDITIONS = ['全新', '几乎全新', '轻微使用痕迹', '明显使用痕迹']

const titleCount = computed(() => form.value.title.length)
const descCount = computed(() => form.value.description.length)

onMounted(async () => {
  try {
    const me = await getMe()
    hasContact.value = !!me.hasContact
  } catch {
    /* 拿不到就当有联系方式，交给后端兜底 */
  } finally {
    loadingMe.value = false
  }
})

/** 前端先校验一遍：只为体验，后端仍会再校验一次（前端校验永远不算数） */
function validate() {
  if (!form.value.title.trim() || form.value.title.trim().length < 5) {
    return '标题至少 5 个字'
  }
  if (form.value.title.length > 100) return '标题最多 100 个字'
  if (form.value.description.length > 1500) return '描述最多 1500 个字'
  if (!form.value.categoryId) return '请选择二级分类'
  if (!form.value.conditionLevel) return '请选择成色'
  if (!form.value.tradePlace.trim()) return '请填写交易地点'
  const price = Number(form.value.price)
  if (!Number.isFinite(price) || price <= 0) return '价格必须大于 0'
  if (form.value.originalPrice !== '') {
    const o = Number(form.value.originalPrice)
    if (!Number.isFinite(o) || o <= 0) return '原价必须大于 0'
    if (o < price) return '原价不能低于现价'
  }
  return ''
}

async function onSubmit() {
  error.value = ''
  const msg = validate()
  if (msg) {
    error.value = msg
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }

  submitting.value = true
  try {
    const payload = {
      title: form.value.title.trim(),
      description: form.value.description.trim() || null,
      categoryId: Number(form.value.categoryId),
      conditionLevel: form.value.conditionLevel,
      price: Number(form.value.price),
      originalPrice: form.value.originalPrice === '' ? null : Number(form.value.originalPrice),
      brand: form.value.brand.trim() || null,
      tradePlace: form.value.tradePlace.trim(),
      images: form.value.images
    }
    await publishProduct(payload)
    toast('发布成功，去「我发布的」看看')
    router.push('/profile?tab=published')
  } catch (e) {
    error.value = e.message
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="container page">
    <div class="sheet card">
      <h1 class="sheet-title">发布闲置</h1>

      <!-- 没填联系方式：先拦住，别让他白填 -->
      <div v-if="!loadingMe && !hasContact" class="banner banner-warn">
        <span>发布前请先填写微信号或 QQ —— 买家拿不到联系方式，你这件东西就没法成交。</span>
        <router-link to="/profile?tab=contact" class="btn btn-sm btn-dark">去设置</router-link>
      </div>

      <p v-if="error" class="msg-error error-box">{{ error }}</p>

      <form @submit.prevent="onSubmit">
        <!-- ==================== 基础信息 ==================== -->
        <section class="block">
          <h2 class="block-title">基础信息</h2>

          <div class="row">
            <label class="label"><i>*</i>宝贝图片</label>
            <div class="control">
              <ImageUploader v-model="form.images" :max="9" />
            </div>
          </div>

          <div class="row">
            <label class="label" for="title"><i>*</i>标题</label>
            <div class="control">
              <input
                id="title"
                v-model="form.title"
                class="input"
                maxlength="100"
                placeholder="例如：【九成新】索尼 WH-1000XM5 头戴降噪耳机 宿舍自提"
              />
              <span class="counter">{{ titleCount }}/100</span>
            </div>
          </div>

          <div class="row">
            <label class="label" for="desc">描述</label>
            <div class="control">
              <textarea
                id="desc"
                v-model="form.description"
                class="textarea"
                maxlength="1500"
                rows="5"
                placeholder="描述一下成色、入手时间、为什么卖、有没有配件…写得越清楚越有人问"
              ></textarea>
              <span class="counter">{{ descCount }}/1500</span>
            </div>
          </div>

          <div class="row">
            <label class="label"><i>*</i>分类</label>
            <div class="control">
              <CategorySelect
                v-model:parent-id="parentId"
                v-model:category-id="form.categoryId"
              />
              <span class="hint">商品只能挂在二级分类上，买家才筛得出来</span>
            </div>
          </div>

          <div class="row">
            <label class="label"><i>*</i>成色</label>
            <div class="control">
              <div class="pills">
                <button
                  v-for="c in CONDITIONS"
                  :key="c"
                  class="pill"
                  :class="{ on: form.conditionLevel === c }"
                  type="button"
                  @click="form.conditionLevel = c"
                >
                  {{ c }}
                </button>
              </div>
            </div>
          </div>

          <div class="row">
            <label class="label" for="brand">品牌</label>
            <div class="control">
              <input
                id="brand"
                v-model="form.brand"
                class="input input-half"
                maxlength="50"
                placeholder="选填，例如 索尼 / 无印良品"
              />
            </div>
          </div>

          <div class="row">
            <label class="label" for="place"><i>*</i>交易地点</label>
            <div class="control">
              <input
                id="place"
                v-model="form.tradePlace"
                class="input input-half"
                maxlength="100"
                placeholder="例如：东区食堂门口 / 3 号宿舍楼下"
              />
            </div>
          </div>
        </section>

        <!-- ==================== 价格 ==================== -->
        <section class="block">
          <h2 class="block-title">价格</h2>

          <div class="row">
            <label class="label" for="price"><i>*</i>价格</label>
            <div class="control price-control">
              <span class="yen">¥</span>
              <input
                id="price"
                v-model="form.price"
                class="input input-price"
                type="number"
                min="0.01"
                step="0.01"
                placeholder="0.00"
              />
            </div>
          </div>

          <div class="row">
            <label class="label" for="original">原价</label>
            <div class="control price-control">
              <span class="yen">¥</span>
              <input
                id="original"
                v-model="form.originalPrice"
                class="input input-price"
                type="number"
                min="0"
                step="0.01"
                placeholder="选填"
              />
              <span class="hint">原价不能低于现价</span>
            </div>
          </div>
        </section>

        <div class="submit-row">
          <button class="btn btn-primary publish-btn" type="submit" :disabled="submitting">
            {{ submitting ? '发布中…' : '发 布' }}
          </button>
        </div>

        <p class="hint note">
          提交后商品会进入<b>待审核</b>，管理员通过后才会出现在列表里。
          （本地测试时可以用后台的「审核开关」跳过这一步。）
        </p>
      </form>
    </div>
  </main>
</template>

<style scoped>
.page {
  padding-top: var(--gap-4);
  padding-bottom: var(--gap-6);
}

.sheet {
  max-width: 860px;
  margin: 0 auto;
  padding: var(--gap-6) var(--gap-8);
}
.sheet-title {
  margin-bottom: var(--gap-5);
  font-size: var(--fs-xl);
  font-weight: 600;
}

.banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gap-3);
  margin-bottom: var(--gap-4);
  padding: 10px 14px;
  font-size: var(--fs-sm);
  border-radius: var(--radius-lg);
}
.banner-warn {
  color: var(--color-tag-credit-text);
  background: var(--color-tag-credit-bg);
}

.error-box {
  margin-bottom: var(--gap-4);
  padding: 8px 12px;
  background: var(--color-tag-warn-bg);
  border-radius: var(--radius-md);
}

.block {
  padding-bottom: var(--gap-4);
  margin-bottom: var(--gap-4);
  border-bottom: 1px solid var(--color-border);
}
.block-title {
  margin-bottom: var(--gap-3);
  font-size: var(--fs-md);
  font-weight: 600;
}

.row {
  display: flex;
  align-items: flex-start;
  gap: var(--gap-3);
  margin-bottom: var(--gap-4);
}
.label {
  flex: none;
  width: 84px;
  padding-top: 11px;
  font-size: var(--fs-sm);
  color: var(--color-text-sub);
  text-align: right;
}
.label i {
  margin-right: 2px;
  font-style: normal;
  color: var(--color-tag-warn-text);
}
.control {
  flex: 1;
  min-width: 0;
  position: relative;
}

.input,
.textarea {
  width: 100%;
  padding: 9px 12px;
  font-size: var(--fs-base);
  background: #fff;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  outline: none;
  transition: border-color var(--dur-fast), box-shadow var(--dur-fast);
}
.input {
  height: 40px;
}
.textarea {
  resize: vertical;
  line-height: 1.7;
}
.input:focus,
.textarea:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(255, 209, 0, 0.18);
}
.input-half {
  max-width: 260px;
}

.counter {
  position: absolute;
  right: 10px;
  bottom: 8px;
  font-size: 11px;
  color: var(--color-text-muted);
  pointer-events: none;
}

.pills {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gap-2);
  padding-top: 4px;
}
.pill {
  padding: 7px 16px;
  font-size: var(--fs-sm);
  color: var(--color-text-sub);
  background: #fff;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  transition: all var(--dur-fast);
}
.pill:hover {
  border-color: var(--color-primary);
}
.pill.on {
  font-weight: 600;
  color: #1a1a1a;
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.price-control {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-top: 2px;
}
.yen {
  font-size: var(--fs-md);
  color: var(--color-text-sub);
}
.input-price {
  width: 160px;
}

.submit-row {
  display: flex;
  justify-content: center;
  margin-top: var(--gap-5);
}
.publish-btn {
  width: 240px;
  height: 46px;
  font-size: var(--fs-md);
  font-weight: 600;
}

.note {
  margin-top: var(--gap-3);
  text-align: center;
}

@media (max-width: 767px) {
  .sheet {
    padding: var(--gap-4);
  }
  .row {
    flex-direction: column;
    gap: 6px;
  }
  .label {
    width: auto;
    padding-top: 0;
    text-align: left;
  }
}
</style>
