/**
 * 测试类别 12：举报
 * 提交举报 → 管理员后台处理 → 举报者收到通知 → 检查"我的举报"记录页
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '12-举报';
  console.log(`\n=== ${C} ===`);

  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const buyer = h.apiWithToken(request, buyerLogin.token);
  const adminLogin = await h.apiLoginAdmin(request);
  const admin = h.apiWithToken(request, adminLogin.token);

  // 1. 提交举报（举报一个动态）
  const postId = ctx.social?.postId;
  let reportId;
  if (postId) {
    const repRes = await buyer.post('/report', {
      targetType: 'post',
      targetId: postId,
      reasonType: 'spam',
      reason: `${h.PREFIX}测试举报理由`
    });
    if (repRes.code === 200) {
      reportId = repRes.data.id || repRes.data;
      h.record(C, '提交举报', 'PASS', `reportId=${reportId}`);
    } else {
      h.record(C, '提交举报', 'FAIL', repRes.message);
    }
  } else {
    h.record(C, '提交举报', 'BLOCK', '无动态数据');
  }

  // 2. 管理员后台处理
  if (reportId) {
    const reportList = await admin.get('/admin/report/list', { pageNum: 1, pageSize: 10 });
    if (reportList.code === 200) {
      h.record(C, '管理员查看举报列表', 'PASS');
    }
    // 处理举报（通过/驳回）
    const handleRes = await admin.put(`/admin/report/${reportId}/handle`, { action: 'ignore', handleResult: `${h.PREFIX}已核实处理` });
    if (handleRes.code === 200) {
      h.record(C, '管理员处理举报', 'PASS');
    } else {
      h.record(C, '管理员处理举报', 'FAIL', `code=${handleRes.code} message=${handleRes.message}`);
    }
    // 3. 举报者收到处理结果通知
    const buyerMsgs = await buyer.get('/message/list', { pageNum: 1, pageSize: 10 });
    if (buyerMsgs.code === 200) {
      const messages = h.unwrapList(buyerMsgs);
      const resultMessage = messages.find(m => String(m.bizType).toLowerCase() === 'report' && String(m.bizId) === String(reportId) && String(m.content || '').includes('处理完毕'));
      h.record(C, '举报者收到处理结果通知', resultMessage ? 'PASS' : 'FAIL', resultMessage ? `messageId=${resultMessage.id}` : `未找到 reportId=${reportId} 的处理通知`);
    }
  }

  // 4. 检查是否有"我的举报"记录页
  const myReport = await buyer.get('/report/my', { pageNum: 1, pageSize: 10 }).catch(() => null);
  if (myReport && myReport.code === 200) {
    const reports = h.unwrapList(myReport);
    h.record(C, '"我的举报"记录接口存在', reportId && reports.some(r => String(r.id) === String(reportId)) ? 'PASS' : 'FAIL', `records=${reports.length}`);
  } else {
    h.record(C, '"我的举报"记录页存在', 'UNCOVERED', 'API /report/my 不存在或不可访问，记录为缺口');
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, buyerLogin.token, buyerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/message`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '12-report', 'report-notification');
  h.record(C, '举报通知截图', 'PASS', '', shot);
  await page.close();
}

module.exports = { run };
