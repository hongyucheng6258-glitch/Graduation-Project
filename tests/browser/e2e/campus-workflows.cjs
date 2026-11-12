// Use an isolated database. SQL fixtures advance clocks without changing application settings.
const assert = require('node:assert/strict')
const { randomBytes } = require('node:crypto')
const { execFileSync } = require('node:child_process')
const { setTimeout: delay } = require('node:timers/promises')
const { resolveEndpoints } = require('./config.cjs')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')

const { BACKEND, STUDENT_WEB } = resolveEndpoints()
const adminPassword = process.env.E2E_ADMIN_PASSWORD
const mysql = process.env.E2E_MYSQL_CONTAINER
const suffix = randomBytes(5).toString('hex')
const password = randomBytes(12).toString('hex')
const campusTime = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai', dateStyle: 'short', timeStyle: 'medium'
})

function timeAfter(minutes) {
  return campusTime.format(new Date(Date.now() + minutes * 60_000)).replace(' ', 'T')
}

function fixture(sql) {
  execFileSync('docker', ['exec', '-i', mysql, 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"'], { input: sql })
}

async function request(path, token, body, method = body ? 'POST' : 'GET') {
  const response = await fetch(BACKEND + '/api' + path, {
    method, signal: AbortSignal.timeout(15_000),
    headers: { ...(token && { Authorization: 'Bearer ' + token }), ...(body && { 'Content-Type': 'application/json' }) },
    body: body && JSON.stringify(body)
  })
  return response.json()
}

async function ok(path, token, body, method) {
  const result = await request(path, token, body, method)
  assert.equal(result.code, 200, `${path}: ${result.message}`)
  return result.data
}

function register(role) {
  return ok('/auth/register', null, { studentNo: `WF${role}${suffix}`, nickname: `流程验收${role}`,
    password, captchaId: 'qa', captchaCode: 'qa' })
}

async function contextFor(browser, auth) {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
  await context.addInitScript(({ token, userInfo }) => {
    localStorage.setItem('token', token)
    localStorage.setItem('userInfo', JSON.stringify(userInfo))
  }, auth)
  return context
}

function button(page, name) {
  return page.getByRole('button', { name, exact: true })
}

async function pageFor(context, path) {
  const page = await context.newPage()
  await page.goto(STUDENT_WEB + path)
  return page
}

async function until(read, accept, seconds = 70) {
  for (let attempt = 0; attempt < seconds; attempt++) {
    const value = await read()
    if (accept(value)) return value
    await delay(1000)
  }
  assert.fail('等候定时任务超时')
}

async function waitlist(owner, first, second, enrolled, admin, ownerContext, firstContext, secondContext, enrolledContext) {
  const title = '候补验收读书会' + suffix
  const activity = await ok('/activity', owner.token, {
    title, description: '图书交流', category: '学习', location: '图书馆',
    startTime: timeAfter(1440).replace('T', ' '), endTime: timeAfter(1500).replace('T', ' '), maxMembers: 1
  })
  const id = activity.id
  assert.ok(Number.isSafeInteger(id))
  if (activity.auditStatus !== 1) await ok(`/admin/audit/activity/${id}/pass`, admin.token, null, 'PUT')
  const path = '/activity/detail/' + id
  const api = '/activity/' + id
  await ok(api + '/signup', enrolled.token, { remark: '原始名额' })
  const initial = (await ok(api + '/members', owner.token)).list[0]
  await ok('/activity/member/' + initial.id + '/handle', owner.token, { approve: true }, 'PUT')

  const firstPage = await pageFor(firstContext, path)
  const secondPage = await pageFor(secondContext, path)
  await button(firstPage, '加入候补').click()
  await firstPage.getByText('候补中', { exact: true }).waitFor()
  await button(secondPage, '加入候补').click()
  await secondPage.getByText('候补中', { exact: true }).waitFor()
  const record = (await ok(api + '/waitlist', first.token)).entry
  assert.equal((await ok(api + '/waitlist', second.token)).entry.state, 'WAITING')
  const listing = await pageFor(secondContext, '/activity/my-signup?tab=waitlist')
  await listing.getByRole('link', { name: title }).waitFor()
  await listing.close()

  const enrolledPage = await pageFor(enrolledContext, path)
  await button(enrolledPage, '取消报名').click()
  await enrolledPage.getByRole('dialog').getByRole('button', { name: /确定/ }).click()
  await until(() => ok(api + '/waitlist', first.token), data => data.entry?.state === 'OFFERED', 10)
  assert.equal((await ok(api + '/waitlist', second.token)).entry.state, 'WAITING')
  assert.notEqual((await request(api + '/signup', enrolled.token, {})).code, 200)
  assert.notEqual((await request(api + '/waitlist/' + record.id + '/accept', second.token, {})).code, 200)
  await button(firstPage, '刷新候补状态').click()
  await button(firstPage, '确认递补').waitFor()

  fixture(`UPDATE activity_waitlist SET offer_until=UTC_TIMESTAMP()+INTERVAL 8 HOUR-INTERVAL 1 MINUTE WHERE id=${Number(record.id)}`)
  assert.notEqual((await request(api + '/waitlist/' + record.id + '/accept', first.token, {})).code, 200)
  await until(() => ok(api + '/waitlist', second.token), data => data.entry?.state === 'OFFERED', 50)
  assert.equal((await ok(api + '/waitlist', first.token)).entry.state, 'EXPIRED')
  await firstPage.reload()
  await button(firstPage, '加入候补').click()
  await button(firstPage, '退出候补').click()
  await firstPage.getByText('已退出候补', { exact: true }).waitFor()
  assert.equal((await ok(api + '/waitlist', second.token)).entry.state, 'OFFERED')
  await button(secondPage, '刷新候补状态').click()
  await button(secondPage, '确认递补').click()
  await button(secondPage, '提交递补报名').click()
  await secondPage.getByText('报名待审批', { exact: true }).waitFor()
  const waiting = (await ok(api + '/waitlist', second.token)).entry
  await ok(api + '/waitlist/' + waiting.id + '/accept', second.token, {})
  assert.equal((await ok(api + '/members', owner.token)).list.length, 1)

  const ownerPage = await pageFor(ownerContext, path)
  await button(ownerPage, '报名名单管理').click()
  const dialog = ownerPage.getByRole('dialog', { name: '报名名单' })
  await dialog.getByRole('button', { name: /通过|同意/ }).click()
  await until(() => ok(api + '/waitlist', second.token), data => data.entry?.state === 'APPROVED', 10)
  await secondPage.reload()
  await secondPage.getByText(/已通过报名/).first().waitFor()
  const invites = (await ok('/message/list?pageSize=30', second.token)).list
    .filter(message => message.title === '活动递补邀请' && message.bizId === id)
  assert.equal(invites.length, 1)
  for (const page of [firstPage, secondPage, enrolledPage, ownerPage]) await page.close()
  console.log('PASS 浏览器候补加入、候补记录、保留名额、超时顺延、限时确认和发布者审批')
}

async function handoff(seller, buyer, other, admin, sellerContext, buyerContext) {
  const title = '交接验收计算器' + suffix
  const item = await ok('/idle', seller.token, { title, description: '函数计算器', expectItem: '交换笔记本', category: '学习用品' })
  if (item.auditStatus !== 1) await ok(`/admin/audit/idle/${item.id}/pass`, admin.token, null, 'PUT')
  const itemPage = await pageFor(buyerContext, '/idle/detail/' + item.id)
  await button(itemPage, '发起预约互换').click()
  await button(itemPage, '确认预约').click()
  await itemPage.getByRole('link', { name: '查看我的预约' }).waitFor()
  const reservation = (await ok('/idle/appoint/my?role=buyer', buyer.token)).list.find(row => row.itemId === item.id)
  assert.ok(reservation && Number.isSafeInteger(reservation.id))
  const api = '/idle/appoint/' + reservation.id
  assert.equal((await request(api + '/handoff', other.token)).code, 403)
  assert.deepEqual((await ok('/idle/appoint/my?role=other&appointmentId=' + reservation.id, other.token)).list, [])
  const buyerPage = await pageFor(buyerContext, '/idle/appointments?role=buyer&appointmentId=' + reservation.id)
  const sellerPage = await pageFor(sellerContext, '/idle/appointments?role=seller&appointmentId=' + reservation.id)
  await button(sellerPage, '接受').click()
  await button(sellerPage, '交接安排').waitFor()
  await buyerPage.reload()
  await button(buyerPage, '提出交接安排').click()
  await buyerPage.getByLabel('交接时间（北京时间）').fill(timeAfter(35).slice(0, 16))
  await buyerPage.getByLabel('交接地点').fill('图书馆一楼服务台')
  await button(buyerPage, '发送交接安排').click()
  await buyerPage.getByText('等待对方确认安排').waitFor()
  assert.notEqual((await request(api + '/finish', buyer.token, null, 'PUT')).code, 200)

  const messages = await pageFor(sellerContext, '/message')
  await messages.getByText('待确认交接安排', { exact: true }).click()
  await messages.waitForURL('**/idle/appointments?role=seller&appointmentId=' + reservation.id)
  await button(messages, '提出改约').click()
  await messages.getByLabel('交接时间（北京时间）').fill(timeAfter(45).slice(0, 16))
  await messages.getByLabel('交接地点').fill('教学楼一楼大厅')
  await button(messages, '发送交接安排').click()
  await messages.getByText('等待对方确认安排').waitFor()
  assert.notEqual((await request(api + '/handoff/confirm', buyer.token, {version: 1}, 'PUT')).code, 200)
  await button(buyerPage, '刷新交接状态').click()
  await button(buyerPage, '同意交接安排').click()
  await buyerPage.getByText('交接安排已确认', { exact: true }).waitFor()
  assert.notEqual((await request(api + '/finish', buyer.token, null, 'PUT')).code, 200)
  assert.notEqual((await request(api + '/review', buyer.token, {score: 5})).code, 200)

  async function reminders(person) {
    return (await ok('/message/list?pageSize=40', person.token)).list
      .filter(message => message.title === '闲置交接即将开始' && message.bizId === reservation.id)
  }
  await until(() => reminders(buyer), rows => rows.length === 1)
  assert.equal((await reminders(seller)).length, 1)
  const older = (await ok(api + '/handoff', buyer.token)).version
  fixture(`UPDATE idle_handoff SET meeting_at=UTC_TIMESTAMP()+INTERVAL 8 HOUR-INTERVAL 1 MINUTE WHERE appointment_id=${Number(reservation.id)} AND version=${Number(older)}`)
  const finish = async page => {
    await button(page, '确认已交接').click()
    await page.getByRole('dialog', { name: '确认交接' }).getByRole('button', { name: /确定/ }).click()
  }
  await finish(buyerPage)
  await buyerPage.getByText('你已确认交接，等待对方确认', { exact: true }).waitFor()
  assert.notEqual((await request(api + '/review', buyer.token, {score: 5})).code, 200)
  assert.notEqual((await request(api + '/handoff', seller.token, { version: older, meetingAt: timeAfter(50), location: '改约' }, 'PUT')).code, 200)
  await messages.reload()
  await finish(messages)
  await button(messages, '评价').click()
  await button(messages, '提交评价').click()
  await messages.getByText('已评价', { exact: true }).waitFor()
  assert.equal((await ok('/idle/' + item.id, buyer.token)).status, 2)
  assert.notEqual((await request(api + '/finish', seller.token, null, 'PUT')).code, 200)
  assert.equal((await reminders(buyer)).length, 1)
  assert.equal((await reminders(seller)).length, 1)
  const legacyItem = await ok('/idle', seller.token, {
    title: '旧预约兼容' + suffix, description: '保留旧预约状态', expectItem: '笔记本', category: '学习用品'
  })
  if (legacyItem.auditStatus !== 1) await ok(`/admin/audit/idle/${legacyItem.id}/pass`, admin.token, null, 'PUT')
  const legacy = await ok('/idle/' + legacyItem.id + '/appoint', buyer.token, {})
  const legacyPath = '/idle/appoint/' + legacy.id
  await ok(legacyPath + '/handle', seller.token, {accept: true}, 'PUT')
  assert.ok(Number.isSafeInteger(legacy.id))
  fixture(`DELETE FROM idle_handoff WHERE appointment_id=${Number(legacy.id)}`)
  const legacyPage = await pageFor(buyerContext, '/idle/appointments?role=buyer&appointmentId=' + legacy.id)
  await button(legacyPage, '确认完成').click()
  await legacyPage.getByRole('dialog', { name: '确认交接' }).getByRole('button', { name: /确定/ }).click()
  await button(legacyPage, '评价').waitFor()
  for (const page of [itemPage, buyerPage, sellerPage, messages, legacyPage]) await page.close()
  console.log('PASS 浏览器预约、改约、通知跳转、双方提醒、双向完成、评价和权限')
}

async function main() {
  assert.ok(adminPassword && mysql, '需要隔离库 E2E_MYSQL_CONTAINER 和 E2E_ADMIN_PASSWORD')
  const [owner, first, second, enrolled, admin] = await Promise.all([
    register('Owner'), register('First'), register('Second'), register('Enrolled'),
    ok('/admin/auth/login', null, { username: process.env.E2E_ADMIN_USERNAME || 'admin', password: adminPassword })
  ])
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
  try {
    const [ownerContext, firstContext, secondContext, enrolledContext] = await Promise.all(
      [owner, first, second, enrolled].map(auth => contextFor(browser, auth)))
    await waitlist(owner, first, second, enrolled, admin, ownerContext, firstContext, secondContext, enrolledContext)
    await handoff(owner, first, second, admin, ownerContext, firstContext)
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
