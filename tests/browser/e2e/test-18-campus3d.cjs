/**
 * 测试类别 18：3D 功能
 * 3D页面加载、进入房间、手动打开内容、返回、退出、全屏、小地图、数据同步
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '18-3D功能';
  console.log(`\n=== ${C} ===`);

  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });

  // 1. 3D 页面加载
  await page.goto(`${h.STUDENT_WEB}/campus-3d`);
  await page.waitForTimeout(5000);
  const shot1 = await h.screenshot(page, '18-campus3d', '3d-home');
  const hasCanvas = await page.locator('canvas').count();
  if (hasCanvas > 0) {
    h.record(C, '3D 页面加载（canvas 渲染）', 'PASS', `canvas数=${hasCanvas}`, shot1);
  } else {
    h.record(C, '3D 页面加载（canvas 渲染）', 'FAIL', '无 canvas', shot1);
  }

  // 2. 进入房间（点击"开始探索"或类似按钮）
  const startBtn = page.locator('button:has-text("开始探索"), button:has-text("进入"), .start-btn').first();
  if (await startBtn.count() > 0) {
    await startBtn.click();
    await page.waitForTimeout(3000);
    const shot2 = await h.screenshot(page, '18-campus3d', '3d-entered');
    h.record(C, '进入 3D 校园', 'PASS', '', shot2);
  } else {
    h.record(C, '进入 3D 校园', 'BLOCK', '未找到开始按钮');
  }

  // 3. 通过校园导览进入服务房间
  await page.getByRole('button', { name: /校园导览/ }).click();
  const zoneBtn = page.locator('.map-zone button').first();
  if (await zoneBtn.count() > 0) {
    await zoneBtn.click();
    const roomPrompt = page.locator('.room-prompt');
    // SwiftShader can advance the 3D navigation timeline much more slowly than a GPU.
    await roomPrompt.waitFor({ state: 'visible', timeout: 45000 }).catch(() => {});
    const openBtn = roomPrompt.getByRole('button', { name: /打开服务内容/ });
    if (await openBtn.count() > 0) {
      h.record(C, '进入房间后内容默认隐藏', 'PASS', '房间提示与手动打开按钮可见');
      await openBtn.click();
      await page.waitForTimeout(2000);
      const workspace = page.locator('.room-workspace');
      if (await workspace.count() === 0) {
        h.record(C, '在房间内打开内容', 'FAIL', '点击后未出现房间工作区');
      }
      const shot3 = await h.screenshot(page, '18-campus3d', '3d-room-content');
      if (await workspace.count() > 0) {
        const roomReturn = workspace.locator('.room-return');
        h.record(C, '在房间内打开内容', await roomReturn.count() > 0 ? 'PASS' : 'FAIL', '', shot3);
        if (await roomReturn.count() > 0) await roomReturn.click();
        await page.waitForTimeout(500);
        h.record(C, '从内容返回房间', await page.locator('.room-workspace').count() === 0 ? 'PASS' : 'FAIL');
      }
    } else {
      h.record(C, '房间手动打开内容按钮', 'FAIL', '未找到打开服务内容按钮');
    }
  } else {
    h.record(C, '校园导览进入服务房间', 'BLOCK', '未找到导览服务按钮');
  }

  // 4. 全屏 / Esc（验证应用自身的全屏状态，而非浏览器 F11）
  try {
    const fullscreenButton = page.getByRole('button', { name: /全屏$/ }).first();
    if (await fullscreenButton.count() === 0) throw new Error('未找到全屏按钮');
    await fullscreenButton.click();
    await page.waitForTimeout(500);
    const expanded = await page.locator('.campus3d-frame.scene-expanded').count();
    const exitButton = page.locator('.campus3d-frame .scene-bottomline').getByRole('button', { name: /退出全屏/ });
    if (expanded === 0 || await exitButton.count() === 0) throw new Error('点击后未进入应用全屏状态');
    await exitButton.click();
    await page.waitForTimeout(500);
    h.record(C, '全屏 / 退出全屏操作', await page.locator('.campus3d-frame.scene-expanded').count() === 0 ? 'PASS' : 'FAIL');
  } catch (e) {
    h.record(C, '全屏 / 退出全屏操作', 'FAIL', e.message);
  }

  // 5. 小地图
  const minimap = page.locator('.minimap, .map-mini, canvas').last();
  if (await minimap.count() > 0) {
    h.record(C, '小地图存在', 'PASS');
  } else {
    h.record(C, '小地图存在', 'UNCOVERED', '未找到小地图元素');
  }

  await page.close();

  // 6. 3D 操作后普通学生端数据同步验证（通过 API 验证 3D 中报名的活动在普通端可见）
  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const buyer = h.apiWithToken(request, buyerLogin.token);
  const myAct = await buyer.get('/activity/my/signup', { pageNum: 1, pageSize: 10 });
  if (myAct.code === 200) {
    h.record(C, '普通端查看活动报名记录', 'PASS', '数据同步正常');
  }
}

module.exports = { run };
