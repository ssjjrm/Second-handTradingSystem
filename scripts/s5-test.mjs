/**
 * S5 功能测试：图片上传 → 发布 → 审核 → 上架 → 我发布的 → 编辑/下架/删除
 *
 * 用法（后端 8081 要在跑）：node scripts\s5-test.mjs
 *
 * 为什么用 Node 而不是 PowerShell：Windows 命令行会把 JSON 里的双引号吃掉，
 * 传 {"wechat":""} 这种带空串的请求体必然失败，排查半天还以为是后端的问题。
 * Node 直接构造对象，没有这层转义。
 */

const B = 'http://localhost:8081'

/** 1x1 透明 PNG，用来测上传 */
const PNG_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='

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

async function api(method, path, { token, body, raw } = {}) {
  const headers = {}
  if (token) headers.Authorization = 'Bearer ' + token
  if (body !== undefined && !raw) headers['Content-Type'] = 'application/json'

  const res = await fetch(B + path, {
    method,
    headers,
    body: raw ? body : body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await res.text()
  let json = null
  try {
    json = JSON.parse(text)
  } catch {
    /* 非 JSON（比如上传返回的图片二进制） */
  }
  return { status: res.status, json, text }
}

async function login(username, password) {
  const r = await api('POST', '/api/auth/login', { body: { username, password } })
  return r.json?.data?.token
}

async function main() {
  console.log('=== S5 测试：发布 + 审核 ===\n')

  const tokS1 = await login('student01', '123456')
  const tokAdmin = await login('admin', 'admin123')
  const tokB1 = await login('buyer01', '123456')
  check('三个账号都能登录', !!tokS1 && !!tokAdmin && !!tokB1)

  // ============ 1. 图片上传 ============
  console.log('\n[1] 图片上传 POST /api/upload/image')
  const form = new FormData()
  form.append('file', new Blob([Buffer.from(PNG_BASE64, 'base64')], { type: 'image/png' }), 'test.png')
  const up = await fetch(B + '/api/upload/image', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + tokS1 },
    body: form
  })
  const upJson = await up.json()
  check('上传成功', up.status === 200 && upJson.data?.url, upJson.data?.url)

  const imgUrl = upJson.data?.url
  if (imgUrl) {
    const img = await fetch(B + imgUrl)
    const buf = Buffer.from(await img.arrayBuffer())
    check('返回的 URL 浏览器能直接访问到图片',
      img.status === 200 && buf.length > 0, `HTTP ${img.status}，${buf.length} 字节`)
    check('确实是刚才那张 PNG', buf.subarray(0, 4).toString('hex') === '89504e47')
  }

  // 故意传非图片
  const badForm = new FormData()
  badForm.append('file', new Blob([Buffer.from('not an image')], { type: 'text/plain' }), 'a.txt')
  const badUp = await fetch(B + '/api/upload/image', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + tokS1 },
    body: badForm
  })
  check('传文本文件被拒绝', badUp.status === 400, `HTTP ${badUp.status}`)

  // 未登录上传
  const anonForm = new FormData()
  anonForm.append('file', new Blob([Buffer.from(PNG_BASE64, 'base64')], { type: 'image/png' }), 'x.png')
  const anonUp = await fetch(B + '/api/upload/image', { method: 'POST', body: anonForm })
  check('未登录不能上传', anonUp.status === 401, `HTTP ${anonUp.status}`)

  // ============ 2. 发布商品 ============
  console.log('\n[2] 发布商品 POST /api/products')

  // 2.1 一级分类应该被拒
  const withParent = await api('POST', '/api/products', {
    token: tokS1,
    body: {
      title: '测试商品-一级分类', categoryId: 1, conditionLevel: '几乎全新',
      price: 100, tradePlace: '东区食堂门口'
    }
  })
  check('挂一级分类 → 400「请选择具体的二级分类」',
    withParent.status === 400 && withParent.json.message.includes('二级分类'), withParent.json.message)

  // 2.2 正常发布
  //     ⚠️ 分类 id 必须从接口动态取，不硬编码 —— 这正是参考项目犯的错：
  //        它前端写死了 :value="156"，换台机器重新导数据后 id 变了，商品全挂到错分类。
  const tree = await api('GET', '/api/categories')
  const secondLevelId = tree.json?.data?.[0]?.children?.[0]?.id
  check('从接口动态拿到一个二级分类 id', secondLevelId > 0, `id=${secondLevelId}`)

  const pub = await api('POST', '/api/products', {
    token: tokS1,
    body: {
      title: '【九成新】测试用机械键盘 87 键 宿舍自提',
      description: '这是 S5 自动化测试发布的商品，用于验证「发布 → 审核 → 上架」全链路。',
      categoryId: secondLevelId,
      conditionLevel: '轻微使用痕迹',
      price: 199.99,
      originalPrice: 499.0,
      images: imgUrl ? [imgUrl] : [],
      brand: '测试品牌',
      tradePlace: '3号宿舍楼下'
    }
  })
  const newId = pub.json?.data?.id
  check('发布成功并返回 id', pub.status === 200 && newId > 0, `id=${newId}，message="${pub.json?.message}"`)

  // 2.3 数据库里应该是待审
  const detail = await api('GET', `/api/products/${newId}`, { token: tokS1 })
  check('新商品 status=0（待审核）', detail.json?.data?.status === 0,
    `status=${detail.json?.data?.status} (${detail.json?.data?.statusText})`)

  // 2.4 前台列表看不到它（用关键词搜）
  const list = await api('GET', '/api/products?keyword=测试用机械键盘&size=50')
  check('前台列表【看不到】待审商品', list.json?.data?.total === 0, `total=${list.json?.data?.total}`)

  // 2.5 但所有者自己能看到详情
  check('所有者自己能看详情（虽然是待审）', detail.status === 200, `HTTP ${detail.status}`)

  // 2.6 别人看不到
  const otherView = await api('GET', `/api/products/${newId}`, { token: tokB1 })
  check('别人看不了待审商品 → 404', otherView.status === 404, `HTTP ${otherView.status}`)

  // 2.7 未填联系方式的用户发布 → 400
  //     临时清空 buyer02 的联系方式
  const mysql = 'C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe'
  const { execFileSync } = await import('node:child_process')
  const sql = (q) => execFileSync(mysql, ['--no-defaults', '-h127.0.0.1', '-P3306', '-uroot', '-p123456',
    '--default-character-set=utf8mb4', '-e', q, 'secondhand_shop'], { stdio: 'pipe' })

  sql("UPDATE user SET wechat=NULL, qq=NULL WHERE username='buyer02'")
  const tokB2 = await login('buyer02', '123456')
  const noContact = await api('POST', '/api/products', {
    token: tokB2,
    body: {
      title: '没填联系方式的商品测试', categoryId: secondLevelId, conditionLevel: '全新',
      price: 50, tradePlace: '南门快递站旁'
    }
  })
  check('没填联系方式 → 400「请先完善微信号或QQ号」',
    noContact.status === 400 && noContact.json.message.includes('微信号'),
    `HTTP ${noContact.status} ${noContact.json.message}`)
  sql("UPDATE user SET wechat='shoushe_nina', qq='1001001005' WHERE username='buyer02'")

  // ============ 3. 管理后台 ============
  console.log('\n[3] 管理后台审核')

  const forbidden = await api('GET', '/api/admin/products/pending', { token: tokS1 })
  check('普通用户访问 /api/admin/** → 403', forbidden.status === 403, `HTTP ${forbidden.status}`)

  const anonAdmin = await api('GET', '/api/admin/products/pending')
  check('未登录访问 /api/admin/** → 401', anonAdmin.status === 401, `HTTP ${anonAdmin.status}`)

  const pending = await api('GET', '/api/admin/products/pending?size=50', { token: tokAdmin })
  const pendingIds = (pending.json?.data?.records || []).map((x) => x.id)
  check('待审列表能查到刚发布的商品', pendingIds.includes(newId),
    `待审 ${pendingIds.length} 条，包含 ${newId}? ${pendingIds.includes(newId)}`)

  // ============ 4. 我的发布（含 contactCount / needAttention）============
  console.log('\n[4] 我发布的商品 GET /api/products/mine')
  const mine = await api('GET', '/api/products/mine?size=50', { token: tokS1 })
  const mineItem = (mine.json?.data?.records || []).find((x) => x.id === newId)
  check('我的发布里有刚发的商品', !!mineItem, `共 ${mine.json?.data?.total} 条`)
  check('带 contactCount 字段', mineItem && mineItem.contactCount !== undefined,
    `contactCount=${mineItem?.contactCount}`)
  check('带 needAttention 字段', mineItem && mineItem.needAttention !== undefined,
    `needAttention=${mineItem?.needAttention}（待审，应为 false）`)

  // ============ 5. 审核通过 ============
  console.log('\n[5] 审核通过 → 商品上架')
  const approve = await api('PUT', `/api/admin/products/${newId}/approve`, { token: tokAdmin })
  check('审核通过成功', approve.status === 200, approve.json?.message)

  const afterApprove = await api('GET', `/api/products/${newId}`)
  check('审核后 status=1（在售）', afterApprove.json?.data?.status === 1,
    `status=${afterApprove.json?.data?.status}`)

  const listAfter = await api('GET', '/api/products?keyword=测试用机械键盘&size=50')
  check('前台列表现在【能看到】了', listAfter.json?.data?.total === 1,
    `total=${listAfter.json?.data?.total}`)

  const approveAgain = await api('PUT', `/api/admin/products/${newId}/approve`, { token: tokAdmin })
  check('重复审核被拒（已不是在售状态）', approveAgain.status === 400, `HTTP ${approveAgain.status}`)

  // ============ 6. 编辑 ============
  console.log('\n[6] 编辑商品 PUT /api/products/{id}')
  const edit = await api('PUT', `/api/products/${newId}`, {
    token: tokS1,
    body: { price: 188.88, brand: '改过的品牌' }
  })
  check('所有者能编辑', edit.status === 200, edit.json?.message)

  const afterEdit = await api('GET', `/api/products/${newId}`)
  check('价格已更新且状态仍是 1（没退回待审）',
    Number(afterEdit.json?.data?.price) === 188.88 && afterEdit.json?.data?.status === 1,
    `price=${afterEdit.json?.data?.price} status=${afterEdit.json?.data?.status}`)

  const editByOther = await api('PUT', `/api/products/${newId}`, {
    token: tokB1, body: { price: 1 }
  })
  check('别人编辑 → 403「无权操作此商品」', editByOther.status === 403, `HTTP ${editByOther.status}`)

  const editOriginal = await api('PUT', `/api/products/${newId}`, {
    token: tokS1, body: { originalPrice: 10 }
  })
  check('原价改成低于现价 → 400', editOriginal.status === 400, editOriginal.json?.message)

  const editEmpty = await api('PUT', `/api/products/${newId}`, { token: tokS1, body: {} })
  check('空 body 编辑 → 400（不是 500）', editEmpty.status === 400, editEmpty.json?.message)

  // ============ 7. 下架 ============
  console.log('\n[7] 下架 PUT /api/products/{id}/off-shelf')
  const off = await api('PUT', `/api/products/${newId}/off-shelf`, { token: tokS1 })
  check('下架成功', off.status === 200, off.json?.message)

  const afterOff = await api('GET', `/api/products/${newId}`, { token: tokS1 })
  check('下架后 status=3（不是 2）', afterOff.json?.data?.status === 3,
    `status=${afterOff.json?.data?.status}（参考项目这里错用了 2=已售出）`)

  const listAfterOff = await api('GET', '/api/products?keyword=测试用机械键盘&size=50')
  check('下架后前台列表看不到', listAfterOff.json?.data?.total === 0, `total=${listAfterOff.json?.data?.total}`)

  // ============ 8. 删除 ============
  console.log('\n[8] 删除商品 DELETE /api/products/{id}')
  const delByOther = await api('DELETE', `/api/products/${newId}`, { token: tokB1 })
  check('别人删除 → 403', delByOther.status === 403, `HTTP ${delByOther.status}`)

  const del = await api('DELETE', `/api/products/${newId}`, { token: tokS1 })
  check('所有者删除成功', del.status === 200, del.json?.message)

  const afterDel = await api('GET', `/api/products/${newId}`, { token: tokS1 })
  check('删除后查不到 → 404', afterDel.status === 404, `HTTP ${afterDel.status}`)

  // ============ 9. 拒绝路径 ============
  console.log('\n[9] 审核拒绝')
  const pub2 = await api('POST', '/api/products', {
    token: tokS1,
    body: {
      title: '【全新】测试用被拒绝的商品', categoryId: secondLevelId, conditionLevel: '全新',
      price: 66, tradePlace: '东区食堂门口'
    }
  })
  const id2 = pub2.json?.data?.id
  const reject = await api('PUT', `/api/admin/products/${id2}/reject`, {
    token: tokAdmin, body: { reason: '图片与描述不符（这是测试原因）' }
  })
  check('拒绝成功', reject.status === 200, reject.json?.message)

  const afterReject = await api('GET', `/api/products/${id2}`, { token: tokS1 })
  check('拒绝后 status=4', afterReject.json?.data?.status === 4, `status=${afterReject.json?.data?.status}`)

  const rejectAnon = await api('PUT', `/api/admin/products/${id2}/reject`, { body: {} })
  check('未登录不能审核', rejectAnon.status === 401, `HTTP ${rejectAnon.status}`)

  // 清理：删掉测试商品
  await api('DELETE', `/api/products/${id2}`, { token: tokS1 })

  console.log(`\n================ 结果 ================`)
  console.log(`通过 ${pass} 项，失败 ${fail} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('\n测试脚本出错：', e)
  process.exit(1)
})
