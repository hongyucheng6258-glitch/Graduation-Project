import { createRouter, createWebHistory } from 'vue-router'

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
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/dashboard/Dashboard.vue'), meta: { title: '数据大屏' } },
      { path: 'user', name: 'UserList', component: () => import('../views/user/UserList.vue'), meta: { title: '用户管理' } },
      { path: 'audit/activity', name: 'ActivityAudit', component: () => import('../views/audit/AuditModule.vue'), props: { type: 'activity' }, meta: { title: '活动审核' } },
{ path: 'audit/idle', name: 'IdleAudit', component: () => import('../views/audit/AuditModule.vue'), props: { type: 'idle' }, meta: { title: '闲置审核' } },
{ path: 'audit/lostfound', name: 'LostFoundAudit', component: () => import('../views/audit/AuditModule.vue'), props: { type: 'lostfound' }, meta: { title: '失物招领审核' } },
{ path: 'audit/post', name: 'PostAudit', component: () => import('../views/audit/AuditModule.vue'), props: { type: 'post' }, meta: { title: '动态审核' } },
      { path: 'audit/partner', name: 'PartnerAudit', component: () => import('../views/audit/AuditModule.vue'), props: { type: 'partner' }, meta: { title: '搭子审核' } },
      { path: 'content', name: 'ContentManage', component: () => import('../views/content/ContentManage.vue'), meta: { title: '内容管理' } },
{ path: 'audit', redirect: '/audit/activity' },
{ path: 'ai/audit', name: 'AiContentAudit', component: () => import('../views/ai/AiContentAudit.vue'), meta: { title: 'AI 内容审核' } },
      { path: 'report', name: 'ReportList', component: () => import('../views/report/ReportList.vue'), meta: { title: '举报处理' } },
      { path: 'notice', name: 'NoticeManage', component: () => import('../views/notice/NoticeManage.vue'), meta: { title: '公告管理' } },
      { path: 'notice/edit/:id?', name: 'NoticeEdit', component: () => import('../views/notice/NoticeEdit.vue'), meta: { title: '公告编辑' } },
      { path: 'ai/config', name: 'AiConfig', component: () => import('../views/ai/AiConfig.vue'), meta: { title: 'AI配置' } },
      { path: 'ai/logs', name: 'AiLogs', component: () => import('../views/ai/AiLogs.vue'), meta: { title: 'AI日志' } },
      { path: 'system/config', name: 'SystemConfig', component: () => import('../views/system/SystemConfig.vue'), meta: { title: '系统配置', superOnly: true } },
      { path: 'system', name: 'AdminList', component: () => import('../views/system/AdminList.vue'), meta: { title: '管理员管理', superOnly: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory('/admin/'),
  routes
})

// 权限守卫：未登录跳登录页；superOnly 页面校验角色
router.beforeEach((to) => {
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

export default router
