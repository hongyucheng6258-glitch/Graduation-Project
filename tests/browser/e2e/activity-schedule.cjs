// Run against an isolated Compose project with the campus Agent enabled.
const assert = require('node:assert/strict')
const { randomBytes } = require('node:crypto')
const { execFileSync } = require('node:child_process')
const { setTimeout: delay } = require('node:timers/promises')
const { resolveEndpoints } = require('./config.cjs')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')

const { BACKEND, STUDENT_WEB } = resolveEndpoints()
const suffix = randomBytes(5).toString('hex')
const password = randomBytes(12).toString('hex')
const campusTime = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai', dateStyle: 'short', timeStyle: 'medium'
})

function timeAfter(minutes) {
  return campusTime.format(new Date(Date.now() + minutes * 60_000))
}

async function request(path, { token, body, method = body ? 'POST' : 'GET' } = {}) {
  const response = await fetch(BACKEND + '/api' + path, {
    method,
    headers: {
      ...(token && { Authorization: `Bearer ${token}` }),
      ...(body && { 'Content-Type': 'application/json' })
    },
    body: body && JSON.stringify(body),
    signal: AbortSignal.timeout(55_000)
  })
  return response.json()
}

async function ok(path, options) {
  const result = await request(path, options)
  assert.equal(result.code, 200, `${path}: ${result.message}`)
  return result.data
}

function student(role) {
  return ok('/auth/register', { body: {
    nickname: role, studentNo: `S${role}${suffix}`, password, captchaId: 'qa', captchaCode: 'qa'
  } })
}

async function publish(owner, admin, name, startTime, endTime) {
  const title = `日程验收${name}${suffix}`
  const activity = await ok('/activity', { token: owner, body: {
    title, description: '校园日程验收', location: '图书馆', category: '学习', startTime, endTime
  } })
  if (activity.auditStatus !== 1) {
    await ok(`/admin/audit/activity/${activity.id}/pass`, { token: admin, method: 'PUT' })
  }
  return activity
}

async function signup(owner, participant, activity, approve) {
  await ok(`/activity/${activity.id}/signup`, { token: participant, body: { remark: '校园日程验收' } })
  if (!approve) return
  const { list } = await ok(`/activity/${activity.id}/members`, { token: owner })
  await ok(`/activity/member/${list[0].id}/handle`, { token: owner, method: 'PUT', body: { approve: true } })
}

async function main() {
  const mysql = process.env.E2E_MYSQL_CONTAINER
  const adminPassword = process.env.E2E_ADMIN_PASSWORD
  assert.ok(mysql && adminPassword, '请设置 E2E_MYSQL_CONTAINER 与 E2E_ADMIN_PASSWORD，使用隔离数据库')
  const [owner, buyer, other, admin] = await Promise.all([
    student('Owner'), student('Buyer'), student('Other'),
    ok('/admin/auth/login', { body: { username: process.env.E2E_ADMIN_USERNAME || 'admin', password: adminPassword } })
  ])
  const baseTime = Date.now() + 2 * 24 * 60 * 60_000
  const activityTime = minutes => campusTime.format(new Date(baseTime + minutes * 60_000))
  const sharedTimes = [activityTime(0), activityTime(60)]
  const enrolled = await publish(owner.token, admin.token, '已通过', ...sharedTimes)
  const pending = await publish(owner.token, admin.token, '待审批', ...sharedTimes)
  const target = await publish(owner.token, admin.token, '半小时冲突', activityTime(30), activityTime(90))
  const adjacent = await publish(owner.token, admin.token, '首尾相接', activityTime(60), activityTime(90))
  const undated = await publish(owner.token, admin.token, '时间待定', null, null)
  await signup(owner.token, buyer.token, enrolled, true)
  await signup(owner.token, buyer.token, pending, false)
  await signup(owner.token, buyer.token, undated, false)
  await ok('/wrong-question', { token: buyer.token, body: { subject: '数学', question: '求 1+1', answer: '2' } })

  const schedulePath = '/activity/my/schedule'
  const conflictPath = activity => `/activity/${activity.id}/signup-conflicts`
  assert.equal((await request(schedulePath)).code, 401)
  assert.equal((await request(conflictPath(target))).code, 401)
  assert.deepEqual((await ok(schedulePath, { token: other.token })).entries, [])
  const schedule = await ok(schedulePath, { token: buyer.token })
  assert.deepEqual(schedule.entries.map(entry => entry.activityId), [enrolled.id, pending.id, undated.id])
  assert.deepEqual(schedule.entries.map(entry => entry.signupStatus), [1, 0, 0])
  const overlap = await ok(conflictPath(target), { token: buyer.token })
  assert.deepEqual(overlap.conflicts.map(item => [item.activityId, item.overlapSeconds]), [[enrolled.id, 1800]])
  assert.deepEqual(await ok(conflictPath(adjacent), { token: buyer.token }), { timeKnown: true, conflicts: [] })
  assert.deepEqual(await ok(conflictPath(undated), { token: buyer.token }), { timeKnown: false, conflicts: [] })
  assert.deepEqual((await ok(conflictPath(target), { token: other.token })).conflicts, [])

  const canceled = await publish(owner.token, admin.token, '已取消', ...sharedTimes)
  const offline = await publish(owner.token, admin.token, '已下架', ...sharedTimes)
  await signup(owner.token, buyer.token, canceled, true)
  await signup(owner.token, buyer.token, offline, true)
  await ok(`/activity/${canceled.id}/signup`, { token: buyer.token, method: 'DELETE' })
  console.log('PASS 日程权限、待审批、待定、半小时重叠与首尾相接')

  const browser = await chromium.launch({
    ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE && { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE }),
    headless: true
  })
  let reminders
  try {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    await context.addInitScript(({ token, userInfo }) => {
      localStorage.setItem('token', token)
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
    }, buyer)
    const page = await context.newPage()
    await page.goto(`${STUDENT_WEB}/activity/my-signup`)
    await page.getByRole('tab', { name: '校园日程' }).click()
    await page.getByRole('link', { name: enrolled.title }).waitFor()
    await page.getByRole('link', { name: pending.title }).waitFor()
    await page.getByRole('link', { name: '今日待复习 1 题' }).waitFor()
    await page.goto(`${STUDENT_WEB}/activity/detail/${target.id}`)
    await page.getByRole('button', { name: '立即报名' }).click()
    await page.getByText('与已通过的报名时间重叠', { exact: true }).waitFor()
    await page.getByText(`${enrolled.title}（重叠 30 分钟）`, { exact: true }).waitFor()
    await page.getByRole('button', { name: '确认报名', exact: true }).click()
    await page.getByText('报名待审批', { exact: true }).waitFor()
    console.log('PASS 移动端日程、待复习入口、网页报名冲突提示及继续报名')

    await page.route(`**/api${conflictPath(adjacent)}`, route => route.fulfill({
      status: 503, json: { code: 503, message: '冲突查询暂时不可用' }
    }))
    await page.goto(`${STUDENT_WEB}/activity/detail/${adjacent.id}`)
    await page.getByRole('button', { name: '立即报名' }).click()
    await page.getByText('暂时无法检查日程，你仍可提交报名。').waitFor()
    await page.getByRole('button', { name: '确认报名', exact: true }).click()
    await page.getByText('报名待审批', { exact: true }).waitFor()
    console.log('PASS 冲突查询故障时原有报名可用')

    // Make the fixtures eligible for the real scheduler in one commit.
    const ids = [enrolled.id, pending.id, canceled.id, offline.id].map(Number)
    assert.ok(ids.every(Number.isSafeInteger))
    const sql = `UPDATE activity SET start_time = '${timeAfter(40)}', end_time = '${timeAfter(100)}', status = CASE WHEN id = ${offline.id} THEN 3 ELSE status END WHERE id IN (${ids.join(',')})`
    execFileSync('docker', ['exec', '-i', mysql, 'sh', '-c',
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"'], { input: sql })

    const reminderMessages = async () => {
      const { list } = await ok('/message/list?type=system&pageSize=50', { token: buyer.token })
      return list.filter(row => row.title === '活动即将开始')
    }
    reminders = (async () => {
      for (let attempt = 0; attempt < 35; attempt++) {
        const rows = await reminderMessages()
        if (rows.length) {
          assert.deepEqual(rows.map(row => row.bizId), [enrolled.id])
          assert.match(rows[0].content, /图书馆/)
          await delay(65_000)
          assert.deepEqual((await reminderMessages()).map(row => row.bizId), [enrolled.id])
          console.log('PASS 开始前提醒、去重、取消/待审批/下架跳过')
          return
        }
        await delay(2_000)
      }
      assert.fail('未在两次调度窗口内收到提醒')
    })()
    reminders.catch(() => {})

    const agentTarget = await publish(owner.token, admin.token, '向导报名', timeAfter(45), timeAfter(90))
    const agent = await ok('/ai/agent/ask', {
      token: buyer.token, body: { question: `请帮我报名活动 ID ${agentTarget.id}「${agentTarget.title}」，生成报名确认卡。` }
    })
    const action = agent.actions.find(row => row.activityId === agentTarget.id)
    assert.ok(action, '校园向导未生成报名卡')
    await page.goto(`${STUDENT_WEB}/ai/chat`)
    await page.getByRole('tab', { name: '校园向导', exact: true }).click()
    await page.getByText('与已通过的报名时间重叠', { exact: true }).waitFor()
    await page.getByRole('button', { name: '确认提交报名' }).click()
    await page.getByText('报名已提交，等待审核。', { exact: true }).waitFor()
    const again = await ok(`/ai/agent/actions/${action.id}/confirm`, { token: buyer.token, method: 'POST' })
    assert.equal(again.status, 'SUBMITTED')
    console.log('PASS 向导报名卡冲突提示、继续报名与重复确认')

    await reminders
    await page.goto(`${STUDENT_WEB}/message`)
    await page.getByText('活动即将开始', { exact: true }).click()
    await page.waitForURL(`**/activity/detail/${enrolled.id}`)
    console.log('PASS 站内提醒跳转活动详情')
  } finally {
    await browser.close()
    await reminders
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
