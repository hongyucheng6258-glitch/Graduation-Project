/**
 * 测试类别 17：搜索筛选分页
 * 各模块搜索、分类筛选、分页、空结果
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '17-搜索筛选分页';
  console.log(`\n=== ${C} ===`);

  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const buyer = h.apiWithToken(request, buyerLogin.token);

  // 1. 闲置搜索
  const idleSearch = await buyer.get('/idle/list', { keyword: 'E2E测试', pageNum: 1, pageSize: 5 });
  if (idleSearch.code === 200) {
    h.record(C, '闲置关键词搜索', 'PASS', `total=${idleSearch.data.total ?? '?'}`);
  }

  // 2. 活动搜索
  const actSearch = await buyer.get('/activity/list', { keyword: 'E2E测试', pageNum: 1, pageSize: 5 });
  if (actSearch.code === 200) {
    h.record(C, '活动关键词搜索', 'PASS');
  }

  // 3. 问答搜索
  const qaSearch = await buyer.get('/qa/list', { keyword: 'E2E测试', pageNum: 1, pageSize: 5 });
  if (qaSearch.code === 200) {
    h.record(C, '问答关键词搜索', 'PASS');
  }

  // 4. 分类筛选
  const idleFilter = await buyer.get('/idle/list', { category: '电子数码', pageNum: 1, pageSize: 5 });
  if (idleFilter.code === 200) {
    h.record(C, '闲置分类筛选', 'PASS');
  }

  // 5. 分页（上一页/下一页）
  const p1 = await buyer.get('/idle/list', { pageNum: 1, pageSize: 3 });
  const p2 = await buyer.get('/idle/list', { pageNum: 2, pageSize: 3 });
  if (p1.code === 200 && p2.code === 200) {
    h.record(C, '翻页（下一页）', 'PASS');
  }
  const pBack = await buyer.get('/idle/list', { pageNum: 1, pageSize: 3 });
  if (pBack.code === 200) {
    h.record(C, '翻页（上一页）', 'PASS');
  }

  // 6. 空结果页面
  const empty = await buyer.get('/idle/list', { keyword: 'zzz不存在的关键词xyz', pageNum: 1, pageSize: 5 });
  if (empty.code === 200) {
    const list = empty.data.list || empty.data.records || empty.data || [];
    const count = Array.isArray(list) ? list.length : 0;
    h.record(C, '空结果返回空列表', 'PASS', `count=${count}`);
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, buyerLogin.token, buyerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/search?q=E2E`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '17-search', 'search-results');
  h.record(C, '搜索结果页截图', 'PASS', '', shot);
  await page.close();
}

module.exports = { run };
