/**
 * 测试类别 6：学习搭子
 * 发布搭子 → 审核通过 → 查看联系方式 → 完成/关闭
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '06-学习搭子';
  console.log(`\n=== ${C} ===`);

  const sellerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.seller.studentNo, h.TEST_ACCOUNTS.seller.password);
  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const seller = h.apiWithToken(request, sellerLogin.token);
  const buyer = h.apiWithToken(request, buyerLogin.token);
  const adminLogin = await h.apiLoginAdmin(request);
  const admin = h.apiWithToken(request, adminLogin.token);

  // 1. 发布搭子
  const pRes = await seller.post('/partner', {
    title: `${h.PREFIX}找自习搭子`,
    subject: '高等数学',
    description: '每天图书馆自习',
    location: '图书馆',
    timeSlot: '晚上'
  });
  let partnerId;
  if (pRes.code === 200) {
    partnerId = pRes.data.id || pRes.data;
    h.record(C, '发布搭子信息', 'PASS', `id=${partnerId}`);
  } else {
    h.record(C, '发布搭子信息', 'FAIL', pRes.message);
  }
  // 2. 审核通过
  if (partnerId) {
    const passRes = await admin.put(`/admin/audit/partner/${partnerId}/pass`);
    if (passRes.code === 200) {
      h.record(C, '管理员审核通过搭子', 'PASS');
    } else {
      h.record(C, '管理员审核通过搭子', 'FAIL', passRes.message);
    }
    // 3. 查看联系方式（另一用户）
    const detail = await buyer.get(`/partner/list`);
    if (detail.code === 200) {
      h.record(C, '搭子列表可查看', 'PASS');
    }
    // 4. 完成/关闭搭子
    const finishRes = await seller.put(`/partner/${partnerId}/finish`);
    if (finishRes.code === 200) {
      h.record(C, '完成/关闭搭子', 'PASS');
    } else {
      h.record(C, '完成/关闭搭子', 'FAIL', finishRes.message);
    }
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, buyerLogin.token, buyerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/partner`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '06-partner', 'partner-list');
  h.record(C, '搭子列表截图', 'PASS', '', shot);
  await page.close();

  ctx.partner = { partnerId };
}

module.exports = { run };
