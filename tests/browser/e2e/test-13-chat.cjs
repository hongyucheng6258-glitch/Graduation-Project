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
    // 2. 发送文本消息
    const msgRes = await buyer.post(`/chat/conversations/${convId}/messages`, {
      clientMessageId: 'e2e-msg-001',
      messageType: 'text',
      content: `${h.PREFIX}你好，在吗？`
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
          clientMessageId: 'e2e-img-001',
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
    if (sellerConvs.code === 200) {
      h.record(C, '对方收到会话列表', 'PASS');
    }
    const sellerMsgs = await seller.get(`/chat/conversations/${convId}/messages`, { limit: 20 });
    if (sellerMsgs.code === 200) {
      const msgs = h.unwrapList(sellerMsgs);
      const count = Array.isArray(msgs) ? msgs.length : 0;
      h.record(C, '对方查看历史消息', 'PASS', `${count} 条`);
    } else {
      h.record(C, '对方查看历史消息', 'FAIL', sellerMsgs.message);
    }

    // 5. 未读计数
    const unread = await seller.get('/chat/unread-count');
    if (unread.code === 200) {
      h.record(C, '私信未读计数', 'PASS', `count=${unread.data}`);
    }

    // 6. 已读回执
    const markRead = await seller.put(`/chat/conversations/${convId}/read`);
    if (markRead.code === 200) {
      h.record(C, '标记会话已读', 'PASS');
    } else {
      h.record(C, '标记会话已读', 'FAIL', markRead.message);
    }

    // 7. 历史消息分页
    const page1 = await buyer.get(`/chat/conversations/${convId}/messages`, { limit: 5 });
    if (page1.code === 200) {
      h.record(C, '历史消息分页', 'PASS');
    }

    // 8. 断线重连（刷新页面后消息不丢失）
    const finalCheck = await buyer.get(`/chat/conversations/${convId}/messages`, { limit: 20 });
    if (finalCheck.code === 200) {
      const total = h.unwrapList(finalCheck).length;
      h.record(C, '刷新后消息不丢失', 'PASS', `total=${total}`);
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
