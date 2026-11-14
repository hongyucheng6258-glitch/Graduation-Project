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

  // 类别 04 用真实签到内容验证重复签到。


  // 3. 重复审批（已审批的不能再审批）— 通过 API 测试
  // 用已完成的预约再尝试处理
  const appointId = ctx.idle?.appointId;
  if (appointId) {
    const dupHandle = await seller.put(`/idle/appoint/${appointId}/handle`, { accept: true });
    if (dupHandle.code !== 200) {
      h.record(C, '重复处理预约被拒绝', 'PASS', `code=${dupHandle.code}`);
    } else {
      h.record(C, '重复处理预约被拒绝', 'FAIL', '预约可能已完成，接口幂等');
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
    const duplicateCount = [first, second].filter(r => r.code === 1009).length;
    const detail = await seller.get(`/idle/${idleId}`);
    const appointments = await seller.get('/idle/appoint/my', { role: 'seller', pageNum: 1, pageSize: 20 });
    const rows = h.unwrapList(appointments).filter(r => String(r.itemId) === String(idleId));
    if (successCount === 1 && duplicateCount === 1 && detail.code === 200 && detail.data.status === 1 && rows.length === 1) {
      h.record(C, '并发预约同一物品仅成功一次', 'PASS', `responses=${first.code},${second.code}`);
      concurrencyVerified = true;
    } else {
      h.record(C, '并发预约同一物品仅成功一次', 'FAIL', `responses=${first.code},${second.code} status=${detail.data?.status} rows=${rows.length}`);
    }
  } else {
    h.record(C, '并发预约同一物品仅成功一次', 'BLOCK', `发布失败: code=${freshItem.code} message=${freshItem.message}`);
  }

  // 5. 重复认领处理
  const lfId = ctx.lostfound?.lfId;
  if (lfId) {
    // 失物已完成，再尝试认领
    const dupClaim = await buyer.post(`/lostfound/${lfId}/claim`, { message: `${h.PREFIX}重复认领` });
    if (dupClaim.code !== 200) {
      h.record(C, '对已完成失物重复认领被拒绝', 'PASS', `code=${dupClaim.code}`);
    } else {
      h.record(C, '对已完成失物重复认领被拒绝', 'FAIL', '允许重复认领');
    }
  }

  // 使用独立的未开始活动，避免签到用例改变报名截止时间。
  const activity = await seller.post('/activity', {
    title: `${h.PREFIX}并发名额验证`, description: '验证最后一个名额', location: '操场',
    category: '体育', maxMembers: 1
  });
  if (activity.code !== 200) throw new Error('无法创建名额验证活动：' + activity.message);
  const activityId = activity.data.id;
  const audit = await admin.put(`/admin/audit/activity/${activityId}/pass`);
  if (audit.code !== 200) throw new Error('无法审核并发名额验证活动');
  const [firstSignup, secondSignup] = await Promise.all([
    buyer.post(`/activity/${activityId}/signup`, { remark: '并发报名一' }),
    third.post(`/activity/${activityId}/signup`, { remark: '并发报名二' })
  ]);
  const responses = [firstSignup, secondSignup];
  const members = await seller.get(`/activity/${activityId}/members`);
  const rows = h.unwrapList(members);
  const detail = await buyer.get(`/activity/${activityId}`);
  const holdsOneSeat = responses.filter(row => row.code === 200).length === 1
    && responses.filter(row => row.code === 400 && /满/.test(row.message)).length === 1
    && rows.length === 1 && rows[0].status === 0 && detail.data?.occupiedSeats === 1
    && detail.data.memberCount === 0 && detail.data.canSignup === false;
  h.record(C, '同时报名仅占用一个待审批名额', holdsOneSeat ? 'PASS' : 'FAIL',
    `responses=${responses.map(row => row.code).join(',')} rows=${rows.length}`);
  if (!holdsOneSeat) throw new Error('并发报名结果不符合名额限制');
  const approved = await seller.put(`/activity/member/${rows[0].id}/handle`, { approve: true });
  const unregistered = firstSignup.code === 200 ? third : buyer;
  const full = await unregistered.post(`/activity/${activityId}/signup`, { remark: '满员验证' });
  h.record(C, '活动达上限后不能再报名',
    approved.code === 200 && full.code === 400 && /满/.test(full.message) ? 'PASS' : 'FAIL',
    `code=${full.code} ${full.message}`);

  h.record(C, '并发/重复操作防护', concurrencyVerified ? 'PASS' : 'BLOCK', concurrencyVerified ? '已完成真实并发请求与最终状态核对' : '未能完成真实并发预约验证');
}

module.exports = { run };
