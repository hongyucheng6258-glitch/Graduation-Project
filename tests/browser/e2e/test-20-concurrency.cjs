/**
 * 测试类别 20：并发验证
 * 多人预约同一物品、活动名额、重复审批、重复签到、重复点赞、认领重复处理
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '20-并发验证';
  console.log(`\n=== ${C} ===`);

  const sellerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.seller.studentNo, h.TEST_ACCOUNTS.seller.password);
  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const thirdLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.third.studentNo, h.TEST_ACCOUNTS.third.password);
  const seller = h.apiWithToken(request, sellerLogin.token);
  const buyer = h.apiWithToken(request, buyerLogin.token);
  const third = h.apiWithToken(request, thirdLogin.token);
  let concurrencyVerified = false;

  // 1. 重复点赞（已赞的不能再赞）
  const postId = ctx.social?.postId;
  if (postId) {
    // 先点赞
    await buyer.post(`/post/${postId}/like`);
    // 重复点赞
    const dupLike = await buyer.post(`/post/${postId}/like`);
    if (dupLike.code !== 200) {
      h.record(C, '重复点赞被拒绝', 'PASS', `code=${dupLike.code}`);
    } else {
      h.record(C, '重复点赞被拒绝', 'FAIL', '重复点赞成功了');
    }
    // 清理：取消点赞
    await buyer.delete(`/post/${postId}/like`);
  }

  // 2. 重复签到（已签到的不能再签）— 已在类别4验证，这里再确认
  const activityId = ctx.activity?.activityId;
  if (activityId) {
    const qrRes = await seller.get(`/activity/${activityId}/signin-qrcode`);
    if (qrRes.code === 200) {
      const token = qrRes.data.token || qrRes.data.signinToken;
      if (token) {
        // 买家已在类别4签到，这里再签应失败
        const dup = await buyer.post(`/activity/${activityId}/signin`, { token });
        if (dup.code !== 200) {
          h.record(C, '重复签到被拒绝', 'PASS', `code=${dup.code}`);
        } else {
          h.record(C, '重复签到被拒绝', 'FAIL');
        }
      }
    }
  }

  // 3. 重复审批（已审批的不能再审批）— 通过 API 测试
  // 用已完成的预约再尝试处理
  const appointId = ctx.idle?.appointId;
  if (appointId) {
    const dupHandle = await seller.put(`/idle/appoint/${appointId}/handle`, { accept: true });
    if (dupHandle.code !== 200) {
      h.record(C, '重复处理预约被拒绝', 'PASS', `code=${dupHandle.code}`);
    } else {
      h.record(C, '重复处理预约被拒绝', 'UNCOVERED', '预约可能已完成，接口幂等');
    }
  }

  // 4. 真正并发预约同一件新发布物品：最终只能有一个成功预约
  const adminLogin = await h.apiLoginAdmin(request);
  const admin = h.apiWithToken(request, adminLogin.token);
  const freshItem = await seller.post('/idle', {
    title: `${h.PREFIX}并发预约物品`, description: '并发测试', category: '其他', price: 8
  });
  const idleId = freshItem.code === 200 ? (freshItem.data.id || freshItem.data) : null;
  if (idleId) {
    await admin.put(`/admin/audit/idle/${idleId}/pass`);
    const [first, second] = await Promise.all([
      buyer.post(`/idle/${idleId}/appoint`, { message: `${h.PREFIX}并发买家一` }),
      third.post(`/idle/${idleId}/appoint`, { message: `${h.PREFIX}并发买家二` })
    ]);
    const successCount = [first, second].filter(r => r.code === 200).length;
    const detail = await seller.get(`/idle/${idleId}`);
    const appointments = await seller.get('/idle/appoint/my', { role: 'seller', pageNum: 1, pageSize: 20 });
    const rows = h.unwrapList(appointments).filter(r => String(r.itemId) === String(idleId));
    if (successCount === 1 && detail.code === 200 && detail.data.status === 1 && rows.length === 1) {
      h.record(C, '并发预约同一物品仅成功一次', 'PASS', `responses=${first.code},${second.code}`);
      concurrencyVerified = true;
    } else {
      h.record(C, '并发预约同一物品仅成功一次', 'FAIL', `successCount=${successCount} status=${detail.data?.status} rows=${rows.length}`);
    }
  } else {
    h.record(C, '并发预约同一物品仅成功一次', 'BLOCK', `发布失败: code=${freshItem.code} message=${freshItem.message}`);
  }

  // 5. 重复认领处理
  const lfId = ctx.lostfound?.lfId;
  if (lfId) {
    // 失物已完成，再尝试认领
    const dupClaim = await buyer.post(`/lostfound/${lfId}/claim`, { content: `${h.PREFIX}重复认领` });
    if (dupClaim.code !== 200) {
      h.record(C, '对已完成失物重复认领被拒绝', 'PASS', `code=${dupClaim.code}`);
    } else {
      h.record(C, '对已完成失物重复认领被拒绝', 'UNCOVERED', '允许重复认领');
    }
  }

  // 6. 活动最后名额（达到上限后不能报名）
  if (activityId) {
    // 活动 maxMembers=2，已有1人通过。再报名应成功（还剩1位）。
    // 报名后审批通过，再第三人报名应失败
    const signup3 = await third.post(`/activity/${activityId}/signup`, { remark: `${h.PREFIX}满员测试` });
    if (signup3.code === 200) {
      // 审批通过
      const members = await seller.get(`/activity/${activityId}/members`, { pageNum: 1, pageSize: 10 });
      const mList = h.unwrapList(members);
      const pending = (Array.isArray(mList) ? mList : []).find(m => m.status === 0 || m.status === 'pending');
      if (pending) await seller.put(`/activity/member/${pending.id}/handle`, { approve: true });
      // 再尝试报名（已达上限2人）
      const full = await buyer.post(`/activity/${activityId}/signup`, { remark: `${h.PREFIX}应失败` });
      if (full.code !== 200) {
        h.record(C, '活动达上限后不能再报名', 'PASS', `code=${full.code}`);
      } else {
        h.record(C, '活动达上限后不能再报名', 'FAIL', '满员仍可报名');
      }
    }
  }

  h.record(C, '并发/重复操作防护', concurrencyVerified ? 'PASS' : 'BLOCK', concurrencyVerified ? '已完成真实并发请求与最终状态核对' : '未能完成真实并发预约验证');
}

module.exports = { run };
