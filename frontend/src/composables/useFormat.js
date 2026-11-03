/**
 * 格式化小工具（纯函数，不依赖 Vue）。
 */

/**
 * 价格：整数不带小数，有小数保留两位。
 *
 * 为什么不用 `toFixed(2)` 一刀切：
 * 列表里满屏 "¥1280.00" 很啰嗦，闲鱼也是整数就显示整数。
 */
export function formatPrice(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '-'
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

/** 带千分位的价格（详情页大价格用，1280 → 1,280） */
export function formatPriceGrouped(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '-'
  const s = Number.isInteger(n) ? String(n) : n.toFixed(2)
  return s.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** ISO 时间 → "3 天前" */
export function timeAgo(iso) {
  if (!iso) return ''
  const t = new Date(String(iso).replace(' ', 'T')).getTime()
  if (Number.isNaN(t)) return ''
  const diff = Date.now() - t
  if (diff < 60_000) return '刚刚'
  const min = Math.floor(diff / 60_000)
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 30) return `${day} 天前`
  const month = Math.floor(day / 30)
  if (month < 12) return `${month} 个月前`
  return `${Math.floor(month / 12)} 年前`
}

/** ISO 时间 → "2025-09-01" */
export function formatDate(iso) {
  if (!iso) return ''
  return String(iso).replace('T', ' ').slice(0, 10)
}

/** 昵称首字（没有头像时当占位） */
export function initialOf(name) {
  return (name || '?').trim().slice(0, 1)
}
