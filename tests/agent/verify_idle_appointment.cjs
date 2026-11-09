const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')

async function main() {
  const fixture = JSON.parse(process.env.CAMPUS_IDLE_FIXTURE)
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
  try {
    const context = await browser.newContext()
    await context.addInitScript((token) => {
      localStorage.setItem('token', token)
      localStorage.setItem('userInfo', JSON.stringify({ nickname: '闲置预约验收' }))
    }, fixture.token)
    const page = await context.newPage()
    const caps = page.waitForResponse(res => res.url().endsWith('/ai/agent/capabilities'))
    await page.goto(`${process.env.CAMPUS_STUDENT_URL || 'http://127.0.0.1:5173'}/ai/chat`)
    assert.equal((await (await caps).json()).data.enabled, true)
    await page.getByRole('tab', { name: /校园向导/ }).click()
    async function ask(question) {
      const response = page.waitForResponse(res => res.url().endsWith('/ai/agent/ask'), { timeout: 45000 })
      const input = page.locator('.ai-input input')
      await input.fill(question)
      await input.press('Enter')
      const result = await (await response).json()
      assert.equal(result.code, 200, result.message)
      return result.data
    }
    assert.equal((await ask(fixture.question)).idleItems[0].itemId, fixture.itemId)
    const data = await ask('我想预约第一个。留言写：有Java教材可互换。先给我预约确认卡。')
    assert.equal(data.idleAppointments[0].itemId, fixture.itemId)
    const card = page.locator('.idle-appointment-card').filter({ hasText: fixture.title })
    await card.getByText(/有Java教材可互换/).waitFor()
    const confirmed = page.waitForResponse(res => res.url().endsWith(`/idle-appointments/${data.idleAppointments[0].id}/confirm`))
    await card.getByRole('button', { name: '确认预约' }).click()
    const result = await (await confirmed).json()
    assert.equal(result.code, 200, result.message)
    assert.equal(result.data.appointmentStatus, 0)
    await card.getByRole('status').filter({ hasText: '等待发布者处理' }).waitFor()
    const reloaded = page.waitForResponse(res => res.url().endsWith('/ai/agent/capabilities'))
    await page.reload()
    await reloaded
    await page.getByRole('tab', { name: /校园向导/ }).click()
    await card.getByRole('status').filter({ hasText: '等待发布者处理' }).waitFor()
    assert.equal(await card.getByRole('button', { name: '确认预约' }).count(), 0)
    console.log('PASS browser search, ordinal appointment, explicit confirmation and restored status')
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error.message); process.exitCode = 1 })
