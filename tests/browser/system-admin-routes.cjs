const path = require('node:path');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const { launchBrowser, ADMIN_WEB } = require('./system-support.cjs');

const outputDir = process.env.CAMPUS_TEST_OUTPUT || path.join(require('node:os').tmpdir(), 'campus-admin-routes');
fs.mkdirSync(outputDir, { recursive: true });

const adminProfile = { id: 1, username: 'admin', nickname: '测试管理员', role: 'super' };
const permissions = ['user:password:reset', 'system:operation-log:list', 'ai:config:edit', 'ai:prompt:edit'];
const auditRow = {
  id: 101,
  title: '九成新校园台灯',
  description: '功能正常，毕业季闲置转让。',
  createTime: '2026-11-19T09:30:00',
  auditStatus: 0,
  auditSource: 'admin',
  aiRiskLevel: null,
  images: []
};
const paged = (list = []) => ({ list, total: list.length });

function responseFor(pathname, method) {
  if (pathname === '/api/auth/captcha') return { captchaId: 'captcha-ui-test', mode: 'math', expression: '2 + 3 = ?', image: '' };
  if (pathname === '/api/admin/auth/login') return { token: 'test-token', adminInfo: adminProfile };
  if (pathname === '/api/admin/auth/me') return { ...adminProfile, permissions };
  if (pathname === '/api/admin/stats/overview') return { totalUsers: 1280, todayActiveUsers: 326, todayAiCalls: 89, pendingAudits: 7 };
  if (pathname === '/api/admin/stats/trend') return {
    dates: ['11-13', '11-14', '11-15', '11-16', '11-17', '11-18', '11-19'],
    dailyActiveUsers: [180, 216, 195, 268, 242, 305, 326]
  };
  if (pathname === '/api/admin/stats/module') return [
    { name: '活动', value: 36 }, { name: '闲置', value: 58 }, { name: '失物', value: 22 }, { name: '动态', value: 74 }, { name: '搭子', value: 18 }
  ];
  if (pathname === '/api/admin/stats/pie') return {
    lostStatus: [{ name: '待认领', value: 12 }, { name: '已找回', value: 28 }, { name: '已关闭', value: 6 }]
  };
  if (pathname === '/api/admin/stats/pending-counts') return { activity: 2, idle: 3, lostfound: 1, post: 1, partner: 0, report: 2, ai: 4 };
  if (pathname === '/api/admin/audit/pending' || pathname === '/api/admin/audit/all') return paged([auditRow]);
  if (/^\/api\/admin\/audit\/[^/]+\/\d+$/.test(pathname)) return auditRow;
  if (pathname === '/api/admin/user/list') return paged([{ id: 8, studentNo: '20260001', nickname: '校园用户', status: 0, createTime: '2026-11-01T08:00:00' }]);
  if (pathname === '/api/admin/report/list') return paged([]);
  if (pathname === '/api/admin/notice/list') return paged([{ id: 5, title: '校园服务通知', status: 1, createTime: '2026-11-18T10:00:00' }]);
  if (pathname === '/api/admin/notice/5') return { id: 5, title: '校园服务通知', content: '## 通知正文', cover: '' };
  if (pathname === '/api/admin/ai/config') return [
    { configKey: 'base_url', configValue: 'https://api.example.com' },
    { configKey: 'model_name', configValue: 'campus-chat' },
    { configKey: 'temperature', configValue: '0.7' }
  ];
  if (pathname === '/api/admin/ai/prompt') return [];
  if (pathname === '/api/admin/ai/log') return paged([]);
  if (pathname === '/api/admin/ai/agent/capabilities') return { enabled: false, ragEnabled: false };
  if (pathname === '/api/admin/assistant/capabilities') return { enabled: true, ragEnabled: true, canManageRules: true };
  if (pathname === '/api/admin/assistant/sessions') return [];
  if (pathname === '/api/admin/assistant/rules') return [];
  if (pathname === '/api/admin/assistant/notice-drafts') return [];
  if (pathname === '/api/admin/password-recovery') return [];
  if (pathname === '/api/admin/system/config') return [
    { configKey: 'site_name', configValue: '梧桐校园', category: 'site', valueType: 'string', description: '站点名称', sort: 1 },
    { configKey: 'audit_enabled', configValue: 'true', category: 'audit', valueType: 'bool', description: '开启内容审核', sort: 2 }
  ];
  if (pathname === '/api/admin/system/admin') return paged([{ id: 1, username: 'admin', nickname: '测试管理员', role: 'super', status: 1 }]);
  if (pathname === '/api/admin/system/operation-log') return paged([{ id: 1, createTime: '2026-11-19T09:40:00', adminUsername: 'admin', module: 'audit', action: 'pass', targetType: 'idle', targetId: 101, source: 'admin', succeeded: true, durationMs: 18 }]);
  if (method !== 'GET') return { success: true };
  return paged([]);
}

(async () => {
  const browser = await launchBrowser({ softwareWebGL: false });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    const errors = [];
    const mutations = [];
    const visited = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/api/**', async route => {
      const request = route.request();
      const url = new URL(request.url());
      const pathname = url.pathname;
      if (!pathname.startsWith('/api/')) return route.continue();
      if (request.method() !== 'GET') mutations.push(`${request.method()} ${pathname}`);
      return route.fulfill({ json: { code: 200, data: responseFor(pathname, request.method()) } });
    });

    // 登录页：验证码、忘记密码、必填校验、记住账号和登录跳转。
    await page.goto(`${ADMIN_WEB}/admin/login`);
    await page.getByRole('heading', { name: '欢迎回来', exact: true }).waitFor();
    await page.screenshot({ path: path.join(outputDir, 'login-desktop.png'), fullPage: true });
    await page.getByRole('button', { name: '登 录' }).click();
    await page.getByText('请输入账号、密码和验证码').waitFor();
    await page.getByRole('button', { name: '忘记密码？' }).click();
    await page.getByText('请联系校园平台运维人员核验身份并重置管理员密码。').waitFor();
    await page.getByRole('button', { name: /当前题目：2 \+ 3/ }).click();
    await page.getByPlaceholder('管理员账号').fill('admin');
    await page.getByPlaceholder('密码', { exact: true }).fill('admin123');
    await page.getByPlaceholder('输入运算结果').fill('5');
    await page.getByText('记住此设备').click();
    await page.getByRole('button', { name: '登 录' }).click();
    await page.getByRole('heading', { name: '欢迎回来，校园管理员' }).waitFor();
    assert.equal(await page.evaluate(() => localStorage.getItem('admin_remember_username')), 'admin');

    // 首页、折叠导航和暗色主题。
    await page.screenshot({ path: path.join(outputDir, 'dashboard-desktop.png'), fullPage: true });
    await page.getByRole('button', { name: '切换管理导航' }).click();
    await page.locator('.admin-shell.sidebar-collapsed').waitFor();
    await page.getByRole('button', { name: '切换管理导航' }).click();
    await page.getByTitle('切换主题').click();
    assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), 'dark');
    await page.waitForTimeout(350);
    await page.screenshot({ path: path.join(outputDir, 'dashboard-dark.png'), fullPage: true });
    await page.getByTitle('切换主题').click();

    // 顶部全局搜索。
    await page.getByRole('searchbox', { name: '搜索用户、内容或工单 ID' }).fill('校园');
    await page.locator('.topbar-search').getByRole('button', { name: '搜索', exact: true }).click();
    await page.waitForURL(/\/admin\/search\?q=/);
    await page.locator('.search-page').waitFor();

    const routes = [
      ['dashboard', '欢迎回来，校园管理员'],
      ['search?q=%E6%A0%A1%E5%9B%AD', '.search-page'],
      ['assistant', '.ai-layout'],
      ['password-recovery', '密码找回审核'],
      ['user', '用户管理'],
      ['audit/activity', '活动审核'],
      ['audit/idle', '闲置审核'],
      ['audit/lostfound', '失物招领审核'],
      ['audit/post', '动态审核'],
      ['audit/partner', '搭子审核'],
      ['content', '内容管理'],
      ['ai/audit', 'AI 内容审核'],
      ['report', '举报处理'],
      ['notice', '系统公告'],
      ['notice/edit', '新建公告'],
      ['ai/config', 'AI 配置'],
      ['ai/logs', '调用日志'],
      ['system/config', '系统配置'],
      ['system/operation-log', '操作日志'],
      ['system', '管理员账号']
    ];

    for (const [route, expected] of routes) {
      await page.goto(`${ADMIN_WEB}/admin/${route}`);
      if (expected.startsWith('.')) await page.locator(expected).waitFor();
      else await page.getByText(expected, { exact: true }).first().waitFor();
      await page.waitForTimeout(180);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1);
      assert.equal(overflow, false, `${route} 出现页面级横向溢出`);
      const name = route.split('?')[0].replaceAll('/', '-');
      await page.screenshot({ path: path.join(outputDir, `${name}.png`), fullPage: true });
      visited.push(route);
    }

    // 审核通过、驳回弹窗与驳回提交。
    await page.goto(`${ADMIN_WEB}/admin/audit/idle`);
    await page.getByRole('button', { name: '通过', exact: true }).click();
    await page.getByText('已通过，作者将收到通知').waitFor();
    await page.getByRole('button', { name: '驳回', exact: true }).click();
    await page.getByPlaceholder('必填，将随消息通知作者').fill('信息不完整，请补充后重新提交');
    await page.getByRole('button', { name: '确认驳回' }).click();
    await page.getByText('已驳回，作者将收到通知').waitFor();
    assert(mutations.some(item => item.includes('/api/admin/audit/idle/101/pass')));
    assert(mutations.some(item => item.includes('/api/admin/audit/idle/101/reject')));

    // Markdown 编辑与实时预览。
    await page.goto(`${ADMIN_WEB}/admin/notice/edit`);
    await page.getByLabel('标题', { exact: true }).fill('测试公告');
    await page.getByPlaceholder(/支持 Markdown 语法/).fill('# 校园公告\n\n正文内容');
    await page.locator('.md-preview h1').getByText('校园公告').waitFor();

    // 移动端首页、抽屉导航及页面级溢出。
    await page.goto(`${ADMIN_WEB}/admin/dashboard`);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole('button', { name: '切换管理导航' }).click();
    await page.locator('.sidebar.drawer-open').waitFor();
    await page.screenshot({ path: path.join(outputDir, 'navigation-mobile.png'), fullPage: true });
    await page.getByRole('button', { name: '关闭管理导航' }).click();
    await page.screenshot({ path: path.join(outputDir, 'dashboard-mobile.png'), fullPage: true });
    const mobileOverflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1);
    assert.equal(mobileOverflow, false, '移动端首页出现页面级横向溢出');

    assert.deepEqual(errors, [], `浏览器运行错误：${errors.join('; ')}`);
    const report = { passed: ['login and captcha', 'remember account', 'all admin routes', 'desktop overflow', 'theme', 'sidebar', 'global search', 'audit pass and reject', 'markdown preview', 'mobile navigation'], visited, mutations, errors };
    fs.writeFileSync(path.join(outputDir, 'report.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report));
  } finally {
    // 断言失败也要关闭浏览器，避免残留 Chromium 影响下一次回归
    await browser.close().catch(() => {});
  }
})().catch(error => {
  console.error(error);
  process.exit(1);
});
