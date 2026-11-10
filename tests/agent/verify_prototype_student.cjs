const assert = require('node:assert/strict')
const { join } = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')

const fixture = JSON.parse(process.env.CAMPUS_BROWSER_FIXTURE)
const base = process.env.CAMPUS_STUDENT_URL || 'http://127.0.0.1:5173'

async function main() {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
    await context.addInitScript(token => {
      localStorage.setItem('token', token)
      localStorage.setItem('userInfo', JSON.stringify({ nickname: '校园验收同学' }))
    }, fixture.studentToken)
    const page = await context.newPage()
    page.setDefaultTimeout(10000)
    await page.goto(`${base}/`)
    await page.getByRole('heading', { name: /课表之外/ }).waitFor()
    assert.equal(await page.locator('.utility-bar').count(), 0, '学生端仍显示主导航下方的工具条')
    assert.equal(await page.locator('.campus-header + main.content').count(), 1)
    const header = await page.locator('.campus-header').boundingBox()
    const hero = await page.locator('.campus-hero').boundingBox()
    assert.equal(Math.round(hero.y - header.y - header.height), 24)
    const services = page.locator('[aria-label="校园服务快捷入口"]')
    assert.equal(await services.getByRole('button').count() + await services.getByRole('link').count(), 6)
    assert.equal(Math.round((await page.locator('.hero-photo').boundingBox()).height), 320)
    await page.getByRole('link', { name: /进入3D校园/ }).waitFor()
    assert.equal(await page.locator('.bulletin').getByRole('link', { name: '全部' }).getAttribute('href'), '/notice')

    const screenshotDir = process.env.CAMPUS_PROTOTYPE_SCREENSHOTS
    if (screenshotDir) await page.screenshot({ path: join(screenshotDir, 'student-home.png'), fullPage: true })

    await page.getByRole('button', { name: '个人中心', exact: true }).click()
    await page.getByRole('menuitem', { name: '个人中心', exact: true }).waitFor()
    await page.getByRole('menuitem', { name: '错题本', exact: true }).click()
    await page.waitForURL(`${base}/ai/wrong`)
    await page.locator('footer.footer').getByRole('link', { name: '你画我猜', exact: true }).click()
    await page.waitForURL(`${base}/draw-guess`)

    await page.goto(`${base}/activity`)
    await page.getByRole('heading', { name: '校园活动', level: 1, exact: true }).waitFor({ timeout: 7000 })
    await page.getByRole('button', { name: '发布活动' }).waitFor()
    const activity = page.getByRole('link', { name: new RegExp(fixture.activityTitle) })
    await activity.waitFor()
    if (screenshotDir) await page.screenshot({ path: join(screenshotDir, 'student-activity.png'), fullPage: true })
    await activity.click()
    await page.waitForURL(`${base}/activity/detail/${fixture.activityId}`)
    await page.getByRole('heading', { name: fixture.activityTitle, exact: true }).waitFor()
    await page.getByRole('heading', { name: '报名参与', exact: true }).waitFor()
    await page.getByRole('button', { name: '立即报名' }).waitFor()

    await page.goto(`${base}/idle`)
    await page.getByRole('heading', { name: '闲置互换', level: 1, exact: true }).waitFor({ timeout: 7000 })
    await page.getByRole('button', { name: /发布闲置/ }).waitFor()

    await page.getByText(fixture.idleTitle, { exact: true }).click()
    await page.waitForURL(`${base}/idle/detail/${fixture.idleId}`)
    await page.getByRole('heading', { name: fixture.idleTitle, exact: true }).waitFor()
    await page.getByRole('button', { name: '发起预约互换' }).click()
    const dialog = page.getByRole('dialog', { name: '发起预约' })
    await dialog.waitFor()
    await dialog.getByRole('button', { name: '取消' }).click()

    for (const width of [820, 390]) {
      await page.setViewportSize({ width, height: 880 })
      for (const route of ['/', '/activity', `/activity/detail/${fixture.activityId}`, '/idle', `/idle/detail/${fixture.idleId}`]) {
        await page.goto(base + route)
        await page.locator('main.content h1').waitFor()
        assert.equal(await page.locator('.utility-bar').count(), 0)
        const { width: available, scrollWidth, elements } = await page.evaluate(() => ({
          width: innerWidth,
          scrollWidth: document.documentElement.scrollWidth,
          elements: [...document.querySelectorAll('main.content *')].filter(el => el.getBoundingClientRect().right > innerWidth + 1)
            .slice(0, 6).map(el => el.className.baseVal || el.className || el.tagName),
        }))
        assert.ok(scrollWidth <= available + 1, `${route} overflows at ${width}px: ${elements.join(', ')}`)
      }
    }
    console.log('PASS student prototype structure, real activity link and mobile layout')
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
