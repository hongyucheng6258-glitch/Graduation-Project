const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')

const fixture = JSON.parse(process.env.CAMPUS_BROWSER_FIXTURE)
const studentUrl = process.env.CAMPUS_STUDENT_URL || 'http://127.0.0.1:5173'
const adminUrl = process.env.CAMPUS_ADMIN_URL || 'http://127.0.0.1:5174'

async function check(browser, width, admin) {
  const context = await browser.newContext({ viewport: { width, height: 880 } })
  try {
    await context.addInitScript(({ isAdmin, token }) => {
      localStorage.setItem(isAdmin ? 'admin_token' : 'token', token)
      localStorage.setItem(isAdmin ? 'admin_info' : 'userInfo', JSON.stringify({ nickname: '布局验收', role: 'super' }))
    }, { isAdmin: admin, token: admin ? fixture.adminToken : fixture.studentToken })
    const page = await context.newPage()
    await page.goto(admin ? `${adminUrl}/admin/assistant` : `${studentUrl}/ai/chat`)
    await page.locator('.ai-layout').waitFor({ timeout: 7000 })
    const tablist = page.locator('.ai-top').getByRole('tablist')
    const chatTab = tablist.getByRole('tab', { name: admin ? '待办与审核' : /校园向导/ })
    await chatTab.click()
    await chatTab.press('ArrowRight')
    assert.equal(await chatTab.getAttribute('aria-selected'), 'false')
    assert.equal(await tablist.getByRole('tab', { selected: true }).evaluate(tab => tab === document.activeElement), true)
    await chatTab.click()
    await page.getByRole('button', { name: /新建会话/ }).waitFor()
    await page.locator('.ai-input input').waitFor()
    const layout = await page.evaluate(() => {
      const side = document.querySelector('.ai-side').getBoundingClientRect()
      const main = document.querySelector('.ai-main').getBoundingClientRect()
      const orb = document.querySelector('.ai-orb').getBoundingClientRect()
      const input = document.querySelector('.ai-input').getBoundingClientRect()
      const send = document.querySelector('.ai-input .send').getBoundingClientRect()
      const log = document.querySelector('.ai-chat')
      const logBox = log.getBoundingClientRect()
      const result = { side: Math.round(side.width), orb: orb.width, send: send.width,
        stacked: side.bottom <= main.top + 1, scroll: getComputedStyle(log).overflowY,
        composerBelowMessages: input.top >= logBox.bottom - 1,
        horizontalOverflow: Math.max(side.right, main.right) > innerWidth + 1 || log.scrollWidth > log.clientWidth + 1 }
      return result
    })
    assert.equal(layout.orb, 34)
    assert.equal(layout.send, 48)
    assert.equal(layout.scroll, 'auto')
    assert.equal(layout.composerBelowMessages, true)
    assert.equal(layout.horizontalOverflow, false, `${admin ? 'admin' : 'student'} assistant overflows at ${width}px`)
    assert.equal(layout.stacked, width <= 820)
    if (width > 820) assert.equal(layout.side, 240)
  } finally {
    await context.close()
  }
}

async function main() {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
  try {
    for (const width of [1440, 820, 390]) {
      await check(browser, width, false)
      await check(browser, width, true)
    }
    console.log('PASS both assistants share their desktop and mobile layout')
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
