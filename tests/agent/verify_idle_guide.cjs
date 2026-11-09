const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')
const url = process.env.CAMPUS_STUDENT_URL || 'http://127.0.0.1:5173'

async function main() {
  const fixture = JSON.parse(process.env.CAMPUS_IDLE_FIXTURE)
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
  try {
    const context = await browser.newContext()
    await context.addInitScript((token) => {
      localStorage.setItem('token', token)
      localStorage.setItem('userInfo', JSON.stringify({ nickname: '闲置向导验收账号' }))
    }, fixture.token)
    const page = await context.newPage()
    const capabilities = page.waitForResponse((res) => res.url().endsWith('/ai/agent/capabilities'))
    await page.goto(`${url}/ai/chat`)
    assert.equal((await (await capabilities).json()).data.enabled, true)
    await page.getByRole('tab', { name: /校园向导/ }).click()
    const response = page.waitForResponse((res) => res.url().endsWith('/ai/agent/ask'), { timeout: 45000 })
    const input = page.locator('.ai-input input')
    await input.fill(fixture.question)
    await input.press('Enter')
    const result = await (await response).json()
    assert.equal(result.code, 200, result.message)
    const card = page.locator('.idle-guide-card').filter({ hasText: fixture.title })
    await card.waitFor()
    const detail = card.getByRole('link', { name: '查看详情' })
    assert.equal(await detail.getAttribute('href'), `/idle/detail/${fixture.itemId}`)
    const refreshedCapabilities = page.waitForResponse((res) => res.url().endsWith('/ai/agent/capabilities'))
    await page.reload()
    await refreshedCapabilities
    await page.getByRole('tab', { name: /校园向导/ }).click()
    await card.waitFor()
    const chat = page.waitForResponse((res) => res.url().endsWith('/api/chat/conversations') && res.request().method() === 'POST')
    await card.getByRole('button', { name: '联系发布者' }).click()
    const conversation = await chat
    const request = conversation.request().postDataJSON()
    assert.equal(request.contextType, 'idle')
    assert.equal(request.contextId, fixture.itemId)
    const data = await conversation.json()
    assert.equal(data.code, 200, data.message)
    await page.waitForURL(`**/chat/${data.data.id}`)
    console.log('PASS live browser search, restored idle card, detail link and contextual chat')
  } finally {
    await browser.close()
  }
}

main().catch((error) => { console.error(error.message); process.exitCode = 1 })
