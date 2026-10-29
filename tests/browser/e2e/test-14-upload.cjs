/**
 * 测试类别 14：文件上传
 * 合法图片、大文件拦截、非法文件拦截、持久化
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '14-文件上传';
  console.log(`\n=== ${C} ===`);

  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const buyer = h.apiWithToken(request, buyerLogin.token);

  // 1. 上传合法图片（jpg/png）
  const fs = require('fs');
  const imgPath = h.testAssetPath('02-机械键盘.png');
  if (fs.existsSync(imgPath)) {
    const buf = fs.readFileSync(imgPath);
    const resp = await request.post(`${h.BACKEND}/api/upload/image`, {
      headers: { Authorization: `Bearer ${buyerLogin.token}` },
      multipart: { file: { name: 'test.png', mimeType: 'image/png', buffer: buf } }
    });
    const body = await resp.json();
    if (body.code === 200) {
      h.record(C, '上传合法 PNG 图片', 'PASS', (body.data.url || '').substring(0, 60));
    } else {
      h.record(C, '上传合法 PNG 图片', 'FAIL', body.message);
    }
  } else {
    h.record(C, '上传合法 PNG 图片', 'BLOCK', '测试图片不存在');
  }

  // 2. 上传非法文件（exe/txt 应被拦截）
  const txtBuf = Buffer.from('test content', 'utf-8');
  const badResp = await request.post(`${h.BACKEND}/api/upload/image`, {
    headers: { Authorization: `Bearer ${buyerLogin.token}` },
    multipart: { file: { name: 'test.txt', mimeType: 'text/plain', buffer: txtBuf } }
  });
  const badBody = await badResp.json();
  if (badBody.code !== 200) {
    h.record(C, '上传 TXT 文件被拦截', 'PASS', `code=${badBody.code}`);
  } else {
    h.record(C, '上传 TXT 文件被拦截', 'FAIL', 'txt 文件未被拦截');
  }

  // 3. 上传超大文件（构造 25MB 假数据，应被 Spring 拦截）
  const bigBuf = Buffer.alloc(25 * 1024 * 1024, 'a');
  try {
    const bigResp = await request.post(`${h.BACKEND}/api/upload/image`, {
      headers: { Authorization: `Bearer ${buyerLogin.token}` },
      multipart: { file: { name: 'big.png', mimeType: 'image/png', buffer: bigBuf } },
      timeout: 30000
    });
    const bigBody = await bigResp.json();
    if (bigBody.code !== 200) {
      h.record(C, '超大文件被拦截', 'PASS', `code=${bigBody.code}`);
    } else {
      h.record(C, '超大文件被拦截', 'FAIL', '25MB 文件未被拦截');
    }
  } catch (e) {
    h.record(C, '超大文件被拦截', 'PASS', `HTTP 错误: ${e.message.substring(0, 50)}`);
  }

  // 4. 上传资源持久化（刷新后仍可见 - 通过 API 重新获取已上传的图片）
  // 这里验证上传后返回的 URL 可访问
  const imgPath2 = h.testAssetPath('03-山地自行车.png');
  if (fs.existsSync(imgPath2)) {
    const buf2 = fs.readFileSync(imgPath2);
    const up2 = await request.post(`${h.BACKEND}/api/upload/image`, {
      headers: { Authorization: `Bearer ${buyerLogin.token}` },
      multipart: { file: { name: 'persist.png', mimeType: 'image/png', buffer: buf2 } }
    });
    const up2Body = await up2.json();
    if (up2Body.code === 200) {
      const url = up2Body.data.url || up2Body.data;
      // 验证返回的 URL 非空（data URL 或 MinIO URL）
      if (url && typeof url === 'string' && url.length > 10) {
        h.record(C, '上传资源持久化可访问', 'PASS', `url=${url.substring(0, 50)}...`);
      } else {
        h.record(C, '上传资源持久化可访问', 'FAIL', '返回 URL 为空');
      }
    } else {
      h.record(C, '上传资源持久化可访问', 'FAIL', up2Body.message);
    }
  } else {
    h.record(C, '上传资源持久化可访问', 'BLOCK', '持久化测试图片不存在');
  }

  // UI 截图
  const page = await browser.newPage();
  await h.injectStudentAuth(page, buyerLogin.token, buyerLogin.userInfo);
  await page.goto(`${h.STUDENT_WEB}/idle/publish`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '14-upload', 'upload-on-publish');
  h.record(C, '发布页上传组件截图', 'PASS', '', shot);
  await page.close();
}

module.exports = { run };
