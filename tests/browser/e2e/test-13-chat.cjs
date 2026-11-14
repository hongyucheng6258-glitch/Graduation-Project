const { randomUUID } = require('node:crypto');
/**
 * 测试类别 13：私信
 * 发起会话 → 发送文本 → 发送图片 → 对方收到 → 历史消息分页 → 未读计数 → 已读回执
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '13-私信';
  console.log(`\n=== ${C} ===`);

  const sellerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.seller.studentNo, h.TEST_ACCOUNTS.seller.password);
  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const seller = h.apiWithToken(request, sellerLogin.token);
  const buyer = h.apiWithToken(request, buyerLogin.token);

  // 1. 发起会话
  const convRes = await buyer.post('/chat/conversations', { targetUserId: sellerLogin.userInfo.id });
  let convId;
  if (convRes.code === 200) {
    convId = convRes.data.id || convRes.data.conversationId || convRes.data;
    h.record(C, '发起会话', 'PASS', `convId=${convId}`);
  } else {
    h.record(C, '发起会话', 'FAIL', convRes.message);
  }

  if (convId) {
    const messageRunId = randomUUID();
    const expectedText = `${h.PREFIX}你好，在吗？ ${messageRunId}`;
    // 2. 发送文本消息
    const msgRes = await buyer.post(`/chat/conversations/${convId}/messages`, {
      clientMessageId: messageRunId,
      messageType: 'text',
      content: expectedText
    });
    if (msgRes.code === 200) {
      h.record(C, '发送文本消息', 'PASS');
    } else {
      h.record(C, '发送文本消息', 'FAIL', msgRes.message);
    }

    // 3. 发送图片消息
    const fs = require('fs');
    const imgPath = h.testAssetPath('05-校园晚霞.png');
    if (fs.existsSync(imgPath)) {
      const buf = fs.readFileSync(imgPath);
      const upResp = await request.post(`${h.BACKEND}/api/upload/image`, {
        headers: { Authorization: `Bearer ${buyerLogin.token}` },
        multipart: { file: { name: 'chat.png', mimeType: 'image/png', buffer: buf } }
      });
      const upBody = await upResp.json();
      if (upBody.code === 200) {
        const imgUrl = upBody.data.url || upBody.data;
        const resourceId = upBody.data.resourceId;
        const imgMsg = await buyer.post(`/chat/conversations/${convId}/messages`, {
          clientMessageId: randomUUID(),
          messageType: 'image',
          content: imgUrl,
          resourceId: resourceId || undefined
        });
        if (imgMsg.code === 200) {
          h.record(C, '发送图片消息', 'PASS');
        } else {
          h.record(C, '发送图片消息', 'FAIL', imgMsg.message);
        }
      } else {
        h.record(C, '发送图片消息', 'FAIL', upBody.message);
      }
    } else {
      h.record(C, '发送图片消息', 'BLOCK', '测试图片不存在');
    }

    // 4. 对方收到消息
    const sellerConvs = await seller.get('/chat/conversations');
    const seenConversation = sellerConvs.code === 200 && h.unwrapList(sellerConvs)
      .some(row => String(row.id || row.conversationId) === String(convId));
    h.record(C, '对方收到会话列表', seenConversation ? 'PASS' : 'FAIL');

    const sellerMsgs = await seller.get(`/chat/conversations/${convId}/messages`, { limit: 20 });
    const messages = h.unwrapList(sellerMsgs);
    const sawMessages = sellerMsgs.code === 200 && messages.some(msg => msg.content === expectedText)
      && messages.some(msg => msg.messageType === 'image');
    h.record(C, '对方查看历史消息', sawMessages ? 'PASS' : 'FAIL', `${messages.length} 条`);

    const unread = await seller.get('/chat/unread-count');
    const count = unread.data?.count;
    h.record(C, '私信未读计数', unread.code === 200 && Number(count) >= 2 ? 'PASS' : 'FAIL', `count=${count}`);

    const lastReadMessageId = Math.max(...messages.map(message => Number(message.id)));
    const markRead = await seller.put(`/chat/conversations/${convId}/read`, { lastReadMessageId });
    const afterRead = await seller.get('/chat/unread-count');
    const readSucceeded = markRead.code === 200 && afterRead.code === 200 && Number(afterRead.data?.count) < Number(count);
    h.record(C, '标记会话已读', readSucceeded ? 'PASS' : 'FAIL', markRead.message);

    const history = `/chat/conversations/${convId}/messages`;
    const page1 = await buyer.get(history, { limit: 1 });
    const first = h.unwrapList(page1);
    const page2 = first.length === 1 ? await buyer.get(history, { limit: 1, beforeId: first[0].id }) : {};
    const second = h.unwrapList(page2);
    const paged = page1.code === 200 && page2.code === 200 && second.length === 1 && first[0].id !== second[0].id;
    h.record(C, '历史消息分页', paged ? 'PASS' : 'FAIL');

    const chatPage = await browser.newPage();
    try {
      await h.injectStudentAuth(chatPage, buyerLogin.token, buyerLogin.userInfo);
      await chatPage.goto(`${h.STUDENT_WEB}/chat/${convId}`);
      await chatPage.getByText(expectedText, { exact: true }).waitFor();
      await chatPage.reload();
      await chatPage.getByText(expectedText, { exact: true }).waitFor();
      h.record(C, '刷新后消息不丢失', 'PASS');
    } finally {
      await chatPage.close();
    }
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, buyerLogin.token, buyerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/chat`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '13-chat', 'conversation-list');
  h.record(C, '会话列表截图', 'PASS', '', shot);
  await page.close();

  ctx.chat = { convId };
}

module.exports = { run };
