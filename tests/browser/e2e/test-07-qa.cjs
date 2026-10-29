/**
 * 测试类别 7：校园问答
 * 发布问题 → 另一用户回答 → 提问者收到通知 → 采纳回答 → 状态已解决 → 分页
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '07-校园问答';
  console.log(`\n=== ${C} ===`);

  const sellerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.seller.studentNo, h.TEST_ACCOUNTS.seller.password);
  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const seller = h.apiWithToken(request, sellerLogin.token);
  const buyer = h.apiWithToken(request, buyerLogin.token);

  // 1. 发布问题
  const qRes = await seller.post('/qa', {
    title: `${h.PREFIX}怎么申请图书馆自习室？`,
    content: '求问图书馆自习室怎么预约？',
    tags: '学习'
  });
  let qId;
  if (qRes.code === 200) {
    qId = qRes.data.id || qRes.data;
    h.record(C, '发布问题', 'PASS', `id=${qId}`);
  } else {
    h.record(C, '发布问题', 'FAIL', qRes.message);
  }

  // 2. 另一用户回答
  if (qId) {
    const aRes = await buyer.post(`/qa/${qId}/answer`, { content: `${h.PREFIX}登录图书馆官网预约即可` });
    let answerId;
    if (aRes.code === 200) {
      answerId = aRes.data.id || aRes.data;
      h.record(C, '另一用户回答问题', 'PASS', `answerId=${answerId}`);
    } else {
      h.record(C, '另一用户回答问题', 'FAIL', aRes.message);
    }
    // 3. 提问者收到通知
    const sellerMsgs = await seller.get('/message/list', { pageNum: 1, pageSize: 10 });
    if (sellerMsgs.code === 200) {
      const answerMessage = h.unwrapList(sellerMsgs).find(m =>
        String(m.type).toLowerCase() === 'interact' && String(m.bizType).toLowerCase() === 'qa'
        && String(m.bizId) === String(qId) && String(m.title || '').includes('回答')
      );
      h.record(C, '提问者收到回答通知', answerMessage ? 'PASS' : 'FAIL', answerMessage ? `messageId=${answerMessage.id}` : `未找到 questionId=${qId} 的回答通知`);
    }
    // 4. 采纳回答
    if (answerId) {
      const acceptRes = await seller.put(`/qa/answer/${answerId}/accept`);
      if (acceptRes.code === 200) {
        h.record(C, '采纳回答', 'PASS');
      } else {
        h.record(C, '采纳回答', 'FAIL', acceptRes.message);
      }
      // 问题状态变为已解决
      const detail = await buyer.get(`/qa/${qId}`, { pageNum: 1, pageSize: 20 });
      const question = detail.data?.question || detail.data;
      if (detail.code === 200 && (question.status === 1 || question.status === 'solved' || question.solved === true) && String(question.acceptedAnswerId) === String(answerId)) {
        h.record(C, '问题状态变为已解决', 'PASS', `status=${question.status}`);
      } else {
        h.record(C, '问题状态变为已解决', 'FAIL', `status=${question?.status} acceptedAnswerId=${question?.acceptedAnswerId}`);
      }
    }
  }

  // 5. 分页验证（创建多个问题）
  for (let i = 0; i < 3; i++) {
    await seller.post('/qa', { title: `${h.PREFIX}分页测试问题${i}`, content: `分页测试${i}`, tags: '测试' });
  }
  const list1 = await buyer.get('/qa/list', { pageNum: 1, pageSize: 5 });
  if (list1.code === 200) {
    const total = list1.data.total || (list1.data.list || []).length;
    h.record(C, '问答分页列表加载', 'PASS', `total=${total}`);
  }
  const list2 = await buyer.get('/qa/list', { pageNum: 2, pageSize: 5 });
  if (list2.code === 200) {
    h.record(C, '翻到第二页', 'PASS');
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, buyerLogin.token, buyerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/qa`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '07-qa', 'qa-list');
  h.record(C, '问答列表截图', 'PASS', '', shot);
  await page.close();

  ctx.qa = { qId };
}

module.exports = { run };
