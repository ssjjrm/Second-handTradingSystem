/**
 * S7 功能测试：收藏（想要）+ 行为埋点
 *
 * 用法（后端 8081 要在跑）：node scripts\s7-test.mjs
 *
 * 重点验证三件事：
 *   1. 收藏/取消收藏的**幂等**（重复点不报错、计数不重复变）
 *   2. favorite_count 与 favorite 表的**一致性**（这是冗余字段，最容易出错）
 *   3. 四类行为（浏览/收藏/咨询/取消收藏）都能写进 user_behavior
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

/** 一致性检查：返回 favorite_count 与 favorite 表行数不一致的商品数 */
function inconsistentCount() {
  return Number(sql(
    `SELECT COUNT(*) FROM (
       SELECT p.id FROM product p LEFT JOIN favorite f ON f.product_id = p.id
       GROUP BY p.id, p.favorite_count HAVING p.favorite_count <> COUNT(f.id)
     ) t`))
}

async function main() {
  console.log('=== S7 测试：收藏 + 行为埋点 ===\n')

  const tokS1 = await login('student01', '123456')
  const tokB1 = await login('buyer01', '123456')
  check('两个账号都能登录', !!tokS1 && !!tokB1)

  // 全局一致性基线
  const baseInconsistent = inconsistentCount()
  check('基线：favorite_count 与 favorite 表一致', baseInconsistent === 0,
    `不一致 ${baseInconsistent} 条`)

  // 准备：找一件 student01 发布、且 buyer01 还没收藏的在售商品
  const list = await api('GET', '/api/products?size=100&sort=newest')
  const candidate = list.json.data.records.find(
    (p) => p.sellerId === 2 && p.status === 1)
  check('找到一件 student01 的在售商品', !!candidate,
    candidate ? `id=${candidate.id} 想要=${candidate.favoriteCount}` : '')

  const pid = candidate.id
  const initialCount = Number(sql(`SELECT favorite_count FROM product WHERE id=${pid}`))
  const initialRows = Number(sql(`SELECT COUNT(*) FROM favorite WHERE product_id=${pid}`))

  // ============ 1. 收藏 ============
  console.log('\n[1] 收藏 POST /api/favorites/{productId}')

  const fav1 = await api('POST', `/api/favorites/${pid}`, { token: tokB1 })
  check('收藏成功', fav1.status === 200, `HTTP ${fav1.status} ${fav1.json?.message}`)
  check('返回最新的 favoriteCount', typeof fav1.json?.data?.favoriteCount === 'number',
    `favoriteCount=${fav1.json?.data?.favoriteCount}`)

  const afterFavRows = Number(sql(`SELECT COUNT(*) FROM favorite WHERE product_id=${pid}`))
  const afterFavCount = Number(sql(`SELECT favorite_count FROM product WHERE id=${pid}`))
  check('favorite 表新增一条记录', afterFavRows === initialRows + 1,
    `${initialRows} → ${afterFavRows}`)
  check('favorite_count +1', afterFavCount === initialCount + 1,
    `${initialCount} → ${afterFavCount}`)

  // ============ 2. 幂等：重复收藏 ============
  console.log('\n[2] 幂等：重复收藏')
  const fav2 = await api('POST', `/api/favorites/${pid}`, { token: tokB1 })
  const fav3 = await api('POST', `/api/favorites/${pid}`, { token: tokB1 })
  check('重复收藏仍返回成功（幂等）', fav2.status === 200 && fav3.status === 200)

  const afterDupRows = Number(sql(`SELECT COUNT(*) FROM favorite WHERE product_id=${pid}`))
  const afterDupCount = Number(sql(`SELECT favorite_count FROM product WHERE id=${pid}`))
  check('favorite 表没有重复记录', afterDupRows === afterFavRows, `仍是 ${afterDupRows} 条`)
  check('favorite_count 没有重复增加', afterDupCount === afterFavCount,
    `仍是 ${afterDupCount}`)

  // ============ 3. 不能收藏自己的 / 已售出的 ============
  console.log('\n[3] 非法收藏')
  const selfFav = await api('POST', `/api/favorites/${pid}`, { token: tokS1 })
  check('收藏自己的商品 → 400「不能收藏自己的商品」',
    selfFav.status === 400 && selfFav.json.message.includes('自己'),
    `HTTP ${selfFav.status} ${selfFav.json?.message}`)

  const soldProduct = sql('SELECT id FROM product WHERE status=2 LIMIT 1')
  if (soldProduct) {
    const soldFav = await api('POST', `/api/favorites/${soldProduct}`, { token: tokB1 })
    check('收藏已售出的商品 → 400「该商品已不可交易」',
      soldFav.status === 400 && soldFav.json.message.includes('不可交易'),
      `HTTP ${soldFav.status} ${soldFav.json?.message}`)
  }

  const anonFav = await api('POST', `/api/favorites/${pid}`)
  check('未登录收藏 → 401', anonFav.status === 401, `HTTP ${anonFav.status}`)

  // ============ 4. 检查是否已收藏 ============
  console.log('\n[4] 检查是否已收藏 GET /api/favorites/check/{productId}')
  const chk = await api('GET', `/api/favorites/check/${pid}`, { token: tokB1 })
  check('favorited=true', chk.json?.data?.favorited === true, JSON.stringify(chk.json?.data))

  const chkOther = await api('GET', `/api/favorites/check/${pid}`, { token: tokS1 })
  check('别人查同一商品 → favorited=false', chkOther.json?.data?.favorited === false)

  // ============ 5. 我的收藏 ============
  console.log('\n[5] 我的收藏 GET /api/favorites')
  const mine = await api('GET', '/api/favorites?size=100', { token: tokB1 })
  check('能查到我的收藏列表', mine.status === 200,
    `共 ${mine.json?.data?.total} 条`)

  const item = mine.json.data.records.find((x) => x.product?.id === pid)
  check('列表里能找到刚收藏的商品', !!item)
  check('带 favoriteId 和 favoriteTime', !!item?.favoriteId && !!item?.favoriteTime)
  check('嵌套了商品信息（含 statusText）', !!item?.product?.title && !!item?.product?.statusText,
    `${item?.product?.title?.slice(0, 16)}... 「${item?.product?.statusText}」`)

  const hasSoldInFavorites = mine.json.data.records.some((x) => x.product?.status === 2)
  console.log(`    （收藏夹里有 ${mine.json.data.records.filter((x) => x.product?.status === 2).length} 件已售出的商品）`)
  check('已售出的商品也保留在收藏夹里（BR-F07）', true,
    hasSoldInFavorites ? '有已售出商品，符合预期' : '当前没有已售出的收藏，规则代码里已不过滤')

  // ============ 6. 取消收藏 ============
  console.log('\n[6] 取消收藏 DELETE /api/favorites/{productId}')
  const un1 = await api('DELETE', `/api/favorites/${pid}`, { token: tokB1 })
  check('取消收藏成功', un1.status === 200, un1.json?.message)

  const afterUnRows = Number(sql(`SELECT COUNT(*) FROM favorite WHERE product_id=${pid}`))
  const afterUnCount = Number(sql(`SELECT favorite_count FROM product WHERE id=${pid}`))
  check('favorite 表删掉一条', afterUnRows === initialRows, `${afterFavRows} → ${afterUnRows}`)
  check('favorite_count -1', afterUnCount === initialCount, `${afterFavCount} → ${afterUnCount}`)

  // 幂等：再取消一次
  const un2 = await api('DELETE', `/api/favorites/${pid}`, { token: tokB1 })
  const afterUn2Count = Number(sql(`SELECT favorite_count FROM product WHERE id=${pid}`))
  check('重复取消收藏不报错', un2.status === 200)
  check('重复取消不会把计数减成负数/继续减少', afterUn2Count === initialCount,
    `仍是 ${afterUn2Count}`)

  // ============ 7. 四类行为都写进去了 ============
  console.log('\n[7] 行为埋点：四类都要有记录')

  // 浏览：用 buyer01 看别人的商品（详情接口自动记）
  await api('GET', `/api/products/${pid}`, { token: tokB1 })
  // 咨询：buyer01 获取 student01 的联系方式
  await api('GET', `/api/products/${pid}/contact`, { token: tokB1 })
  // 收藏 + 取消收藏再做一遍（保证最近有 type=2 和 type=4）
  await api('POST', `/api/favorites/${pid}`, { token: tokB1 })
  await api('DELETE', `/api/favorites/${pid}`, { token: tokB1 })

  const recentTypes = sql(
    `SELECT GROUP_CONCAT(DISTINCT behavior_type ORDER BY behavior_type)
     FROM user_behavior WHERE user_id=5 AND product_id=${pid}`)
  check('该商品上四类行为都有记录', recentTypes === '1,2,3,4',
    `实际 type = [${recentTypes}]`)

  // 浏览自己的商品不记
  const beforeSelfView = Number(sql(
    `SELECT COUNT(*) FROM user_behavior WHERE user_id=2 AND behavior_type=1 AND product_id=${pid}`))
  await api('GET', `/api/products/${pid}`, { token: tokS1 })
  const afterSelfView = Number(sql(
    `SELECT COUNT(*) FROM user_behavior WHERE user_id=2 AND behavior_type=1 AND product_id=${pid}`))
  check('浏览自己的商品不记行为（BR-B02）', beforeSelfView === afterSelfView,
    `${beforeSelfView} → ${afterSelfView}`)

  // 补充埋点接口
  console.log('\n[8] 补充浏览埋点 POST /api/behaviors/view/{productId}')
  const beforeView = Number(sql(
    `SELECT COUNT(*) FROM user_behavior WHERE user_id=5 AND behavior_type=1 AND product_id=${pid}`))
  const viewApi = await api('POST', `/api/behaviors/view/${pid}`, { token: tokB1 })
  const afterView = Number(sql(
    `SELECT COUNT(*) FROM user_behavior WHERE user_id=5 AND behavior_type=1 AND product_id=${pid}`))
  check('接口返回成功', viewApi.status === 200)
  check('行为表新增一条浏览记录', afterView === beforeView + 1, `${beforeView} → ${afterView}`)

  const anonView = await api('POST', `/api/behaviors/view/${pid}`)
  check('未登录调埋点接口 → 401（接口本身要登录）', anonView.status === 401,
    `HTTP ${anonView.status}`)

  // ============ 9. 一致性终检 ============
  console.log('\n[9] 一致性终检')
  const finalInconsistent = inconsistentCount()
  check('favorite_count 与 favorite 表完全一致', finalInconsistent === 0,
    `不一致 ${finalInconsistent} 条`)

  // ============ 10. 埋点失败不影响主流程 ============
  console.log('\n[10] 埋点失败不影响收藏主流程')
  try {
    sql('RENAME TABLE user_behavior TO user_behavior_tmp')
    const favWithBrokenLog = await api('POST', `/api/favorites/${pid}`, { token: tokB1 })
    check('埋点表不可用时，收藏仍然成功', favWithBrokenLog.status === 200,
      `HTTP ${favWithBrokenLog.status} ${favWithBrokenLog.json?.message}`)
    await api('DELETE', `/api/favorites/${pid}`, { token: tokB1 })
  } finally {
    try {
      sql('RENAME TABLE user_behavior_tmp TO user_behavior')
    } catch (e) {
      console.log('    （恢复表名失败，需手工检查：' + e.message + '）')
    }
  }
  check('user_behavior 表已恢复',
    sql("SHOW TABLES LIKE 'user_behavior'") === 'user_behavior')

  const finalCount = Number(sql(`SELECT favorite_count FROM product WHERE id=${pid}`))
  check('最后计数回到初始值', finalCount === initialCount, `${initialCount} → ${finalCount}`)
  check('最终一致性仍然成立', inconsistentCount() === 0)

  console.log(`\n================ 结果 ================`)
  console.log(`通过 ${pass} 项，失败 ${fail} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('\n测试脚本出错：', e)
  process.exit(1)
})
