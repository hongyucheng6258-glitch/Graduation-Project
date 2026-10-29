/**
 * E2E 测试主运行器
 * 按顺序执行所有测试类别，收集结果，生成报告
 */
const { getExitCode, resolveOnlyCategories } = require('./run-status.cjs');

let only;
try {
  only = resolveOnlyCategories(process.argv);
} catch (error) {
  console.error(error.message);
  process.exit(2);
}
const h = require('./helpers.cjs');
function shouldRun(category) {
  return !only || only.has(category);
}

(async () => {
  console.log('=== Ai-campus E2E 测试开始 ===');
  console.log(`后端: ${h.BACKEND}`);
  console.log(`学生端: ${h.STUDENT_WEB}`);
  console.log(`管理端: ${h.ADMIN_WEB}`);
  console.log(`截图目录: ${h.SHOT_DIR}`);

  const browser = await h.launchBrowser();
  const ctx = await browser.newContext();
  const request = ctx.request;

  const context = {};
  let runtimeError = false;

  try {
    // 类别 1: 注册登录权限
    let authCtx = {};
    if (shouldRun('01')) {
      const t1 = require('./test-01-auth.cjs');
      authCtx = await t1.run(browser, request);
      Object.assign(context, authCtx);
    }

    // 类别 2: 管理员审核流程
    if (shouldRun('02')) await require('./test-02-audit.cjs').run(browser, request, context);

    // 类别 3: 闲置预约全流程
    if (shouldRun('03')) await require('./test-03-idle-appoint.cjs').run(browser, request, context);

    // 类别 4: 活动报名签到
    if (shouldRun('04')) await require('./test-04-activity.cjs').run(browser, request, context);

    // 类别 5: 失物认领
    if (shouldRun('05')) await require('./test-05-lostfound.cjs').run(browser, request, context);

    // 类别 6: 学习搭子
    if (shouldRun('06')) await require('./test-06-partner.cjs').run(browser, request, context);

    // 类别 7: 校园问答
    if (shouldRun('07')) await require('./test-07-qa.cjs').run(browser, request, context);

    // 类别 8: 校园动态
    if (shouldRun('08')) await require('./test-08-social.cjs').run(browser, request, context);

    // 类别 9: 公告
    if (shouldRun('09')) await require('./test-09-notice.cjs').run(browser, request, context);

    // 类别 10: 通知中心
    if (shouldRun('10')) await require('./test-10-message.cjs').run(browser, request, context);

    // 类别 11: 收藏
    if (shouldRun('11')) await require('./test-11-favorite.cjs').run(browser, request, context);

    // 类别 12: 举报
    if (shouldRun('12')) await require('./test-12-report.cjs').run(browser, request, context);

    // 类别 13: 私信
    if (shouldRun('13')) await require('./test-13-chat.cjs').run(browser, request, context);

    // 类别 14: 文件上传
    if (shouldRun('14')) await require('./test-14-upload.cjs').run(browser, request, context);

    // 类别 15: AI 功能
    if (shouldRun('15')) await require('./test-15-ai.cjs').run(browser, request, context);

    // 类别 16: AI 故障处理
    if (shouldRun('16')) await require('./test-16-ai-fault.cjs').run(browser, request, context);

    // 类别 17: 搜索筛选分页
    if (shouldRun('17')) await require('./test-17-search.cjs').run(browser, request, context);

    // 类别 18: 3D 功能
    if (shouldRun('18')) await require('./test-18-campus3d.cjs').run(browser, request, context);

    // 类别 19: 桌面移动端适配
    if (shouldRun('19')) await require('./test-19-responsive.cjs').run(browser, request, context);

    // 类别 20: 并发验证
    if (shouldRun('20')) await require('./test-20-concurrency.cjs').run(browser, request, context);

  } catch (e) {
    runtimeError = true;
    console.error('测试运行异常:', e);
    h.record('E2E运行器', '分类执行异常', 'FAIL', e.message);
  } finally {
    await browser.close();
  }

  // 输出统计
  const pass = h.results.filter(r => r.status === 'PASS').length;
  const fail = h.results.filter(r => r.status === 'FAIL').length;
  const block = h.results.filter(r => r.status === 'BLOCK').length;
  const uncov = h.results.filter(r => r.status === 'UNCOVERED').length;
  console.log(`\n=== 测试统计 ===`);
  console.log(`通过: ${pass}, 失败: ${fail}, 阻塞: ${block}, 未覆盖: ${uncov}`);

  // 保存结果 JSON
  const fs = require('fs');
  const outPath = process.env.E2E_RESULT_PATH || require('path').join(require('os').tmpdir(), 'ai-campus-e2e-results.json');
  fs.writeFileSync(outPath, JSON.stringify(h.results, null, 2), 'utf-8');
  console.log(`结果已保存: ${outPath}`);
  process.exitCode = getExitCode(h.results, runtimeError);
})().catch(e => { console.error(e); process.exit(1); });
