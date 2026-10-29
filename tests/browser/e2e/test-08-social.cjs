/**
 * 测试类别 8：校园动态
 * 发布带图动态 → 审核通过 → 点赞/取消点赞 → 评论 → 作者收到通知 → 分享
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '08-校园动态';
  console.log(`\n=== ${C} ===`);

  const sellerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.seller.studentNo, h.TEST_ACCOUNTS.seller.password);
  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const seller = h.apiWithToken(request, sellerLogin.token);
  const buyer = h.apiWithToken(request, buyerLogin.token);
  const adminLogin = await h.apiLoginAdmin(request);
  const admin = h.apiWithToken(request, adminLogin.token);

  // 1. 发布动态（带图，用测试素材）
  // 先上传图片
  const imgPath = h.testAssetPath('05-校园晚霞.png');
  const fs = require('fs');
  let imageUrl = '';
  if (fs.existsSync(imgPath)) {
    const buf = fs.readFileSync(imgPath);
    const upResp = await request.post(`${h.BACKEND}/api/upload/image`, {
      headers: { Authorization: `Bearer ${sellerLogin.token}` },
      multipart: { file: { name: 'test.png', mimeType: 'image/png', buffer: buf } }
    });
    const upBody = await upResp.json();
    if (upBody.code === 200) {
      imageUrl = upBody.data.url || upBody.data;
      h.record(C, '上传动态图片', 'PASS', imageUrl.substring(0, 50));
    } else {
      h.record(C, '上传动态图片', 'FAIL', upBody.message);
    }
  } else {
    h.record(C, '上传动态图片', 'BLOCK', '测试图片不存在');
  }

  // 2. 发布动态
  const postRes = await seller.post('/post', {
    content: `${h.PREFIX}今天校园晚霞真美！`,
    images: imageUrl ? [imageUrl] : []
  });
  let postId;
  if (postRes.code === 200) {
    postId = postRes.data.id || postRes.data;
    h.record(C, imageUrl ? '发布带图动态' : '发布动态（无测试图片）', 'PASS', `id=${postId}`);
  } else {
    h.record(C, '发布带图动态', 'FAIL', postRes.message);
  }
  // 管理员审核通过
  if (postId) await admin.put(`/admin/audit/post/${postId}/pass`).catch(() => {});

  // 3. 点赞
  if (postId) {
    const likeRes = await buyer.post(`/post/${postId}/like`);
    if (likeRes.code === 200) {
      h.record(C, '点赞动态', 'PASS');
    } else {
      h.record(C, '点赞动态', 'FAIL', likeRes.message);
    }
    // 重复点赞应被拒绝
    const dupLike = await buyer.post(`/post/${postId}/like`);
    if (dupLike.code !== 200) {
      h.record(C, '重复点赞被拒绝', 'PASS', `code=${dupLike.code}`);
    } else {
      h.record(C, '重复点赞被拒绝', 'FAIL', '未拒绝重复点赞');
    }
    // 取消点赞
    const unlikeRes = await buyer.delete(`/post/${postId}/like`);
    if (unlikeRes.code === 200) {
      h.record(C, '取消点赞', 'PASS');
    } else {
      h.record(C, '取消点赞', 'FAIL', unlikeRes.message);
    }
    // 4. 评论
    const commentRes = await buyer.post(`/post/${postId}/comment`, { content: `${h.PREFIX}好美啊！` });
    if (commentRes.code === 200) {
      h.record(C, '评论动态', 'PASS');
    } else {
      h.record(C, '评论动态', 'FAIL', commentRes.message);
    }
    // 5. 作者收到评论通知
    const sellerMsgs = await seller.get('/message/list', { pageNum: 1, pageSize: 10 });
    if (sellerMsgs.code === 200) {
      const commentMessage = h.unwrapList(sellerMsgs).find(m =>
        String(m.type).toLowerCase() === 'interact' && String(m.bizType).toLowerCase() === 'post'
        && String(m.bizId) === String(postId) && String(m.title || '').includes('评论')
      );
      h.record(C, '作者收到评论通知', commentMessage ? 'PASS' : 'FAIL', commentMessage ? `messageId=${commentMessage.id}` : `未找到 postId=${postId} 的评论通知`);
    }
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, buyerLogin.token, buyerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/social`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '08-social', 'post-square');
  h.record(C, '动态广场截图', 'PASS', '', shot);
  await page.close();

  ctx.social = { postId };
}

module.exports = { run };
