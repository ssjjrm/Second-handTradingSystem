<script setup>
import { ref } from 'vue'
import { toast } from '@/composables/useToast'

/**
 * 「获取联系方式」弹窗。
 *
 * 为什么不跳页：跳到独立页面再回来，浏览节奏就断了；
 * 而且联系方式是"临门一脚"的信息，弹窗最轻。
 *
 * ⚠️ 后端这个接口**有副作用**：每调一次就记一条「咨询」行为。
 * 所以打开弹窗只请求一次，前端绝不能重复请求（否则咨询数会虚高）。
 */
const props = defineProps({
  open: { type: Boolean, default: false },
  contact: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})
const emit = defineEmits(['close'])

const copied = ref('')

async function copy(text, label) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(String(text))
    copied.value = label
    toast(`已复制${label}`)
    setTimeout(() => (copied.value = ''), 1600)
  } catch {
    toast('复制失败，请手动选中复制', 'error')
  }
}
</script>

<template>
  <transition name="dialog">
    <div v-if="open" class="mask" @click.self="emit('close')">
      <div class="dialog" role="dialog" aria-modal="true" aria-label="联系卖家">
        <header class="head">
          <h3>联系卖家</h3>
          <button class="close" type="button" aria-label="关闭" @click="emit('close')">×</button>
        </header>

        <p v-if="loading" class="hint loading">正在获取联系方式…</p>
        <p v-else-if="error" class="msg-error">{{ error }}</p>

        <template v-else-if="contact">
          <p class="product ellipsis">{{ contact.productTitle }}</p>

          <ul class="list">
            <li v-if="contact.wechat">
              <span class="k">微信号</span>
              <span class="v">{{ contact.wechat }}</span>
              <button class="btn btn-ghost btn-sm" type="button" @click="copy(contact.wechat, '微信号')">
                {{ copied === '微信号' ? '已复制' : '复制' }}
              </button>
            </li>
            <li v-if="contact.qq">
              <span class="k">QQ</span>
              <span class="v">{{ contact.qq }}</span>
              <button class="btn btn-ghost btn-sm" type="button" @click="copy(contact.qq, 'QQ')">
                {{ copied === 'QQ' ? '已复制' : '复制' }}
              </button>
            </li>
            <li v-if="contact.tradePlace">
              <span class="k">交易地点</span>
              <span class="v">{{ contact.tradePlace }}</span>
            </li>
          </ul>

          <p class="tip">
            ⚠️ 平台不参与交易、不收款。请<b>当面验货后再付款</b>，不要提前转账。
          </p>
        </template>

        <footer class="foot">
          <button class="btn btn-primary" type="button" @click="emit('close')">我知道了</button>
        </footer>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: var(--z-dialog);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: var(--color-mask);
}

.dialog {
  width: 100%;
  max-width: 380px;
  padding: var(--gap-5);
  background: var(--color-card);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-dialog);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--gap-2);
}
.head h3 {
  font-size: var(--fs-md);
  font-weight: 600;
}
.close {
  width: 26px;
  height: 26px;
  font-size: 18px;
  line-height: 1;
  color: var(--color-text-muted);
  background: transparent;
  border: none;
  border-radius: 50%;
}
.close:hover {
  color: var(--color-text);
  background: #f2f3f5;
}

.loading {
  padding: var(--gap-4) 0;
  text-align: center;
}

.product {
  margin: 0 0 var(--gap-3);
  font-size: var(--fs-sm);
  color: var(--color-text-sub);
}

.list {
  padding: var(--gap-3);
  background: #fafbfc;
  border-radius: var(--radius-lg);
}
.list li {
  display: flex;
  align-items: center;
  gap: var(--gap-2);
  padding: 6px 0;
  font-size: var(--fs-base);
}
.list li + li {
  border-top: 1px dashed var(--color-border);
}
.k {
  flex: none;
  width: 64px;
  color: var(--color-text-muted);
  font-size: var(--fs-sm);
}
.v {
  flex: 1;
  font-weight: 600;
  word-break: break-all;
}

.tip {
  margin: var(--gap-3) 0 0;
  padding: 8px 10px;
  font-size: var(--fs-xs);
  line-height: 1.6;
  color: var(--color-tag-credit-text);
  background: var(--color-tag-credit-bg);
  border-radius: var(--radius-md);
}

.foot {
  margin-top: var(--gap-4);
  text-align: right;
}

/* 进场：遮罩淡入 + 卡片从 0.96 放大到 1 */
.dialog-enter-active,
.dialog-leave-active {
  transition: opacity var(--dur-base) var(--ease);
}
.dialog-enter-active .dialog,
.dialog-leave-active .dialog {
  transition: transform var(--dur-base) var(--ease);
}
.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}
.dialog-enter-from .dialog,
.dialog-leave-to .dialog {
  transform: scale(0.96);
}
</style>
