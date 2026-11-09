const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')

async function main() {
  const fixture = JSON.parse(process.env.CAMPUS_OPS_FIXTURE)
  const url = process.env.CAMPUS_ADMIN_URL || 'http://127.0.0.1:5174'
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
  try {
    const context = await browser.newContext()
    await context.addInitScript(token => {
      localStorage.setItem('admin_token', token)
      localStorage.setItem('admin_info', JSON.stringify({ nickname: '运营验收员', role: 'super' }))
    }, fixture.token)
    const page = await context.newPage()
    await page.goto(`${url}/admin/assistant`)
    await page.getByRole('tab', { name: '公告草稿' }).click()
    await page.getByRole('textbox', { name: '公告要求' }).fill(`拟一份周五图书馆闭馆通知，标题包含 ${fixture.marker}。具体时间待确定。`)
    await page.getByRole('button', { name: '生成公告草稿' }).click()
    await page.getByRole('link', { name: '核对并编辑草稿' }).click()
    await page.getByRole('textbox', { name: '标题' }).fill(`${fixture.marker} 图书馆闭馆通知`)
    await page.locator('.md-editor textarea').fill('图书馆周五闭馆，时间以正式通知为准。')
    assert.equal(await page.getByRole('button', { name: '保存并发布' }).count(), 0)
    const save = page.waitForResponse(res => res.url().endsWith('/confirm') && res.url().includes('/notice-drafts/'))
    await page.getByRole('button', { name: '保存草稿' }).click()
    const saved = await (await save).json()
    assert.equal(saved.code, 200, saved.message)
    assert.equal(saved.data.state, 'SAVED')
    await page.waitForURL(`${url}/admin/notice`)

    await page.goto(`${url}/admin/assistant`)
    await page.getByRole('tab', { name: '运行诊断' }).click()
    await page.getByText(fixture.marker, { exact: true }).waitFor()
    console.log('PASS browser edits an AI draft, saves without publishing and loads real diagnostics')
    if (!fixture.pdf) return

    await page.getByRole('tab', { name: '内部规则' }).click()
    const upload = page.waitForResponse(res => res.url().endsWith('/assistant/rules') && res.request().method() === 'POST')
    await page.getByLabel('上传规则 PDF').setInputFiles({ name: `${fixture.marker}.pdf`, mimeType: 'application/pdf', buffer: Buffer.from(fixture.pdf, 'base64') })
    const doc = await (await upload).json()
    assert.equal(doc.code, 200, doc.message)
    const row = page.getByRole('row').filter({ hasText: `${fixture.marker}.pdf` })
    await row.waitFor()
    for (let retry = 0; retry < 45 && !(await row.getByText('可查询').count()); retry++) {
      const refreshed = page.waitForResponse(res => res.url().endsWith('/assistant/rules') && res.request().method() === 'GET')
      await page.getByRole('button', { name: '刷新索引状态' }).click()
      await refreshed
      await new Promise(resolve => setTimeout(resolve, 700))
    }
    await row.getByText('可查询').waitFor()
    await page.getByRole('textbox', { name: '规则问题' }).fill('What is the makerspace access identifier? Give the exact identifier.')
    await page.getByRole('button', { name: '查找规则' }).click()
    const reply = page.getByRole('article', { name: '规则答复' })
    await reply.getByText(fixture.marker, { exact: false }).waitFor({ timeout: 60000 })
    const popup = page.waitForEvent('popup')
    const pdf = page.waitForResponse(res => res.url().includes(`/assistant/rules/${doc.data.id}/file?`))
    await page.getByRole('button', { name: `${fixture.marker}.pdf · 第 2 页` }).click()
    assert.equal((await pdf).headers()['content-type'], 'application/pdf')
    await (await popup).waitForURL(/^blob:.*#page=2$/)
    console.log('PASS browser uploads internal rules, queries and opens the exact PDF citation')
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
