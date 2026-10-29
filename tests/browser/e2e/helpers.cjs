/**
 * E2E 测试公共工具模块
 * - Playwright 启动、API 封装、截图记录、结果收集
 * - 所有测试通过真实 HTTP 请求访问后端 localhost:8080，不使用 mock
 */
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');
const path = require('path');
const os = require('os');
const fs = require('fs');

const { resolveEndpoints, resolveTestAssetPath } = require('./config.cjs');
const { BACKEND, STUDENT_WEB, ADMIN_WEB } = resolveEndpoints();
const SHOT_DIR = process.env.E2E_SHOT_DIR || path.join(os.tmpdir(), 'ai-campus-e2e-shots');

// 测试账号；管理员密码从本机环境变量读取，不写入仓库。
const TEST_PASSWORD = process.env.E2E_TEST_PASSWORD;
if (!TEST_PASSWORD) throw new Error('Set E2E_TEST_PASSWORD to the password used by the seeded browser-test accounts.');
const TEST_ACCOUNTS = {
  seller: { studentNo: 'E2E_2201', password: TEST_PASSWORD, nickname: 'E2E卖家' },
  buyer:  { studentNo: 'E2E_2202', password: TEST_PASSWORD, nickname: 'E2E买家' },
  third:  { studentNo: 'E2E_2203', password: TEST_PASSWORD, nickname: 'E2E第三方' },
};
const ADMIN = {
  username: process.env.E2E_ADMIN_USERNAME || 'admin',
  password: process.env.E2E_ADMIN_PASSWORD || ''
};
const PREFIX = '[E2E测试]';

// 结果收集
const results = [];
function record(category, name, status, detail, screenshot) {
  if (!['PASS', 'FAIL', 'BLOCK', 'UNCOVERED'].includes(status)) {
    throw new Error(`无效测试状态: ${status}`);
  }
  results.push({ category, name, status, detail: detail || '', screenshot: screenshot || '' });
  const icon = status === 'PASS' ? '✓' : status === 'FAIL' ? '✗' : status === 'BLOCK' ? '⊘' : '○';
  console.log(`  ${icon} [${category}] ${name}: ${status}${detail ? ' — ' + detail : ''}`);
}

/** 解包后端分页响应，未知结构直接报错，避免测试静默通过。 */
function unwrapPage(body) {
  const data = body?.data;
  if (Array.isArray(data)) return { list: data, total: data.length };
  if (data && Array.isArray(data.list)) return { list: data.list, total: data.total ?? data.list.length };
  if (data && Array.isArray(data.records)) return { list: data.records, total: data.total ?? data.records.length };
  throw new Error(`分页响应结构无法识别: ${JSON.stringify(body).slice(0, 500)}`);
}

function unwrapList(body) {
  return unwrapPage(body).list;
}

function messageMatches(messages, predicate) {
  return unwrapList({ data: messages }).find(predicate) || null;
}

/** 启动浏览器 */
async function launchBrowser() {
  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--disable-web-security']
  };
  if (process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE) {
    launchOptions.executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
  }
  return chromium.launch(launchOptions);
}

/** 学生端登录（通过 UI 操作） */
async function studentLogin(page, studentNo, password) {
  await page.goto(`${STUDENT_WEB}/login`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1000);
  // Element Plus el-input 渲染为 input 嵌套在 .el-input 内
  const inputs = page.locator('.el-form input');
  await inputs.nth(0).fill(studentNo);
  await inputs.nth(1).fill(password);
  // 验证码字段（后端已关闭校验，任意值即可，但前端校验要求非空）
  const captchaInput = page.locator('.el-form input').nth(2);
  if (await captchaInput.count() > 0) {
    await captchaInput.fill('e2e');
  }
  await page.locator('button.submit, button:has-text("登 录"), button:has-text("登录")').first().click();
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(2000);
}

/** 学生端退出 */
async function studentLogout(page) {
  await page.evaluate(() => { localStorage.removeItem('token'); localStorage.removeItem('userInfo'); });
  await page.goto(`${STUDENT_WEB}/`);
  await page.waitForLoadState('networkidle');
}

/** 通过 API 登录学生，返回 token */
async function apiLoginStudent(request, studentNo, password) {
  const resp = await request.post(`${BACKEND}/api/auth/login`, {
    data: { studentNo, password, captchaId: 'e2e-test', captchaCode: 'e2e' }
  });
  const body = await resp.json();
  if (body.code !== 200) throw new Error(`登录失败: ${body.message}`);
  return body.data;
}

/** 通过 API 注册学生 */
async function apiRegisterStudent(request, studentNo, password, nickname) {
  const resp = await request.post(`${BACKEND}/api/auth/register`, {
    data: { studentNo, password, nickname, captchaId: 'e2e-test', captchaCode: 'e2e' }
  });
  const body = await resp.json();
  return body;
}

/** 通过 API 登录管理员 */
async function apiLoginAdmin(request) {
  if (!ADMIN.password) throw new Error('Set E2E_ADMIN_PASSWORD to use the local admin account in E2E runs.');
  const resp = await request.post(`${BACKEND}/api/admin/auth/login`, {
    data: { username: ADMIN.username, password: ADMIN.password }
  });
  const body = await resp.json();
  if (body.code !== 200) throw new Error(`管理员登录失败: ${body.message}`);
  return body.data;
}

/** 封装带 token 的 API 调用（自动加 /api 前缀） */
function apiWithToken(request, token) {
  const P = '/api';
  return {
    async get(path, params) {
      const resp = await request.get(`${BACKEND}${P}${path}`, {
        headers: { Authorization: `Bearer ${token}` },
        params
      });
      return resp.json();
    },
    async post(path, data) {
      const resp = await request.post(`${BACKEND}${P}${path}`, {
        headers: { Authorization: `Bearer ${token}` },
        data
      });
      return resp.json();
    },
    async put(path, data) {
      const resp = await request.put(`${BACKEND}${P}${path}`, {
        headers: { Authorization: `Bearer ${token}` },
        data: data || {}
      });
      return resp.json();
    },
    async delete(path) {
      const resp = await request.delete(`${BACKEND}${P}${path}`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      return resp.json();
    }
  };
}

/** 截图 */
async function screenshot(page, subdir, name) {
  const dir = path.join(SHOT_DIR, subdir);
  fs.mkdirSync(dir, { recursive: true });
  const fp = path.join(dir, `${name}.png`);
  await page.screenshot({ path: fp, fullPage: false });
  return fp;
}

/** 把 token 注入 localStorage（绕过 UI 登录） */
async function injectStudentAuth(page, token, userInfo) {
  await page.addInitScript(([t, u]) => {
    localStorage.setItem('token', t);
    localStorage.setItem('userInfo', JSON.stringify(u));
  }, [token, userInfo]);
}

/** 等待并断言 API 业务码 */
function assertApi(body, expectCode = 200, msg = '') {
  if (body.code !== expectCode) {
    throw new Error(`API 业务码不符: 期望 ${expectCode}, 实际 ${body.code}, ${msg}, 消息: ${body.message}`);
  }
}

module.exports = {
  chromium, launchBrowser, studentLogin, studentLogout,
  apiLoginStudent, apiRegisterStudent, apiLoginAdmin, apiWithToken,
  screenshot, injectStudentAuth, assertApi,
  testAssetPath: resolveTestAssetPath,
  unwrapPage, unwrapList, messageMatches,
  record, results,
  BACKEND, STUDENT_WEB, ADMIN_WEB, SHOT_DIR,
  TEST_ACCOUNTS, ADMIN, PREFIX
};
