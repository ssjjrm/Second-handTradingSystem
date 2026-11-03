import { computed, ref } from 'vue'

/**
 * 登录态（一个极简的"全局状态"）。
 *
 * 为什么不引 Pinia：全项目只有 token + user 两个值，
 * 十几行就能搞定。等真有一堆跨页面的共享状态再上 Pinia 也不迟。
 *
 * ⚠️ localStorage 不是响应式的 —— 别的页面写入后，这里的 ref 不会自己变。
 * 所以 `App.vue` 在**每次路由变化时**调一次 `sync()`，
 * 把最新的 localStorage 拉进 ref，顶栏就会跟着更新。
 */

const token = ref(localStorage.getItem('token') || '')
const user = ref(safeParse(localStorage.getItem('user')))

function safeParse(raw) {
  try {
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/** 从 localStorage 重新读一次（登录、退出、改资料后调用） */
function sync() {
  token.value = localStorage.getItem('token') || ''
  user.value = safeParse(localStorage.getItem('user'))
}

/** 登录成功后写入（登录页调用） */
function setAuth(data) {
  localStorage.setItem('token', data.token)
  localStorage.setItem('user', JSON.stringify(data.user))
  sync()
}

/** 只更新本地 user 缓存（改昵称头像后调用，不重新登录） */
function patchUser(partial) {
  const merged = { ...(user.value || {}), ...partial }
  localStorage.setItem('user', JSON.stringify(merged))
  user.value = merged
}

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  sync()
}

const isLoggedIn = computed(() => !!token.value)
const isAdmin = computed(() => user.value?.role === 'admin')
const displayName = computed(() => user.value?.nickname || user.value?.username || '')

export function useUser() {
  return {
    token,
    user,
    isLoggedIn,
    isAdmin,
    displayName,
    sync,
    setAuth,
    patchUser,
    logout
  }
}
