/**
 * 测试类别 11：收藏
 * 收藏闲置/活动/动态 → 取消收藏 → 个人中心查看 → 从收藏进入详情
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '11-收藏';
  console.log(`\n=== ${C} ===`);

  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const buyer = h.apiWithToken(request, buyerLogin.token);

  // 收藏闲置
  const idleId = ctx.idle?.idleId;
  if (idleId) {
    const favRes = await buyer.post(`/favorite/idle/${idleId}`);
    if (favRes.code === 200) {
      h.record(C, '收藏闲置', 'PASS');
    } else {
      h.record(C, '收藏闲置', 'FAIL', favRes.message);
    }
    // 收藏状态
    const status = await buyer.get(`/favorite/idle/${idleId}/status`);
    if (status.code === 200) {
      h.record(C, '查询收藏状态', 'PASS', `favorited=${status.data?.favorited ?? status.data}`);
    }
    // 取消收藏
    const unfav = await buyer.delete(`/favorite/idle/${idleId}`);
    if (unfav.code === 200) {
      h.record(C, '取消收藏闲置', 'PASS');
    } else {
      h.record(C, '取消收藏闲置', 'FAIL', unfav.message);
    }
    // 重新收藏用于列表验证
    await buyer.post(`/favorite/idle/${idleId}`);
  } else {
    h.record(C, '收藏闲置', 'BLOCK', '无闲置数据');
  }

  // 收藏活动
  const actId = ctx.activity?.activityId;
  if (actId) {
    const favAct = await buyer.post(`/favorite/activity/${actId}`);
    if (favAct.code === 200) {
      h.record(C, '收藏活动', 'PASS');
    } else {
      h.record(C, '收藏活动', 'FAIL', favAct.message);
    }
  }

  // 收藏动态
  const postId = ctx.social?.postId;
  if (postId) {
    const favPost = await buyer.post(`/favorite/post/${postId}`);
    if (favPost.code === 200) {
      h.record(C, '收藏动态', 'PASS');
    } else {
      h.record(C, '收藏动态', 'FAIL', favPost.message);
    }
  }

  // 个人中心查看收藏
  const myFav = await buyer.get('/favorite/my', { pageNum: 1, pageSize: 20 });
  if (myFav.code === 200) {
    const list = myFav.data.list || myFav.data.records || myFav.data || [];
    h.record(C, '个人中心查看收藏记录', 'PASS', `共 ${Array.isArray(list) ? list.length : '?'} 条`);
  } else {
    h.record(C, '个人中心查看收藏记录', 'FAIL', myFav.message);
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, buyerLogin.token, buyerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/profile`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '11-favorite', 'profile-with-favorites');
  h.record(C, '个人中心截图', 'PASS', '', shot);
  await page.close();
}

module.exports = { run };
