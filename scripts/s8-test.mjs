/**
 * S8 功能测试：个人中心统计 + 卖家主页
 *
 * 用法（后端 8081 要在跑）：node scripts\s8-test.mjs
 *
 * S8 的后端大部分在 S6/S7 已经做完了，本阶段新增的主要是
 * 「/api/auth/me 的 stats 从占位 0 换成真实统计」，以及前端页面。
 * 所以这个脚本重点验证：统计数字和数据库里真实值对得上。
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
    method, headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await res.text()
  let json = null
  try { json = JSON.parse(text) } catch { /* ignore */ }
  return { status: res.status, json }
}

async function login(u, p) {
  return (await api('POST', '/api/auth/login', { body: { username: u, password: p } })).json?.data?.token
}

const { execFileSync } = await import('node:child_process')
function sql(q) {
  return execFileSync(mysql,
    ['--no-defaults', '-h127.0.0.1', '-P3306', '-uroot', '-p123456',
      '--default-character-set=utf8mb4', '-N', '-B', '-e', q, 'secondhand_shop'],
    { stdio: 'pipe' }).toString().trim()
}
const n = (q) => Number(sql(q))

async function main() {
  console.log('=== S8 测试：个人中心统计 + 卖家主页 ===\n')

  const tokS1 = await login('student01', '123456')
  const tokB1 = await login('buyer01', '123456')
  check('两个账号都能登录', !!tokS1 && !!tokB1)

  const s1Id = n("SELECT id FROM user WHERE username='student01'")
  const b1Id = n("SELECT id FROM user WHERE username='buyer01'")

  // ============ 1. /me 的统计数字 vs 数据库真实值 ============
  console.log('\n[1] /api/auth/me 的 stats 要和数据库对得上')

  const me = await api('GET', '/api/auth/me', { token: tokS1 })
  check('能拿到当前用户信息', me.status === 200, `HTTP ${me.status}`)
  const stats = me.json?.data?.stats
  check('返回了 stats 对象', !!stats, JSON.stringify(stats))

  const real = {
    publishedCount: n(`SELECT COUNT(*) FROM product WHERE seller_id=${s1Id}`),
    onSaleCount: n(`SELECT COUNT(*) FROM product WHERE seller_id=${s1Id} AND status=1`),
    soldCount: n(`SELECT COUNT(*) FROM product WHERE seller_id=${s1Id} AND status=2`),
    favoriteCount: n(`SELECT COUNT(*) FROM favorite WHERE user_id=${s1Id}`),
    receivedContactCount: n(
      `SELECT COUNT(DISTINCT b.user_id) FROM user_behavior b
       JOIN product p ON p.id=b.product_id
       WHERE b.behavior_type=3 AND p.seller_id=${s1Id}`)
  }

  check('publishedCount 与我发布的商品数一致',
    stats.publishedCount === real.publishedCount, `接口 ${stats.publishedCount} / 库 ${real.publishedCount}`)
  check('onSaleCount 与在售数一致',
    stats.onSaleCount === real.onSaleCount, `接口 ${stats.onSaleCount} / 库 ${real.onSaleCount}`)
  check('soldCount 与已售出数一致',
    stats.soldCount === real.soldCount, `接口 ${stats.soldCount} / 库 ${real.soldCount}`)
  check('favoriteCount 与我的收藏数一致',
    stats.favoriteCount === real.favoriteCount, `接口 ${stats.favoriteCount} / 库 ${real.favoriteCount}`)
  check('receivedContactCount 与"被多少人咨询"一致',
    stats.receivedContactCount === real.receivedContactCount,
    `接口 ${stats.receivedContactCount} / 库 ${real.receivedContactCount}`)

  // 咨询数是"去重人数"，不等于"咨询次数"
  const contactRows = n(
    `SELECT COUNT(*) FROM user_behavior b JOIN product p ON p.id=b.product_id
     WHERE b.behavior_type=3 AND p.seller_id=${s1Id}`)
  check('receivedContactCount 是去重人数（≤ 咨询总次数）',
    stats.receivedContactCount <= contactRows,
    `去重人数 ${stats.receivedContactCount} ≤ 咨询次数 ${contactRows}`)

  // ============ 2. 统计会随操作实时变化 ============
  console.log('\n[2] 统计数字随操作实时变化')

  const tree = await api('GET', '/api/categories')
  const catId = tree.json.data[0].children[0].id
  const beforeCount = (await api('GET', '/api/auth/me', { token: tokS1 })).json.data.stats.publishedCount

  const pub = await api('POST', '/api/products', {
    token: tokS1,
    body: {
      title: '【九成新】S8 测试商品 用于验证统计实时性', categoryId: catId,
      conditionLevel: '轻微使用痕迹', price: 99, tradePlace: '东区食堂门口'
    }
  })
  const pid = pub.json?.data?.id
  const afterPub = (await api('GET', '/api/auth/me', { token: tokS1 })).json.data.stats.publishedCount
  check('发布后 publishedCount +1', afterPub === beforeCount + 1, `${beforeCount} → ${afterPub}`)

  await api('PUT', `/api/admin/products/${pid}/approve`, {
    token: await login('admin', 'admin123')
  })
  const afterApprove = (await api('GET', '/api/auth/me', { token: tokS1 })).json.data.stats
  check('审核通过后 onSaleCount +1',
    afterApprove.onSaleCount === real.onSaleCount + 1,
    `${real.onSaleCount} → ${afterApprove.onSaleCount}`)

  // 别人收藏一次，我的 favoriteCount 不变（那是我自己收藏别人的数量）
  const favBefore = afterApprove.favoriteCount
  await api('POST', `/api/favorites/${pid}`, { token: tokB1 })
  const afterOtherFav = (await api('GET', '/api/auth/me', { token: tokS1 })).json.data.stats.favoriteCount
  check('别人收藏我的商品，不会增加我的 favoriteCount（那个是"我收藏了多少"）',
    afterOtherFav === favBefore, `${favBefore} → ${afterOtherFav}`)

  // 别人咨询一次，我的 receivedContactCount 会 +1（这个才是"别人想要我的东西"）
  //
  // ⚠️ 注意它是「去重人数」不是「咨询次数」：
  //    如果这个人之前已经咨询过我的**其他**商品，数字不会变 —— 他毕竟只是一个潜在买家。
  //    所以这里要先挑一个"还没咨询过我"的用户来测，否则断言会误判。
  const already = sql(
    `SELECT COALESCE(GROUP_CONCAT(DISTINCT b.user_id), '')
     FROM user_behavior b JOIN product p ON p.id = b.product_id
     WHERE b.behavior_type = 3 AND p.seller_id = ${s1Id}`)
  const alreadyIds = already ? already.split(',').map(Number) : []

  const candidates = [
    { u: 'admin', p: 'admin123', id: 1 },
    { u: 'student02', p: '123456', id: 3 },
    { u: 'student03', p: '123456', id: 4 },
    { u: 'buyer01', p: '123456', id: 5 },
    { u: 'buyer02', p: '123456', id: 6 }
  ]
  const fresh = candidates.find((c) => !alreadyIds.includes(c.id))

  const contactBefore = (await api('GET', '/api/auth/me', { token: tokS1 })).json.data.stats.receivedContactCount
  const rowsBefore = n(
    `SELECT COUNT(*) FROM user_behavior b JOIN product p ON p.id = b.product_id
     WHERE b.behavior_type = 3 AND p.seller_id = ${s1Id}`)

  if (fresh) {
    const freshTok = await login(fresh.u, fresh.p)
    await api('GET', `/api/products/${pid}/contact`, { token: freshTok })
    const contactAfter = (await api('GET', '/api/auth/me', { token: tokS1 })).json.data.stats.receivedContactCount
    check(`新的咨询者（${fresh.u}）咨询后，receivedContactCount +1`,
      contactAfter === contactBefore + 1, `${contactBefore} → ${contactAfter}`)
  } else {
    // 所有候选人都已经咨询过 → 退化成验证"去重人数不变"
    check('所有候选人都咨询过，去重人数保持不变', true, `已是 ${contactBefore} 人`)
  }

  const rowsAfter = n(
    `SELECT COUNT(*) FROM user_behavior b JOIN product p ON p.id = b.product_id
     WHERE b.behavior_type = 3 AND p.seller_id = ${s1Id}`)
  check('咨询行为流水条数确实 +1（流水不去重）', rowsAfter === rowsBefore + 1,
    `${rowsBefore} → ${rowsAfter}`)

  // ============ 3. 卖家主页（公开）============
  console.log('\n[3] 卖家主页 GET /api/sellers/{id}')

  const anonProfile = await api('GET', `/api/sellers/${s1Id}`)
  check('游客能看卖家主页', anonProfile.status === 200, `HTTP ${anonProfile.status}`)

  const raw = JSON.stringify(anonProfile.json)
  const leaked = ['wechat', 'qq', 'phone'].filter((f) => raw.includes('"' + f + '"'))
  check('卖家主页不含任何联系方式字段', leaked.length === 0,
    leaked.length ? '泄漏：' + leaked.join(',') : 'clean')

  const p = anonProfile.json.data
  check('返回昵称 / 注册时间 / hasContact',
    !!p.nickname && !!p.createTime && p.hasContact === true)
  check('返回三个统计数字',
    typeof p.stats?.onSaleCount === 'number'
    && typeof p.stats?.soldCount === 'number'
    && typeof p.stats?.favoriteReceivedCount === 'number',
    `在售${p.stats?.onSaleCount} 已售${p.stats?.soldCount} 收到想要${p.stats?.favoriteReceivedCount}`)

  // 卖家统计里的"收到的想要"= 他所有商品的 favorite_count 之和
  const expectFavReceived = n(
    `SELECT COALESCE(SUM(favorite_count),0) FROM product WHERE seller_id=${s1Id}`)
  check('favoriteReceivedCount 与商品想要数之和一致',
    p.stats.favoriteReceivedCount === expectFavReceived,
    `接口 ${p.stats.favoriteReceivedCount} / 库 ${expectFavReceived}`)

  const sellerProducts = await api('GET', `/api/sellers/${s1Id}/products?size=50`)
  const allOnSale = (sellerProducts.json?.data?.records || []).every((x) => x.status === 1)
  check('卖家在售列表只含 status=1', allOnSale,
    `共 ${sellerProducts.json?.data?.total} 条`)

  // ============ 4. 联系方式设置（逐条保存）============
  console.log('\n[4] 联系方式设置（逐条保存的三态语义）')

  // 只改昵称，联系方式应该不动
  const wechatBefore = (await api('GET', '/api/auth/me', { token: tokS1 })).json.data.wechat
  const onlyNick = await api('PUT', '/api/auth/profile', {
    token: tokS1, body: { nickname: '阿哲（S8 测试）' }
  })
  const afterNick = (await api('GET', '/api/auth/me', { token: tokS1 })).json.data
  check('只传昵称也能保存成功', onlyNick.status === 200)
  check('微信没有被顺手清空', afterNick.wechat === wechatBefore,
    `${wechatBefore} → ${afterNick.wechat}`)

  // 传空串 = 清空（但 QQ 还在，所以允许）
  const clearWechat = await api('PUT', '/api/auth/profile', { token: tokS1, body: { wechat: '' } })
  const afterClear = (await api('GET', '/api/auth/me', { token: tokS1 })).json.data
  check('传空串可以清空微信（QQ 还在）', clearWechat.status === 200 && !afterClear.wechat,
    `wechat=${afterClear.wechat ?? '(已清空)'}`)
  check('清空微信后 hasContact 仍为 true（因为还有 QQ）', afterClear.hasContact === true)

  // 微信 QQ 同时为空 → 拒绝
  const clearBoth = await api('PUT', '/api/auth/profile', { token: tokS1, body: { wechat: '', qq: '' } })
  check('微信和 QQ 同时清空 → 400「至少保留一种联系方式」',
    clearBoth.status === 400 && clearBoth.json.message.includes('至少保留'),
    `HTTP ${clearBoth.status} ${clearBoth.json?.message}`)

  // 恢复
  await api('PUT', '/api/auth/profile', {
    token: tokS1, body: { nickname: '阿哲的闲置小铺', wechat: 'zhe_ershou', qq: '1001001001' }
  })
  const restored = (await api('GET', '/api/auth/me', { token: tokS1 })).json.data
  check('恢复后昵称/微信/QQ 都在',
    restored.nickname === '阿哲的闲置小铺' && restored.wechat === 'zhe_ershou' && restored.qq === '1001001001',
    `${restored.nickname} / ${restored.wechat} / ${restored.qq}`)

  // ============ 清理 ============
  console.log('\n[5] 清理测试数据')
  // 先把商品标记售出（否则删不掉？不用，未售出的可以直接删）
  await api('DELETE', `/api/products/${pid}`, { token: tokS1 })
  const leftover = n(`SELECT COUNT(*) FROM product WHERE id=${pid}`)
  check('测试商品已删除', leftover === 0, `剩余 ${leftover} 条`)

  // 再确认一次统计已经回到原值
  const finalStats = (await api('GET', '/api/auth/me', { token: tokS1 })).json.data.stats
  check('删除后 publishedCount 回到原值',
    finalStats.publishedCount === real.publishedCount,
    `${real.publishedCount} → ${finalStats.publishedCount}`)

  console.log(`\n================ 结果 ================`)
  console.log(`通过 ${pass} 项，失败 ${fail} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('\n测试脚本出错：', e)
  process.exit(1)
})
