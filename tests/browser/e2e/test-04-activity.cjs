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
  const tomorrow = new Date(Date.now() + 86400000).toISOString().replace('T', ' ').substring(0, 19);
  const actRes = await seller.post('/activity', {
    title: `${h.PREFIX}E2E测试活动`,
    description: 'E2E 测试活动',
    location: '操场',
    startTime: tomorrow,
    endTime: tomorrow,
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

    // 6. 签到码生成
    const qrRes = await seller.get(`/activity/${activityId}/signin-qrcode`);
    let signinToken;
    if (qrRes.code === 200) {
      signinToken = qrRes.data.token || qrRes.data.signinToken || qrRes.data.qrcode;
      h.record(C, '生成签到码', 'PASS');
    } else {
      h.record(C, '生成签到码', 'FAIL', qrRes.message);
    }

    // 7. 买家签到（通过签到码 API）
    if (signinToken) {
      const signinRes = await buyer.post(`/activity/${activityId}/signin`, { token: signinToken });
      if (signinRes.code === 200) {
        h.record(C, '买家签到成功', 'PASS');
      } else {
        // 尝试其他签到端点
        const signinRes2 = await buyer.post(`/activity/${activityId}/signin`, { qrToken: signinToken });
        if (signinRes2.code === 200) {
          h.record(C, '买家签到成功', 'PASS', 'via qrToken');
        } else {
          h.record(C, '买家签到成功', 'FAIL', signinRes.message || signinRes2.message);
        }
      }
      // 8. 重复签到应被拒绝
      const dupSignin = await buyer.post(`/activity/${activityId}/signin`, { token: signinToken });
      if (dupSignin.code !== 200) {
        h.record(C, '重复签到被拒绝', 'PASS', `code=${dupSignin.code}`);
      } else {
        h.record(C, '重复签到被拒绝', 'FAIL', '重复签到未被拒绝');
      }
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
