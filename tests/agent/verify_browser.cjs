const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')
const studentUrl = (process.env.CAMPUS_STUDENT_URL || 'http://127.0.0.1:5173').replace(/\/$/, '')
const adminUrl = (process.env.CAMPUS_ADMIN_URL || 'http://127.0.0.1:5174').replace(/\/$/, '')

async function jsonResponse(response) {
  const body = await response.json()
  assert.equal(body.code, 200, body.message)
  return body.data
}

async function browserStudent(browser, token) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } })
  await context.addInitScript((token) => {
    localStorage.setItem('token', token)
    localStorage.setItem('userInfo', JSON.stringify({ nickname: '浏览器验收账号' }))
  }, token)
  const page = await context.newPage()
  const capabilities = page.waitForResponse((response) => response.url().endsWith('/ai/agent/capabilities'))
  await page.goto(`${studentUrl}/ai/chat`)
  assert.deepEqual(await jsonResponse(await capabilities), { enabled: true, ragEnabled: true })
  return { context, page }
}

async function ask(page, question, path) {
  const response = page.waitForResponse((item) => item.url().endsWith(path), { timeout: 45000 })
  await page.locator('.ai-input input').fill(question)
  await page.locator('.ai-input input').press('Enter')
  return jsonResponse(await response)
}

async function main() {
  const fixture = JSON.parse(process.env.CAMPUS_BROWSER_FIXTURE)
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
  })
  try {
    const { page, context } = await browserStudent(browser, fixture.studentToken)
    await page.getByRole('tab', { name: /校园向导/ }).click()
    const proposal = await ask(page, `我要报名活动 ID ${fixture.activityId}，请生成报名确认卡。`, '/ai/agent/ask')
    assert.equal(proposal.actions[0].activityTitle, fixture.activityTitle)
    const confirm = page.getByRole('button', { name: '确认提交报名' })
    await confirm.waitFor()
    const submission = page.waitForResponse((response) => response.url().endsWith('/confirm'))
    await confirm.click()
    assert.equal((await jsonResponse(await submission)).status, 'SUBMITTED')
    await page.getByText('报名已提交，等待审核。', { exact: true }).waitFor()
    await page.reload()
    await page.getByRole('tab', { name: /校园向导/ }).click()
    await page.getByText('报名已提交，等待审核。', { exact: true }).waitFor()
    console.log('PASS student browser submits an Agent confirmation card')

    await page.getByRole('tab', { name: /PDF问答/ }).click()
    await page.locator('input[type="file"]').setInputFiles({
      name: 'agent-browser.pdf', mimeType: 'application/pdf', buffer: Buffer.from(fixture.pdf, 'base64')
    })
    await page.getByText(/agent-browser\.pdf.*可问答/).waitFor({ timeout: 55000 })
    const knowledge = page.waitForResponse((response) => /\/ai\/knowledge\/\d+\/ask$/.test(response.url()), { timeout: 45000 })
    await page.locator('.ai-input input').fill('What is the makerspace access identifier? Give the exact identifier.')
    await page.locator('.ai-input input').press('Enter')
    const result = await jsonResponse(await knowledge)
    assert.match(result.answer, new RegExp(fixture.identifier))
    const source = result.citations.find((citation) => citation.page === 2)
    assert.ok(source)
    const file = page.waitForResponse((response) => response.url().endsWith(`/ai/knowledge/${source.documentId}/file`)
      && response.request().method() === 'GET')
    const popup = page.waitForEvent('popup')
    await page.getByRole('button', { name: 'agent-browser.pdf · 第 2 页' }).click()
    const raw = await file
    assert.equal(raw.status(), 200)
    assert.match(raw.headers()['content-type'], /^application\/pdf/)
    const actualPdf = await page.evaluate(async ({ id, token }) => {
      const response = await fetch(`/api/ai/knowledge/${id}/file`, { headers: { Authorization: `Bearer ${token}` } })
      return (await (await response.blob()).slice(0, 5).text())
    }, { id: source.documentId, token: fixture.studentToken })
    assert.equal(actualPdf, '%PDF-')
    await (await popup).waitForURL(/^blob:.*#page=2$/, { timeout: 10000 })
    await page.reload()
    await page.getByRole('tab', { name: /PDF问答/ }).click()
    await page.getByRole('button', { name: 'agent-browser.pdf · 第 2 页' }).waitFor()
    console.log('PASS browser uploads and indexes PDF, answers and opens an authorized citation')

    const visitor = await browserStudent(browser, fixture.visitorToken)
    const hidden = await visitor.page.request.get(`${studentUrl}/api/ai/knowledge/${source.documentId}`, {
      headers: { Authorization: `Bearer ${fixture.visitorToken}` }
    })
    assert.equal((await hidden.json()).code, 404)
    await visitor.context.close()
    console.log('PASS second browser account cannot read the first account PDF')
    await page.setViewportSize({ width: 390, height: 844 })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await context.close()

    const admin = await browser.newContext()
    await admin.addInitScript((token) => {
      localStorage.setItem('admin_token', token)
      localStorage.setItem('admin_info', JSON.stringify({ nickname: '验收管理员', role: 'super' }))
    }, fixture.adminToken)
    const settings = await admin.newPage()
    await settings.goto(`${adminUrl}/admin/ai/config`)
    await settings.getByText('校园向导工作台', { exact: true }).waitFor()
    const logs = settings.waitForResponse((response) => response.url().endsWith('/ai/agent/runs'))
    await settings.getByRole('tab', { name: '运行记录' }).click()
    assert.ok((await jsonResponse(await logs)).some((row) => row.tools.includes('prepare_activity_signup')))
    console.log('PASS admin browser shows real Agent tool run')
  } finally {
    await browser.close()
  }
}

main().catch((error) => { console.error(error.message); process.exitCode = 1 })
