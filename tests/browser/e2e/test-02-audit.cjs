/**
 * 测试类别 2：管理员审核流程
 * 学生发布闲置/活动/动态（待审核）→ 管理员通过 → 另一条驳回 → 学生修改重提 → 管理员通过
 */
const h = require('./helpers.cjs');

async function run(browser, request, ctx) {
  const C = '02-管理员审核';
  console.log(`\n=== ${C} ===`);

  const seller = h.apiWithToken(request, ctx.sellerToken);
  let adminToken;
  try {
    const adminLogin = await h.apiLoginAdmin(request);
    adminToken = adminLogin.token;
  } catch (e) {
    h.record(C, '管理员登录', 'BLOCK', e.message);
    return;
  }
  const admin = h.apiWithToken(request, adminToken);

  // 1. 学生发布闲置（待审核）
  const idleData = {
    title: `${h.PREFIX}审核测试闲置物品`,
    description: '测试用闲置物品，待审核',
    category: '电子数码',
    price: 99,
    status: 0
  };
  const idleRes = await seller.post('/idle', idleData);
  let idleId;
  if (idleRes.code === 200) {
    idleId = idleRes.data.id || idleRes.data;
    h.record(C, '学生发布闲置（待审核）', 'PASS', `id=${idleId}`);
  } else {
    h.record(C, '学生发布闲置（待审核）', 'FAIL', `code=${idleRes.code} msg=${idleRes.message}`);
  }

  // 2. 管理员审核通过
  if (idleId) {
    const passRes = await admin.put(`/admin/audit/idle/${idleId}/pass`);
    if (passRes.code === 200) {
      h.record(C, '管理员审核通过闲置', 'PASS');
    } else {
      h.record(C, '管理员审核通过闲置', 'FAIL', `code=${passRes.code} msg=${passRes.message}`);
    }
    // 验证闲置状态已通过
    const detail = await seller.get(`/idle/${idleId}`);
    if (detail.code === 200 && detail.data.auditStatus === 1 && detail.data.status === 0) {
      h.record(C, '审核后闲置状态为已通过', 'PASS');
    } else {
      h.record(C, '审核后闲置状态为已通过', 'FAIL', `auditStatus=${detail.data?.auditStatus} status=${detail.data?.status}`);
    }
  }

  // 3. 学生发布另一条闲置，管理员驳回
  const idle2 = await seller.post('/idle', {
    title: `${h.PREFIX}待驳回闲置`,
    description: '应被驳回的闲置',
    category: '教材书籍',
    price: 10,
    status: 0
  });
  let idle2Id;
  if (idle2.code === 200) {
    idle2Id = idle2.data.id || idle2.data;
    h.record(C, '学生发布第二条闲置（待驳回）', 'PASS', `id=${idle2Id}`);
  } else {
    h.record(C, '学生发布第二条闲置（待驳回）', 'FAIL', idle2.message);
  }

  if (idle2Id) {
    const rejectRes = await admin.put(`/admin/audit/idle/${idle2Id}/reject`, { reason: `${h.PREFIX}内容不合规测试` });
    if (rejectRes.code === 200) {
      h.record(C, '管理员驳回闲置并填写原因', 'PASS');
    } else {
      h.record(C, '管理员驳回闲置并填写原因', 'FAIL', rejectRes.message);
    }
    // 学生看到驳回原因
    const myIdle = await seller.get('/idle/my', { pageNum: 1, pageSize: 10 });
    if (myIdle.code === 200) {
      const item = (myIdle.data.list || myIdle.data.records || myIdle.data || []).find(x => x.id === idle2Id);
      if (item && (item.auditReason || item.rejectReason || item.reason)) {
        h.record(C, '学生看到驳回原因', 'PASS', `reason=${item.auditReason || item.rejectReason || item.reason}`);
      } else {
        h.record(C, '学生看到驳回原因', 'FAIL', '未找到驳回原因字段');
      }
    }
    // 学生修改后重新提交
    const updateRes = await seller.put(`/idle/${idle2Id}`, {
      title: `${h.PREFIX}修改后闲置`,
      description: '修改后重新提交',
      category: '教材书籍',
      price: 10
    });
    if (updateRes.code === 200) {
      h.record(C, '学生修改被驳回闲置后重新提交', 'PASS');
    } else {
      h.record(C, '学生修改被驳回闲置后重新提交', 'FAIL', updateRes.message);
    }
    // 管理员再次通过
    const pass2 = await admin.put(`/admin/audit/idle/${idle2Id}/pass`);
    if (pass2.code === 200) {
      h.record(C, '管理员再次审核通过', 'PASS');
    } else {
      h.record(C, '管理员再次审核通过', 'FAIL', pass2.message);
    }
  }

  // 4. UI 截图：管理端审核列表
  const page = await browser.newPage();
  await page.goto(`${h.ADMIN_WEB}/`);
  await page.waitForTimeout(2000);
  // 管理端需要登录 - 注入 token
  await page.evaluate(([t]) => {
    localStorage.setItem('admin-token', t);
    localStorage.setItem('adminToken', t);
  }, [adminToken]);
  await page.goto(`${h.ADMIN_WEB}/`);
  await page.waitForTimeout(2000);
  const shot = await h.screenshot(page, '02-audit', 'admin-audit-page');
  h.record(C, '管理端审核页面截图', 'PASS', '', shot);
  await page.close();

  ctx.audit = { idleId, idle2Id };
}

module.exports = { run };
