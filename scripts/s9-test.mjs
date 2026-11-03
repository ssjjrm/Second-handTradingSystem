/**
 * S9 功能测试：管理后台（6 个接口 + 权限反证）
 *
 * 用法（后端 8081 要在跑）：node scripts\s9-test.mjs
 *
 * 覆盖接口文档 §10.1~10.9：
 *   10.1~10.3 待审/通过/拒绝（S5 已做，这里只做一次回归）
 *   10.4 全部商品   10.5 强制下架
 *   10.6 咨询记录   10.7 用户列表   10.8 禁用启用   10.9 数据统计
 *
 * 本脚本会临时改动数据（发布一件商品、禁用再启用 buyer02），
 * 结束时全部还原 —— 跑完可以直接再跑一遍，结果应该完全一样。
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
  const r = await api('POST', '/api/auth/login', { body: { username: u, password: p } })
  return r.json?.data?.token
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
  console.log('=== S9 测试：管理后台 ===\n')

  const tokAdmin = await login('admin', 'admin123')
  const tokS1 = await login('student01', '123456')
  const tokB1 = await login('buyer01', '123456')
  check('admin / student01 / buyer01 都能登录', !!tokAdmin && !!tokS1 && !!tokB1)

  const s1Id = n("SELECT id FROM user WHERE username='student01'")
  const b2Id = n("SELECT id FROM user WHERE username='buyer02'")
  const adminId = n("SELECT id FROM user WHERE username='admin'")

  // ==================================================================
  console.log('\n[1] 权限反证：401 / 403 / 200 三层')
  // ==================================================================

  const anon = await api('GET', '/api/admin/stats')
  check('游客（不带 token）→ 401', anon.status === 401,
    `HTTP ${anon.status} ${anon.json?.message}`)

  const normal = await api('GET', '/api/admin/stats', { token: tokS1 })
  check('普通用户 → 403', normal.status === 403,
    `HTTP ${normal.status} ${normal.json?.message}`)

  const admin = await api('GET', '/api/admin/stats', { token: tokAdmin })
  check('管理员 → 200', admin.status === 200, `HTTP ${admin.status}`)

  // 每一条后台接口都要过同一道门
  const guardPaths = [
    ['GET', '/api/admin/products/pending'],
    ['GET', '/api/admin/products'],
    ['GET', '/api/admin/behaviors'],
    ['GET', '/api/admin/users'],
    ['GET', '/api/admin/stats']
  ]
  let all403 = true
  for (const [m, p] of guardPaths) {
    const r = await api(m, p, { token: tokB1 })
    if (r.status !== 403) all403 = false
  }
  check('全部 5 条后台列表接口对普通用户都是 403', all403)

  // ==================================================================
  console.log('\n[2] §10.4 全部商品（含所有状态）')
  // ==================================================================

  const allRes = await api('GET', '/api/admin/products?page=1&size=100', { token: tokAdmin })
  check('不传 status 也能查', allRes.status === 200, `HTTP ${allRes.status}`)

  const totalInDb = n('SELECT COUNT(*) FROM product')
  check('total 等于商品表总行数（所有状态都算）',
    allRes.json?.data?.total === totalInDb,
    `接口 ${allRes.json?.data?.total} / 库 ${totalInDb}`)

  // 逐个状态核对
  for (const st of [0, 1, 2, 3, 4]) {
    const r = await api('GET', `/api/admin/products?status=${st}&size=1`, { token: tokAdmin })
    const dbCount = n(`SELECT COUNT(*) FROM product WHERE status=${st}`)
    check(`status=${st} 的 total 与数据库一致`, r.json?.data?.total === dbCount,
      `接口 ${r.json?.data?.total} / 库 ${dbCount}`)
  }

  // 五种状态加起来 = 总数（这条能抓住"漏掉某个状态"的错误）
  const sumByStatus = [0, 1, 2, 3, 4]
    .map((st) => n(`SELECT COUNT(*) FROM product WHERE status=${st}`))
    .reduce((a, b) => a + b, 0)
  check('五个状态之和 = 商品总数', sumByStatus === totalInDb, `${sumByStatus} = ${totalInDb}`)

  // 关键词筛选
  const kw = '索尼'
  const kwRes = await api('GET', `/api/admin/products?keyword=${encodeURIComponent(kw)}&size=100`, { token: tokAdmin })
  const kwDb = n(`SELECT COUNT(*) FROM product
                  WHERE title LIKE '%${kw}%' OR description LIKE '%${kw}%' OR brand LIKE '%${kw}%'`)
  check('keyword 筛选的 total 与数据库 LIKE 一致',
    kwRes.json?.data?.total === kwDb, `接口 ${kwRes.json?.data?.total} / 库 ${kwDb}`)

  const allMatch = (kwRes.json?.data?.records || []).every((p) =>
    (p.title || '').includes(kw) || (p.description || '').includes(kw) || (p.brand || '').includes(kw))
  check('keyword 返回的每一条都真的包含关键词', allMatch)

  // 列表项该有的字段
  const sample = allRes.json?.data?.records?.find((p) => p.status === 1)
  check('列表项带 description 和 seller（审核需要）',
    !!(sample?.description && sample?.seller?.nickname),
    `卖家=${sample?.seller?.nickname}`)
  check('列表项带 statusText（前端不自己维护映射表）',
    !!sample?.statusText, sample?.statusText)

  // ==================================================================
  console.log('\n[3] §10.5 强制下架（管理员能下架别人的商品）')
  // ==================================================================

  const tree = await api('GET', '/api/categories')
  const catId = tree.json.data[0].children[0].id

  const pub = await api('POST', '/api/products', {
    token: tokS1,
    body: {
      title: '【八成新】S9 测试商品 用于验证强制下架', categoryId: catId,
      conditionLevel: '轻微使用痕迹', price: 66, tradePlace: '西区快递站'
    }
  })
  const pid = pub.json?.data?.id
  check('student01 发布了一件测试商品', !!pid, `id=${pid}`)

  // 待审核时不能强制下架（status 不是 1）
  const offPending = await api('PUT', `/api/admin/products/${pid}/off-shelf`, { token: tokAdmin })
  check('待审核商品强制下架 → 400', offPending.status === 400,
    `HTTP ${offPending.status} ${offPending.json?.message}`)

  await api('PUT', `/api/admin/products/${pid}/approve`, { token: tokAdmin })
  check('审核通过后 status=1', n(`SELECT status FROM product WHERE id=${pid}`) === 1)

  // ★ 核心：admin 下架 student01 的商品
  const forceOff = await api('PUT', `/api/admin/products/${pid}/off-shelf`, { token: tokAdmin })
  check('管理员强制下架【别人的】商品 → 200', forceOff.status === 200,
    `HTTP ${forceOff.status}`)
  check('下架后 status=3（已下架，不是 2 已售出）',
    n(`SELECT status FROM product WHERE id=${pid}`) === 3,
    `status=${n(`SELECT status FROM product WHERE id=${pid}`)}`)

  // C 端列表看不到它
  const stillVisible = (await api('GET', `/api/products?keyword=S9 测试商品&size=50`))
    .json?.data?.records?.some((p) => p.id === pid)
  check('下架后 C 端列表搜不到它', stillVisible === false)

  // 重复下架 → 400
  const offAgain = await api('PUT', `/api/admin/products/${pid}/off-shelf`, { token: tokAdmin })
  check('同一件再下架一次 → 400', offAgain.status === 400,
    `HTTP ${offAgain.status} ${offAgain.json?.message}`)

  // 不存在的商品 → 404（不是 400）
  const offMissing = await api('PUT', '/api/admin/products/99999999/off-shelf', { token: tokAdmin })
  check('不存在的商品 → 404', offMissing.status === 404,
    `HTTP ${offMissing.status} ${offMissing.json?.message}`)

  // ==================================================================
  console.log('\n[4] §10.6 咨询记录（替代原「订单管理」）')
  // ==================================================================

  const beh = await api('GET', '/api/admin/behaviors?size=20', { token: tokAdmin })
  check('行为列表能查', beh.status === 200, `HTTP ${beh.status}`)
  const behTotal = n('SELECT COUNT(*) FROM user_behavior')
  check('total 等于 user_behavior 总行数', beh.json?.data?.total === behTotal,
    `接口 ${beh.json?.data?.total} / 库 ${behTotal}`)

  const behRec = beh.json?.data?.records?.[0]
  check('每条都翻译成了人话（昵称 + 标题 + 价格）',
    !!behRec?.userNickname && !!behRec?.productTitle && behRec?.productPrice !== undefined,
    `${behRec?.userNickname} / ${behRec?.productTitle}`)
  check('带 behaviorTypeText 中文', !!behRec?.behaviorTypeText, behRec?.behaviorTypeText)

  // summary 必须和数据库一致
  const summary = beh.json?.data?.summary
  const dbSummary = {
    viewCount: n('SELECT COUNT(*) FROM user_behavior WHERE behavior_type=1'),
    favoriteCount: n('SELECT COUNT(*) FROM user_behavior WHERE behavior_type=2'),
    contactCount: n('SELECT COUNT(*) FROM user_behavior WHERE behavior_type=3'),
    unfavoriteCount: n('SELECT COUNT(*) FROM user_behavior WHERE behavior_type=4')
  }
  check('summary.viewCount 与库一致', summary?.viewCount === dbSummary.viewCount,
    `接口 ${summary?.viewCount} / 库 ${dbSummary.viewCount}`)
  check('summary.favoriteCount 与库一致', summary?.favoriteCount === dbSummary.favoriteCount,
    `接口 ${summary?.favoriteCount} / 库 ${dbSummary.favoriteCount}`)
  check('summary.contactCount 与库一致', summary?.contactCount === dbSummary.contactCount,
    `接口 ${summary?.contactCount} / 库 ${dbSummary.contactCount}`)
  check('summary.unfavoriteCount 与库一致', summary?.unfavoriteCount === dbSummary.unfavoriteCount,
    `接口 ${summary?.unfavoriteCount} / 库 ${dbSummary.unfavoriteCount}`)

  // 按类型筛选
  const onlyContact = await api('GET', '/api/admin/behaviors?behaviorType=3&size=100', { token: tokAdmin })
  const allType3 = (onlyContact.json?.data?.records || []).every((b) => b.behaviorType === 3)
  check('behaviorType=3 筛出来的全是咨询', allType3,
    `共 ${onlyContact.json?.data?.total} 条`)
  check('筛选后的 total 与数据库一致',
    onlyContact.json?.data?.total === dbSummary.contactCount,
    `接口 ${onlyContact.json?.data?.total} / 库 ${dbSummary.contactCount}`)
  check('summary 不受筛选影响（仍是全站总量）',
    onlyContact.json?.data?.summary?.viewCount === dbSummary.viewCount,
    `浏览 ${onlyContact.json?.data?.summary?.viewCount}（筛选后仍应是 ${dbSummary.viewCount}）`)

  // 按用户 / 商品筛选
  const byUser = await api('GET', `/api/admin/behaviors?userId=${b2Id}&size=100`, { token: tokAdmin })
  const userOk = (byUser.json?.data?.records || []).every((b) => b.userId === b2Id)
  check(`userId=${b2Id} 筛出来的都属于该用户`, userOk,
    `共 ${byUser.json?.data?.total} 条`)

  const somePid = n('SELECT product_id FROM user_behavior LIMIT 1')
  const byProduct = await api('GET', `/api/admin/behaviors?productId=${somePid}&size=100`, { token: tokAdmin })
  const productOk = (byProduct.json?.data?.records || []).every((b) => b.productId === somePid)
  check(`productId=${somePid} 筛出来的都属于该商品`, productOk,
    `共 ${byProduct.json?.data?.total} 条`)

  // ==================================================================
  console.log('\n[5] §10.7 用户列表')
  // ==================================================================

  const users = await api('GET', '/api/admin/users?size=100', { token: tokAdmin })
  check('用户列表能查', users.status === 200, `HTTP ${users.status}`)
  const userTotal = n('SELECT COUNT(*) FROM user')
  check('total 等于用户表行数', users.json?.data?.total === userTotal,
    `接口 ${users.json?.data?.total} / 库 ${userTotal}`)

  // ★ 隐私：不能有 password，必须有 wechat/qq
  const rawUsers = JSON.stringify(users.json)
  check('响应里没有任何 password 字段', !rawUsers.includes('"password"'))
  const u1 = users.json?.data?.records?.find((u) => u.username === 'student01')
  check('管理员能看到微信/QQ（处理纠纷要用）', !!u1?.wechat && !!u1?.qq,
    `${u1?.wechat} / ${u1?.qq}`)
  check('带 statusText 中文', u1?.statusText === '正常', u1?.statusText)

  // 三个统计数字 vs 数据库
  check('publishedCount 与库一致',
    u1?.publishedCount === n(`SELECT COUNT(*) FROM product WHERE seller_id=${s1Id}`),
    `接口 ${u1?.publishedCount} / 库 ${n(`SELECT COUNT(*) FROM product WHERE seller_id=${s1Id}`)}`)
  check('soldCount 与库一致',
    u1?.soldCount === n(`SELECT COUNT(*) FROM product WHERE seller_id=${s1Id} AND status=2`),
    `接口 ${u1?.soldCount} / 库 ${n(`SELECT COUNT(*) FROM product WHERE seller_id=${s1Id} AND status=2`)}`)
  check('favoriteCount 与库一致',
    u1?.favoriteCount === n(`SELECT COUNT(*) FROM favorite WHERE user_id=${s1Id}`),
    `接口 ${u1?.favoriteCount} / 库 ${n(`SELECT COUNT(*) FROM favorite WHERE user_id=${s1Id}`)}`)

  // 搜索
  const searchUsers = await api('GET', '/api/admin/users?keyword=student&size=100', { token: tokAdmin })
  const searchDb = n("SELECT COUNT(*) FROM user WHERE username LIKE '%student%' OR nickname LIKE '%student%'")
  check('keyword 搜索的 total 与库一致',
    searchUsers.json?.data?.total === searchDb,
    `接口 ${searchUsers.json?.data?.total} / 库 ${searchDb}`)

  // ==================================================================
  console.log('\n[6] §10.8 禁用 / 启用')
  // ==================================================================

  const tokB2Before = await login('buyer02', '123456')
  check('buyer02 禁用前能登录', !!tokB2Before)

  const disable = await api('PUT', `/api/admin/users/${b2Id}/status?status=0`, { token: tokAdmin })
  check('禁用 buyer02 → 200', disable.status === 200, `HTTP ${disable.status}`)
  check('数据库里 status 变成 0', n(`SELECT status FROM user WHERE id=${b2Id}`) === 0)

  const loginDisabled = await api('POST', '/api/auth/login',
    { body: { username: 'buyer02', password: '123456' } })
  check('禁用后登录 → 403「账号已被禁用」',
    loginDisabled.status === 403 && (loginDisabled.json?.message || '').includes('禁用'),
    `HTTP ${loginDisabled.status} ${loginDisabled.json?.message}`)

  // ★ 旧 token 也要立刻失效（拦截器每次请求查库，不是靠 token 里的角色说了算）
  const oldToken = await api('GET', '/api/auth/me', { token: tokB2Before })
  check('禁用前拿到的旧 token 也立刻失效（401）', oldToken.status === 401,
    `HTTP ${oldToken.status} ${oldToken.json?.message}`)

  // 启用回去
  const enable = await api('PUT', `/api/admin/users/${b2Id}/status?status=1`, { token: tokAdmin })
  check('启用 buyer02 → 200', enable.status === 200)
  const tokB2After = await login('buyer02', '123456')
  check('启用后又可以登录了', !!tokB2After)

  // 管理员不能禁用自己
  const disableSelf = await api('PUT', `/api/admin/users/${adminId}/status?status=0`, { token: tokAdmin })
  check('管理员禁用自己 → 400「不能禁用当前登录账号」',
    disableSelf.status === 400 && (disableSelf.json?.message || '').includes('不能禁用当前登录账号'),
    `HTTP ${disableSelf.status} ${disableSelf.json?.message}`)
  check('管理员状态没被改动', n(`SELECT status FROM user WHERE id=${adminId}`) === 1)

  // 非法 status
  const badStatus = await api('PUT', `/api/admin/users/${b2Id}/status?status=9`, { token: tokAdmin })
  check('status=9 → 400', badStatus.status === 400,
    `HTTP ${badStatus.status} ${badStatus.json?.message}`)

  // 不存在的用户
  const noUser = await api('PUT', '/api/admin/users/99999999/status?status=0', { token: tokAdmin })
  check('不存在的用户 → 404', noUser.status === 404,
    `HTTP ${noUser.status} ${noUser.json?.message}`)

  // 重复禁用是幂等的（不报错）
  await api('PUT', `/api/admin/users/${b2Id}/status?status=1`, { token: tokAdmin })
  const again = await api('PUT', `/api/admin/users/${b2Id}/status?status=1`, { token: tokAdmin })
  check('重复启用是幂等的（不报错）', again.status === 200)

  // ==================================================================
  console.log('\n[7] §10.9 数据统计')
  // ==================================================================

  const stats = (await api('GET', '/api/admin/stats', { token: tokAdmin })).json?.data
  const real = {
    totalUsers: n('SELECT COUNT(*) FROM user'),
    totalProducts: n('SELECT COUNT(*) FROM product'),
    onSaleProducts: n('SELECT COUNT(*) FROM product WHERE status=1'),
    soldProducts: n('SELECT COUNT(*) FROM product WHERE status=2'),
    pendingProducts: n('SELECT COUNT(*) FROM product WHERE status=0'),
    totalFavoriteRecords: n('SELECT COUNT(*) FROM favorite'),
    totalContactRecords: n('SELECT COUNT(*) FROM user_behavior WHERE behavior_type=3'),
    todayNewUsers: n('SELECT COUNT(*) FROM user WHERE DATE(create_time)=CURDATE()'),
    todayNewProducts: n('SELECT COUNT(*) FROM product WHERE DATE(create_time)=CURDATE()'),
    todayContacts: n('SELECT COUNT(*) FROM user_behavior WHERE behavior_type=3 AND DATE(behavior_time)=CURDATE()')
  }

  for (const key of Object.keys(real)) {
    check(`${key} 与数据库一致`, stats?.[key] === real[key],
      `接口 ${stats?.[key]} / 库 ${real[key]}`)
  }

  // 语义检查：没有"成交额"字段（这是刻意的，不是漏做）
  const rawStats = JSON.stringify(stats)
  check('没有"成交额"字段（平台不参与交易，无法得知真实成交价）',
    !rawStats.includes('Amount') && !rawStats.includes('amount'),
    Object.keys(stats || {}).join(', '))

  // 在售 + 已售出 ≤ 总数（剩下的是待审核/已下架/已拒绝）
  check('在售 + 已售出 ≤ 总数（差额是三种后台状态）',
    stats.onSaleProducts + stats.soldProducts <= stats.totalProducts,
    `${stats.onSaleProducts} + ${stats.soldProducts} ≤ ${stats.totalProducts}`)

  // 今日数字通不过不能超过总量
  check('今日新增商品 ≤ 商品总数', stats.todayNewProducts <= stats.totalProducts,
    `${stats.todayNewProducts} ≤ ${stats.totalProducts}`)
  check('今日咨询 ≤ 咨询总数', stats.todayContacts <= stats.totalContactRecords,
    `${stats.todayContacts} ≤ ${stats.totalContactRecords}`)

  // ==================================================================
  console.log('\n[8] 审核开关（§10.10，开发调试用）')
  // ==================================================================

  // ① 默认值
  const swDef = await api('GET', '/api/admin/settings/review', { token: tokAdmin })
  check('默认是「需要审核」（正式模式）', swDef.json?.data?.enabled === true,
    JSON.stringify(swDef.json?.data))
  check('同时返回配置文件里的值（方便提示"重启后会回到哪个值"）',
    swDef.json?.data?.configValue === true && swDef.json?.data?.configKey === 'app.review.enabled')

  // ② 权限：普通用户不能改
  const swForbidden = await api('PUT', '/api/admin/settings/review?enabled=false', { token: tokB1 })
  check('普通用户改开关 → 403（挂在 /api/admin 下面自动受保护）',
    swForbidden.status === 403, `HTTP ${swForbidden.status}`)
  check('普通用户那一次没改成功，开关还是开着',
    (await api('GET', '/api/admin/settings/review', { token: tokAdmin })).json?.data?.enabled === true)

  // ③ 关闭 → 发布即上架
  const swOff = await api('PUT', '/api/admin/settings/review?enabled=false', { token: tokAdmin })
  check('关闭审核 → 200 且 enabled=false',
    swOff.status === 200 && swOff.json?.data?.enabled === false, `HTTP ${swOff.status}`)

  const healthAfterOff = await api('GET', '/api/health')
  check('/api/health 里也能看到 reviewEnabled=false（一个 curl 就能确认当前模式）',
    healthAfterOff.json?.data?.reviewEnabled === false,
    String(healthAfterOff.json?.data?.reviewEnabled))

  const pubNoReview = await api('POST', '/api/products', {
    token: tokS1,
    body: {
      title: '【开关测试】审核关闭时应直接上架', categoryId: catId,
      conditionLevel: '全新', price: 1, tradePlace: '测试用'
    }
  })
  const pidNoReview = pubNoReview.json?.data?.id
  check('审核关闭时发布成功', !!pidNoReview, `id=${pidNoReview}`)
  check('★ 新商品 status=1（直接上架，不是 0 待审核）',
    n(`SELECT status FROM product WHERE id=${pidNoReview}`) === 1,
    `status=${n(`SELECT status FROM product WHERE id=${pidNoReview}`)}`)

  const visAfterOff = (await api('GET',
    '/api/products?keyword=' + encodeURIComponent('开关测试') + '&size=50'))
    .json?.data?.records?.some((p) => p.id === pidNoReview)
  check('发布后立刻能被前台搜到（不用审核）', visAfterOff === true)

  // ④ 打开 → 回到待审核
  const swOn = await api('PUT', '/api/admin/settings/review?enabled=true', { token: tokAdmin })
  check('重新开启审核 → 200 且 enabled=true',
    swOn.status === 200 && swOn.json?.data?.enabled === true)

  const pubWithReview = await api('POST', '/api/products', {
    token: tokS1,
    body: {
      title: '【开关测试】审核开启时应进入待审核', categoryId: catId,
      conditionLevel: '全新', price: 1, tradePlace: '测试用'
    }
  })
  const pidWithReview = pubWithReview.json?.data?.id
  check('★ 开关恢复后，新商品 status=0（回到待审核）',
    n(`SELECT status FROM product WHERE id=${pidWithReview}`) === 0,
    `status=${n(`SELECT status FROM product WHERE id=${pidWithReview}`)}`)

  const visAfterOn = (await api('GET',
    '/api/products?keyword=' + encodeURIComponent('开关测试') + '&size=50'))
    .json?.data?.records?.some((p) => p.id === pidWithReview)
  check('待审核商品前台搜不到（正式流程没被破坏）', visAfterOn === false)

  // ⑤ 开关只影响初始状态，不绕过其他业务规则
  await api('PUT', '/api/admin/settings/review?enabled=false', { token: tokAdmin })
  const badCat = await api('POST', '/api/products', {
    token: tokS1,
    body: {
      title: '【开关测试】一级分类应该还是被拦', categoryId: tree.json.data[0].id,
      conditionLevel: '全新', price: 1, tradePlace: '测试用'
    }
  })
  check('审核关闭时，"必须选二级分类"的规则依然生效 → 400',
    badCat.status === 400, `HTTP ${badCat.status} ${badCat.json?.message}`)
  await api('PUT', '/api/admin/settings/review?enabled=true', { token: tokAdmin })

  // ⑥ 清场
  await api('DELETE', `/api/products/${pidNoReview}`, { token: tokS1 })
  await api('DELETE', `/api/products/${pidWithReview}`, { token: tokS1 })
  check('两件开关测试商品都已删除',
    n(`SELECT COUNT(*) FROM product WHERE id IN (${pidNoReview},${pidWithReview})`) === 0)
  check('开关已恢复为「需要审核」',
    (await api('GET', '/api/admin/settings/review', { token: tokAdmin })).json?.data?.enabled === true)

  // ==================================================================
  console.log('\n[9] 清理测试数据')
  // ==================================================================

  // 测试商品现在是 status=3（已下架），卖家自己删得掉（已售出才不让删）
  await api('DELETE', `/api/products/${pid}`, { token: tokS1 })
  check('测试商品已删除', n(`SELECT COUNT(*) FROM product WHERE id=${pid}`) === 0)

  check('buyer02 状态已还原为 1', n(`SELECT status FROM user WHERE id=${b2Id}`) === 1)
  check('管理员状态是 1', n(`SELECT status FROM user WHERE id=${adminId}`) === 1)

  console.log(`\n================ 结果 ================`)
  console.log(`通过 ${pass} 项，失败 ${fail} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('\n测试脚本出错：', e)
  process.exit(1)
})
