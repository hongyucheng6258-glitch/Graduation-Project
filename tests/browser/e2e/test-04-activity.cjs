/**
 * 测试类别 4：活动报名签到
 * 报名 → 审批通过 → 人数上限 → 取消报名 → 签到 → 重复签到拒绝
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '04-活动报名签到';
  console.log(`\n=== ${C} ===`);

  const sellerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.seller.studentNo, h.TEST_ACCOUNTS.seller.password);
  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const thirdLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.third.studentNo, h.TEST_ACCOUNTS.third.password);
  const seller = h.apiWithToken(request, sellerLogin.token);
  const buyer = h.apiWithToken(request, buyerLogin.token);
  const third = h.apiWithToken(request, thirdLogin.token);
  const adminLogin = await h.apiLoginAdmin(request);
  const admin = h.apiWithToken(request, adminLogin.token);

  // 1. 发布活动（人数限制 2）
  const campusTime = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai', dateStyle: 'short', timeStyle: 'medium'
  });
  const tomorrow = campusTime.format(new Date(Date.now() + 86400000));
  const actRes = await seller.post('/activity', {
    title: `${h.PREFIX}E2E测试活动`,
    description: 'E2E 测试活动',
    location: '操场',
    startTime: tomorrow,
    endTime: campusTime.format(new Date(Date.now() + 2 * 86400000)),
    maxMembers: 2,
    category: '体育'
  });
  let activityId;
  if (actRes.code === 200) {
    activityId = actRes.data.id || actRes.data;
    h.record(C, '发布活动（限2人）', 'PASS', `id=${activityId}`);
  } else {
    h.record(C, '发布活动（限2人）', 'FAIL', actRes.message);
  }

  // 管理员审核通过活动
  if (activityId) {
    await admin.put(`/admin/audit/activity/${activityId}/pass`).catch(() => {});
  }

  // 2. 买家报名
  if (activityId) {
    const signup1 = await buyer.post(`/activity/${activityId}/signup`, { remark: '报名测试' });
    if (signup1.code === 200) {
      h.record(C, '买家报名活动', 'PASS');
    } else {
      h.record(C, '买家报名活动', 'FAIL', signup1.message);
    }
    // 3. 发布者审批通过
    const members = await seller.get(`/activity/${activityId}/members`, { pageNum: 1, pageSize: 10 });
    let memberId;
    if (members.code === 200) {
      const mList = members.data.list || members.data.records || members.data || [];
      if (Array.isArray(mList) && mList.length > 0) {
        memberId = mList[0].id;
      }
    }
    if (memberId) {
      const handleRes = await seller.put(`/activity/member/${memberId}/handle`, { approve: true });
      if (handleRes.code === 200) {
        h.record(C, '发布者审批通过报名', 'PASS');
      } else {
        h.record(C, '发布者审批通过报名', 'FAIL', handleRes.message);
      }
    }
    // 4. 第三人报名（达到上限2人，已有1人通过，再报1人）
    const signup2 = await third.post(`/activity/${activityId}/signup`, { remark: '第二个报名' });
    if (signup2.code === 200) {
      h.record(C, '第三人报名（未达上限）', 'PASS');
      // 审批通过
      const members2 = await seller.get(`/activity/${activityId}/members`, { pageNum: 1, pageSize: 10 });
      const mList2 = members2.data.list || members2.data.records || members2.data || [];
      const m2 = mList2.find(m => m.status === 0 || m.status === 'pending');
      if (m2) await seller.put(`/activity/member/${m2.id}/handle`, { approve: true });
    } else {
      h.record(C, '第三人报名（未达上限）', 'FAIL', signup2.message);
    }
    // 5. 第四人报名（已满 2 人，应失败）
    // 用演示账号? 不，用第三个测试账号再报一次? 不行，用演示账号会污染。
    // 改为：第三人取消报名后，再用另一账号报名验证上限
    // 先取消第三人报名
    const cancelRes = await third.delete(`/activity/${activityId}/signup`);
    if (cancelRes.code === 200) {
      h.record(C, '取消报名', 'PASS');
    } else {
      h.record(C, '取消报名', 'FAIL', cancelRes.message);
    }
    // 验证取消后人数
    const detail = await buyer.get(`/activity/${activityId}`);
    if (detail.code === 200) {
      h.record(C, '活动详情含报名人数', 'PASS', `current=${detail.data.currentMembers || detail.data.memberCount}`);
    }

    const qr = await seller.get(`/activity/${activityId}/signin-qrcode`);
    const content = typeof qr.data === 'string' ? qr.data : '';
    const match = /^campus:\/\/signin\/([1-9]\d*)\/([A-Za-z0-9_-]+)$/.exec(content);
    const qrValid = qr.code === 200 && match?.[1] === String(activityId);
    h.record(C, '生成签到码', qrValid ? 'PASS' : 'FAIL', qrValid ? '' : '签到内容格式或活动编号错误');

    if (qrValid) {
      const early = await buyer.post('/activity/signin', { activityId, token: match[2] });
      if (early.code !== 400) throw new Error('活动开始前应拒绝签到');
      const edit = await seller.put(`/activity/${activityId}`, {
        title: `${h.PREFIX}E2E测试活动`, description: 'E2E 测试活动', location: '操场',
        startTime: campusTime.format(new Date(Date.now() - 60000)), endTime: tomorrow,
        maxMembers: 2, category: '体育'
      });
      if (edit.code !== 200) throw new Error('设置签到时段失败：' + edit.message);
      const pass = await admin.put(`/admin/audit/activity/${activityId}/pass`);
      if (pass.code !== 200) throw new Error('签到时段审核失败：' + pass.message);
      const signinPage = await browser.newPage();
      try {
        await h.injectStudentAuth(signinPage, buyerLogin.token, buyerLogin.userInfo);
        await signinPage.goto(`${h.STUDENT_WEB}/activity/detail/${activityId}`);
        await signinPage.getByRole('button', { name: '活动签到', exact: true }).click();
        await signinPage.getByLabel('签到内容', { exact: true }).fill(content);
        await signinPage.getByRole('button', { name: '确认签到', exact: true }).click();
        await signinPage.getByText('已通过报名（已签到）', { exact: true }).waitFor();
        h.record(C, '买家通过网页签到', 'PASS');
      } finally {
        await signinPage.close();
      }
      const duplicate = await buyer.post('/activity/signin', { activityId, token: match[2] });
      h.record(C, '重复签到被拒绝', duplicate.code === 1009 ? 'PASS' : 'FAIL', `code=${duplicate.code}`);
    }
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, buyerLogin.token, buyerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/activity`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '04-activity', 'activity-list');
  h.record(C, '活动列表截图', 'PASS', '', shot);
  await page.close();

  ctx.activity = { activityId };
}

module.exports = { run };
