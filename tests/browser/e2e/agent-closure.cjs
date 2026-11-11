// Run against an isolated database and a configured AI provider.
const assert = require('node:assert/strict')
const { randomBytes } = require('node:crypto')
const { execFileSync } = require('node:child_process')

const base = process.env.E2E_BACKEND_URL?.replace(/\/$/, '')
const adminPassword = process.env.E2E_ADMIN_PASSWORD
const studentPassword = randomBytes(12).toString('hex')

async function request(path, { token, body, method = body ? 'POST' : 'GET' } = {}) {
  const response = await fetch(base + '/api' + path, {
    method,
    headers: {
      ...(token && { Authorization: `Bearer ${token}` }),
      ...(body && { 'Content-Type': 'application/json' })
    },
    body: body && JSON.stringify(body),
    signal: AbortSignal.timeout(55000)
  })
  return response.json()
}

async function ok(path, options) {
  const result = await request(path, options)
  assert.equal(result.code, 200, `${path}: ${result.message}`)
  return result.data
}

function post(path, token, body) {
  return ok(path, { token, body, method: 'POST' })
}

function timeAfter(days, hour) {
  const time = new Date(Date.now() + days * 86400000)
  const date = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', dateStyle: 'short' }).format(time)
  return `${date} ${hour}:00:00`
}

async function createStudent(nickname) {
  return post('/auth/register', null, {
    nickname,
    studentNo: 'QA' + randomBytes(5).toString('hex'),
    password: studentPassword,
    captchaId: 'isolated-test',
    captchaCode: 'isolated-test'
  })
}

async function publishAndReview(publisher, admin, title) {
  const draftReply = await post('/ai/agent/ask', publisher, { question: `帮我发布活动：《${title}》` })
  const draft = draftReply.activityDrafts[0]
  assert.ok(draft?.id && draft.state === 'DRAFT', '发布意图应生成活动草稿')
  const path = `/ai/agent/activity-drafts/${draft.id}/confirm`
  const incomplete = await request(path, { token: publisher, body: { title } })
  assert.notEqual(incomplete.code, 200, '缺少活动地点和时间时应拒绝提交')
  assert.equal((await ok(`/ai/agent/activity-drafts/${draft.id}`, { token: publisher })).state, 'DRAFT')

  const form = {
    title, description: '同学自带一本书分享心得。因有校外来访，请审核时核对来访安排。', category: '学习交流', location: '图书馆二楼研讨室',
    startTime: timeAfter(3, '15'), endTime: timeAfter(3, '17'), signupDeadline: timeAfter(2, '18'), maxMembers: 12
  }
  const confirmed = await Promise.all([post(path, publisher, form), post(path, publisher, form)])
  const id = confirmed[0].activityId
  assert.ok(id)
  assert.ok(confirmed.every(result => String(result.activityId) === String(id) && result.state === 'SUBMITTED'))

  const before = await ok(`/admin/audit/activity/${id}`, { token: admin })
  assert.equal(before.auditStatus, 0)
  const tasks = await post('/admin/assistant/ask', admin, { question: '目前有多少待审核活动？' })
  assert.ok(tasks.turn.tasks.some(task => task.key === 'activity' && task.count > 0), '助手应查询实时活动待办')
  const detail = await post('/admin/assistant/ask', admin, {
    sessionId: tasks.sessionId,
    question: `查一下待审核活动编号 ${id}，名称是${title}，我要核对这条活动。`
  })
  const found = detail.turn.records.find(row => String(row.id) === String(id))
  assert.ok(found?.activity?.version, '助手应返回该活动的审核版本')
  const card = await post('/admin/assistant/reviews/prepare', admin, {
    sessionId: detail.sessionId, kind: 'activity', itemId: String(id), version: found.activity.version, decision: 'PASS'
  })
  assert.equal((await ok(`/admin/audit/activity/${id}`, { token: admin })).auditStatus, 0)
  const reviewed = await Promise.all([
    post(`/admin/assistant/reviews/${card.id}/confirm`, admin),
    post(`/admin/assistant/reviews/${card.id}/confirm`, admin)
  ])
  assert.ok(reviewed.every(result => result.auditStatus === 1))
  const refreshed = await ok(`/ai/agent/activity-drafts/${draft.id}`, { token: publisher })
  assert.equal(refreshed.auditStatus, 1)
  const inbox = await ok('/message/list?pageNum=1&pageSize=20', { token: publisher })
  assert.equal(inbox.list.filter(message => message.bizType === 'activity' && String(message.bizId) === String(id)).length, 1)
  console.log('PASS 活动草稿、缺项校验、重复提交、助手核对审核、学生通知')

  const mysql = process.env.E2E_MYSQL_CONTAINER
  if (mysql) {
    const expired = (await post('/ai/agent/ask', publisher, { question: '帮我发布活动：《过期验收活动》' })).activityDrafts[0]
    assert.match(expired.id, /^[0-9a-f-]{36}$/)
    execFileSync('docker', ['exec', mysql, 'sh', '-ec',
      'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; mysql -uroot "$MYSQL_DATABASE" -e "$1"', 'sh',
      `UPDATE agent_activity_draft SET expires_at=DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id='${expired.id}' AND activity_id IS NULL`
    ], { stdio: 'pipe' })
    const rejected = await request(`/ai/agent/activity-drafts/${expired.id}/confirm`, { token: publisher, body: form })
    assert.notEqual(rejected.code, 200)
    const snapshot = await ok(`/ai/agent/activity-drafts/${expired.id}`, { token: publisher })
    assert.equal(snapshot.state, 'EXPIRED')
    assert.equal(snapshot.activityId, null)
    console.log('PASS 过期草稿拒绝提交且未创建活动')
  }
}

async function appoint(buyer, seller, admin, title) {
  const item = await post('/idle', seller, { title, category: '图书教材', description: '九成新，校内当面交接', expectItem: '一本空白笔记本' })
  await ok(`/admin/audit/idle/${item.id}/pass`, { token: admin, method: 'PUT' })
  const search = await post('/ai/agent/ask', buyer, { question: `帮我查找闲置“${title}”` })
  assert.ok(search.idleItems.some(hit => String(hit.id ?? hit.itemId) === String(item.id)), '未定位到指定闲置')
  const proposal = await post('/ai/agent/ask', buyer, {
    sessionId: search.sessionId, question: `帮我预约闲置编号 ${item.id} 的${title}，备注：希望在图书馆门口交接。`
  })
  const card = proposal.idleAppointments.find(value => String(value.itemId) === String(item.id))
  assert.ok(card?.id && card.state === 'PENDING')
  const path = `/ai/agent/idle-appointments/${card.id}/confirm`
  const responses = await Promise.all([post(path, buyer), post(path, buyer)])
  const id = responses[0].appointmentId
  assert.ok(id)
  assert.ok(responses.every(result => String(result.appointmentId) === String(id)))
  const queue = await ok('/idle/appoint/my?role=seller&pageNum=1&pageSize=10', { token: seller })
  assert.equal(queue.list.filter(row => String(row.id) === String(id)).length, 1)
  await ok(`/idle/appoint/${id}/handle`, { token: seller, method: 'PUT', body: { accept: true } })
  const buyerQueue = await ok('/idle/appoint/my?role=buyer&pageNum=1&pageSize=10', { token: buyer })
  assert.equal(buyerQueue.list.find(row => String(row.id) === String(id))?.status, 1)
  console.log('PASS 助手查闲置、预约确认、重复确认、对方接单')
}

async function main() {
  assert.ok(base && adminPassword, '需要 E2E_BACKEND_URL 和 E2E_ADMIN_PASSWORD')
  const [admin, publisher, seller] = await Promise.all([
    post('/admin/auth/login', null, { username: process.env.E2E_ADMIN_USERNAME || 'admin', password: adminPassword }),
    createStudent('活动验收学生'),
    createStudent('闲置验收学生')
  ])
  const suffix = randomBytes(3).toString('hex')
  await publishAndReview(publisher.token, admin.token, `松林阅读会${suffix}`)
  await appoint(publisher.token, seller.token, admin.token, `青松词典${suffix}`)
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
