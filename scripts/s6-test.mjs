/**
 * S6 功能测试：获取联系方式（核心）+ 标记已售出（乐观锁）+ 卖家模块
 *
 * 用法（后端 8081 要在跑）：node scripts\s6-test.mjs
 */

const B = 'http://localhost:8081'
const mysql = 'C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe'

let pass = 0
let fail = 0

function check(name, ok, detail = '') {
  if (ok) {
    pass++
    console.log(`  \u2713 ${name}${detail ? '  ' + detail : ''}`)
  } else {
    fail++
    console.log(`  \u2717 ${name}${detail ? '  ' + detail : ''}`)
  }
}

async function api(method, path, { token, body } = {}) {
  const headers = {}
  if (token) headers.Authorization = 'Bearer ' + token
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(B + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await res.text()
  let json = null
  try {
    json = JSON.parse(text)
  } catch {
    /* ignore */
  }
  return { status: res.status, json, text }
}

async function login(username, password) {
  const r = await api('POST', '/api/auth/login', { body: { username, password } })
  return r.json?.data?.token
}

const { execFileSync } = await import('node:child_process')
function sql(q, db = 'secondhand_shop') {
  return execFileSync(
    mysql,
    ['--no-defaults', '-h127.0.0.1', '-P3306', '-uroot', '-p123456',
      '--default-character-set=utf8mb4', '-N', '-B', '-e', q, db],
    { stdio: 'pipe' }
  ).toString().trim()
}

async function main() {
  console.log('=== S6 测试：交易功能（核心） ===\n')

  const tokS1 = await login('student01', '123456')
  const tokB1 = await login('buyer01', '123456')
  const tokAdmin = await login('admin', 'admin123')
  check('三个账号都能登录', !!tokS1 && !!tokB1 && !!tokAdmin)

  // ---------- 准备：给 student01 发一件新商品并审核通过 ----------
  const tree = await api('GET', '/api/categories')
  const catId = tree.json.data[0].children[0].id

  const pub = await api('POST', '/api/products', {
    token: tokS1,
    body: {
      title: '【九成新】S6 测试用显卡 RTX3060 宿舍自提',
      description: 'S6 自动化测试发布的商品，用于验证获取联系方式与标记售出。',
      categoryId: catId,
      conditionLevel: '轻微使用痕迹',
      price: 1299.0,
      originalPrice: 2499.0,
      tradePlace: '3号宿舍楼下'
    }
  })
  const pid = pub.json?.data?.id
  check('准备测试商品', !!pid, `id=${pid}`)

  await api('PUT', `/api/admin/products/${pid}/approve`, { token: tokAdmin })
  const afterApprove = await api('GET', `/api/products/${pid}`)
  check('商品已审核上架（status=1）', afterApprove.json?.data?.status === 1)

  // ============ 1. 获取联系方式：五种校验 ============
  console.log('\n[1] 获取联系方式 GET /api/products/{id}/contact')

  const anon = await api('GET', `/api/products/${pid}/contact`)
  check('未登录 → 401', anon.status === 401, `HTTP ${anon.status} ${anon.json?.message}`)

  const self = await api('GET', `/api/products/${pid}/contact`, { token: tokS1 })
  check('联系自己发布的商品 → 400「不能联系自己发布的商品」',
    self.status === 400 && self.json.message.includes('自己发布'),
    `HTTP ${self.status} ${self.json.message}`)

  const notExist = await api('GET', '/api/products/999999/contact', { token: tokB1 })
  check('商品不存在 → 404', notExist.status === 404, `HTTP ${notExist.status}`)

  // 记录调用前的咨询数
  const contactBefore = Number(sql(
    `SELECT COUNT(*) FROM user_behavior WHERE behavior_type=3 AND product_id=${pid}`))

  const ok = await api('GET', `/api/products/${pid}/contact`, { token: tokB1 })
  check('正常获取联系方式（200）', ok.status === 200, `HTTP ${ok.status}`)
  const d = ok.json?.data || {}
  check('返回卖家微信号', !!d.wechat, `wechat=${d.wechat}`)
  check('返回卖家 QQ', !!d.qq, `qq=${d.qq}`)
  check('返回交易地点', !!d.tradePlace, `tradePlace=${d.tradePlace}`)
  check('带上商品标题（前端弹窗直接显示）', !!d.productTitle,
    `productTitle=${d.productTitle?.slice(0, 16)}...`)
  check('带上卖家昵称', !!d.sellerNickname, `sellerNickname=${d.sellerNickname}`)

  const contactAfter = Number(sql(
    `SELECT COUNT(*) FROM user_behavior WHERE behavior_type=3 AND product_id=${pid}`))
  check('写入了一条 type=3 咨询行为', contactAfter === contactBefore + 1,
    `${contactBefore} → ${contactAfter}`)

  // 同一人再点一次：咨询行应该 +1（流水可重复），但 contactCount 去重后不变
  await api('GET', `/api/products/${pid}/contact`, { token: tokB1 })
  const contactAfter2 = Number(sql(
    `SELECT COUNT(*) FROM user_behavior WHERE behavior_type=3 AND product_id=${pid}`))
  check('同一人再点一次 → 行为流水继续 +1（流水只追加）', contactAfter2 === contactAfter + 1,
    `${contactAfter} → ${contactAfter2}`)

  const mine = await api('GET', '/api/products/mine?size=50', { token: tokS1 })
  const mineItem = (mine.json?.data?.records || []).find((x) => x.id === pid)
  check('但 contactCount 按人去重，仍是 1', mineItem?.contactCount === 1,
    `contactCount=${mineItem?.contactCount}`)
  check('needAttention=true（在售且有咨询）', mineItem?.needAttention === true)

  // ============ 2. 卖家主页 ============
  console.log('\n[2] 卖家模块 GET /api/sellers/{id}')

  const sellerId = d.sellerId
  const profile = await api('GET', `/api/sellers/${sellerId}`)
  check('游客能看卖家资料', profile.status === 200, `HTTP ${profile.status}`)

  const raw = JSON.stringify(profile.json)
  check('卖家资料里【没有】联系方式',
    !raw.includes('"wechat"') && !raw.includes('"qq"') && !raw.includes('"phone"'))
  check('只有 hasContact 表示"填没填"', profile.json?.data?.hasContact === true)

  const st = profile.json?.data?.stats
  check('带统计数据（在售/已售/收到的想要）',
    st && st.onSaleCount >= 0 && st.soldCount >= 0 && st.favoriteReceivedCount >= 0,
    `在售${st?.onSaleCount} 已售${st?.soldCount} 想要${st?.favoriteReceivedCount}`)

  const sellerProducts = await api('GET', `/api/sellers/${sellerId}/products?size=50`)
  const allOnSale = (sellerProducts.json?.data?.records || []).every((x) => x.status === 1)
  check('卖家的商品列表只含在售（status=1）', allOnSale,
    `共 ${sellerProducts.json?.data?.total} 条`)

  const notExistSeller = await api('GET', '/api/sellers/999999')
  check('不存在的卖家 → 404', notExistSeller.status === 404, `HTTP ${notExistSeller.status}`)

  // ============ 3. 标记已售出 ============
  console.log('\n[3] 标记已售出 PUT /api/products/{id}/mark-sold')

  const markByOther = await api('PUT', `/api/products/${pid}/mark-sold`, { token: tokB1 })
  check('非所有者 → 403「无权操作此商品」',
    markByOther.status === 403 && markByOther.json.message.includes('无权'),
    `HTTP ${markByOther.status} ${markByOther.json.message}`)

  const markAnon = await api('PUT', `/api/products/${pid}/mark-sold`)
  check('未登录 → 401', markAnon.status === 401, `HTTP ${markAnon.status}`)

  const mark = await api('PUT', `/api/products/${pid}/mark-sold`, { token: tokS1 })
  check('所有者标记售出成功', mark.status === 200, `${mark.json?.message}`)
  check('返回 status=2 / statusText=已售出',
    mark.json?.data?.status === 2 && mark.json?.data?.statusText === '已售出',
    JSON.stringify(mark.json?.data))

  const afterSold = await api('GET', `/api/products/${pid}`)
  check('商品状态变成 2（已售出）', afterSold.json?.data?.status === 2)

  const soldContact = await api('GET', `/api/products/${pid}/contact`, { token: tokB1 })
  check('已售出商品再获取联系方式 → 400「该商品已不可交易」',
    soldContact.status === 400 && soldContact.json.message.includes('不可交易'),
    soldContact.json?.message)

  // ============ 4. 乐观锁：并发验证 ============
  console.log('\n[4] 乐观锁：并发标记售出')

  const pub2 = await api('POST', '/api/products', {
    token: tokS1,
    body: {
      title: '【全新】S6 并发测试商品 用来验证乐观锁', categoryId: catId,
      conditionLevel: '全新', price: 88, tradePlace: '东区食堂门口'
    }
  })
  const pid2 = pub2.json?.data?.id
  await api('PUT', `/api/admin/products/${pid2}/approve`, { token: tokAdmin })

  const results = await Promise.all([
    api('PUT', `/api/products/${pid2}/mark-sold`, { token: tokS1 }),
    api('PUT', `/api/products/${pid2}/mark-sold`, { token: tokS1 }),
    api('PUT', `/api/products/${pid2}/mark-sold`, { token: tokS1 })
  ])
  const successCount = results.filter((r) => r.status === 200).length
  const failCount = results.filter((r) => r.status === 400).length
  check('并发 3 次请求，只有 1 次成功', successCount === 1,
    `成功 ${successCount} 次，被拒 ${failCount} 次`)
  check('被拒的都返回 400「商品已非在售状态」',
    results.filter((r) => r.status === 400).every((r) => r.json.message.includes('已非在售')),
    results.find((r) => r.status === 400)?.json?.message)

  const finalStatus = (await api('GET', `/api/products/${pid2}`)).json?.data?.status
  check('最终状态确实是 2', finalStatus === 2, `status=${finalStatus}`)

  // ============ 5. 埋点失败不影响主流程 ============
  console.log('\n[5] 埋点失败不能影响主流程（BR-B08）')
  // 做法：把 user_behavior 表临时改名，让 INSERT 失败，再调 contact
  //       —— 此时接口仍应正常返回联系方式
  const pub3 = await api('POST', '/api/products', {
    token: tokS1,
    body: {
      title: '【全新】S6 埋点失败测试商品', categoryId: catId,
      conditionLevel: '全新', price: 66, tradePlace: '东区食堂门口'
    }
  })
  const pid3 = pub3.json?.data?.id
  await api('PUT', `/api/admin/products/${pid3}/approve`, { token: tokAdmin })

  try {
    sql('RENAME TABLE user_behavior TO user_behavior_tmp')
    const r = await api('GET', `/api/products/${pid3}/contact`, { token: tokB1 })
    check('埋点表不可用时，仍然正常返回联系方式', r.status === 200 && !!r.json?.data?.wechat,
      `HTTP ${r.status}，wechat=${r.json?.data?.wechat}`)
  } finally {
    try {
      sql('RENAME TABLE user_behavior_tmp TO user_behavior')
    } catch (e) {
      console.log('    （恢复表名失败，需要手工检查：' + e.message + '）')
    }
  }
  const restored = sql("SHOW TABLES LIKE 'user_behavior'")
  check('user_behavior 表已恢复', restored === 'user_behavior', restored)

  // ============ 6. 已售出的商品不能删 ============
  console.log('\n[6] 已售出的商品不能删除（业务规则）')
  const delSold = await api('DELETE', `/api/products/${pid}`, { token: tokS1 })
  check('删除已售出商品 → 400「已售出的商品不能删除」',
    delSold.status === 400 && delSold.json.message.includes('已售出'),
    `HTTP ${delSold.status} ${delSold.json?.message}`)

  // ---------- 清理 ----------
  // ⚠️ 测试数据用 SQL 直接清，**不走接口** ——
  //    因为按业务规则"已售出的商品不能删除"，接口根本删不掉它们。
  const ids = [pid, pid2, pid3].filter(Boolean).join(',')
  sql(`DELETE FROM product WHERE id IN (${ids})`)
  const leftover = sql(`SELECT COUNT(*) FROM product WHERE id IN (${ids})`)
  check('测试数据已清理（走 SQL，绕过"已售出不能删"的规则）', leftover === '0', `剩余 ${leftover} 条`)

  console.log(`\n================ 结果 ================`)
  console.log(`通过 ${pass} 项，失败 ${fail} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('\n测试脚本出错：', e)
  process.exit(1)
})
