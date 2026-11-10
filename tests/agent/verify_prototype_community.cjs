const assert = require('node:assert/strict')
const { join } = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')

const fixture = JSON.parse(process.env.CAMPUS_BROWSER_FIXTURE)
const base = process.env.CAMPUS_STUDENT_URL || 'http://127.0.0.1:5173'

async function capture(page, name) {
  const dir = process.env.CAMPUS_PROTOTYPE_SCREENSHOTS
  if (dir) await page.screenshot({ path: join(dir, name + '.png') })
}

async function signIn(browser, id, token, width) {
  const page = await browser.newPage({ viewport: { width, height: 900 } })
  await page.addInitScript(({ id, token }) => {
    localStorage.setItem('token', token)
    localStorage.setItem('userInfo', JSON.stringify({ id, nickname: '校园验收同学' }))
  }, { id, token })
  page.setDefaultTimeout(8000)
  return page
}

async function checkWidth(page) {
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), '页面出现横向溢出')
}

async function checkLostFound(browser) {
  const finder = await signIn(browser, fixture.visitorId, fixture.visitorToken, 1440)
  const claimant = await signIn(browser, fixture.studentId, fixture.studentToken, 390)
  await claimant.goto(base + '/lostfound')
  await claimant.getByRole('heading', { name: '失物招领', exact: true, level: 1 }).waitFor()
  await claimant.getByRole('button', { name: /登记信息/ }).waitFor()
  const found = claimant.getByRole('button', { name: '我拾到物品', exact: true })
  await found.click()
  assert.equal(await found.getAttribute('aria-pressed'), 'true')
  const card = claimant.getByRole('link', { name: new RegExp(fixture.lostTitle) })
  await card.waitFor()
  await checkWidth(claimant)
  assert.equal(Math.round((await card.locator('.cover').boundingBox()).height), 140)
  await capture(claimant, '失物招领-手机')
  await card.press('Enter')
  const url = base + '/lostfound/detail/' + fixture.lostId
  await claimant.waitForURL(url)
  await claimant.getByRole('heading', { name: fixture.lostTitle, exact: true, level: 1 }).waitFor()
  await claimant.getByRole('button', { name: '申请认领', exact: true }).click()
  const application = claimant.getByRole('dialog', { name: '申请认领', exact: true })
  await application.getByPlaceholder('物品特征/证明信息…').fill('绿色卡套，背面有图书馆标签')
  const [claimResponse] = await Promise.all([
    claimant.waitForResponse(response => response.url() === base + '/api/lostfound/' + fixture.lostId + '/claim'),
    application.getByRole('button', { name: '提交申请' }).click(),
  ])
  const claimResult = await claimResponse.json()
  assert.equal(claimResult.code, 200, claimResult.message)
  await claimant.getByText('待确认', { exact: true }).waitFor()
  await checkWidth(claimant)
  await finder.goto(url)
  await finder.getByRole('button', { name: /认领申请/ }).click()
  await finder.getByRole('dialog', { name: '认领申请管理' }).getByRole('button', { name: '同意', exact: true }).click()
  await finder.getByText('等待失主确认', { exact: true }).waitFor()
  await claimant.reload()
  await claimant.getByRole('button', { name: '确认已找回' }).click()
  await claimant.getByText('已归还', { exact: true }).waitFor()
  console.log('PASS 失物筛选、键盘进详情、认领、同意、确认归还及手机宽度')
}

async function checkPartner(browser) {
  const page = await signIn(browser, fixture.studentId, fixture.studentToken, 1440)
  await page.goto(base + '/partner')
  await page.getByRole('button', { name: /发布需求/ }).waitFor()
  const math = page.getByRole('button', { name: '数学', exact: true })
  await math.click()
  assert.equal(await math.getAttribute('aria-pressed'), 'true')
  const card = page.locator('.p-card').filter({ hasText: fixture.partnerSubject })
  await card.getByText('周末上午', { exact: false }).waitFor()
  assert.equal(await page.locator('.partner-list .grid').evaluate(el =>
    getComputedStyle(el).gridTemplateColumns.split(' ').length), 2)
  await capture(page, '学习搭子-桌面')
  await card.getByRole('link', { name: '查看主页' }).click()
  await page.waitForURL(base + '/user/' + fixture.visitorId)
  await page.goto(base + '/partner')
  await page.setViewportSize({ width: 390, height: 900 })
  await card.waitFor()
  await checkWidth(page)
  await card.getByRole('button', { name: '私信邀约' }).click()
  await page.waitForURL(/\/chat\/\d+$/)
  console.log('PASS 学习搭子学科检索、两列与手机布局、真实主页和私信会话')
}

async function checkSocial(browser) {
  const page = await signIn(browser, fixture.studentId, fixture.studentToken, 1440)
  await page.goto(base + '/social')
  const publish = page.getByRole('button', { name: /发动态/ })
  await publish.waitFor()
  assert.equal(await publish.getAttribute('aria-expanded'), 'false')
  const card = page.locator('.post-card').filter({ hasText: fixture.postText })
  await card.waitFor()
  assert.ok((await card.boundingBox()).width <= 720)
  await capture(page, '校园动态-桌面')
  await card.getByRole('button', { name: '点赞', exact: true }).press('Enter')
  await card.getByRole('button', { name: '取消点赞', exact: true }).waitFor()
  await card.getByRole('button', { name: '收藏', exact: true }).click()
  await card.getByRole('button', { name: '取消收藏', exact: true }).waitFor()
  await card.getByRole('button', { name: '查看评论', exact: true }).click()
  await card.getByPlaceholder('友善评论，温暖校园').fill('图书馆光线真好')
  await card.getByRole('button', { name: '发表', exact: true }).click()
  await card.getByText('图书馆光线真好', { exact: true }).waitFor()

  await publish.click()
  assert.equal(await publish.getAttribute('aria-expanded'), 'true')
  await page.getByPlaceholder('分享校园生活…').fill('浏览器验收动态 ' + fixture.identifier)
  const [published] = await Promise.all([
    page.waitForResponse(r => r.url() === base + '/api/post' && r.request().method() === 'POST'),
    page.getByRole('button', { name: '发布动态', exact: true }).click(),
  ])
  const body = await published.json()
  assert.equal(body.code, 200, body.message)
  await page.getByText('已提交，待管理员审核后公开', { exact: true }).waitFor()

  await page.setViewportSize({ width: 390, height: 900 })
  await page.goto(base + '/social?post=' + fixture.postId)
  await page.getByText('正在查看分享的动态', { exact: false }).waitFor()
  assert.equal(await card.count(), 1)
  await checkWidth(page)
  console.log('PASS 动态发布提交、点赞收藏、发表评论、分享直达及手机宽度')
}

async function checkQuestions(browser) {
  const student = await signIn(browser, fixture.studentId, fixture.studentToken, 390)
  const author = await signIn(browser, fixture.visitorId, fixture.visitorToken, 1440)
  await student.goto(base + '/qa')
  await student.getByRole('heading', { name: '互助问答', exact: true, level: 1 }).waitFor()
  const item = student.getByRole('link', { name: new RegExp(fixture.questionTitle) })
  await item.waitFor()
  await checkWidth(student)
  await item.click()
  const url = base + '/qa/detail/' + fixture.questionId
  await student.waitForURL(url)
  const answer = '可以在图书馆服务台预约'
  await student.getByPlaceholder('写下你的回答，帮同学解决问题…').fill(answer)
  await student.getByRole('button', { name: '提交回答' }).click()
  await student.getByText(answer, { exact: true }).waitFor()
  await author.goto(url)
  await author.getByRole('button', { name: '采纳为最佳回答' }).click()
  await author.getByText('✅ 已采纳', { exact: true }).waitFor()
  await student.goto(base + '/qa')
  await item.getByText('已采纳', { exact: true }).waitFor()
  console.log('PASS 互助问答列表、真实回答、采纳并刷新状态')
}

async function checkNotice(browser) {
  const page = await signIn(browser, fixture.studentId, fixture.studentToken, 820)
  await page.goto(base + '/notice')
  const row = page.getByRole('link', { name: new RegExp(fixture.noticeTitle) })
  await row.waitFor()
  await checkWidth(page)
  await row.press('Enter')
  await page.waitForURL(base + '/notice/detail/' + fixture.noticeId)
  await page.getByRole('heading', { name: fixture.noticeTitle, exact: true, level: 1 }).waitFor()
  await page.getByText('周末开放时间为 9:00 至 17:00', { exact: true }).waitFor()
  console.log('PASS 校园公告真实列表、键盘查看及公告正文')
}

async function main() {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
  try {
    await checkLostFound(browser)
    await checkPartner(browser)
    await checkSocial(browser)
    await checkQuestions(browser)
    await checkNotice(browser)
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
