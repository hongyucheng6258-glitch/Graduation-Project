/**
 * 测试类别 10：通知中心
 * 分页、分类筛选、标记已读、全部已读、未读角标、点击跳转、账号隔离
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '10-通知中心';
  console.log(`\n=== ${C} ===`);

  const sellerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.seller.studentNo, h.TEST_ACCOUNTS.seller.password);
  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const seller = h.apiWithToken(request, sellerLogin.token);
  const buyer = h.apiWithToken(request, buyerLogin.token);

  // 1. 通知分页
  const page1 = await seller.get('/message/list', { pageNum: 1, pageSize: 5 });
  if (page1.code === 200) {
    h.record(C, '通知列表分页加载', 'PASS', `total=${page1.data.total ?? '?'}`);
  } else {
    h.record(C, '通知列表分页加载', 'FAIL', page1.message);
  }
  const page2 = await seller.get('/message/list', { pageNum: 2, pageSize: 5 });
  if (page2.code === 200) {
    h.record(C, '翻到第二页通知', 'PASS');
  }

  // 2. 分类筛选
  const sysFilter = await seller.get('/message/list', { type: 'system', pageNum: 1, pageSize: 5 });
  if (sysFilter.code === 200) {
    h.record(C, '按系统通知筛选', 'PASS');
  }
  const interactFilter = await seller.get('/message/list', { type: 'interact', pageNum: 1, pageSize: 5 });
  if (interactFilter.code === 200) {
    h.record(C, '按互动通知筛选', 'PASS');
  }

  // 3. 未读计数
  const unread = await seller.get('/message/unread-count');
  if (unread.code === 200) {
    h.record(C, '未读消息计数', 'PASS', `count=${unread.data}`);
  } else {
    h.record(C, '未读消息计数', 'FAIL', unread.message);
  }

  // 4. 标记单条已读
  const allMsgs = await seller.get('/message/list', { pageNum: 1, pageSize: 20 });
  let unreadId;
  if (allMsgs.code === 200) {
    const list = allMsgs.data.list || allMsgs.data.records || allMsgs.data || [];
    const unreadItem = (Array.isArray(list) ? list : []).find(m => !m.isRead && !m.readStatus);
    if (unreadItem) {
      unreadId = unreadItem.id;
      const markRead = await seller.put(`/message/${unreadId}/read`);
      if (markRead.code === 200) {
        h.record(C, '标记单条通知已读', 'PASS');
      } else {
        h.record(C, '标记单条通知已读', 'FAIL', markRead.message);
      }
    } else {
      h.record(C, '标记单条通知已读', 'BLOCK', '无未读通知');
    }
  }

  // 5. 全部已读
  const markAll = await seller.put('/message/read-all');
  if (markAll.code === 200) {
    h.record(C, '全部标记已读', 'PASS');
  } else {
    h.record(C, '全部标记已读', 'FAIL', markAll.message);
  }

  // 6. 账号隔离（不同用户看到不同消息）
  const buyerMsgs = await buyer.get('/message/list', { pageNum: 1, pageSize: 20 });
  const sellerMsgs = await seller.get('/message/list', { pageNum: 1, pageSize: 20 });
  if (buyerMsgs.code === 200 && sellerMsgs.code === 200) {
    const bList = (buyerMsgs.data.list || buyerMsgs.data.records || buyerMsgs.data || []);
    const sList = (sellerMsgs.data.list || sellerMsgs.data.records || sellerMsgs.data || []);
    // 两个用户的消息列表不应完全相同
    const bIds = (Array.isArray(bList) ? bList : []).map(m => m.id).join(',');
    const sIds = (Array.isArray(sList) ? sList : []).map(m => m.id).join(',');
    if (bIds !== sIds || bList.length !== sList.length) {
      h.record(C, '账号隔离（不同用户消息不同）', 'PASS');
    } else {
      h.record(C, '账号隔离（不同用户消息不同）', 'FAIL', '两用户消息相同');
    }
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, sellerLogin.token, sellerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/message`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '10-message', 'message-center');
  h.record(C, '通知中心截图', 'PASS', '', shot);
  await page.close();
}

module.exports = { run };
