const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')

async function main() {
  const fixture = JSON.parse(process.env.CAMPUS_IDLE_FIXTURE)
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
  try {
    const context = await browser.newContext()
    await context.addInitScript((token) => {
      localStorage.setItem('token', token)
      localStorage.setItem('userInfo', JSON.stringify({ nickname: '闲置发布验收' }))
    }, fixture.token)
    const page = await context.newPage()
    const capabilities = page.waitForResponse((res) => res.url().endsWith('/ai/agent/capabilities'))
    await page.goto(`${process.env.CAMPUS_STUDENT_URL || 'http://127.0.0.1:5173'}/ai/chat`)
    assert.equal((await (await capabilities).json()).data.enabled, true)
    await page.getByRole('tab', { name: /校园向导/ }).click()
    const ask = page.waitForResponse((res) => res.url().endsWith('/ai/agent/ask'), { timeout: 45000 })
    const input = page.locator('.ai-input input')
    await input.fill(fixture.question)
    await input.press('Enter')
    const result = await (await ask).json()
    assert.equal(result.code, 200, result.message)
    const draft = result.data.idleDrafts[0]
    assert.equal(draft.state, 'DRAFT')
    await page.getByRole('link', { name: '编辑并确认发布' }).click()
    const title = page.getByPlaceholder('如：九成新《数据结构》教材')
    await title.waitFor()
    assert.equal(await title.inputValue(), draft.title)
    await title.fill(fixture.title)
    const upload = page.waitForResponse((res) => res.url().endsWith('/api/upload/image'))
    await page.locator('input[type=file]').setInputFiles({
      name: 'idle.png', mimeType: 'image/png',
      buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+X2ioAAAAASUVORK5CYII=', 'base64')
    })
    const resource = await (await upload).json()
    assert.equal(resource.code, 200, resource.message)
    await page.locator('.upload-img button').filter({ hasText: /上传图片.*1\/9/ }).waitFor()
    const confirmed = page.waitForResponse((res) => res.url().endsWith(`/idle-drafts/${draft.id}/confirm`), { timeout: 65000 })
    await page.getByRole('button', { name: '提交审核' }).click()
    const response = await confirmed
    assert.deepEqual(response.request().postDataJSON().imageIds, [resource.data.resourceId])
    const receipt = await response.json()
    assert.equal(receipt.code, 200, receipt.message)
    assert.equal(receipt.data.state, 'SUBMITTED')
    await page.waitForURL('**/profile')
    console.log('PASS browser draft card, real image upload, edit and explicit confirmation')
  } finally {
    await browser.close()
  }
}

main().catch((error) => { console.error(error.message); process.exitCode = 1 })
