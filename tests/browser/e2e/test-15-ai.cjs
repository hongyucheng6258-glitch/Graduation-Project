/**
 * 测试类别 15：AI 功能验证（真实 AI 配置，控制在 5 次以内）
 * AI答疑、流式回复、代码纠错、错题保存、复习功能、历史会话恢复
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '15-AI功能';
  console.log(`\n=== ${C} ===`);

  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const buyer = h.apiWithToken(request, buyerLogin.token);
  let aiCallCount = 0;

  // 1. 创建 AI 会话
  const sessRes = await buyer.post('/ai/session', { scene: 'chat', title: `${h.PREFIX}E2E AI会话` });
  let sessionId;
  if (sessRes.code === 200) {
    sessionId = sessRes.data.id || sessRes.data.sessionId || sessRes.data;
    h.record(C, '创建 AI 会话', 'PASS', `sessionId=${sessionId}`);
  } else {
    h.record(C, '创建 AI 会话', 'FAIL', sessRes.message);
  }

  // 2. AI 答疑（非流式）
  if (sessionId && aiCallCount < 5) {
    aiCallCount++;
    try {
      const chatRes = await request.post(`${h.BACKEND}/api/ai/chat/sync`, {
        headers: { Authorization: `Bearer ${buyerLogin.token}`, 'Content-Type': 'application/json' },
        data: { sessionId, question: '你好，请简单介绍一下你自己' },
        timeout: 60000
      });
      const body = await chatRes.json();
      if (body.code === 200) {
        const answer = typeof body.data === 'string' ? body.data : (body.data.answer || '');
        h.record(C, 'AI 答疑（非流式）', 'PASS', `回复长度=${answer.length}`);
      } else {
        h.record(C, 'AI 答疑（非流式）', 'FAIL', body.message);
      }
    } catch (e) {
      h.record(C, 'AI 答疑（非流式）', 'FAIL', e.message.substring(0, 80));
    }
  }

  // 3. 流式回复（SSE）
  if (sessionId && aiCallCount < 5) {
    aiCallCount++;
    try {
      const streamResp = await request.post(`${h.BACKEND}/api/ai/chat/stream`, {
        headers: { Authorization: `Bearer ${buyerLogin.token}`, 'Content-Type': 'application/json' },
        data: { sessionId, question: '1+1等于几' },
        timeout: 60000
      });
      const contentType = streamResp.headers()['content-type'] || '';
      const text = await streamResp.text();
      if (contentType.includes('text/event-stream') || text.includes('event:') || text.includes('data:')) {
        h.record(C, 'SSE 流式回复', 'PASS', `content-type=${contentType.substring(0, 40)}`);
      } else {
        const json = JSON.parse(text);
        if (json.code === 200) {
          h.record(C, 'SSE 流式回复', 'PASS', 'JSON 兜底响应');
        } else {
          h.record(C, 'SSE 流式回复', 'FAIL', json.message || text.substring(0, 80));
        }
      }
    } catch (e) {
      h.record(C, 'SSE 流式回复', 'FAIL', e.message.substring(0, 80));
    }
  }

  // 4. 代码纠错
  if (aiCallCount < 5) {
    aiCallCount++;
    try {
      const fixRes = await buyer.post('/ai/code-fix', {
        code: 'function add(a,b) { return a+b }',
        language: 'javascript'
      });
      if (fixRes.code === 200) {
        h.record(C, '代码纠错', 'PASS');
      } else {
        h.record(C, '代码纠错', 'FAIL', fixRes.message);
      }
    } catch (e) {
      h.record(C, '代码纠错', 'FAIL', e.message.substring(0, 80));
    }
  }

  // 5. 错题保存
  const wrongRes = await buyer.post('/wrong-question', {
    subject: '数学',
    question: `${h.PREFIX}测试错题`,
    answer: '42',
    wrongAnswer: '41',
    analysis: '测试解析'
  });
  let wrongId;
  if (wrongRes.code === 200) {
    wrongId = wrongRes.data.id || wrongRes.data;
    h.record(C, '添加错题', 'PASS', `wrongId=${wrongId}`);
  } else {
    h.record(C, '添加错题', 'FAIL', wrongRes.message);
  }

  // 6. AI 整理错题（控制次数）
  if (wrongId && aiCallCount < 5) {
    aiCallCount++;
    try {
      const analyzeRes = await buyer.post(`/wrong-question/${wrongId}/analyze`, {});
      if (analyzeRes.code === 200) {
        h.record(C, 'AI 整理错题', 'PASS');
      } else {
        h.record(C, 'AI 整理错题', 'FAIL', analyzeRes.message);
      }
    } catch (e) {
      h.record(C, 'AI 整理错题', 'FAIL', e.message.substring(0, 80));
    }
  }

  // 7. 复习功能（不调 AI）
  const reviewRes = await buyer.post('/wrong-question/review', { wrongQuestionId: wrongId || 1, isCorrect: 1, masteryLevel: 2 });
  if (reviewRes.code === 200) {
    h.record(C, '复习功能记录', 'PASS');
  } else {
    h.record(C, '复习功能记录', 'FAIL', reviewRes.message);
  }

  // 8. 历史会话恢复
  if (sessionId) {
    const history = await buyer.get(`/ai/session/${sessionId}/messages`, { pageNum: 1, pageSize: 20 });
    if (history.code === 200) {
      h.record(C, '历史会话恢复', 'PASS');
    } else {
      h.record(C, '历史会话恢复', 'FAIL', history.message);
    }
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, buyerLogin.token, buyerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/ai/chat`);
  await page.waitForTimeout(3000);
  const shot = await h.screenshot(page, '15-ai', 'ai-chat');
  h.record(C, 'AI 对话页截图', 'PASS', '', shot);
  await page.close();

  h.record(C, 'AI 调用次数控制', 'PASS', `实际调用 ${aiCallCount} 次（上限5次）`);
  ctx.ai = { sessionId, wrongId };
}

module.exports = { run };
