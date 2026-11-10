const assert = require('node:assert/strict')
const { join } = require('node:path')
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
    const shell = await page.evaluate(isAdmin => {
      const root = getComputedStyle(document.documentElement)
      const top = document.querySelector(isAdmin ? '.topbar' : '.campus-header')
      const sidebar = document.querySelector(isAdmin ? '.admin-shell > .sidebar' : '.desktop-nav')
      return { brand: root.getPropertyValue('--brand').trim().toLowerCase(),
        paper: root.getPropertyValue('--paper').trim().toLowerCase(),
        topHeight: Math.round(top.getBoundingClientRect().height),
        navWidth: Math.round(sidebar.getBoundingClientRect().width), overflow: document.documentElement.scrollWidth > innerWidth + 1 }
    }, admin)
    assert.equal(shell.overflow, false, `${admin ? 'admin' : 'student'} page overflows at ${width}px`)
    assert.equal(shell.brand, '#2e5bff')
    assert.equal(shell.paper, '#f5f7fb')
    if (width === 1440) {
      assert.equal(shell.topHeight, admin ? 64 : 72)
      if (admin) assert.equal(shell.navWidth, 248)
      else await page.getByRole('navigation', { name: '校园导航' }).getByRole('link', { name: '学习搭子' }).waitFor()
      await page.getByRole('button', { name: admin ? '切换主题' : '切换为深色', exact: true }).click()
      await page.locator('html[data-theme="dark"]').waitFor()
      const darkPaper = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--paper').trim().toLowerCase())
      assert.notEqual(darkPaper, shell.paper)
    }
    if (admin) {
      if (width === 1440) await page.getByRole('button', { name: '切换主题', exact: true }).click()
      await page.goto(`${adminUrl}/admin/dashboard`)
      await page.getByRole('heading', { name: '今天的校园，也交给你了。' }).waitFor()
      await page.locator('.system-pill').filter({ hasText: '概览数据已更新' }).waitFor({ state: 'attached', timeout: 7000 })
      assert.equal(await page.locator('.stat-card').count(), 4)
      assert.equal(await page.locator('.dashboard canvas').count(), 3)
      const dashboard = await page.evaluate(() => ({
        greeting: getComputedStyle(document.querySelector('.admin-greeting')).backgroundColor,
        gridGap: getComputedStyle(document.querySelector('.stat-grid')).columnGap,
        overflow: document.documentElement.scrollWidth > innerWidth + 1,
      }))
      assert.equal(dashboard.greeting, 'rgba(0, 0, 0, 0)')
      if (width === 1440) assert.equal(dashboard.gridGap, '18px')
      assert.equal(dashboard.overflow, false, `dashboard overflows at ${width}px`)
      const screenshotDir = process.env.CAMPUS_PROTOTYPE_SCREENSHOTS
      if (screenshotDir && width === 1440) {
        await page.screenshot({ path: join(screenshotDir, 'admin-dashboard.png'), fullPage: true })
      }
    }
    if (!admin && width <= 820) {
      assert.equal(shell.navWidth, 0)
      const menu = page.getByRole('button', { name: '切换导航' })
      await menu.click()
      await page.getByRole('navigation', { name: '全部功能' }).getByRole('link', { name: '3D校园' }).waitFor()
      await page.getByRole('button', { name: '关闭导航' }).press('Escape')
      assert.equal(await menu.getAttribute('aria-expanded'), 'false')
    }
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
    console.log('PASS both assistants and the live admin dashboard at desktop and mobile widths')
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
