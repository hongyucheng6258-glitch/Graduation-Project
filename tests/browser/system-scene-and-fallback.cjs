const path = require('node:path');
const fs = require('node:fs');
const outputDir = process.env.CAMPUS_TEST_OUTPUT || path.join(require('node:os').tmpdir(), 'campus-browser-checks');
fs.mkdirSync(outputDir, { recursive: true });
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');
const assert = require('node:assert/strict');
const BASE = 'http://localhost:5173';

// 通用：登录态 + 舒适模式（镜头瞬时到位）+ 监听未处理 rejection 与 WebGL 上下文。
async function seedContext(page) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'browser-test-only');
    localStorage.setItem('userInfo', JSON.stringify({ id: 9, nickname: '测试同学' }));
    localStorage.setItem('wutong-campus-comfort', 'true');
    window.__rejections = [];
    window.addEventListener('unhandledrejection', (e) => {
      window.__rejections.push(String((e.reason && e.reason.message) || e.reason));
    });
    window.__gl = { created: 0, lost: 0 };
    const orig = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (type, ...rest) {
      if (typeof type === 'string' && /webgl/i.test(type)) {
        window.__gl.created += 1;
        this.addEventListener('webglcontextlost', () => { window.__gl.lost += 1; });
      }
      return orig.apply(this, [type, ...rest]);
    };
  });
}
// 房间内所有接口的兜底 200，避免空数据请求报错。
async function stubApi(page, extra = () => false) {
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    const p = url.pathname;
    if (!p.startsWith('/api/')) return route.continue();
    if (await extra(route, p, url)) return true;
    let data = {};
    if (p.endsWith('/list')) data = { list: [], total: 0 };
    else if (p.endsWith('/conversations') || p.endsWith('/sessions') || p.endsWith('/subjects') || p.endsWith('/today') || p.endsWith('/messages')) data = [];
    else if (p.endsWith('/unread-count')) data = { count: 0 };
    else if (p.endsWith('/ws-ticket')) data = { ticket: 'test' };
    return route.fulfill({ json: { code: 200, data } });
  });
}
async function openRoom(page, roomName) {
  await page.getByRole('button', { name: '校园导览', exact: false }).first().click();
  await page.locator('.map-zone button').filter({ hasText: roomName }).click();
  await page.getByRole('button', { name: '打开服务内容' }).click();
}
async function closeRoom(page) {
  await page.getByRole('button', { name: '返回房间', exact: false }).first().click();
}

(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true, args: ['--no-sandbox', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const passed = [];

  // ---------- A. Monaco CDN 被拦截：降级 textarea，可编辑/选语言/可提交，无未处理 rejection ----------
  {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await seedContext(page);
    await page.route('**/cdn.jsdelivr.net/**', (route) => route.abort());
    const writes = [];
    await stubApi(page, async (route, p) => {
      if (route.request().method() !== 'GET') writes.push({ p, data: route.request().postDataJSON() });
      if (p === '/api/ai/code-fix') { await route.fulfill({ json: { code: 200, data: '**问题定位**：缺少分号导致编译失败。\n\n修复后即可运行。' } }); return true; }
      return false;
    });
    await page.goto(`${BASE}/campus-3d`);
    await page.getByRole('button', { name: '开始探索' }).click();
    await openRoom(page, '代码纠错');
    await page.locator('.codefix').waitFor();
    // Monaco 加载失败后必须出现可用的 textarea，且不挂载 Monaco。
    await page.locator('.plain-code-editor').waitFor({ state: 'visible' });
    await page.waitForTimeout(300);
    assert.equal(await page.locator('.monaco-editor').count(), 0, 'Monaco 不应在 CDN 失败时挂载');
    await page.locator('.plain-code-editor').fill('def foo()\n    print("hi")');
    // 切换语言为 Python。
    await page.locator('.codefix .el-select').first().click();
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: 'python' }).first().click();
    await page.getByRole('button', { name: '开始纠错' }).click();
    await page.locator('.result.md-body').waitFor();
    const resultText = await page.locator('.result.md-body').innerText();
    assert.match(resultText, /问题定位/);
    assert.equal(writes.filter((x) => x.p === '/api/ai/code-fix').length, 1, '纠错请求只应发送一次');
    assert.deepEqual(await page.evaluate(() => window.__rejections), [], '不得出现未处理 Promise rejection');
    assert.equal(new URL(page.url()).pathname, '/campus-3d');
    await page.screenshot({ path: path.join(outputDir, 'system-codefix-textarea.png') });
    passed.push('Monaco 失败降级 textarea，可编辑/选语言/提交且无未处理 rejection');
    await closeRoom(page);
    assert.deepEqual(errors, []);
    await page.close();
  }

  // ---------- B. 编辑器资源挂起时离开页面：取消加载，无残留 rejection，场景仍可用 ----------
  {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await seedContext(page);
    // 永不返回，模拟资源卡住；页面离开时必须主动取消。
    await page.route('**/cdn.jsdelivr.net/**', () => new Promise(() => {}));
    await stubApi(page);
    await page.goto(`${BASE}/campus-3d`);
    await page.getByRole('button', { name: '开始探索' }).click();
    await openRoom(page, '代码纠错');
    await page.locator('.codefix').waitFor();
    await page.locator('.plain-code-editor').waitFor({ state: 'visible' }); // 加载中也能直接用基础编辑器
    await page.waitForTimeout(250);
    await closeRoom(page); // 触发 onBeforeUnmount -> cancelCodeEditorLoad
    await page.locator('.room-prompt').waitFor();
    await page.waitForTimeout(700);
    assert.deepEqual(await page.evaluate(() => window.__rejections), [], '离开页面取消加载不得产生 rejection');
    assert.ok(await page.evaluate(() => !!window.__campusScene), '离开服务后 3D 场景仍在');
    passed.push('编辑器加载挂起时离开页面可安全取消');
    assert.deepEqual(errors, []);
    await page.close();
  }

  // ---------- C. 503 维护模式：房间内打开维护页，URL 不变，不清 token；恢复后可返回 ----------
  {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await seedContext(page);
    let maintenance = false;
    await stubApi(page, async (route, p) => {
      if (maintenance) { await route.fulfill({ status: 503, json: { code: 503, message: '系统维护中，请稍后再试' } }); return true; }
      if (p === '/api/home/aggregate') { await route.fulfill({ json: { code: 200, data: {} } }); return true; }
      return false;
    });
    await page.goto(`${BASE}/campus-3d`);
    await page.getByRole('button', { name: '开始探索' }).click();
    // 先正常进入活动房间（房间 memory router 激活），再开启维护并触发一次列表请求。
    await openRoom(page, '校园活动');
    await page.locator('.activity-page').waitFor();
    maintenance = true;
    await page.getByPlaceholder('搜索活动…').press('Enter');
    await page.locator('.room-page-host .maintenance-page').waitFor();
    assert.equal(new URL(page.url()).pathname, '/campus-3d', '维护页必须在房间 memory router 内');
    assert.equal(await page.evaluate(() => localStorage.getItem('token')), 'browser-test-only', '503 不得清除登录');
    await page.screenshot({ path: path.join(outputDir, 'system-maintenance-in-room.png') });
    maintenance = false;
    await page.getByRole('button', { name: '刷新重试' }).click();
    await page.locator('.room-page-host .maintenance-page').waitFor({ state: 'hidden' });
    assert.equal(new URL(page.url()).pathname, '/campus-3d', '恢复后仍停留在 3D 房间地址');
    passed.push('503 在房间内显示维护页、保留登录、恢复后可返回');
    assert.deepEqual(errors, []);
    await page.close();
  }

  // ---------- D. 重复进出 3D：renderer/canvas 与 WebGL 上下文不累积；三档画质与缩放无错 ----------
  {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await seedContext(page);
    await stubApi(page);
    const canvasCount = () => page.locator('canvas.campus-canvas').count();
    await page.goto(`${BASE}/campus-3d`); // 仅首次整页加载，之后全部走 SPA 挂载/卸载
    for (let cycle = 1; cycle <= 3; cycle += 1) {
      if (cycle > 1) {
        await page.locator('.campus3d-entry').first().click(); // 从首页 SPA 重新进入
      }
      await page.locator('canvas.campus-canvas').waitFor();
      assert.equal(await canvasCount(), 1, `第 ${cycle} 次进入应只有 1 个 canvas`);
      assert.ok(await page.evaluate(() => !!window.__campusScene), 'dev 场景实例应已暴露');
      // 三档画质切换 + 视口缩放，均不得报错。
      for (const quality of ['low', 'balanced', 'high']) {
        await page.evaluate((q) => window.__campusScene.setQuality(q), quality);
        await page.setViewportSize({ width: quality === 'low' ? 1280 : 1440, height: quality === 'low' ? 800 : 1000 });
        await page.waitForTimeout(120);
      }
      // SPA 内退出（不刷新页面），触发 onBeforeUnmount 销毁。
      await page.getByRole('button', { name: '返回学生端' }).click();
      await page.waitForTimeout(300);
      assert.equal(await canvasCount(), 0, `第 ${cycle} 次退出后 canvas 应被移除`);
      assert.equal(await page.evaluate(() => window.__campusScene), null, '退出后场景引用应清空');
    }
    await page.waitForTimeout(800);
    const gl = await page.evaluate(() => window.__gl);
    assert.equal(gl.created, 3, `3 次进入应只创建 3 个 WebGL 上下文，实际 ${gl.created}`);
    assert.equal(gl.lost, 3, `3 次退出应释放 3 个 WebGL 上下文，实际 ${gl.lost}`);
    passed.push('重复进出 3D 不累积 canvas/WebGL 上下文；三档画质与窗口缩放正常');
    assert.deepEqual(errors, []);
    await page.close();
  }

  // ---------- E. 房间空间/装饰截图（穿模与主题色人工核查） ----------
  {
    const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await seedContext(page);
    await stubApi(page);
    await page.goto(`${BASE}/campus-3d`);
    await page.getByRole('button', { name: '开始探索' }).click();
    await page.waitForTimeout(400);
    await page.locator('.campus3d-frame').screenshot({ path: path.join(outputDir, 'scene-hall.png') });
    for (const [id, file] of [['activity', 'scene-room-activity'], ['idle', 'scene-room-idle'], ['lost', 'scene-room-lost'], ['code', 'scene-room-code'], ['wrong', 'scene-room-wrong']]) {
      await page.evaluate((roomId) => window.__campusScene.navigate(roomId, { instant: true }), id);
      await page.waitForFunction((roomId) => window.__campusScene && window.__campusScene.getDebug().insideId === roomId, id, { timeout: 5000 });
      await page.waitForTimeout(350);
      await page.locator('.campus3d-frame').screenshot({ path: path.join(outputDir, `${file}.png`) });
    }
    passed.push('已生成大厅与各主题房间截图供穿模/装饰核查');
    assert.deepEqual(errors, []);
    await page.close();
  }

  console.log(JSON.stringify({ passed, outputDir }, null, 2));
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
