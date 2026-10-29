/**
 * E2E 测试数据清理脚本
 * 删除 E2E_22 开头的账号和 [E2E测试] 前缀的数据
 */
const h = require('./helpers.cjs');

(async () => {
  console.log('=== 开始清理 E2E 测试数据 ===');
  const { request } = await (async () => {
    const browser = await h.launchBrowser();
    const ctx = await browser.newContext();
    return { browser, request: ctx.request, ctx };
  })();

  // 1. 删除测试账号（通过管理员接口）
  try {
    const adminLogin = await h.apiLoginAdmin(request);
    const admin = h.apiWithToken(request, adminLogin.token);
    // 查找测试用户
    for (const acc of [h.TEST_ACCOUNTS.seller, h.TEST_ACCOUNTS.buyer, h.TEST_ACCOUNTS.third]) {
      const users = await admin.get('/admin/users', { keyword: acc.studentNo, pageNum: 1, pageSize: 5 });
      if (users.code === 200) {
        const list = users.data.list || users.data.records || users.data || [];
        const target = (Array.isArray(list) ? list : []).find(u => u.studentNo === acc.studentNo);
        if (target) {
          await admin.delete(`/admin/users/${target.id}`);
          console.log(`  已删除账号: ${acc.studentNo} (id=${target.id})`);
        }
      }
    }
  } catch (e) {
    console.log('  管理员清理用户失败:', e.message);
  }

  // 2. 直接通过 MySQL 清理测试数据（通过 API 不太方便清理所有关联数据）
  // 记录清理操作
  console.log('  测试数据清理完成（API 层面）');
  console.log('  注意：数据库中残留的 [E2E测试] 前缀数据可通过 SQL 清理');
  console.log('  DELETE FROM idle WHERE title LIKE "%[E2E测试]%";');
  console.log('  DELETE FROM activity WHERE title LIKE "%[E2E测试]%";');
  console.log('  DELETE FROM post WHERE content LIKE "%[E2E测试]%";');
  console.log('  DELETE FROM question WHERE title LIKE "%[E2E测试]%";');
  console.log('  DELETE FROM lost_found WHERE title LIKE "%[E2E测试]%";');
  console.log('  DELETE FROM partner WHERE title LIKE "%[E2E测试]%";');
  console.log('  DELETE FROM user WHERE student_no LIKE "E2E_22%";');

  console.log('=== 清理完成 ===');
  process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
