const { randomBytes } = require('node:crypto');
const h = require('./helpers.cjs');

async function run(browser, request) {
  const category = '16-AI故障处理';
  const question = { question: '请简短解释什么是二分查找' };
  const anonymous = await (await request.post(h.BACKEND + '/api/ai/chat/sync', { data: question })).json();
  h.record(category, '未登录访问 AI 接口被拒', [401, 403].includes(anonymous.code) ? 'PASS' : 'FAIL', `code=${anonymous.code}`);

  const studentNo = 'E2EFAULT' + randomBytes(5).toString('hex');
  const registration = await h.apiRegisterStudent(request, studentNo, h.TEST_ACCOUNTS.buyer.password, 'AI 故障验收');
  if (registration.code !== 200) throw new Error('AI 故障测试账号注册失败');
  const login = await h.apiLoginStudent(request, studentNo, h.TEST_ACCOUNTS.buyer.password);
  const student = h.apiWithToken(request, login.token);

  const unavailableBase = process.env.E2E_AI_UNCONFIGURED_URL;
  if (!unavailableBase) throw new Error('Set E2E_AI_UNCONFIGURED_URL to the isolated backend with no model key.');
  const unavailable = await (await request.post(unavailableBase + '/api/ai/chat/sync', {
    headers: { Authorization: `Bearer ${login.token}` }, data: question
  })).json();
  h.record(category, '未配置模型时返回可读错误',
    unavailable.code === 1010 && /未配置/.test(unavailable.message) ? 'PASS' : 'FAIL', `code=${unavailable.code}`);

  if (process.env.E2E_ISOLATED !== '1') throw new Error('Quota test requires an isolated backend (E2E_ISOLATED=1).');
  const admin = h.apiWithToken(request, (await h.apiLoginAdmin(request)).token);
  const config = await admin.get('/admin/ai/config');
  const previous = config.data?.find(row => row.configKey === 'rate_limit_per_day')?.configValue;
  if (!previous) throw new Error('Unable to read the original AI quota.');
  async function setQuota(value) {
    const result = await admin.put('/admin/ai/config', { configs: { rate_limit_per_day: value } });
    if (result.code !== 200) throw new Error('Unable to change the isolated AI quota.');
  }
  try {
    await setQuota('1');
    const results = await Promise.all([
      student.post('/ai/chat/sync', question),
      student.post('/ai/chat/sync', question)
    ]);
    const accepted = results.filter(result => result.code === 200 && typeof result.data === 'string' && result.data.trim());
    const limited = results.filter(result => result.code === 429 && /上限/.test(result.message));
    h.record(category, '并发请求仅有一次获得剩余额度',
      accepted.length === 1 && limited.length === 1 ? 'PASS' : 'FAIL', results.map(result => result.code).join(','));
    const next = await student.post('/ai/chat/sync', question);
    h.record(category, '超过每日限额后返回可读错误',
      next.code === 429 && /上限/.test(next.message) ? 'PASS' : 'FAIL', `code=${next.code}`);
  } finally {
    await setQuota(previous);
  }
}

module.exports = { run };
