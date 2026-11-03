/**
 * 端到端测试：用 Edge 的 CDP 驱动真实浏览器，走一遍"用户真实操作"的流程。
 *
 * 为什么不用 playwright？—— 本机没装，而 Edge 自带调试协议（CDP），
 * Node 22 又有内置 WebSocket，几十行就能连上，不用额外下载浏览器内核。
 *
 * 用法（必须先启动前端 5174 和后端 8081）：
 *     node scripts\e2e-test.mjs
 *
 * S10 起，这里测试的是**正式页面**（`frontend/src/views/`）；
 * 开发期的 `public/verify/*.html` 已经删除。
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
      }, 20000)
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
      '--hide-scrollbars',
      '--window-size=1440,900',
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
    await cdp.send('Emulation.setDeviceMetricsOverride', {
      width: 1440,
      height: 900,
      deviceScaleFactor: 1,
      mobile: false
    })

    /** 在页面里登录并写入 localStorage（不刷新页面） */
    const loginInPage = async (username, password) =>
      cdp.eval(`(async () => {
        const r = await fetch('/api/auth/login', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: '${username}', password: '${password}' })
        })
        const j = await r.json()
        if (j.code !== 200) return 'FAIL ' + j.code + ' ' + j.message
        localStorage.setItem('token', j.data.token)
        localStorage.setItem('user', JSON.stringify(j.data.user))
        return 'OK ' + j.data.user.username + ' ' + j.data.user.role
      })()`)

    /** 打开某个页面并等待渲染 */
    const goto = async (path, ms = 2200) => {
      await cdp.send('Page.navigate', { url: FRONT + path })
      await sleep(ms)
    }

    // ==================================================================
    // 1. 登录页
    // ==================================================================
    console.log('\n[1] 登录页（黄顶栏 + 白卡片）')
    await goto('/login')

    const title = await cdp.eval('document.title')
    check('页面标题正确', title.includes('登录'), `title="${title}"`)

    const cardTitle = await cdp.eval('document.querySelector(".form-card h2")?.textContent || ""')
    check('登录卡片已渲染', cardTitle.includes('登录'), `"${cardTitle}"`)

    const inputCount = await cdp.eval('document.querySelectorAll(".form-card input").length')
    check('有用户名和密码两个输入框', inputCount === 2, `实际 ${inputCount} 个`)

    // ==================================================================
    // 2. 错误密码
    // ==================================================================
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
    check('失败时没有写入 token', !(await cdp.eval("localStorage.getItem('token')")))

    // ==================================================================
    // 3. 正确密码
    // ==================================================================
    console.log('\n[3] 用 admin / admin123 登录')
    await cdp.eval(`(() => {
      const ins = document.querySelectorAll('.form-card input')
      const set = (el, v) => { el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })) }
      set(ins[0], 'admin'); set(ins[1], 'admin123')
      document.querySelector('.form-card button[type=submit]').click()
      return true
    })()`)
    await sleep(2400)

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
    check('登录后跳转离开 /login', (await cdp.eval('location.pathname')) !== '/login')

    const topbarText = await cdp.eval('document.querySelector(".topbar")?.textContent || ""')
    check('顶栏显示昵称', topbarText.includes('平台管理员'),
      topbarText.replace(/\s+/g, ' ').trim().slice(0, 40))

    // ==================================================================
    // 4. 全局骨架的视觉规范（S10 的重点）
    // ==================================================================
    console.log('\n[4] 视觉规范：顶栏 / 容器 / 卡片 / 悬浮条 / 页脚')

    await cdp.send('Page.reload')
    await sleep(2500)
    check('刷新后 token 还在', (await cdp.eval("localStorage.getItem('token') || ''")) === token)

    const barBg = await cdp.eval('getComputedStyle(document.querySelector(".topbar")).backgroundColor')
    check('顶栏是品牌黄 #FFD100', barBg === 'rgb(255, 209, 0)', barBg)

    const barH = await cdp.eval('document.querySelector(".topbar").getBoundingClientRect().height')
    check('顶栏高度 84px（主行 58 + 热搜 26）', Math.abs(barH - 84) <= 3, `${barH}px`)

    const containerMax = await cdp.eval('getComputedStyle(document.querySelector(".container")).maxWidth')
    check('内容容器最大宽度 1200px', containerMax === '1200px', containerMax)

    const bodyBg = await cdp.eval('getComputedStyle(document.body).backgroundColor')
    check('页面底色 #F7F8FA', bodyBg === 'rgb(247, 248, 250)', bodyBg)

    check('有公告条', await cdp.eval('!!document.querySelector(".notice")'))
    check('右侧悬浮工具有「发闲置」',
      (await cdp.eval('document.querySelector(".floating-bar")?.innerText || ""')).includes('发闲置'))
    check('有页脚', await cdp.eval('!!document.querySelector(".footer")'))

    // ==================================================================
    // 5. 商品列表页
    // ==================================================================
    console.log('\n[5] 商品列表页 /products：5 列网格 / 排序 / 筛选')

    await goto('/products')
    const gridCols = await cdp.eval(
      'getComputedStyle(document.querySelector(".grid")).gridTemplateColumns.split(" ").length'
    )
    check('商品网格是 5 列', gridCols === 5, `${gridCols} 列`)

    const cardCount = await cdp.eval('document.querySelectorAll(".grid .card").length')
    check('一页有 20 个商品卡片', cardCount === 20, `${cardCount} 个`)

    const cardRadius = await cdp.eval('getComputedStyle(document.querySelector(".grid .card")).borderRadius')
    check('卡片圆角 8px', cardRadius === '8px', cardRadius)

    const priceColor = await cdp.eval('getComputedStyle(document.querySelector(".price-tag .now")).color')
    check('价格颜色 #FF5000', priceColor === 'rgb(255, 80, 0)', priceColor)

    const titleClamp = await cdp.eval(
      'getComputedStyle(document.querySelector(".grid .card .title")).webkitLineClamp'
    )
    check('标题最多 2 行（line-clamp）', titleClamp === '2', titleClamp)

    const privacy = await cdp.eval(`(async () => {
      const t = await (await fetch('/api/products?size=100')).text()
      return ['wechat', 'qq', 'phone'].filter(f => t.includes('"' + f + '"')).join(',') || 'clean'
    })()`)
    check('列表数据不含任何联系方式', privacy === 'clean', privacy)

    const sortCheck = await cdp.eval(`(async () => {
      const asc = await (await fetch('/api/products?sort=priceAsc&size=1')).json()
      const desc = await (await fetch('/api/products?sort=priceDesc&size=1')).json()
      return asc.data.records[0].price + '|' + desc.data.records[0].price
    })()`)
    const [ascPrice, descPrice] = sortCheck.split('|').map(Number)
    check('价格排序接口生效', ascPrice < descPrice, `升序首项 ¥${ascPrice} < 降序首项 ¥${descPrice}`)

    await cdp.eval(`(() => {
      const btn = [...document.querySelectorAll('.sort')].find(b => b.textContent.includes('价格'))
      btn?.click(); return !!btn
    })()`)
    await sleep(1400)
    check('点「价格」后 URL 带 sort=priceAsc',
      (await cdp.eval('location.search')).includes('sort=priceAsc'),
      await cdp.eval('location.search'))

    const priceGuard = await cdp.eval(`(() => {
      const ins = document.querySelectorAll('.input-price')
      if (ins.length < 2) return false
      const set = (el, v) => { el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })) }
      set(ins[0], '500'); set(ins[1], '100')
      const btn = [...document.querySelectorAll('button')].find(b => b.textContent.trim() === '确定')
      btn?.click()
      return true
    })()`)
    await sleep(700)
    const filterErr = await cdp.eval('document.querySelector(".filter-error")?.textContent || ""')
    check('最低价 > 最高价时前端拦住并提示', priceGuard === true && filterErr.includes('不能高于'), filterErr)

    const someCat = await cdp.eval(`(async () => {
      const t = await (await fetch('/api/categories')).json()
      return t.data[0].children[0].id
    })()`)
    await goto('/products?categoryId=' + someCat)
    const catTitle = await cdp.eval('document.querySelector(".result-title")?.textContent || ""')
    check('按分类筛选时标题显示「父 / 子」分类名', catTitle.includes('/'), catTitle)

    await goto('/products?keyword=这个词肯定搜不到xyz')
    check('搜不到时显示空状态', await cdp.eval('!!document.querySelector(".empty")'))

    // ==================================================================
    // 6. 商品详情页
    // ==================================================================
    console.log('\n[6] 商品详情页 /product/:id')

    await goto('/product/2')
    const detailTitle = await cdp.eval('document.querySelector(".info .title")?.textContent || ""')
    check('详情页标题渲染', detailTitle.length > 5, `"${detailTitle.slice(0, 24)}…"`)

    const attrCount = await cdp.eval('document.querySelectorAll(".attrs .attr").length')
    check('参数网格有 4 项（品牌/分类/成色/交易地点）', attrCount === 4, `${attrCount} 项`)

    check('详情页有状态标签', await cdp.eval('!!document.querySelector(".info-head .status-tag")'))

    const detailText = await cdp.eval('document.querySelector("main").innerText || ""')
    check('详情页不显示卖家微信号', !detailText.includes('zhe_ershou'))

    await goto('/product/999999')
    const notFoundText = await cdp.eval('document.querySelector(".empty")?.innerText || ""')
    check('不存在的商品显示「商品不存在或已下架」', notFoundText.includes('不存在'), notFoundText.slice(0, 30))

    await cdp.eval("localStorage.removeItem('token'); localStorage.removeItem('user')")
    await goto('/product/2')
    const guestBtns = await cdp.eval(
      '[...document.querySelectorAll(".actions button")].map(b => b.textContent.trim()).join("|")'
    )
    check('游客看到「想要 / 获取联系方式」',
      guestBtns.includes('想要') && guestBtns.includes('获取联系方式'), guestBtns)

    // ==================================================================
    // 7. 收藏（想要）
    // ==================================================================
    console.log('\n[7] 收藏：点星标 → 数字 +1 → 再点取消')
    await loginInPage('buyer01', '123456')
    await goto('/product/2')

    const favBefore = await cdp.eval(`(async () => {
      const j = await (await fetch('/api/products/2')).json()
      return j.data.favoriteCount
    })()`)

    await cdp.eval(`(() => {
      const btn = [...document.querySelectorAll('.actions button')].find(b => b.textContent.includes('想要'))
      btn?.click(); return !!btn
    })()`)
    await sleep(1600)
    const favAfterAdd = await cdp.eval('document.querySelector(".price-block .want")?.textContent || ""')
    check(`收藏后「想要」数 +1（${favBefore} → ${favBefore + 1}）`,
      favAfterAdd.includes(String(favBefore + 1)), favAfterAdd)

    await cdp.eval(`(() => {
      const btn = [...document.querySelectorAll('.actions button')].find(b => b.textContent.includes('想要'))
      btn?.click(); return !!btn
    })()`)
    await sleep(1600)
    const favAfterRemove = await cdp.eval('document.querySelector(".price-block .want")?.textContent || ""')
    check('取消收藏后回到原值', favAfterRemove.includes(String(favBefore)), favAfterRemove)

    // ==================================================================
    // 8. 获取联系方式弹窗
    // ==================================================================
    console.log('\n[8] 获取联系方式：点按钮 → 弹窗 → 关闭')

    await cdp.eval(`(() => {
      const btn = [...document.querySelectorAll('.actions button')].find(b => b.textContent.includes('获取联系方式'))
      btn?.click(); return !!btn
    })()`)
    await sleep(1800)

    const dialog = await cdp.eval('document.querySelector(".dialog")?.innerText || ""')
    check('弹窗出现并显示微信号',
      dialog.includes('微信号') && dialog.includes('zhe_ershou'),
      dialog.replace(/\s+/g, ' ').slice(0, 60))
    check('弹窗显示 QQ', dialog.includes('1001001001'))
    check('弹窗有交易地点', dialog.includes('交易地点'))
    check('弹窗有防诈骗提示', dialog.includes('当面验货'))

    await cdp.eval(`(() => {
      const btn = [...document.querySelectorAll('.dialog button')].find(b => b.textContent.includes('我知道了'))
      btn?.click(); return !!btn
    })()`)
    await sleep(700)
    check('点「我知道了」能关闭弹窗', !(await cdp.eval('!!document.querySelector(".dialog")')))

    await loginInPage('student01', '123456')
    await goto('/product/2')
    const ownerText = await cdp.eval('document.querySelector(".info")?.innerText || ""')
    check('卖家本人看到的是管理按钮（标记售出/下架/删除）',
      ownerText.includes('标记售出') && ownerText.includes('下架'), '有管理按钮')

    // ==================================================================
    // 9. 发布页
    // ==================================================================
    console.log('\n[9] 发布页 /publish：分类联动 / 前端校验 / 真实发布')

    await goto('/publish')
    const selects = await cdp.eval('document.querySelectorAll(".category-select select").length')
    check('分类是「一级 + 二级」两个下拉', selects === 2, `${selects} 个`)

    await cdp.eval(`(() => {
      const sel = document.querySelectorAll('.category-select select')[0]
      sel.value = sel.options[1].value
      sel.dispatchEvent(new Event('change', { bubbles: true }))
      return sel.value
    })()`)
    await sleep(600)
    const childCount = await cdp.eval('document.querySelectorAll(".category-select select")[1].options.length')
    check('选了一级分类后，二级分类有可选项', childCount > 1, `${childCount} 个 option`)

    await cdp.eval(`(() => {
      document.querySelector('.publish-btn').click()
      return true
    })()`)
    await sleep(700)
    const publishErr = await cdp.eval('document.querySelector(".error-box")?.textContent || ""')
    check('空表单提交被前端校验拦住', publishErr.includes('标题'), publishErr)

    await cdp.eval(`(async () => {
      const setInput = (sel, v) => {
        const el = document.querySelector(sel)
        el.value = v
        el.dispatchEvent(new Event('input', { bubbles: true }))
      }
      setInput('#title', '【e2e】浏览器发布的测试商品 请忽略')
      setInput('#desc', '这是 e2e 脚本自动发布的测试数据，测试结束会自动删除。')
      setInput('#brand', 'e2e')
      setInput('#place', '东区食堂门口')
      setInput('#price', '12.34')
      setInput('#original', '99')

      const sel2 = document.querySelectorAll('.category-select select')[1]
      sel2.value = sel2.options[1].value
      sel2.dispatchEvent(new Event('change', { bubbles: true }))

      const pill = [...document.querySelectorAll('.pill')].find(p => p.textContent.trim() === '全新')
      pill?.click()
      await new Promise(r => setTimeout(r, 250))
      document.querySelector('.publish-btn').click()
      return 'clicked'
    })()`)
    await sleep(2800)

    check('发布后跳转到个人中心',
      (await cdp.eval('location.pathname')) === '/profile',
      `当前 ${await cdp.eval('location.pathname')}${await cdp.eval('location.search')}`)

    const newProduct = await cdp.eval(`(async () => {
      const j = await (await fetch('/api/products/mine?size=5', {
        headers: { Authorization: 'Bearer ' + localStorage.getItem('token') } })).json()
      const hit = (j.data.records || []).find(p => p.title.includes('【e2e】'))
      return hit ? hit.id + '|' + hit.status : 'none'
    })()`)
    check('新商品能在「我发布的」里查到，且是待审核（status=0）',
      newProduct !== 'none' && newProduct.endsWith('|0'), newProduct)

    const cleanup = await cdp.eval(`(async () => {
      const h = { Authorization: 'Bearer ' + localStorage.getItem('token') }
      const j = await (await fetch('/api/products/mine?size=5', { headers: h })).json()
      const hit = (j.data.records || []).find(p => p.title.includes('【e2e】'))
      if (!hit) return 'nothing'
      const r = await fetch('/api/products/' + hit.id, { method: 'DELETE', headers: h })
      return 'deleted ' + r.status
    })()`)
    check('测试商品已删除（不污染数据）', cleanup === 'deleted 200', cleanup)

    // ==================================================================
    // 10. 个人中心
    // ==================================================================
    console.log('\n[10] 个人中心 /profile：左菜单 / 5 个统计 / 行卡片')

    await goto('/profile', 2800)
    const profileTitle = await cdp.eval('document.querySelector("h1")?.textContent || ""')
    check('个人中心能加载', profileTitle.includes('个人中心'), `"${profileTitle.trim()}"`)

    const menuItems = await cdp.eval(
      '[...document.querySelectorAll(".side-menu .menu-item")].map(a => a.textContent.trim()).join("|")'
    )
    check('左侧菜单 3 项（我发布的 / 我的收藏 / 联系方式设置）',
      menuItems.includes('我发布的') && menuItems.includes('我的收藏') && menuItems.includes('联系方式设置'),
      menuItems.replace(/\s+/g, ' '))

    const statCount = await cdp.eval('document.querySelectorAll(".stats .stat").length')
    check('资料卡下有 5 个统计数字', statCount === 5, `实际 ${statCount} 个`)

    const rows = await cdp.eval('document.querySelectorAll(".row-card").length')
    check('「我发布的」有数据', rows > 0, `${rows} 行`)

    const attention = await cdp.eval('document.querySelectorAll(".attention").length')
    console.log(`    （其中 ${attention} 条带「N 人想要，成交后请标记售出」提醒）`)

    await cdp.eval(`(() => {
      const a = [...document.querySelectorAll('.side-menu .menu-item')].find(x => x.textContent.includes('联系方式'))
      a?.click(); return !!a
    })()`)
    await sleep(1500)
    check('联系方式设置 URL 带 tab=contact',
      (await cdp.eval('location.search')).includes('tab=contact'),
      await cdp.eval('location.search'))

    const fieldCount = await cdp.eval('document.querySelectorAll(".field").length')
    check('联系方式是逐条保存（每个字段一个保存按钮）', fieldCount >= 5, `${fieldCount} 个字段行`)

    const keepContact = await cdp.eval(`(async () => {
      const h = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + localStorage.getItem('token') }
      const before = (await (await fetch('/api/auth/me', { headers: h })).json()).data
      await fetch('/api/auth/profile', { method: 'PUT', headers: h,
        body: JSON.stringify({ nickname: '阿哲的闲置小铺' }) })
      const after = (await (await fetch('/api/auth/me', { headers: h })).json()).data
      return (before.wechat === after.wechat) + '|' + after.wechat
    })()`)
    check('只提交昵称时，微信号没有被顺手清空', keepContact.startsWith('true'), keepContact)

    await cdp.eval(`(() => {
      const a = [...document.querySelectorAll('.side-menu .menu-item')].find(x => x.textContent.includes('收藏'))
      a?.click(); return !!a
    })()`)
    await sleep(1700)
    const favPanel = await cdp.eval('document.querySelector(".panel")?.innerText || ""')
    check('我的收藏面板能打开', favPanel.includes('我的收藏'), favPanel.split('\n')[1] || '')

    // ==================================================================
    // 11. 卖家主页
    // ==================================================================
    console.log('\n[11] 卖家主页 /seller/:id')
    await goto('/seller/2', 2600)

    const sellerTitle = await cdp.eval('document.querySelector(".seller-card h1")?.textContent || ""')
    check('卖家主页能加载', sellerTitle.includes('阿哲'), `"${sellerTitle.trim()}"`)

    const sellerBody = await cdp.eval('document.body.innerText')
    check('卖家主页不显示微信号', !sellerBody.includes('zhe_ershou'),
      sellerBody.includes('zhe_ershou') ? '泄漏了微信号！' : '页面里没有微信号')
    check('卖家主页显示「已填写联系方式」但不显示内容', sellerBody.includes('已填写联系方式'))

    const sellerCards = await cdp.eval('document.querySelectorAll(".grid .card").length')
    check('卖家在售商品以卡片展示', sellerCards > 0, `${sellerCards} 张卡片`)

    // ==================================================================
    // 12. 首页
    // ==================================================================
    console.log('\n[12] 首页 /：分类侧栏 + 分类 Tab + 4 列商品流')
    await goto('/', 2800)

    const catItems = await cdp.eval('document.querySelectorAll(".sidebar .cat-item").length')
    check('左侧分类栏有 10 项（全部闲置 + 9 个一级分类）', catItems === 10, `${catItems} 项`)

    const tabCount = await cdp.eval('document.querySelectorAll(".tabs .tab").length')
    check('分类 Tab 有 10 个（猜你喜欢 + 9 个分类）', tabCount === 10, `${tabCount} 个`)

    const homeCols = await cdp.eval(
      'getComputedStyle(document.querySelector(".grid")).gridTemplateColumns.split(" ").length'
    )
    check('首页商品流是 4 列（含侧栏，所以比列表页少一列）', homeCols === 4, `${homeCols} 列`)

    const homeCards = await cdp.eval('document.querySelectorAll(".grid .card").length')
    check('首页有商品卡片', homeCards > 0, `${homeCards} 张`)

    const catFilter = await cdp.eval(`(async () => {
      const items = document.querySelectorAll('.sidebar .cat-item')
      items[1].click()
      await new Promise(r => setTimeout(r, 1500))
      const title = document.querySelector('.section-title')?.textContent || ''
      const cards = document.querySelectorAll('.grid .card').length
      return title + '|' + cards
    })()`)
    check('点左侧分类后商品流按该分类过滤', /\|/.test(catFilter), catFilter)

    // ==================================================================
    // 13. 管理后台
    // ==================================================================
    console.log('\n[13] 管理后台：5 个模块 + 权限守卫')

    await loginInPage('admin', 'admin123')
    await goto('/admin', 2800)

    const sideLinks = await cdp.eval(
      '[...document.querySelectorAll(".side nav a")].map(a => a.textContent.trim().split(/\\s+/)[0]).join("|")'
    )
    check('侧边栏有 5 个模块',
      ['数据统计', '商品审核', '商品管理', '咨询记录', '用户管理'].every((m) => sideLinks.includes(m)),
      sideLinks)

    const statCards = await cdp.eval('document.querySelectorAll(".card b").length')
    check('统计页渲染出数字卡片', statCards >= 10, `${statCards} 张`)

    const statsText = await cdp.eval('document.querySelector(".body")?.innerText || ""')
    check('统计页明确说明「没有成交额」', statsText.includes('没有「成交额」'))

    await goto('/admin/review')
    const switchBar = await cdp.eval('document.querySelector(".switch-bar")?.innerText || ""')
    check('审核页有「审核开关」且默认已开启', switchBar.includes('已开启'),
      switchBar.replace(/\s+/g, ' ').slice(0, 40))

    await goto('/admin/products')
    const prodRows = await cdp.eval('document.querySelectorAll("tbody tr").length')
    check('商品管理表格有数据', prodRows > 0, `${prodRows} 行`)
    check('在售商品带「强制下架」按钮',
      await cdp.eval('[...document.querySelectorAll("tbody button")].some(b => b.textContent.includes("强制下架"))'))

    await goto('/admin/behaviors')
    const summaryNums = await cdp.eval('document.querySelectorAll(".summary .sum").length')
    check('咨询记录页有 4 个全站汇总数字', summaryNums === 4, `${summaryNums} 个`)

    await goto('/admin/users')
    const headers = await cdp.eval(
      '[...document.querySelectorAll("thead th")].map(th => th.textContent.trim()).join("|")'
    )
    check('用户表含微信/QQ/手机号但不含密码列',
      headers.includes('微信') && !headers.includes('密码'), headers)

    await loginInPage('student01', '123456')
    await goto('/admin')
    check('普通用户访问 /admin 被守卫弹回首页',
      (await cdp.eval('location.pathname')) === '/', `当前 ${await cdp.eval('location.pathname')}`)
    check('普通用户顶栏没有「管理后台」入口',
      !(await cdp.eval('[...document.querySelectorAll(".topbar a")].some(a => a.textContent.includes("管理后台"))')))

    const backendGuard = await cdp.eval(`(async () => {
      const r = await fetch('/api/admin/stats', {
        headers: { Authorization: 'Bearer ' + localStorage.getItem('token') } })
      const j = await r.json()
      return r.status + '|' + j.code
    })()`)
    check('普通用户直接请求后台接口 → 403', backendGuard === '403|403', backendGuard)

    // ==================================================================
    // 14. 兜底：404 页 + 响应式
    // ==================================================================
    console.log('\n[14] 404 页 + 响应式兜底')
    await goto('/no-such-page', 1800)
    const nf = await cdp.eval('document.querySelector(".code")?.textContent || ""')
    check('未匹配的地址显示 404 页', nf === '404', `"${nf}"`)

    await goto('/products?keyword=耳机')
    check('列表页没有横向滚动条',
      (await cdp.eval('document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1')) === true)

    await cdp.send('Emulation.setDeviceMetricsOverride', {
      width: 375,
      height: 812,
      deviceScaleFactor: 2,
      mobile: true
    })
    await goto('/products', 2800)
    const mobileCols = await cdp.eval(
      'getComputedStyle(document.querySelector(".grid")).gridTemplateColumns.split(" ").length'
    )
    const mobileOverflow = await cdp.eval(
      'document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1'
    )
    check('375px 宽下商品网格变 2 列', mobileCols === 2, `${mobileCols} 列`)
    check('375px 宽下没有横向滚动条', mobileOverflow === true)

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
