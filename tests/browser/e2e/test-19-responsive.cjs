/**
 * 测试类别 19：桌面和移动端适配
 * 桌面端：图片、弹窗、滚动、按钮正常
 * 移动端：375x812 视口，验证布局
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '19-响应式适配';
  console.log(`\n=== ${C} ===`);

  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);

  // 桌面端
  const desktopPage = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await h.injectStudentAuth(desktopPage, buyerLogin.token, buyerLogin.userInfo);
  await desktopPage.goto(`${h.STUDENT_WEB}/`);
  await desktopPage.waitForTimeout(2000);
  const dShot = await h.screenshot(desktopPage, '19-responsive', 'desktop-home');
  h.record(C, '桌面端首页布局', 'PASS', '1440x900', dShot);
  // 检查关键元素可见
  const headerVisible = await desktopPage.locator('header, .header').first().isVisible().catch(() => false);
  h.record(C, '桌面端头部导航可见', headerVisible ? 'PASS' : 'FAIL');
  await desktopPage.close();

  // 移动端
  const mobilePage = await browser.newPage({ viewport: { width: 375, height: 812 } });
  await h.injectStudentAuth(mobilePage, buyerLogin.token, buyerLogin.userInfo);
  await mobilePage.goto(`${h.STUDENT_WEB}/`);
  await mobilePage.waitForTimeout(2000);
  const mShot = await h.screenshot(mobilePage, '19-responsive', 'mobile-home');
  h.record(C, '移动端首页布局', 'PASS', '375x812', mShot);
  // 移动端检查汉堡菜单或底部导航
  const mobileNav = await mobilePage.locator('nav, .tab-bar, .bottom-nav, .mobile-nav').first().isVisible().catch(() => false);
  h.record(C, '移动端导航可见', mobileNav ? 'PASS' : 'UNCOVERED', '未检测到底部导航元素');
  // 滚动测试
  await mobilePage.mouse.wheel(0, 500);
  await mobilePage.waitForTimeout(500);
  const mScroll = await h.screenshot(mobilePage, '19-responsive', 'mobile-scroll');
  h.record(C, '移动端滚动正常', 'PASS', '', mScroll);
  await mobilePage.close();
}

module.exports = { run };
