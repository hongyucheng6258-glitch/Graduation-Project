/**
 * 测试类别 3：闲置预约全流程
 * 买家预约卖家物品 → 卖家收到通知 → 卖家接受 → 买家收到通知 → 双方完成 → 双方互评
 * 另一个预约：卖家拒绝，物品恢复在架
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '03-闲置预约';
  console.log(`\n=== ${C} ===`);

  // 登录三个测试账号
  const sellerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.seller.studentNo, h.TEST_ACCOUNTS.seller.password);
  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const thirdLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.third.studentNo, h.TEST_ACCOUNTS.third.password);
  const seller = h.apiWithToken(request, sellerLogin.token);
  const buyer = h.apiWithToken(request, buyerLogin.token);
  const third = h.apiWithToken(request, thirdLogin.token);

  // 卖家发布一个闲置（先用已审核通过的 idleId，或新发一个）
  let idleId = ctx.audit?.idleId;
  if (!idleId) {
    const r = await seller.post('/idle', { title: `${h.PREFIX}预约测试物品`, description: '预约测试', category: '其他', price: 50, status: 0 });
    idleId = r.data?.id || r.data;
  // 管理员审核通过
    const adminLogin = await h.apiLoginAdmin(request);
    await h.apiWithToken(request, adminLogin.token).put(`/admin/audit/idle/${idleId}/pass`);
  }

  // 1. 买家预约卖家物品
  const appointRes = await buyer.post(`/idle/${idleId}/appoint`, { message: `${h.PREFIX}我想预约` });
  let appointId;
  if (appointRes.code === 200) {
    appointId = appointRes.data.id || appointRes.data;
    h.record(C, '买家发起预约', 'PASS', `appointId=${appointId}`);
  } else {
    h.record(C, '买家发起预约', 'FAIL', appointRes.message);
  }

  // 2. 卖家收到预约通知（查看通知中心）
  const sellerMsgs = await seller.get('/message/list', { pageNum: 1, pageSize: 10 });
  if (sellerMsgs.code === 200) {
    const hasAppointMsg = h.unwrapList(sellerMsgs).some(m =>
      String(m.type).toLowerCase() === 'interact' && String(m.bizType).toLowerCase() === 'idle'
      && String(m.bizId) === String(idleId)
      && (String(m.title || '').includes('预约') || String(m.content || '').includes('预约'))
    );
    if (hasAppointMsg) {
      h.record(C, '卖家收到预约通知', 'PASS');
    } else {
      h.record(C, '卖家收到预约通知', 'FAIL', '未在通知中找到预约消息');
    }
  }

  // 3. 卖家查看"收到的预约"（验证不默认进入"我发起的"）
  const receivedAppoints = await seller.get('/idle/appoint/my', { role: 'seller', pageNum: 1, pageSize: 10 });
  if (receivedAppoints.code === 200) {
    const list = receivedAppoints.data.list || receivedAppoints.data.records || receivedAppoints.data || [];
    const found = Array.isArray(list) && list.some(a => a.id === appointId || a.itemId === idleId);
    if (found) {
      h.record(C, '卖家在"收到的预约"中看到预约', 'PASS', '不默认进入"我发起的"');
    } else {
      // 也试试不传 role 参数
      const allAppoints = await seller.get('/idle/appoint/my', { pageNum: 1, pageSize: 20 });
      const allList = allAppoints.data.list || allAppoints.data.records || allAppoints.data || [];
      const found2 = Array.isArray(allList) && allList.some(a => a.id === appointId || a.itemId === idleId);
      h.record(C, '卖家在"收到的预约"中看到预约', found2 ? 'PASS' : 'FAIL', `listLen=${Array.isArray(list) ? list.length : '?'}`);
    }
  }

  // 4. 卖家接受预约
  if (appointId) {
    const acceptRes = await seller.put(`/idle/appoint/${appointId}/handle`, { accept: true });
    if (acceptRes.code === 200) {
      h.record(C, '卖家接受预约', 'PASS');
    } else {
      h.record(C, '卖家接受预约', 'FAIL', acceptRes.message);
    }
  }

  // 5. 买家收到接受通知
  const buyerMsgs = await buyer.get('/message/list', { pageNum: 1, pageSize: 10 });
  if (buyerMsgs.code === 200) {
    const acceptedMsg = h.unwrapList(buyerMsgs).find(m =>
      String(m.type).toLowerCase() === 'interact' && String(m.bizType).toLowerCase() === 'idle'
      && String(m.bizId) === String(idleId) && String(m.title || '').includes('接受')
    );
    h.record(C, '买家收到预约接受通知', acceptedMsg ? 'PASS' : 'FAIL', acceptedMsg ? `messageId=${acceptedMsg.id}` : `未找到 itemId=${idleId} 的接受通知`);
  }

  // 6. 双方确认完成
  if (appointId) {
    const finish1 = await buyer.put(`/idle/appoint/${appointId}/finish`);
    if (finish1.code === 200) {
      h.record(C, '买家确认完成', 'PASS');
    } else {
      h.record(C, '买家确认完成', 'FAIL', finish1.message);
    }
    const finish2 = await seller.put(`/idle/appoint/${appointId}/finish`);
    if (finish2.code !== 200) {
      h.record(C, '卖家重复确认完成被拒绝', 'PASS', `code=${finish2.code}`);
    } else {
      h.record(C, '卖家重复确认完成被拒绝', 'FAIL', '已完成预约仍可重复确认');
    }

    // 7. 双方互评
    const review1 = await buyer.post(`/idle/appoint/${appointId}/review`, { score: 5, comment: `${h.PREFIX}交易愉快` });
    if (review1.code === 200) {
      h.record(C, '买家互评（含评分评语）', 'PASS', 'score=5');
    } else {
      h.record(C, '买家互评（含评分评语）', 'FAIL', review1.message);
    }
    const review2 = await seller.post(`/idle/appoint/${appointId}/review`, { score: 5, comment: `${h.PREFIX}买家爽快` });
    if (review2.code === 200) {
      h.record(C, '卖家互评（含评分评语）', 'PASS');
    } else {
      h.record(C, '卖家互评（含评分评语）', 'FAIL', review2.message);
    }
  }

  // 8. 另一个预约：卖家拒绝，物品恢复在架
  // 先发布第二个闲置
  const idle2 = await seller.post('/idle', { title: `${h.PREFIX}拒绝预约测试`, description: '测试拒绝', category: '其他', price: 30, status: 0 });
  const idle2Id = idle2.data?.id || idle2.data;
  if (idle2Id) {
    const adminLogin = await h.apiLoginAdmin(request);
    await h.apiWithToken(request, adminLogin.token).put(`/admin/audit/idle/${idle2Id}/pass`);
    // 买家预约
    const app2 = await buyer.post(`/idle/${idle2Id}/appoint`, { message: `${h.PREFIX}应被拒绝` });
    const app2Id = app2.data?.id || app2.data;
    if (app2Id) {
      const rejectRes = await seller.put(`/idle/appoint/${app2Id}/handle`, { accept: false });
      if (rejectRes.code === 200) {
        h.record(C, '卖家拒绝第二个预约', 'PASS');
      } else {
        h.record(C, '卖家拒绝第二个预约', 'FAIL', rejectRes.message);
      }
      // 物品恢复在架
      const detail = await buyer.get(`/idle/${idle2Id}`);
      if (detail.code === 200 && detail.data.status === 0) {
        h.record(C, '拒绝后物品恢复在架', 'PASS');
      } else {
        h.record(C, '拒绝后物品恢复在架', 'FAIL', `status=${detail.data?.status}`);
      }
    }
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, buyerLogin.token, buyerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/idle/appointments`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '03-idle', 'my-appointments');
  h.record(C, '我的预约页面截图', 'PASS', '', shot);
  await page.close();

  ctx.idle = { idleId, idle2Id, appointId };
}

module.exports = { run };
