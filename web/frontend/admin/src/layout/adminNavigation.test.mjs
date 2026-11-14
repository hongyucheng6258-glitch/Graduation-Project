import test from 'node:test'
import assert from 'node:assert/strict'
import { adminBreadcrumbs, adminMenu, pendingQueues, pendingTotal } from './adminNavigation.mjs'

const notice = { name: 'NoticeManage', meta: { title: '系统公告', menu: { group: '系统管理' } } }
const system = { name: 'SystemConfig', meta: { title: '系统配置', menu: { group: '系统管理' }, superOnly: true } }
const routes = [{ path: 'audit', redirect: '/audit/activity' }, notice, system]

test('a list route keeps its group and ignores unnamed redirects', () => {
  const trail = adminBreadcrumbs({ ...notice, params: {}, query: {} }, routes)
  assert.deepEqual(trail.map(({ label }) => label), ['首页', '系统管理', '系统公告'])
})

test('notice editing links back to the original list page', () => {
  const trail = adminBreadcrumbs({
    name: 'NoticeEdit',
    meta: { activeMenu: 'NoticeManage' },
    params: { id: '7' },
    query: { page: '2' }
  }, routes)
  assert.deepEqual(trail.at(-2).to, { name: 'NoticeManage', query: { page: '2' } })
  assert.equal(trail.at(-1).label, '编辑公告')
})

test('search detail links preserve the result location', () => {
  const trail = adminBreadcrumbs({
    name: 'UserList',
    meta: { title: '用户管理', detailTitle: '用户详情' },
    params: {},
    query: { record: '42', searchQ: '42', searchScope: 'user', searchPages: 'user:2', searchTarget: 'result-user-42' }
  }, routes)
  assert.deepEqual(trail.at(-2).to, {
    name: 'AdminSearch', query: { q: '42', scope: 'user', pages: 'user:2' }, hash: '#result-user-42'
  })
  assert.equal(trail.at(-1).label, '用户详情')
})

test('super-only entries remain hidden for the audit role', () => {
  assert.deepEqual(adminMenu(routes, false)[0].items, [notice])
  assert.deepEqual(adminMenu(routes, true)[0].items, [notice, system])
})

test('pending entries open filtered queues and do not count AI risk twice', () => {
  const activity = { name: 'ActivityAudit', meta: { title: '活动审核', menu: { countKey: 'activity' } } }
  const report = { name: 'ReportList', meta: { title: '举报处理', menu: { countKey: 'report' } } }
  const ai = { name: 'AiContentAudit', meta: { title: 'AI 内容审核', menu: { countKey: 'ai' } } }
  assert.deepEqual(pendingQueues([activity, ai, report, notice]).map(({ to }) => to), [
    { name: 'ActivityAudit', query: { view: 'pending' } },
    { name: 'ReportList', query: { view: 'pending' } },
    { name: 'AiContentAudit' }
  ])
  assert.equal(pendingTotal({ activity: 2, report: 1, ai: 2 }), 3)
})
