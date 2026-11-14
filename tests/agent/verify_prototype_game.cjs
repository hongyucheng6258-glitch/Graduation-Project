const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')

const fixture = JSON.parse(process.env.CAMPUS_BROWSER_FIXTURE)
const base = process.env.CAMPUS_STUDENT_URL || 'http://127.0.0.1:5173'

async function signIn(browser, id, token, width) {
  const context = await browser.newContext({ viewport: { width, height: 900 } })
  await context.addInitScript(({ id, token }) => {
    localStorage.setItem('token', token)
    localStorage.setItem('userInfo', JSON.stringify({ id, nickname: '游戏验收' }))
  }, { id, token })
  const page = await context.newPage()
  page.setDefaultTimeout(12000)
  return page
}

async function main() {
  const browser = await chromium.launch({
    headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE,
    args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
  })
  try {
    const host = await signIn(browser, fixture.studentId, fixture.studentToken, 1440)
    const guest = await signIn(browser, fixture.visitorId, fixture.visitorToken, 390)
    await host.goto(base + '/draw-guess')
    await host.getByRole('heading', { name: '你画我猜', exact: true, level: 1 }).waitFor()
    const create = host.getByRole('button', { name: '+ 创建房间', exact: true })
    assert.equal(await create.getAttribute('aria-expanded'), 'false')
    await create.click()
    const roomTitle = 'PrototypeGame-' + fixture.identifier
    await host.getByLabel('房间名称').fill(roomTitle)
    await host.getByRole('button', { name: /创建游戏房/ }).click()
    await host.waitForURL('**/draw-guess/room/*')
    await host.getByText('实时已连接', { exact: true }).waitFor()
    const code = (await host.locator('.room-invite b').innerText()).trim()

    await guest.goto(base + '/draw-guess')
    await guest.getByRole('heading', { name: roomTitle, exact: true }).waitFor()
    await guest.getByRole('button', { name: '输入房间码加入', exact: true }).click()
    await guest.getByLabel('房间码', { exact: true }).fill(code)
    await guest.locator('#join-game').getByRole('button', { name: /加入房间/ }).click()
    await guest.waitForURL(host.url())
    await guest.getByText('实时已连接', { exact: true }).waitFor()
    await host.getByRole('button', { name: /开始游戏/ }).click()
    await guest.locator('.timer').waitFor()
    const activeBoard = '.canvas-frame:not(.canvas-locked) canvas'
    const drawer = await Promise.race([host, guest].map(page =>
      page.locator(activeBoard).waitFor().then(() => page)))
    const watcher = drawer === host ? guest : host
    const canvas = drawer.locator(activeBoard)
    await canvas.scrollIntoViewIfNeeded()
    const bounds = await canvas.boundingBox()
    const left = bounds.x + bounds.width / 3
    const top = bounds.y + bounds.height / 3
    await drawer.mouse.move(left, top)
    await drawer.mouse.down()
    await drawer.mouse.move(left + 35, top + 25, { steps: 5 })
    await drawer.mouse.up()
    await watcher.waitForFunction(() => {
      const canvas = document.querySelector('.draw-canvas')
      const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data
      return pixels.some((value, index) => index % 4 === 3 && value > 0)
    })
    assert.equal(await guest.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false)
    await guest.getByRole('button', { name: '聊天', exact: true }).click()
    const message = 'Prototype game websocket'
    await guest.getByRole('textbox', { name: '输入猜词或聊天内容' }).fill(message)
    await guest.getByRole('button', { name: '发送消息' }).click()
    await host.getByText(message, { exact: true }).waitFor()

    await host.goto(base + '/campus-3d')
    await host.getByRole('button', { name: /开始探索/ }).waitFor({ timeout: 35000 })
    await host.getByRole('button', { name: /开始探索/ }).click()
    await host.locator('.scene-canvas canvas').waitFor()
    await host.getByRole('button', { name: '校园导览', exact: true }).click({ timeout: 35000 })
    await host.locator('.map-zone').first().waitFor()
    console.log('PASS game lobby, two-player live room, synchronized drawing and chat, 390px layout and 3D campus guide')
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
