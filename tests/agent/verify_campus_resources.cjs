const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')

async function main() {
  const fixture = JSON.parse(process.env.CAMPUS_RESOURCE_FIXTURE)
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
  try {
    const context = await browser.newContext()
    await context.addInitScript((token) => {
      localStorage.setItem('token', token)
      localStorage.setItem('userInfo', JSON.stringify({ nickname: '校园联动验收' }))
    }, fixture.token)
    const page = await context.newPage()
    const url = process.env.CAMPUS_STUDENT_URL || 'http://127.0.0.1:5173'
    async function openGuide() {
      const capabilities = page.waitForResponse(res => res.url().endsWith('/ai/agent/capabilities'))
      await page.goto(`${url}/ai/chat`)
      assert.equal((await (await capabilities).json()).data.enabled, true)
      await page.getByRole('tab', { name: /校园向导/ }).click()
    }
    for (const entry of fixture.rows) {
      await openGuide()
      const response = page.waitForResponse(res => res.url().endsWith('/ai/agent/ask'), { timeout: 45000 })
      const input = page.locator('.ai-input input')
      await input.fill(entry.question)
      await input.press('Enter')
      const result = await (await response).json()
      assert.equal(result.code, 200, result.message)
      const [resource] = result.data.resources
      assert.equal(resource.kind, entry.kind)
      assert.equal(resource.id, entry.id)
      const card = page.locator('.resource-card').filter({ hasText: resource.title }).last()
      const link = card.getByRole('link')
      assert.equal(await link.getAttribute('href'), entry.path)
      await link.click()
      await page.waitForURL(`${url}${entry.path}`)
    }
    console.log('PASS browser routes from lost-and-found, study partner and Q&A cards')
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error.message); process.exitCode = 1 })
