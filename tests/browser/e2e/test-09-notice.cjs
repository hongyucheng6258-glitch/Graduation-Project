/**
 * 测试类别 9：公告
 * 公告列表、公告详情（富文本、图片）
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '09-公告';
  console.log(`\n=== ${C} ===`);

  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const buyer = h.apiWithToken(request, buyerLogin.token);

  // 1. 公告列表
  const listRes = await buyer.get('/notice/list', { pageNum: 1, pageSize: 10 });
  let firstId;
  if (listRes.code === 200) {
    const list = listRes.data.list || listRes.data.records || listRes.data || [];
    const arr = Array.isArray(list) ? list : (list.records || list.list || []);
    h.record(C, '公告列表加载', 'PASS', `共 ${arr.length} 条`);
    if (arr.length > 0) firstId = arr[0].id;
  } else {
    h.record(C, '公告列表加载', 'FAIL', listRes.message);
  }

  // 2. 公告详情
  if (firstId) {
    const detail = await buyer.get(`/notice/${firstId}`);
    if (detail.code === 200 && detail.data) {
      h.record(C, '公告详情含内容', 'PASS', `title=${detail.data.title?.substring(0, 20)}`);
    } else {
      h.record(C, '公告详情含内容', 'FAIL', detail.message);
    }
  } else {
    h.record(C, '公告详情', 'BLOCK', '无公告数据');
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, buyerLogin.token, buyerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/notice`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '09-notice', 'notice-list');
  h.record(C, '公告列表截图', 'PASS', '', shot);
  await page.close();
}

module.exports = { run };
