/**
 * 测试类别 5：失物认领
 * 发布失物 → 另一用户认领 → 发布者处理 → 认领者收到通知 → 归还确认 → 已完成
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '05-失物认领';
  console.log(`\n=== ${C} ===`);

  const sellerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.seller.studentNo, h.TEST_ACCOUNTS.seller.password);
  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const seller = h.apiWithToken(request, sellerLogin.token);
  const buyer = h.apiWithToken(request, buyerLogin.token);
  const adminLogin = await h.apiLoginAdmin(request);
  const admin = h.apiWithToken(request, adminLogin.token);

  // 1. 发布招领信息（type=1 招领，才可被认领）
  const lfRes = await seller.post('/lostfound', {
    title: `${h.PREFIX}捡到校园卡`,
    description: '在图书馆捡到校园卡一张',
    type: 1,
    location: '图书馆三楼',
    images: []
  });
  let lfId;
  if (lfRes.code === 200) {
    lfId = lfRes.data.id || lfRes.data;
    h.record(C, '发布失物信息', 'PASS', `id=${lfId}`);
  } else {
    h.record(C, '发布失物信息', 'FAIL', lfRes.message);
  }
  // 管理员审核通过
  if (lfId) await admin.put(`/admin/audit/lostfound/${lfId}/pass`).catch(() => {});

  // 2. 另一用户发起认领
  if (lfId) {
    const claimRes = await buyer.post(`/lostfound/${lfId}/claim`, { content: `${h.PREFIX}我捡到了，特征匹配` });
    let claimId;
    if (claimRes.code === 200) {
      // POST /claim intentionally returns R<Void>; query the authenticated user's
      // claim record to obtain its id for the subsequent handle/confirm steps.
      const myClaimRes = await buyer.get(`/lostfound/${lfId}/my-claim`);
      if (myClaimRes.code === 200 && myClaimRes.data?.id != null) {
        claimId = myClaimRes.data.id;
        h.record(C, '发起认领', 'PASS', `claimId=${claimId}`);
      } else {
        h.record(C, '发起认领', 'FAIL', `申请已提交但无法读取认领记录: code=${myClaimRes.code} msg=${myClaimRes.message}`);
      }
    } else {
      h.record(C, '发起认领', 'FAIL', claimRes.message);
    }

    // 3. 发布者收到通知，处理认领
    if (claimId) {
      const handleRes = await seller.put(`/lostfound/claim/${claimId}/handle`, { accept: true });
      if (handleRes.code === 200) {
        h.record(C, '发布者同意认领', 'PASS');
      } else {
        h.record(C, '发布者同意认领', 'FAIL', handleRes.message);
      }
      // 4. 认领者收到结果通知
      const buyerMsgs = await buyer.get('/message/list', { pageNum: 1, pageSize: 10 });
      if (buyerMsgs.code === 200) {
        const resultMessage = h.unwrapList(buyerMsgs).find(m =>
          String(m.type).toLowerCase() === 'interact' && String(m.bizType).toLowerCase() === 'lostfound'
          && String(m.bizId) === String(lfId) && String(m.title || '').includes('通过')
        );
        h.record(C, '认领者收到处理结果通知', resultMessage ? 'PASS' : 'FAIL', resultMessage ? `messageId=${resultMessage.id}` : `未找到 lostfoundId=${lfId} 的处理通知`);
      }
      // 5. 归还确认
      const confirmRes = await buyer.put(`/lostfound/claim/${claimId}/confirm`);
      if (confirmRes.code === 200) {
        h.record(C, '归还确认', 'PASS');
      } else {
        h.record(C, '归还确认', 'FAIL', confirmRes.message);
      }
      // 最终状态为已完成
      const detail = await buyer.get(`/lostfound/${lfId}`);
      if (detail.code === 200 && (detail.data.status === 1 || detail.data.status === 'completed')) {
        h.record(C, '失物状态为已完成', 'PASS', `status=${detail.data.status}`);
      } else {
        h.record(C, '失物状态为已完成', 'FAIL', `status=${detail.data?.status}`);
      }
    }
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, sellerLogin.token, sellerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/lostfound`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '05-lostfound', 'lostfound-list');
  h.record(C, '失物列表截图', 'PASS', '', shot);
  await page.close();

  ctx.lostfound = { lfId };
}

module.exports = { run };
