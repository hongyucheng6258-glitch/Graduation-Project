/**
 * 测试类别 16：AI 故障处理
 * 验证 AI 未配置时的错误提示（通过 API 模拟不可用场景）
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '16-AI故障处理';
  console.log(`\n=== ${C} ===`);

  const buyerLogin = await h.apiLoginStudent(request, h.TEST_ACCOUNTS.buyer.studentNo, h.TEST_ACCOUNTS.buyer.password);
  const buyer = h.apiWithToken(request, buyerLogin.token);

  // 不修改配置文件（风险高），改为：
  // 1. 验证 AI 接口在正常配置下返回业务错误时的处理
  // 2. 验证匿名用户访问 AI 接口被拒
  const anonCheck = await request.post(`${h.BACKEND}/api/ai/chat`, {
    data: { question: 'test' }
  });
  const anonBody = await anonCheck.json();
  if (anonBody.code === 401 || anonBody.code === 403) {
    h.record(C, '未登录访问 AI 接口被拒', 'PASS', `code=${anonBody.code}`);
  } else {
    h.record(C, '未登录访问 AI 接口被拒', 'FAIL', `code=${anonBody.code}`);
  }

  // 验证每日限额提示：连续调用多次 AI（但控制在合理范围）
  // 不实际触发限额，只验证接口有 rate-limit 机制
  h.record(C, 'AI 限额/未配置错误提示', 'UNCOVERED',
    '需临时修改 application.yml 关闭 AI 配置才能验证，为避免影响运行环境未执行；正常路径已在类别15验证');

  ctx.aiFault = {};
}

module.exports = { run };
