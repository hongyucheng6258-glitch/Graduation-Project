import { ElMessage } from 'element-plus'
import { createRouter, createWebHistory } from 'vue-router'
import { useAdminStore } from '../store/admin'

/**
 * 管理端路由 + 权限守卫（A5）。
 * meta.superOnly 标记仅 super 角色可见（系统管理，D8）。
 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('../layout/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/dashboard/Dashboard.vue'), meta: { menu: { group: '概览', icon: 'dashboard' }, title: '数据看板' } },
      { path: 'search', name: 'AdminSearch', component: () => import('../views/search/AdminSearch.vue'), meta: { title: '全局搜索' } },
      { path: 'assistant', name: 'AdminAssistant', component: () => import('../views/ai/AdminAssistant.vue'), meta: { menu: { group: '概览', icon: 'spark' }, title: '管理智能助手' } },
      { path: 'password-recovery', name: 'PasswordRecovery', component: () => import('../views/user/PasswordRecovery.vue'), meta: { menu: { group: '用户与社区', icon: 'users' }, title: '密码找回审核', permission: 'user:password:reset' } },
      { path: 'user', name: 'UserList', component: () => import('../views/user/UserList.vue'), meta: { menu: { group: '用户与社区', icon: 'users' }, detailTitle: '用户详情', title: '用户管理' } },
      { path: 'audit/activity', name: 'ActivityAudit', component: () => import('../views/audit/AuditModule.vue'), props: { type: 'activity' }, meta: { menu: { group: '内容审核', icon: 'audit', countKey: 'activity' }, detailTitle: '审核详情', title: '活动审核' } },
      { path: 'audit/idle', name: 'IdleAudit', component: () => import('../views/audit/AuditModule.vue'), props: { type: 'idle' }, meta: { menu: { group: '内容审核', icon: 'audit', countKey: 'idle' }, detailTitle: '审核详情', title: '闲置审核' } },
      { path: 'audit/lostfound', name: 'LostFoundAudit', component: () => import('../views/audit/AuditModule.vue'), props: { type: 'lostfound' }, meta: { menu: { group: '内容审核', icon: 'audit', countKey: 'lostfound' }, detailTitle: '审核详情', title: '失物招领审核' } },
      { path: 'audit/post', name: 'PostAudit', component: () => import('../views/audit/AuditModule.vue'), props: { type: 'post' }, meta: { menu: { group: '内容审核', icon: 'audit', countKey: 'post' }, detailTitle: '审核详情', title: '动态审核' } },
      { path: 'audit/partner', name: 'PartnerAudit', component: () => import('../views/audit/AuditModule.vue'), props: { type: 'partner' }, meta: { menu: { group: '内容审核', icon: 'audit', countKey: 'partner' }, detailTitle: '审核详情', title: '搭子审核' } },
      { path: 'content', name: 'ContentManage', component: () => import('../views/content/ContentManage.vue'), meta: { menu: { group: '内容管理', icon: 'log' }, detailTitle: '内容详情', title: '内容管理' } },
      { path: 'audit', redirect: '/audit/activity' },
      { path: 'ai/audit', name: 'AiContentAudit', component: () => import('../views/ai/AiContentAudit.vue'), meta: { menu: { group: '内容审核', icon: 'spark', countKey: 'ai' }, title: 'AI 内容审核' } },
      { path: 'report', name: 'ReportList', component: () => import('../views/report/ReportList.vue'), meta: { menu: { group: '用户与社区', icon: 'warn', countKey: 'report' }, detailTitle: '举报详情', title: '举报处理' } },
      { path: 'notice', name: 'NoticeManage', component: () => import('../views/notice/NoticeManage.vue'), meta: { menu: { group: '系统管理', icon: 'bell' }, title: '系统公告' } },
      { path: 'notice/edit/:id?', name: 'NoticeEdit', component: () => import('../views/notice/NoticeEdit.vue'), meta: { activeMenu: 'NoticeManage', title: '公告编辑' } },
      { path: 'ai/config', name: 'AiConfig', component: () => import('../views/ai/AiConfig.vue'), meta: { menu: { group: '系统管理', icon: 'gear' }, title: 'AI 配置' } },
      { path: 'ai/logs', name: 'AiLogs', component: () => import('../views/ai/AiLogs.vue'), meta: { menu: { group: '系统管理', icon: 'log' }, title: '调用日志' } },
      { path: 'system/config', name: 'SystemConfig', component: () => import('../views/system/SystemConfig.vue'), meta: { menu: { group: '系统管理', icon: 'gear' }, title: '系统配置', superOnly: true } },
      { path: 'system/operation-log', name: 'AdminOperationLog', component: () => import('../views/system/AdminOperationLog.vue'), meta: { menu: { group: '系统管理', icon: 'log' }, title: '操作日志', permission: 'system:operation-log:list' } },
      { path: 'system', name: 'AdminList', component: () => import('../views/system/AdminList.vue'), meta: { menu: { group: '系统管理', icon: 'admin' }, title: '管理员账号', superOnly: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

export const adminRoutes = routes[1].children

const router = createRouter({
  history: createWebHistory('/admin/'),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.name === 'AdminSearch' && from.name === 'AdminSearch' && to.query.q === from.query.q
        && to.query.scope === from.query.scope) return false
    if (to.name === 'AdminSearch' || from.name === 'AdminSearch') return { top: 0 }
    if (from.name === 'Dashboard' && to.name === 'IdleAudit' && to.query.record) return { top: 0 }
  }
})

// 权限守卫：未登录跳登录页；superOnly 页面校验角色
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const token = localStorage.getItem('admin_token')
  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.superOnly) {
    // admin_info 本地 JSON 可能损坏：容错解析，解析失败按非超管处理，不令导航守卫崩溃
    const info = safeParseAdminInfo()
    if (info?.role !== 'super') {
      return { path: '/dashboard' }
    }
  }
  if (to.meta.permission) {
    const admin = useAdminStore()
    try {
      await admin.loadSession(to.fullPath)
    } catch {
      if (!admin.isLoggedIn) return { name: 'Login', query: { redirect: to.fullPath } }
      return { name: 'Dashboard' }
    }
    if (!admin.can(to.meta.permission)) {
      ElMessage.warning('无权限查看该页面')
      return { name: 'Dashboard' }
    }
  }
  return true
})

/** 容错解析 admin_info 本地缓存，损坏时返回 null */
function safeParseAdminInfo() {
  try {
    return JSON.parse(localStorage.getItem('admin_info') || 'null')
  } catch (e) {
    localStorage.removeItem('admin_info')
    return null
  }
}

// An open tab may still reference chunks from the preceding deployment.
router.onError((error, to) => {
  if (!/Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module/i.test(error?.message || '')) return
  const destination = router.resolve(to).href
  const key = 'admin-route-recovery'
  try {
    const previous = JSON.parse(sessionStorage.getItem(key) || 'null')
    if (previous && Date.now() - previous.time < 30_000) {
      ElMessage.error('页面资源加载失败，请检查网络后刷新重试。')
      return
    }
    sessionStorage.setItem(key, JSON.stringify({ time: Date.now() }))
  } catch {
    ElMessage.error('页面资源加载失败，请刷新后重试。')
    return
  }
  window.location.replace(destination)
})

export default router
