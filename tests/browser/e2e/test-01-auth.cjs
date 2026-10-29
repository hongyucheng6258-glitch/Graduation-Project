/**
 * 测试类别 1：注册、登录、退出、权限隔离
 */
const h = require('./helpers.cjs');

async function run(browser, request) {
  const C = '01-注册登录权限';
  console.log(`\n=== ${C} ===`);

  // 1. 注册新测试账号 E2E_2201（如果已存在则忽略）
  let reg1 = await h.apiRegisterStudent(request, h.TEST_ACCOUNTS.seller.studentNo, h.TEST_ACCOUNTS.seller.password, h.TEST_ACCOUNTS.seller.nickname);
  if (reg1.code === 200) {
    h.record(C, '注册 E2E_2201', 'PASS', '新账号注册成功');
  } else if (reg1.message && /已存在|已注册|duplicate|学号/.test(reg1.message)) {
    h.record(C, '注册 E2E_2201', 'PASS', '账号已存在（幂等）');
  } else {
    h.record(C, '注册 E2E_2201', 'FAIL', `code=${reg1.code} msg=${reg1.message}`);
  }

  // 注册 E2E_2202, E2E_2203
  for (const acc of [h.TEST_ACCOUNTS.buyer, h.TEST_ACCOUNTS.third]) {
    const r = await h.apiRegisterStudent(request, acc.studentNo, acc.password, acc.nickname);
    if (r.code === 200 || (r.message && /已存在|duplicate|学号/.test(r.message))) {
      h.record(C, `注册 ${acc.studentNo}`, 'PASS');
    } else {
      h.record(C, `注册 ${acc.studentNo}`, 'FAIL', `code=${r.code} msg=${r.message}`);
    }
  }

  // 2. API 登录验证
  let sellerLogin;
  try {
    sellerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.seller.studentNo, h.TEST_ACCOUNTS.seller.password);
    h.record(C, 'API 登录 E2E_2201', 'PASS', `user id=${sellerLogin.userInfo.id}`);
  } catch (e) {
    h.record(C, 'API 登录 E2E_2201', 'FAIL', e.message);
  }

  // 错误密码登录应失败
  const badLogin = await request.post(`${h.BACKEND}/api/auth/login`, {
    data: { studentNo: h.TEST_ACCOUNTS.seller.studentNo, password: 'wrong-password', captchaId: 't', captchaCode: 't' }
  });
  const badBody = await badLogin.json();
  if (badBody.code !== 200) {
    h.record(C, '错误密码登录被拒', 'PASS', `code=${badBody.code}`);
  } else {
    h.record(C, '错误密码登录被拒', 'FAIL', '应该返回错误码');
  }

  // 3. UI 登录 + 退出（使用卖家账号）
  const page = await browser.newPage();
  await h.studentLogin(page, h.TEST_ACCOUNTS.seller.studentNo, h.TEST_ACCOUNTS.seller.password);
  const shot1 = await h.screenshot(page, '01-auth', 'ui-login-success');
  const token = await page.evaluate(() => localStorage.getItem('token'));
  if (token) {
    h.record(C, 'UI 登录成功并写入 token', 'PASS', '截图: ui-login-success.png', shot1);
  } else {
    h.record(C, 'UI 登录成功并写入 token', 'FAIL', 'localStorage 无 token', shot1);
  }

  // 未登录访问需鉴权页面应跳转登录
  await page.context().clearCookies();
  await page.evaluate(() => { localStorage.removeItem('token'); localStorage.removeItem('userInfo'); });
  await page.goto(`${h.STUDENT_WEB}/ai/chat`);
  await page.waitForTimeout(2000);
  const urlAfter = page.url();
  if (urlAfter.includes('/login') || await page.locator('input[type="password"]').count() > 0) {
    h.record(C, '未登录访问鉴权页跳转登录', 'PASS', `当前URL: ${urlAfter}`);
  } else {
    const shot = await h.screenshot(page, '01-auth', 'auth-redirect-fail');
    h.record(C, '未登录访问鉴权页跳转登录', 'FAIL', `未跳转, URL=${urlAfter}`, shot);
  }

  // 4. 学生账号不能访问管理端接口
  const sellerToken = sellerLogin.token;
  const adminApiTest = await request.get(`${h.BACKEND}/api/admin/users?pageNum=1&pageSize=1`, {
    headers: { Authorization: `Bearer ${sellerToken}` }
  });
  const adminBody = await adminApiTest.json();
  if (adminBody.code === 403 || adminBody.code === 401 || adminApiTest.status() === 403) {
    h.record(C, '学生访问管理端接口被拒', 'PASS', `code=${adminBody.code || adminApiTest.status()}`);
  } else {
    h.record(C, '学生访问管理端接口被拒', 'FAIL', `code=${adminBody.code} 未拒绝`);
  }

  // 5. UI 退出登录（通过头像下拉菜单）
  await h.studentLogin(page, h.TEST_ACCOUNTS.seller.studentNo, h.TEST_ACCOUNTS.seller.password);
  await page.goto(`${h.STUDENT_WEB}/`);
  await page.waitForTimeout(2000);
  // 点击头像下拉
  const avatarDrop = page.locator('.el-dropdown:has(.header-avatar)');
  if (await avatarDrop.count() > 0) {
    await avatarDrop.locator('.header-avatar').click();
    await page.waitForTimeout(500);
    const logoutItem = page.locator('.el-dropdown-menu__item:has-text("退出登录")');
    if (await logoutItem.count() > 0) {
      await logoutItem.click();
      await page.waitForTimeout(1000);
      // 处理确认弹窗
      const confirmBtn = page.locator('.el-message-box__btn button, .el-button--primary').filter({ hasText: /确定|确认/ }).first();
      if (await confirmBtn.count() > 0) await confirmBtn.click().catch(() => {});
      await page.waitForTimeout(2000);
      const afterToken = await page.evaluate(() => localStorage.getItem('token'));
      if (!afterToken) {
        h.record(C, 'UI 退出登录清除 token', 'PASS');
      } else {
        h.record(C, 'UI 退出登录清除 token', 'FAIL', 'token 仍存在');
      }
    } else {
      h.record(C, 'UI 退出登录清除 token', 'BLOCK', '下拉菜单中无退出登录项');
    }
  } else {
    const shot = await h.screenshot(page, '01-auth', 'logout-dropdown-not-found');
    h.record(C, 'UI 退出登录清除 token', 'BLOCK', '未找到头像下拉', shot);
  }

  await page.close();
  return { sellerToken, sellerUser: sellerLogin.userInfo };
}

module.exports = { run };
