const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')
const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { resolveEndpoints } = require('./config.cjs')

const { ADMIN_WEB: admin, STUDENT_WEB: student } = resolveEndpoints()
const adminLogin = { username: process.env.E2E_ADMIN_USERNAME || 'admin', password: process.env.E2E_ADMIN_PASSWORD }
const studentNo = process.env.E2E_STUDENT_NO || 'CAMPUSDEMO01'

async function authenticate(context, isAdmin) {
  const url = isAdmin ? admin + '/api/admin/auth/login' : student + '/api/auth/login'
  const data = isAdmin ? adminLogin : {
    studentNo,
    password: readFileSync(process.env.E2E_STUDENT_PASSWORD_FILE, 'utf8').trim()
  }
  const login = await (await context.request.post(url, { data })).json()
  assert.equal(login.code, 200, login.message)
  await context.addInitScript(({ auth, adminRole }) => {
    localStorage.setItem(adminRole ? 'admin_token' : 'token', auth.token)
    localStorage.setItem(adminRole ? 'admin_info' : 'userInfo', JSON.stringify(adminRole ? auth.adminInfo : auth.userInfo))
  }, { auth: login.data, adminRole: isAdmin })
}

async function withPage(browser, width, height, test) {
  const context = await browser.newContext({ viewport: { width, height } })
  try {
    const page = await context.newPage()
    page.setDefaultTimeout(10000)
    await test(context, page)
  } finally {
    await context.close()
  }
}

function adminSearch(browser, width) {
  return withPage(browser, width, 720, async (context, page) => {
    await authenticate(context, true)
    const query = new URLSearchParams({ q: '校园', scope: 'content' })
    await page.goto(admin + '/admin/search?' + query)
    const group = page.getByRole('region', { name: '校园动态', exact: true })
    await group.getByRole('button', { name: '下一页' }).click()
    await group.getByText('第 2 页', { exact: true }).waitFor()
    const result = group.locator('.result-row').first()
    const id = new URL(await result.getAttribute('href'), admin).searchParams.get('record')
    await result.click()
    await page.waitForURL(/\/admin\/audit\/post/)
    await page.getByRole('navigation', { name: '面包屑' }).getByRole('link', { name: '搜索结果' }).click()
    await page.waitForURL(/\/admin\/search/)
    await group.getByText('第 2 页', { exact: true }).waitFor()
    await page.waitForFunction(id => document.activeElement?.id === 'result-post-' + id, id)
    const row = await page.locator('#result-post-' + id).boundingBox()
    const header = await page.locator('.topbar').boundingBox()
    assert.ok(row.y > header.y + header.height && row.y + row.height <= 720)
    console.log('PASS 管理端搜索返回第 2 页并聚焦原记录', width)
  })
}

function studentSearch(browser) {
  return withPage(browser, 390, 780, async (context, page) => {
    let failed = false
    await page.route(/\/api\/idle\/list\?/, route => {
      if (failed) return route.continue()
      failed = true
      return route.fulfill({ status: 500, json: { code: 500, message: '测试：暂不可用' } })
    })
    const query = new URLSearchParams({ q: '校园' })
    const posts = await (await context.request.get(student + '/api/post/list', {
      params: { keyword: query.get('q'), pageNum: 1, pageSize: 4 }
    })).json()
    assert.ok(posts.data.total > 4)
    await page.goto(student + '/search?' + query)
    await page.getByRole('button', { name: '校园动态 ' + posts.data.total, exact: true }).waitFor()
    const results = page.getByRole('region', { name: '校园动态', exact: true }).locator('.result-list > a')
    await results.first().waitFor()
    assert.equal(await results.count(), 4)
    const retry = page.getByRole('region', { name: '闲置物品', exact: true }).getByRole('button', { name: '重试' })
    await retry.click()
    await retry.waitFor({ state: 'hidden' })
    assert.ok(failed)
    console.log('PASS 学生端搜索真实总数与分类重试')
  })
}

function assistantFallback(browser, isAdmin) {
  return withPage(browser, 390, 844, async (context, page) => {
    await authenticate(context, isAdmin)
    const endpoint = isAdmin ? '**/api/admin/assistant/ask' : '**/api/ai/agent/ask'
    await page.route(endpoint, route => route.fulfill({ json: { code: 1002, message: '测试：模型超时' } }))
    await page.goto(isAdmin ? admin + '/admin/assistant' : student + '/ai/chat')
    if (!isAdmin) await page.getByRole('tab', { name: '校园向导' }).click()
    const input = page.locator('textarea')
    const question = isAdmin ? '目前有多少待审活动？' : '帮我发布一个校园读书会'
    await input.fill(question)
    await page.getByRole('button', { name: '发送', exact: true }).click()
    const links = page.getByLabel(isAdmin ? '继续处理管理待办' : '继续使用校园服务')
    await links.waitFor()
    assert.equal(await input.inputValue(), question)
    await links.getByRole('link', { name: isAdmin ? '待审活动' : '发布活动', exact: true }).click()
    await page.waitForURL(isAdmin ? '**/admin/audit/activity?view=pending' : '**/activity/publish')
    console.log('PASS 助手故障后打开业务入口', isAdmin ? '管理端' : '学生端')
  })
}

function campusFallback(browser) {
  return withPage(browser, 390, 844, async (context, page) => {
    await authenticate(context, false)
    await page.addInitScript(() => {
      const getContext = HTMLCanvasElement.prototype.getContext
      HTMLCanvasElement.prototype.getContext = function (type, ...args) {
        return /webgl/i.test(type) ? null : getContext.call(this, type, ...args)
      }
    })
    await page.goto(student + '/campus-3d')
    await page.getByRole('button', { name: '从导览打开校园服务' }).click()
    await page.locator('.map-zone').getByRole('button', { name: /校园活动/ }).click()
    await page.waitForURL('**/activity')
    await page.getByRole('heading', { name: /校园活动/ }).first().waitFor()
    console.log('PASS WebGL 不可用时可从导览进入校园活动')
  })
}

function timelineImages(browser) {
  return withPage(browser, 390, 844, async (context, page) => {
    const legacy = await (await context.request.get(student + '/api/post/list?pageSize=10')).text()
    const compact = await (await context.request.get(student + '/api/post/list?pageSize=10&compactImages=true')).text()
    assert.ok(compact.length < legacy.length * 0.2, '列表仍携带完整图片')
    const post = JSON.parse(compact).data.list.find(row => row.imageList.some(url => url.startsWith('/api/post/')))
    assert.ok(post, '缺少带图片的演示动态')
    const index = post.imageList.findIndex(url => url.startsWith('/api/post/'))
    const image = await context.request.get(student + post.imageList[index])
    assert.ok(image.ok() && image.headers()['content-type'].startsWith('image/'))
    const original = JSON.parse(legacy).data.list.find(row => row.id === post.id).imageList[index]
    assert.deepEqual(await image.body(), Buffer.from(original.split(',')[1], 'base64'))
    await page.goto(student + '/social')
    await page.waitForFunction(() => [...document.querySelectorAll('.post-card img')].some(
      img => img.complete && img.naturalWidth > 0 && img.getAttribute('src')?.startsWith('/api/post/')
    ))
    console.log('PASS 动态按需加载图片，原图字节一致')
  })
}

async function main() {
  assert.ok(adminLogin.password && process.env.E2E_STUDENT_PASSWORD_FILE, '需要隔离环境的管理员密码和学生密码文件')
  const options = process.env.E2E_BROWSER_CHANNEL ? { channel: process.env.E2E_BROWSER_CHANNEL } : {}
  const browser = await chromium.launch(options)
  try {
    const results = await Promise.allSettled([
      adminSearch(browser, 1440), adminSearch(browser, 390), studentSearch(browser),
      assistantFallback(browser, true), assistantFallback(browser, false), campusFallback(browser), timelineImages(browser)
    ])
    for (const result of results) {
      if (result.status === 'rejected') {
        console.error(result.reason.message)
        process.exitCode = 1
      }
    }
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error.message); process.exitCode = 1 })
