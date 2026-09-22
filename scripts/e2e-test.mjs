/**
 * 端到端测试：用 Edge 的 CDP 驱动真实浏览器，走一遍"用户真实操作"的流程。
 *
 * 为什么不用 playwright？—— 本机没装，而 Edge 自带调试协议（CDP），
 * Node 22 又有内置 WebSocket，几十行就能连上，不用额外下载浏览器内核。
 *
 * 用法（必须先启动前端 5174 和后端 8081）：
 *     node scripts/e2e-test.mjs
 *
 * S11 阶段会把它扩展成完整的回归脚本（覆盖全部 35 个接口）。
 */

import { spawn } from 'node:child_process'
import { existsSync, mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const EDGE_CANDIDATES = [
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe'
]

const DEBUG_PORT = 9222
const FRONT = 'http://localhost:5174'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

let passCount = 0
let failCount = 0

function check(name, ok, detail = '') {
  if (ok) {
    passCount++
    console.log(`  \u2713 ${name}${detail ? '  ' + detail : ''}`)
  } else {
    failCount++
    console.log(`  \u2717 ${name}${detail ? '  ' + detail : ''}`)
  }
}

/** 极简 CDP 客户端 */
class Cdp {
  constructor(ws) {
    this.ws = ws
    this.seq = 0
    this.pending = new Map()
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
      }
    })
  }

  send(method, params = {}) {
    const id = ++this.seq
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id)
          reject(new Error(`CDP 超时: ${method}`))
        }
      }, 15000)
    })
  }

  /** 在页面里执行一段 JS，返回其值 */
  async eval(expression) {
    const r = await this.send('Runtime.evaluate', {
      expression,
      returnByValue: true,
      awaitPromise: true
    })
    if (r.exceptionDetails) {
      throw new Error('页面 JS 报错: ' + (r.exceptionDetails.exception?.description || ''))
    }
    return r.result.value
  }
}

async function waitForDebugger() {
  for (let i = 0; i < 40; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/list`)
      const list = await res.json()
      const page = list.find((t) => t.type === 'page')
      if (page?.webSocketDebuggerUrl) return page.webSocketDebuggerUrl
    } catch {
      /* 还没起来，继续等 */
    }
    await sleep(500)
  }
  throw new Error('Edge 调试端口没起来')
}

async function main() {
  const realEdge = EDGE_CANDIDATES.find((p) => existsSync(p))
  if (!realEdge) throw new Error('找不到 Edge / Chrome')
  const userDataDir = mkdtempSync(join(tmpdir(), 'sh-e2e-'))

  console.log(`浏览器: ${realEdge}`)
  const browser = spawn(
    realEdge,
    [
      '--headless=new',
      `--remote-debugging-port=${DEBUG_PORT}`,
      `--user-data-dir=${userDataDir}`,
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-gpu',
      '--window-size=1280,900',
      'about:blank'
    ],
    { stdio: 'ignore' }
  )

  try {
    const wsUrl = await waitForDebugger()
    const ws = new WebSocket(wsUrl)
    await new Promise((resolve, reject) => {
      ws.addEventListener('open', resolve)
      ws.addEventListener('error', reject)
    })
    const cdp = new Cdp(ws)
    await cdp.send('Page.enable')
    await cdp.send('Runtime.enable')

    // ================= 1. 打开登录页 =================
    console.log('\n[1] 打开登录页 ' + FRONT + '/login')
    await cdp.send('Page.navigate', { url: FRONT + '/login' })
    await sleep(2500)

    const title = await cdp.eval('document.title')
    check('页面标题正确', title.includes('登录'), `title="${title}"`)

    const cardTitle = await cdp.eval('document.querySelector(".form-card h2")?.textContent || ""')
    check('登录卡片已渲染', cardTitle.includes('登录'), `"${cardTitle}"`)

    const inputCount = await cdp.eval('document.querySelectorAll(".form-card input").length')
    check('有用户名和密码两个输入框', inputCount === 2, `实际 ${inputCount} 个`)

    // ================= 2. 错误密码 =================
    console.log('\n[2] 用错误密码登录（应显示错误提示，且不写入 token）')
    await cdp.eval(`(() => {
      const ins = document.querySelectorAll('.form-card input')
      const set = (el, v) => { el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })) }
      set(ins[0], 'admin'); set(ins[1], 'definitely-wrong')
      document.querySelector('.form-card button[type=submit]').click()
      return true
    })()`)
    await sleep(1500)

    const errText = await cdp.eval('document.querySelector(".form-card .msg-error")?.textContent || ""')
    check('显示了错误提示', errText.includes('用户名或密码错误'), `"${errText.trim()}"`)

    const tokenAfterFail = await cdp.eval("localStorage.getItem('token')")
    check('失败时没有写入 token', !tokenAfterFail)

    // ================= 3. 正确密码 =================
    console.log('\n[3] 用 admin / admin123 登录')
    await cdp.eval(`(() => {
      const ins = document.querySelectorAll('.form-card input')
      const set = (el, v) => { el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })) }
      set(ins[0], 'admin'); set(ins[1], 'admin123')
      document.querySelector('.form-card button[type=submit]').click()
      return true
    })()`)
    await sleep(2000)

    const token = await cdp.eval("localStorage.getItem('token') || ''")
    check('localStorage 里有 token', token.length > 100, `长度 ${token.length}`)

    const userRaw = await cdp.eval("localStorage.getItem('user') || ''")
    let user = null
    try {
      user = JSON.parse(userRaw)
    } catch {
      /* ignore */
    }
    check('localStorage 里有 user', !!user?.username, `username=${user?.username} role=${user?.role}`)

    const path = await cdp.eval('location.pathname')
    check('登录后跳转离开 /login', path !== '/login', `当前路径 ${path}`)

    const topbar = await cdp.eval('document.querySelector(".topbar")?.textContent || ""')
    check('顶栏显示昵称', topbar.includes('平台管理员'), `"${topbar.replace(/\\s+/g, ' ').trim()}"`)

    // ================= 4. 刷新后登录态不丢 =================
    console.log('\n[4] 刷新页面，检查登录态是否保留')
    await cdp.send('Page.reload')
    await sleep(2200)

    const tokenAfterReload = await cdp.eval("localStorage.getItem('token') || ''")
    check('刷新后 token 还在', tokenAfterReload === token)

    const topbar2 = await cdp.eval('document.querySelector(".topbar")?.textContent || ""')
    check('刷新后顶栏仍显示已登录', topbar2.includes('平台管理员'))

    // ================= 5. 验证页共享登录态 =================
    console.log('\n[5] 打开 S3 验证页，检查它能否读到同一份 token')
    await cdp.send('Page.navigate', { url: FRONT + '/verify/s3-auth.html' })
    await sleep(1500)

    const statText = await cdp.eval('document.getElementById("stat")?.textContent || ""')
    check('验证页显示已登录（同源共享 localStorage）', statText.includes('已登录'), `"${statText}"`)

    // 用验证页真实请求一次 /api/auth/me
    const meResult = await cdp.eval(`(async () => {
      const res = await fetch('/api/auth/me', { headers: { Authorization: 'Bearer ' + localStorage.getItem('token') } })
      const json = await res.json()
      return json.code + '|' + (json.data?.username || '') + '|' + (json.data?.stats ? 'hasStats' : 'noStats')
    })()`)
    check('验证页能带 token 拿到 /api/auth/me', meResult.startsWith('200|admin|'), meResult)

    // ================= 6. 退出登录 =================
    console.log('\n[6] 退出登录')
    await cdp.send('Page.navigate', { url: FRONT + '/' })
    await sleep(1800)
    await cdp.eval(`(() => {
      const link = [...document.querySelectorAll('.topbar a')].find(a => a.textContent.includes('退出'))
      link?.click()
      return !!link
    })()`)
    await sleep(1200)

    const tokenAfterLogout = await cdp.eval("localStorage.getItem('token')")
    check('退出后 token 已清除', !tokenAfterLogout)

    const pathAfterLogout = await cdp.eval('location.pathname')
    check('退出后回到登录页', pathAfterLogout === '/login', `当前路径 ${pathAfterLogout}`)

    ws.close()
  } finally {
    browser.kill()
    await sleep(600)
    try {
      rmSync(userDataDir, { recursive: true, force: true })
    } catch {
      /* ignore */
    }
  }

  console.log(`\n================ 结果 ================`)
  console.log(`通过 ${passCount} 项，失败 ${failCount} 项`)
  process.exit(failCount === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('\n测试脚本出错：', e.message)
  process.exit(1)
})
