/**
 * `v-img-fallback` 指令：图片加载失败时换成占位图。
 *
 * 为什么必须有：种子数据里的图片是 `picsum.photos` 外链，
 * **断网 / 该站被墙时整页都是破图图标**，演示时非常难看。
 * 加上这层兜底，最差也是灰色的"暂无图片"，版面不会崩。
 *
 * 用法：
 *   `<img :src="url" v-img-fallback>`
 *   `<img :src="url" v-img-fallback="'/img/other.svg'">`  ← 自定义占位图
 */
const DEFAULT_PLACEHOLDER = '/img/placeholder.svg'

export const imgFallback = {
  mounted(el, binding) {
    const fallback = binding.value || DEFAULT_PLACEHOLDER

    el.__onImgError = () => {
      // 防止占位图本身也 404 导致无限循环
      if (el.dataset.fallbackApplied) return
      el.dataset.fallbackApplied = '1'
      el.src = fallback
    }

    el.addEventListener('error', el.__onImgError)
  },

  unmounted(el) {
    el.removeEventListener('error', el.__onImgError)
    delete el.__onImgError
  }
}
