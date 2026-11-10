<template>
  <div class="admin-shell">
    <!-- ===================== 深色侧栏 ===================== -->
    <aside class="sidebar">
      <RouterLink to="/dashboard" class="brand" aria-label="梧桐校园 · 后台">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 40 40" fill="currentColor" focusable="false">
            <path d="M4 5h13a3 3 0 0 1 3 3v12H8a4 4 0 0 1-4-4Z" />
            <path d="M23 4h13v12a4 4 0 0 1-4 4H23Z" opacity=".65" />
            <path d="M4 23h16v13H8a4 4 0 0 1-4-4Z" opacity=".65" />
            <path d="M23 23h13v13H23Z" opacity=".3" />
          </svg>
        </span>
        <b class="brand-name">梧桐校园 · 后台</b>
      </RouterLink>

      <nav class="side-nav" aria-label="管理导航">
        <div v-for="group in visibleGroups" :key="group.label" class="nav-group">
          <h2 class="nav-group-label">{{ group.label }}</h2>
          <RouterLink
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            class="nav-item"
            :class="{ active: isActive(item.to) }"
            :aria-current="isActive(item.to) ? 'page' : undefined"
            :aria-label="item.label"
            :title="item.label"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              aria-hidden="true" focusable="false" v-html="ICONS[item.icon]"></svg>
            <span>{{ item.label }}</span>
            <em v-if="item.countKey && counts[item.countKey] > 0" class="nav-count">{{ counts[item.countKey] }}</em>
          </RouterLink>
        </div>
      </nav>

      <div class="sidebar-foot">
        <span class="side-avatar" aria-hidden="true">{{ adminStore.isSuper ? '管' : roleText.charAt(0) }}</span>
        <div class="side-user-meta">
          <b>{{ roleText }}</b>
          <span v-if="adminAccount">{{ adminAccount }}</span>
        </div>
      </div>
    </aside>

    <!-- ===================== 主区 ===================== -->
    <div class="main">
      <header class="topbar">
        <div class="crumbs">
          <span>首页</span><span class="sep">/</span><b>{{ $route.meta.title || '管理后台' }}</b>
        </div>
        <div class="topbar-search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>
          <input ref="searchRef" v-model="keyword" type="search" placeholder="搜索用户 / 内容 / 工单 ID…（Ctrl + K）" @keydown.enter="onGlobalSearch" />
        </div>
        <div class="top-actions">
          <button class="icon-btn" title="切换主题" @click="toggleTheme">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>
          </button>
          <button class="icon-btn" title="待办通知" @click="goNotice">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/></svg>
            <span v-if="countError" class="badge-dot retry" title="待办数加载失败，点击重试" @click.stop="loadCounts">!</span>
            <span v-else class="badge-dot">{{ totalPending }}</span>
          </button>
          <button class="icon-btn" title="退出" @click="logout">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/></svg>
          </button>
        </div>
      </header>

      <div class="content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminStore } from '../store/admin'
import { statsPendingCounts } from '../api/stats'
import { matchesNavigationPath } from './adminSidebarNavigation.mjs'

const router = useRouter()
const route = useRoute()
const adminStore = useAdminStore()
const keyword = ref('')
const searchRef = ref()
const counts = ref({})
const countError = ref(false)
let countTimer = null

const ICONS = {
  dashboard: '<rect x="3" y="3" width="8" height="8" rx="2"/><rect x="13" y="3" width="8" height="8" rx="2"/><rect x="3" y="13" width="8" height="8" rx="2"/><rect x="13" y="13" width="8" height="8" rx="2"/>',
  audit: '<path d="M9 12l2 2 4-4"/><circle cx="12" cy="12" r="9"/>',
  spark: '<path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1"/><circle cx="12" cy="12" r="3"/>',
  users: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3.87-3.87M16 3.13a4 4 0 0 1 0 7.75"/>',
  warn: '<path d="M12 9v4M12 17h.01"/><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>',
  bell: '<path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>',
  admin: '<path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1"/>',
  log: '<path d="M4 4h16v16H4z"/><path d="M8 9h8M8 13h8M8 17h5"/>'
}

const auditTypes = [
  { key: 'activity', icon: 'audit', to: '/audit/activity', label: '活动审核', countKey: 'activity' },
  { key: 'idle', icon: 'audit', to: '/audit/idle', label: '闲置审核', countKey: 'idle' },
  { key: 'lostfound', icon: 'audit', to: '/audit/lostfound', label: '失物招领审核', countKey: 'lostfound' },
  { key: 'post', icon: 'audit', to: '/audit/post', label: '动态审核', countKey: 'post' },
  { key: 'partner', icon: 'audit', to: '/audit/partner', label: '搭子审核', countKey: 'partner' }
]

const navGroups = [
  {
    label: '概览',
    items: [
      { to: '/dashboard', label: '数据看板', icon: 'dashboard' },
      { to: '/assistant', label: '管理智能助手', icon: 'spark' }
    ]
  },
  {
    label: '内容审核',
    items: [
      ...auditTypes,
      { to: '/ai/audit', label: 'AI 内容审核', icon: 'spark', countKey: 'ai' }
    ]
  },
  {
    label: '内容管理',
    items: [
      { to: '/content', label: '内容管理', icon: 'log' }
    ]
  },
  {
    label: '用户与社区',
    items: [
      { to: '/user', label: '用户管理', icon: 'users' },
      { to: '/report', label: '举报处理', icon: 'warn', countKey: 'report' }
    ]
  },
  {
    label: '系统',
    items: [
      { to: '/notice', label: '系统公告', icon: 'bell' },
      { to: '/ai/config', label: 'AI 配置', icon: 'gear' },
      { to: '/ai/logs', label: '调用日志', icon: 'log' },
      { to: '/system/config', label: '系统配置', icon: 'gear', superOnly: true },
      { to: '/system', label: '管理员账号', icon: 'admin', superOnly: true }
    ]
  }
]

const visibleGroups = computed(() =>
  navGroups
    .map((g) => ({ ...g, items: g.items.filter((m) => !m.superOnly || adminStore.isSuper) }))
    .filter((g) => g.items.length > 0)
)

const adminAccount = computed(() => adminStore.adminInfo?.username || adminStore.adminInfo?.nickname || '')

const roleText = computed(() => {
  const role = adminStore.adminInfo?.role
  // 后端角色定义：super=超级管理员 / audit=审核员（与 AdminSaveDTO 角色约束一致）
  return { super: '超级管理员', audit: '审核员' }[role] || role || '管理员'
})

const totalPending = computed(() => {
  // 待办角标 = 各审核类型 + 举报（互斥业务项）；ai 复核是待审内容的子集，不累加避免重复计数
  const keys = ['activity', 'idle', 'lostfound', 'post', 'partner', 'report']
  return keys.reduce((a, k) => a + (Number(counts.value[k]) || 0), 0)
})

function isActive(to) {
  return matchesNavigationPath(to, route.path)
}

function onGlobalSearch() {
  const q = keyword.value.trim()
  router.push(q ? { path: '/user', query: { q } } : '/user')
}

function toggleTheme() {
  const root = document.documentElement
  const next = root.dataset.theme === 'dark' ? 'light' : 'dark'
  root.dataset.theme = next
  localStorage.setItem('admin_theme', next)
}

function logout() {
  adminStore.logout()
  router.push('/login')
}

function onKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    searchRef.value?.focus()
  }
}

function goNotice() {
  // 按待办数排序的跳转优先级（counts 含 report/ai）。
  // 路由使用 router 内部路径（base=/admin/ 由 createWebHistory 统一处理），不得再拼 /admin 前缀。
  const order = [
    { k: 'report', to: '/report' },
    { k: 'activity', to: '/audit/activity' },
    { k: 'idle', to: '/audit/idle' },
    { k: 'lostfound', to: '/audit/lostfound' },
    { k: 'post', to: '/audit/post' },
    { k: 'partner', to: '/audit/partner' },
    { k: 'ai', to: '/ai/audit' },
  ]
  const top = order.reduce((best, it) =>
    (Number(counts.value[it.k]) || 0) > (Number(counts.value[best.k]) || 0) ? it : best, order[0])
  if (Number(counts.value[top.k]) > 0) {
    router.push(top.to)
  } else {
    // 无待办时进入举报中心（含各分类待办入口）
    router.push('/report')
  }
}

async function loadCounts() {
  try {
    const data = await statsPendingCounts()
    counts.value = data || {}
    countError.value = ''
  } catch (e) {
    // 接口失败：保留上次计数并进入可重试失败态，不能静默显示成 0 条
    countError.value = true
  }
}

onMounted(() => {
  const saved = localStorage.getItem('admin_theme')
  if (saved) document.documentElement.dataset.theme = saved
  loadCounts()
  window.addEventListener('keydown', onKeydown)
  // 待办数定时刷新（举报/审核处置后红点实时更新）
  countTimer = window.setInterval(loadCounts, 30000)
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  if (countTimer) window.clearInterval(countTimer)
})
</script>

<style scoped>
.admin-shell {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  min-height: 100vh;
}

/* —— 深色侧栏 —— */
.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  height: 100dvh;
  z-index: 30;
  display: flex;
  flex-direction: column;
  background: var(--side-bg);
  color: var(--side-ink);
}
.brand {
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;
  padding: 22px 20px 18px;
}
.brand-mark { width: 32px; height: 32px; flex: none; color: #fff; }
.brand-name { color: #fff; font-size: 1.15rem; letter-spacing: .5px; white-space: nowrap; }
.side-nav { flex: 1; min-height: 0; overflow: auto; padding: 6px 12px; }
.nav-group { margin: 16px 0 6px; }
.nav-group-label {
  margin-bottom: 6px;
  padding: 0 10px;
  color: var(--side-ink-3);
  font-size: .6875rem;
  font-weight: 400;
  letter-spacing: .1em;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 2px 0;
  padding: 10px;
  border-radius: var(--r-sm);
  color: var(--side-ink);
  font-size: .9375rem;
  white-space: nowrap;
}
.nav-item svg { width: 17px; height: 17px; flex: none; }
.nav-item:hover { background: var(--side-hover); color: #fff; }
.nav-item.active { background: var(--side-active); color: #fff; font-weight: 600; }
.sidebar a:focus-visible { outline-color: #fff; outline-offset: -2px; }
.nav-count {
  margin-left: auto;
  padding: 1px 8px;
  border-radius: var(--r-pill);
  background: rgba(255, 255, 255, .16);
  font-size: 10px;
  font-style: normal;
}
.sidebar-foot {
  display: flex;
  flex: none;
  gap: 10px;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid var(--side-line);
}
.side-avatar {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand-strong);
  font-weight: 600;
}
.side-user-meta { min-width: 0; }
.side-user-meta b, .side-user-meta span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.side-user-meta b { color: #fff; font-size: .8125rem; }
.side-user-meta span { color: var(--side-ink-3); font-size: .6875rem; }

/* —— 主区 —— */
.main { display: flex; flex-direction: column; min-width: 0; }
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 18px;
  height: 64px;
  padding: 0 28px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}
.crumbs { display: flex; align-items: center; gap: 8px; font-size: var(--fs-sm); color: var(--ink-3); }
.crumbs b { color: var(--ink); font-weight: 600; }
.crumbs .sep { opacity: .5; }
.topbar-search { flex: 1; max-width: 260px; min-width: 0; margin-left: auto; position: relative; }
.topbar-search svg {
  position: absolute; left: 12px; top: 50%; width: 16px; height: 16px;
  transform: translateY(-50%); color: var(--ink-3); pointer-events: none;
}
.topbar-search input {
  width: 100%; height: 38px; padding: 0 14px 0 36px;
  border: 1px solid var(--line); border-radius: var(--r-sm);
  background: var(--surface-2); color: var(--ink); font-size: var(--fs-sm);
  transition: border-color .2s, box-shadow .2s, background .2s;
}
.topbar-search input:focus { outline: none; border-color: var(--brand); background: var(--surface); box-shadow: 0 0 0 3px var(--brand-soft); }
.top-actions { display: flex; align-items: center; gap: 12px; margin-left: auto; }
.topbar-search + .top-actions { margin-left: 0; }
.icon-btn {
  position: relative;
  width: 38px;
  height: 38px;
  border-radius: var(--r-sm);
  display: grid;
  place-items: center;
  color: var(--ink-2);
  border: 1px solid var(--line);
  background: var(--surface);
  cursor: pointer;
  transition: background .18s, color .18s, border-color .18s;
}
.icon-btn:hover { background: var(--surface-2); color: var(--ink); }
.icon-btn svg { width: 17px; height: 17px; }
.badge-dot {
  position: absolute; top: -4px; right: -4px;
  min-width: 16px; height: 16px; padding: 0 4px;
  border-radius: var(--r-pill); background: var(--accent); color: var(--accent-ink);
  font-size: 10px; font-weight: 700; display: grid; place-items: center;
}
.badge-dot.retry {
  background: var(--error);
  cursor: pointer;
  min-width: 18px;
}
.content { padding: 26px 28px; max-width: 1440px; width: 100%; margin: 0 auto; }

/* —— 响应式 —— */
@media (max-width: 920px) {
  .admin-shell { grid-template-columns: 64px minmax(0, 1fr); }
  .side-nav { padding: 4px 8px; }
  .brand-name, .nav-group-label, .nav-item span, .nav-count, .side-user-meta { display: none; }
  .nav-group { margin: 5px 0; }
  .nav-item { justify-content: center; padding: 10px 0; }
  .brand, .sidebar-foot { justify-content: center; padding: 12px 0; }
  .crumbs { display: none; }
}
@media (max-width: 780px) {
  .content { padding: var(--s-4); }
  .topbar { padding: 0 var(--s-4); }
  .topbar-search { display: none; }
  .topbar-search + .top-actions { margin-left: auto; }
}
</style>
