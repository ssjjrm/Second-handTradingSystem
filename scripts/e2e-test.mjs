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

    // ================= 7. S4 分类 + 商品（此时是未登录状态）=================
    console.log('\n[7] S4 验证页：分类树 / 商品列表 / 详情 / 浏览量')
    await cdp.send('Page.navigate', { url: FRONT + '/verify/s4-products.html' })
    await sleep(1500)

    const pageTitle = await cdp.eval('document.querySelector("h1")?.textContent || ""')
    check('S4 验证页能加载', pageTitle.includes('S4'), `"${pageTitle.trim()}"`)

    const treeInfo = await cdp.eval(`(async () => {
      const r = await fetch('/api/categories'); const j = await r.json();
      const roots = j.data;
      const children = roots.reduce((s, x) => s + (x.children ? x.children.length : 0), 0);
      return roots.length + '|' + children + '|' + roots[0].name + '|' + roots[0].children[0].name;
    })()`)
    check('分类树：9 个一级 / 41 个二级，父子正确',
      treeInfo.startsWith('9|41|数码电子|手机'), treeInfo)

    const listInfo = await cdp.eval(`(async () => {
      const r = await fetch('/api/products?size=3'); const j = await r.json();
      const first = j.data.records[0];
      return j.data.records.length + '|' + j.data.total + '|' + Array.isArray(first.images)
           + '|' + (first.statusText || '') + '|' + (first.categoryName || '');
    })()`)
    check('商品列表：分页生效、images 是数组、带 statusText/categoryName',
      listInfo.startsWith('3|53|true|'), listInfo)

    const privacy = await cdp.eval(`(async () => {
      const r = await fetch('/api/products?size=100'); const t = await r.text();
      return ['wechat', 'qq', 'phone'].filter(f => t.includes('"' + f + '"')).join(',') || 'clean';
    })()`)
    check('列表不含任何联系方式', privacy === 'clean', privacy)

    const sortCheck = await cdp.eval(`(async () => {
      const asc = await (await fetch('/api/products?sort=priceAsc&size=3')).json();
      const desc = await (await fetch('/api/products?sort=priceDesc&size=3')).json();
      return asc.data.records[0].price + '|' + desc.data.records[0].price;
    })()`)
    const [ascPrice, descPrice] = sortCheck.split('|').map(Number)
    check('价格排序生效', ascPrice < descPrice, `升序首项 ¥${ascPrice} < 降序首项 ¥${descPrice}`)

    const detailCheck = await cdp.eval(`(async () => {
      const ok = await (await fetch('/api/products/2')).json();
      const notFound = await fetch('/api/products/99999');
      const nfJson = await notFound.json();
      const pending = await fetch('/api/products/3');
      return [ok.data.canContact, ok.data.contactHint, ok.data.isFavorited,
              notFound.status, nfJson.code, pending.status].join('|');
    })()`)
    const dc = detailCheck.split('|')
    check('未登录详情：canContact=false 且提示正确',
      dc[0] === 'false' && dc[1] === '请先登录后再联系卖家', `canContact=${dc[0]} hint="${dc[1]}"`)
    check('未登录详情：isFavorited=false', dc[2] === 'false')
    check('不存在的商品返回 HTTP 404', dc[3] === '404' && dc[4] === '404', `HTTP ${dc[3]}`)
    check('未登录访问待审商品返回 404', dc[5] === '404', `HTTP ${dc[5]}`)

    const viewCheck = await cdp.eval(`(async () => {
      const seen = [];
      for (let i = 0; i < 3; i++) {
        const j = await (await fetch('/api/products/5')).json();
        seen.push(j.data.viewCount);
      }
      return seen.join(',');
    })()`)
    const vc = viewCheck.split(',').map(Number)
    check('浏览量每访问一次只 +1', vc[1] === vc[0] + 1 && vc[2] === vc[1] + 1, `${vc.join(' → ')}`)

    // ================= 8. 登录后详情应能识别身份（验证拦截器拆分是否正确）=================
    console.log('\n[8] 登录后访问公开的商品详情，身份应被识别')
    const identityCheck = await cdp.eval(`(async () => {
      const lr = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'buyer01', password: '123456' })
      });
      const lj = await lr.json();
      const token = lj.data.token;

      // 找一个 buyer01 收藏过的商品，isFavorited 应该是 true
      const list = await (await fetch('/api/products?size=100')).json();
      let favorited = null, contactable = null;
      for (const p of list.data.records) {
        const d = await (await fetch('/api/products/' + p.id,
          { headers: { Authorization: 'Bearer ' + token } })).json();
        if (d.data.isFavorited && d.data.canContact && favorited === null) { favorited = p.id; break; }
      }
      return String(favorited);
    })()`)
    check('登录后公开接口能识别身份（isFavorited=true 且 canContact=true 都能拿到）',
      identityCheck !== 'null', `找到商品 id=${identityCheck}`)

    // ================= 9. S5 发布页（分类下拉必须动态加载）=================
    console.log('\n[9] S5 验证页：分类下拉动态加载 / 上传 / 发布')

    // 先登录（第 6 步退出过，第 8 步的登录没写进 localStorage）
    await cdp.eval(`(async () => {
      const r = await fetch('/api/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'student01', password: '123456' })
      });
      const j = await r.json();
      localStorage.setItem('token', j.data.token);
      localStorage.setItem('user', JSON.stringify(j.data.user));
      return true;
    })()`)

    await cdp.send('Page.navigate', { url: FRONT + '/verify/s5-publish.html' })
    await sleep(2000)

    const catOptions = await cdp.eval('document.querySelectorAll("#cat option").length')
    check('分类下拉从接口动态加载（41 个二级 + 1 个一级用于反例）',
      catOptions === 42, `option 数量 = ${catOptions}`)

    const uploadResult = await cdp.eval(`(async () => {
      const b64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';
      const bytes = Uint8Array.from(atob(b64), c => c.charCodeAt(0));
      const fd = new FormData();
      fd.append('file', new Blob([bytes], { type: 'image/png' }), 'e2e.png');
      const r = await fetch('/api/upload/image', {
        method: 'POST',
        headers: { Authorization: 'Bearer ' + localStorage.getItem('token') },
        body: fd
      });
      const j = await r.json();
      if (j.code !== 200) return 'FAIL|' + j.message;
      const img = await fetch(j.data.url);
      return j.data.url + '|' + img.status;
    })()`)
    check('上传图片成功且返回的 URL 可直接访问', uploadResult.endsWith('|200'), uploadResult)

    // 发布 → 待审（审核流程已在 s5-test.mjs 里完整覆盖）
    const publishResult = await cdp.eval(`(async () => {
      const h = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + localStorage.getItem('token') };
      const tree = await (await fetch('/api/categories')).json();
      const catId = tree.data[0].children[0].id;

      const bad = await fetch('/api/products', {
        method: 'POST', headers: h,
        body: JSON.stringify({ title: 'e2e 反例：一级分类', categoryId: tree.data[0].id,
          conditionLevel: '全新', price: 10, tradePlace: '东区食堂门口' })
      });
      const badStatus = bad.status;

      const ok = await fetch('/api/products', {
        method: 'POST', headers: h,
        body: JSON.stringify({ title: '【e2e】浏览器发布的测试商品', categoryId: catId,
          conditionLevel: '全新', price: 12.34, tradePlace: '东区食堂门口' })
      });
      const okJson = await ok.json();
      const newId = okJson.data?.id;

      const list = await (await fetch('/api/products?keyword=e2e&size=50')).json();
      if (newId) await fetch('/api/products/' + newId, { method: 'DELETE', headers: h });

      return [badStatus, ok.status, newId > 0, list.data.total].join('|');
    })()`)
    const pc = publishResult.split('|')
    check('选一级分类发布 → 400', pc[0] === '400', `HTTP ${pc[0]}`)
    check('正常发布成功并返回 id', pc[1] === '200' && pc[2] === 'true')
    check('待审商品在前台列表看不到', pc[3] === '0', `total=${pc[3]}`)

    // ================= 10. S6 交易功能 =================
    console.log('\n[10] S6 验证页：获取联系方式 / 卖家主页')
    await cdp.send('Page.navigate', { url: FRONT + '/verify/s6-contact.html' })
    await sleep(1500)

    const s6Title = await cdp.eval('document.querySelector("h1")?.textContent || ""')
    check('S6 验证页能加载', s6Title.includes('S6'), `"${s6Title.trim()}"`)

    const contactCheck = await cdp.eval(`(async () => {
      // 换成 buyer01（student01 的商品 id=2 是他发的）
      const lr = await fetch('/api/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'buyer01', password: '123456' })
      });
      const lj = await lr.json();
      const t = lj.data.token;
      localStorage.setItem('token', t);
      localStorage.setItem('user', JSON.stringify(lj.data.user));

      const r = await fetch('/api/products/2/contact', { headers: { Authorization: 'Bearer ' + t } });
      const j = await r.json();
      return [r.status, j.data?.wechat || '-', j.data?.qq || '-',
              j.data?.tradePlace || '-', j.data?.sellerNickname || '-'].join('|');
    })()`)
    const cc = contactCheck.split('|')
    check('获取联系方式成功（返回微信 / QQ / 交易地点 / 卖家昵称）',
      cc[0] === '200' && cc[1] !== '-' && cc[2] !== '-', contactCheck)

    const selfContact = await cdp.eval(`(async () => {
      const lr = await fetch('/api/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'student01', password: '123456' })
      });
      const t = (await lr.json()).data.token;
      const r = await fetch('/api/products/2/contact', { headers: { Authorization: 'Bearer ' + t } });
      const j = await r.json();
      return r.status + '|' + j.message;
    })()`)
    check('联系自己发布的商品 → 400', selfContact.startsWith('400|'), selfContact)

    const sellerCheck = await cdp.eval(`(async () => {
      const r = await (await fetch('/api/sellers/2')).json();
      const raw = JSON.stringify(r);
      const leaked = ['wechat', 'qq', 'phone'].filter(f => raw.includes('"' + f + '"'));
      return (leaked.length ? 'LEAK:' + leaked.join(',') : 'clean')
           + '|' + r.data.hasContact + '|' + r.data.stats.onSaleCount;
    })()`)
    check('卖家主页不含联系方式，只有 hasContact 和统计',
      sellerCheck.startsWith('clean|'), sellerCheck)

    // ================= 11. S7 收藏 + 行为埋点 =================
    console.log('\n[11] S7 验证页：收藏幂等 / 计数一致 / 四类行为')
    await cdp.send('Page.navigate', { url: FRONT + '/verify/s7-favorite.html' })
    await sleep(1500)

    const s7Title = await cdp.eval('document.querySelector("h1")?.textContent || ""')
    check('S7 验证页能加载', s7Title.includes('S7'), `"${s7Title.trim()}"`)

    const favCheck = await cdp.eval(`(async () => {
      // buyer01 登录
      const lr = await fetch('/api/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'buyer01', password: '123456' })
      });
      const lj = await lr.json();
      localStorage.setItem('token', lj.data.token);
      localStorage.setItem('user', JSON.stringify(lj.data.user));
      const h = { Authorization: 'Bearer ' + lj.data.token };

      // 找一件不是自己发的、在售的商品
      const list = await (await fetch('/api/products?size=100')).json();
      const target = list.data.records.find(p => p.sellerId !== lj.data.user.id && p.status === 1);
      if (!target) return 'NO_TARGET';
      const id = target.id;

      // 先确保没收藏
      await fetch('/api/favorites/' + id, { method: 'DELETE', headers: h });
      const before = (await (await fetch('/api/products/' + id)).json()).data.favoriteCount;

      // 收藏两次（验证幂等）
      const r1 = await (await fetch('/api/favorites/' + id, { method: 'POST', headers: h })).json();
      const r2 = await (await fetch('/api/favorites/' + id, { method: 'POST', headers: h })).json();

      // 取消
      const r3 = await (await fetch('/api/favorites/' + id, { method: 'DELETE', headers: h })).json();

      return [id, before, r1.data.favoriteCount, r2.data.favoriteCount, r3.data.favoriteCount].join('|');
    })()`)
    const fv = favCheck.split('|').map((x, i) => (i === 0 ? x : Number(x)))
    check('收藏后想要数 +1', fv[2] === fv[1] + 1, `${fv[1]} → ${fv[2]}`)
    check('重复收藏保持幂等（不再增加）', fv[3] === fv[2], `${fv[2]} → ${fv[3]}`)
    check('取消收藏后回到原值', fv[4] === fv[1], `${fv[3]} → ${fv[4]}`)

    const behaviorCheck = await cdp.eval(`(async () => {
      const h = { Authorization: 'Bearer ' + localStorage.getItem('token') };
      const list = await (await fetch('/api/products?size=100')).json();
      const target = list.data.records.find(p => p.sellerId !== 5 && p.status === 1);

      // 制造四类行为
      await fetch('/api/products/' + target.id, { headers: h });                 // 1 浏览
      await fetch('/api/favorites/' + target.id, { method: 'POST', headers: h }); // 2 收藏
      await fetch('/api/products/' + target.id + '/contact', { headers: h });     // 3 咨询
      await fetch('/api/favorites/' + target.id, { method: 'DELETE', headers: h }); // 4 取消

      const mine = await (await fetch('/api/favorites?size=100', { headers: h })).json();
      const hasProduct = mine.data.records.some(x => x.product && x.product.statusText);
      return mine.code + '|' + hasProduct;
    })()`)
    check('我的收藏返回嵌套商品信息', behaviorCheck === '200|true', behaviorCheck)

    // ================= 12. S8 个人中心 + 卖家主页 =================
    console.log('\n[12] S8 页面：个人中心 / 卖家主页')

    // 用 student01 登录（他有商品、有被咨询的数据）
    await cdp.eval(`(async () => {
      const r = await fetch('/api/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'student01', password: '123456' })
      });
      const j = await r.json();
      localStorage.setItem('token', j.data.token);
      localStorage.setItem('user', JSON.stringify(j.data.user));
      return true;
    })()`)

    await cdp.send('Page.navigate', { url: FRONT + '/profile' })
    await sleep(2200)

    const profileTitle = await cdp.eval('document.querySelector("h1")?.textContent || ""')
    check('个人中心能加载', profileTitle.includes('个人中心'), `"${profileTitle.trim()}"`)

    const statCount = await cdp.eval('document.querySelectorAll(".stats .stat").length')
    check('顶部有 5 个统计数字', statCount === 5, `实际 ${statCount} 个`)

    const tabs = await cdp.eval(
      '[...document.querySelectorAll(".tabs button")].map(b => b.textContent).join("|")')
    check('3 个 Tab（我发布的 / 我的收藏 / 联系方式设置）',
      tabs.includes('我发布的') && tabs.includes('我的收藏') && tabs.includes('联系方式设置'), tabs)

    const rows = await cdp.eval('document.querySelectorAll(".row-card").length')
    check('「我发布的」有数据', rows > 0, `${rows} 行`)

    const attention = await cdp.eval('document.querySelectorAll(".attention").length')
    console.log(`    （其中 ${attention} 条带「N 人想要，成交后请标记售出」提醒）`)

    // 切到"联系方式设置" Tab，验证逐条保存的界面存在
    await cdp.eval(`(() => {
      const btn = [...document.querySelectorAll('.tabs button')].find(b => b.textContent.includes('联系方式'))
      btn?.click(); return !!btn
    })()`)
    await sleep(800)
    const fieldCount = await cdp.eval('document.querySelectorAll(".field").length')
    check('联系方式设置是逐条保存（每个字段一个保存按钮）', fieldCount >= 4, `${fieldCount} 个字段行`)

    // 卖家主页
    await cdp.send('Page.navigate', { url: FRONT + '/seller/2' })
    await sleep(1800)

    const sellerTitle = await cdp.eval('document.querySelector(".profile h1")?.textContent || ""')
    check('卖家主页能加载', sellerTitle.length > 0, `"${sellerTitle}"`)

    const bodyText = await cdp.eval('document.body.innerText')
    check('卖家主页【不显示】卖家的微信号', !bodyText.includes('zhe_ershou'),
      bodyText.includes('zhe_ershou') ? '泄漏了微信号！' : '页面文本里没有微信号')
    check('卖家主页提示"已填写联系方式"（但不显示内容）',
      bodyText.includes('已填写联系方式'))

    const cards = await cdp.eval('document.querySelectorAll(".grid .card").length')
    check('卖家在售商品以卡片展示', cards > 0, `${cards} 张卡片`)

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
