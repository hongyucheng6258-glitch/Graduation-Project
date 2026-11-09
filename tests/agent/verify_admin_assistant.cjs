const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')

async function main() {
  const fixture = JSON.parse(process.env.CAMPUS_ADMIN_ASSISTANT_FIXTURE)
  const url = process.env.CAMPUS_ADMIN_URL || 'http://127.0.0.1:5174'
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
  try {
    const context = await browser.newContext()
    await context.addInitScript(token => {
      localStorage.setItem('admin_token', token)
      localStorage.setItem('admin_info', JSON.stringify({ nickname: '助手验收员', role: 'audit' }))
    }, fixture.token)
    const page = await context.newPage()
    await page.goto(`${url}/admin/dashboard`)
    await page.getByRole('link', { name: '管理智能助手' }).click()
    await page.getByRole('heading', { name: '管理智能助手' }).waitFor()
    async function ask(question) {
      const query = page.waitForResponse(res => res.url().endsWith('/api/admin/assistant/ask'), { timeout: 40000 })
      await page.getByRole('textbox', { name: '向管理助手提问' }).fill(question)
      await page.getByRole('button', { name: '发送' }).click()
      const reply = await (await query).json()
      assert.equal(reply.code, 200, reply.message)
      return reply.data.turn
    }
    if (fixture.idleId) {
      assert.equal((await ask(fixture.idleQuestion)).records[0].id, String(fixture.idleId))
      assert.equal((await ask(fixture.reportQuestion)).records[0].target.title, fixture.title)
      const idle = page.getByRole('link', { name: '打开闲置审核记录' })
      const report = page.getByRole('link', { name: '打开举报记录' })
      assert.equal(await idle.getAttribute('href'), `/admin/audit/idle?record=${fixture.idleId}`)
      const idleResponse = page.waitForResponse(res => res.url().endsWith(`/api/admin/audit/idle/${fixture.idleId}`))
      await idle.click()
      assert.equal((await (await idleResponse).json()).data.title, fixture.title)
      await page.getByText(fixture.title, { exact: true }).waitFor()
      await page.goBack()
      await page.getByText('验收举报理由', { exact: false }).waitFor()
      const reportResponse = page.waitForResponse(res => res.url().endsWith(`/api/admin/report/${fixture.reportId}`))
      await report.click()
      assert.equal((await (await reportResponse).json()).data.reason, '验收举报理由')
      await page.getByText('验收举报理由', { exact: true }).waitFor()
      console.log('PASS admin browser opens the exact idle and report records from evidence cards')
      return
    }
    assert.equal((await ask(fixture.question)).tool, 'get_pending_tasks')
    const report = page.getByRole('region', { name: '实时待办结果' }).getByRole('link', { name: /待处理举报/ })
    await report.waitFor()
    assert.match(await report.innerText(), new RegExp(`${fixture.count} 项`))
    await page.reload()
    await report.waitFor()
    const reportList = page.waitForResponse(res => new URL(res.url()).pathname === '/api/admin/report/list')
    await report.click()
    assert.equal(new URL((await reportList).url()).searchParams.get('status'), '0')
    await page.waitForURL(`${url}/admin/report?view=pending`)
    console.log('PASS admin browser opens assistant, queries real count, restores history and opens filtered report list')
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
