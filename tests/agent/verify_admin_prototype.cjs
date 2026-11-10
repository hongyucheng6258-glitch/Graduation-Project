const assert = require('node:assert/strict')
const { join } = require('node:path')
const { randomBytes } = require('node:crypto')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')

const fixture = JSON.parse(process.env.CAMPUS_BROWSER_FIXTURE)
const admin = (process.env.CAMPUS_ADMIN_URL || 'http://127.0.0.1:5174') + '/admin'
const student = process.env.CAMPUS_STUDENT_URL || 'http://127.0.0.1:5173'
const screenshots = process.env.CAMPUS_PROTOTYPE_SCREENSHOTS

async function widthCheck(page, route) {
  const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth)
  assert.ok(scrollWidth <= page.viewportSize().width + 1, route + ' 横向溢出，页面宽度 ' + scrollWidth)
}

async function checkAudit(page) {
  await page.goto(admin + '/audit/activity')
  const nav = page.getByRole('navigation', { name: '审核分类' })
  await nav.waitFor()
  assert.equal(await nav.getByRole('link').count(), 5)
  await nav.getByRole('link', { name: '闲置', exact: true }).click()
  await page.waitForURL(admin + '/audit/idle')
  const idle = page.locator('.review-card').filter({ hasText: fixture.idleTitle })
  await idle.getByText('高风险', { exact: true }).waitFor()
  await idle.getByText('验收用高风险预审记录', { exact: false }).waitFor()
  if (screenshots) await page.screenshot({ path: join(screenshots, '内容审核-桌面.png') })
  await page.setViewportSize({ width: 390, height: 900 })
  await widthCheck(page, '/audit/idle')
  await page.setViewportSize({ width: 1440, height: 900 })

  const failed = url => url.pathname === '/api/admin/audit/all' && url.searchParams.get('type') === 'post'
  await page.route(failed, route => route.fulfill({ status: 503 }), { times: 1 })
  const response = page.waitForResponse(res => failed(new URL(res.url())))
  await nav.getByRole('link', { name: '动态', exact: true }).click()
  assert.equal((await response).status(), 503)
  await page.locator('.review-list .el-loading-mask').last().waitFor({ state: 'hidden' })
  assert.equal(await idle.count(), 0, '切换分类失败后仍可操作上个分类的记录')

  await page.goto(admin + '/ai/audit')
  const pending = page.locator('.risk-card').filter({ hasText: fixture.idleTitle })
  await pending.getByText('高风险', { exact: true }).waitFor()
  await pending.getByRole('button', { name: '恢复展示', exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await pending.waitFor({ state: 'hidden' })
  await page.goto(admin + '/audit/idle?record=' + fixture.idleId)
  await idle.getByText('已通过', { exact: true }).waitFor()
  await idle.getByRole('button', { name: '驳回', exact: true }).click()
  const reject = page.getByRole('dialog', { name: '驳回原因' })
  await reject.getByRole('textbox').fill('验收驳回，请补充物品说明')
  await reject.getByRole('button', { name: '确认驳回' }).click()
  await idle.getByText('已驳回', { exact: true }).waitFor()
  console.log('PASS 审核分类、风险证据、AI 复核恢复展示、手工驳回和精确记录')
}

async function checkManagement(page, browser) {
  await page.goto(admin + '/content')
  const record = page.getByRole('row', { name: new RegExp(fixture.activityTitle) })
  await record.getByRole('button', { name: '下架', exact: true }).click()
  await record.getByText('已下架', { exact: true }).waitFor()
  await record.getByRole('button', { name: /恢复/ }).click()
  await record.getByRole('button', { name: '下架', exact: true }).waitFor()

  await page.goto(admin + '/notice')
  await page.getByRole('button', { name: /新建公告/ }).click()
  const title = '管理员公告验收 ' + fixture.identifier
  await page.locator('input[maxlength="64"]').fill(title)
  await page.getByRole('textbox', { name: '内容' }).fill('**校园公告验收正文**')
  await page.getByRole('button', { name: '保存并发布' }).click()
  await page.waitForURL(admin + '/notice')
  const row = page.getByRole('row', { name: new RegExp(title) })
  await row.getByText('已发布', { exact: true }).waitFor()
  const reader = await browser.newPage()
  await reader.goto(student + '/notice')
  await reader.getByRole('link', { name: new RegExp(title) }).click()
  await reader.getByText('校园公告验收正文', { exact: true }).waitFor()
  await reader.close()
  console.log('PASS 内容下架和恢复、公告创建发布及学生端可见')
}

async function checkOperations(page) {
  await page.goto(admin + '/user')
  const studentRow = page.getByRole('row', { name: /Browser Student/ })
  await studentRow.getByRole('button', { name: '禁用', exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await studentRow.getByText('已禁用', { exact: true }).waitFor()
  await studentRow.getByRole('button', { name: '启用', exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await studentRow.getByText('正常', { exact: true }).waitFor()

  await page.goto(admin + '/report')
  const report = page.getByRole('row', { name: new RegExp(fixture.reportReason) })
  await report.getByRole('button', { name: '处置' }).click()
  const handle = page.getByRole('dialog', { name: '举报处置' })
  await handle.getByText('举报不成立', { exact: true }).click()
  await handle.getByPlaceholder('必填，将通知举报人').fill('验收处置结果')
  await handle.getByRole('button', { name: '确认处置' }).click()
  await report.getByText('已处理', { exact: true }).waitFor()

  await page.goto(admin + '/ai/logs')
  const log = page.getByRole('row', { name: new RegExp(fixture.modelName) })
  await log.getByText('32 + 16', { exact: true }).waitFor()
  await log.getByText('成功', { exact: true }).waitFor()

  await page.goto(admin + '/system')
  await page.getByRole('button', { name: /新建管理员/ }).click()
  const account = page.getByRole('dialog', { name: '新建管理员' })
  const username = 'qa-' + fixture.identifier
  await account.getByRole('textbox', { name: /用户名/ }).fill(username)
  await account.locator('input[type=password]').fill(randomBytes(10).toString('hex'))
  await account.getByRole('textbox', { name: '昵称', exact: true }).fill('验收审核员')
  await account.getByRole('button', { name: '保存', exact: true }).click()
  await page.getByRole('row', { name: new RegExp(username) }).getByText('审核员', { exact: true }).waitFor()
  console.log('PASS 用户禁用与启用、举报处置、真实调用日志、创建管理员')
}

async function saveSettings(page, selector, value, endpoint, buttonName) {
  await selector.fill(value)
  await selector.press('Tab')
  const [response] = await Promise.all([
    page.waitForResponse(res => new URL(res.url()).pathname === endpoint && res.request().method() === 'PUT'),
    page.getByRole('button', { name: buttonName, exact: true }).click(),
  ])
  const result = await response.json()
  assert.equal(result.code, 200, result.message)
  await page.getByText('配置已保存并即时生效', { exact: true }).waitFor()
}

async function checkSettings(page) {
  await page.goto(admin + '/system/config')
  const slogan = page.getByPlaceholder('配置键：site_slogan')
  await slogan.waitFor()
  const originalSlogan = await slogan.inputValue()
  const newSlogan = '校园原型验收标语'
  try {
    await saveSettings(page, slogan, newSlogan, '/api/admin/system/config', '保存全部配置')
    await page.reload()
    await slogan.waitFor()
    assert.equal(await slogan.inputValue(), newSlogan)
  } finally {
    await saveSettings(page, slogan, originalSlogan, '/api/admin/system/config', '保存全部配置')
  }

  await page.goto(admin + '/ai/config')
  const tokenLimit = page.getByRole('spinbutton', { name: '最大输出 Token', exact: true })
  await tokenLimit.waitFor()
  await page.locator('.model-form .el-loading-mask').waitFor({ state: 'hidden' })
  const originalLimit = await tokenLimit.inputValue()
  const newLimit = originalLimit === '4096' ? '3840' : '4096'
  try {
    await saveSettings(page, tokenLimit, newLimit, '/api/admin/ai/config', '保存配置')
    await page.reload()
    await tokenLimit.waitFor()
    await page.locator('.model-form .el-loading-mask').waitFor({ state: 'hidden' })
    assert.equal(await tokenLimit.inputValue(), newLimit)
  } finally {
    await saveSettings(page, tokenLimit, originalLimit, '/api/admin/ai/config', '保存配置')
  }
  console.log('PASS 站点配置和模型参数保存、刷新读取和恢复原值')
}

async function checkShell(page) {
  const routes = [
    ['/user', true], ['/content', true], ['/report', true], ['/notice', true],
    ['/notice/edit', false], ['/ai/logs', true], ['/system', true], ['/ai/config', false], ['/system/config', false], ['/ai/audit', false],
  ]
  for (const width of [1440, 820, 390]) {
    await page.setViewportSize({ width, height: 900 })
    for (const [route, table] of routes) {
      await page.goto(admin + route)
      await page.locator('.content h1').waitFor()
      if (table) await page.locator('.admin-list-table').first().waitFor()
      await widthCheck(page, route)
      if (screenshots && width === 1440 && route === '/content') {
        await page.getByText(fixture.activityTitle, { exact: true }).first().waitFor()
        await page.screenshot({ path: join(screenshots, '内容管理-桌面.png') })
      }
    }
  }
  console.log('PASS 管理端核心页面在 1440、820 和 390 像素下完成布局检查')
}

async function main() {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
    await context.addInitScript(token => {
      localStorage.setItem('admin_token', token)
      localStorage.setItem('admin_info', JSON.stringify({ nickname: '原型验收', role: 'super' }))
    }, fixture.adminToken)
    const page = await context.newPage()
    page.setDefaultTimeout(10000)
    await checkAudit(page)
    await checkManagement(page, browser)
    await checkOperations(page)
    await checkSettings(page)
    await checkShell(page)
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
