<template>
  <div class="admin-shell" :class="{ 'sidebar-collapsed': collapsed }">
    <div v-if="drawerOpen" class="sidebar-backdrop" aria-hidden="true" @click="closeDrawer()" />
    <!-- ===================== 深色侧栏 ===================== -->
    <aside id="admin-sidebar" ref="sidebar" class="sidebar" :class="{ 'drawer-open': drawerOpen }"
      :role="drawerOpen ? 'dialog' : undefined" :aria-modal="drawerOpen || undefined"
      :aria-label="drawerOpen ? '管理导航' : undefined">
      <button v-if="drawerOpen" ref="closeButton" type="button" class="icon-btn drawer-close"
        aria-label="关闭管理导航" @click="closeDrawer()">×</button>
      <RouterLink to="/dashboard" class="brand" @click="closeDrawer()" aria-label="梧桐校园 · 后台">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 40 40" fill="currentColor" focusable="false">
            <path d="M4 5h13a3 3 0 0 1 3 3v12H8a4 4 0 0 1-4-4Z" />
            <path d="M23 4h13v12a4 4 0 0 1-4 4H23Z" opacity=".65" />
            <path d="M4 23h16v13H8a4 4 0 0 1-4-4Z" opacity=".65" />
            <path d="M23 23h13v13H23Z" opacity=".3" />
          </svg>
        </span>
        <span class="brand-name"><b>梧桐校园 · 后台</b><small>CAMPUS OPERATIONS</small></span>
      </RouterLink>

      <nav class="side-nav" aria-label="管理导航">
        <div v-for="group in visibleGroups" :key="group.label" class="nav-group">
          <h2 class="nav-group-label">{{ group.label }}</h2>
          <RouterLink
            v-for="item in group.items"
            :key="item.name"
            :to="{ name: item.name }"
            class="nav-item"
            @click="closeDrawer()"
            :class="{ active: isActive(item.name) }"
            :aria-current="isActive(item.name) ? 'page' : undefined"
            :aria-label="item.meta.title"
            :title="item.meta.title"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              aria-hidden="true" focusable="false" v-html="ICONS[item.meta.menu.icon]"></svg>
            <span>{{ item.meta.title }}</span>
            <em v-if="item.meta.menu.countKey && pending.counts?.[item.meta.menu.countKey] > 0" class="nav-count">{{ pending.counts?.[item.meta.menu.countKey] }}</em>
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
    <div class="main" :inert="drawerOpen || undefined">
      <header class="topbar">
        <button ref="toggleButton" type="button" class="icon-btn" aria-label="切换管理导航"
          aria-controls="admin-sidebar" :aria-expanded="narrow ? drawerOpen : !collapsed" @click="toggleSidebar">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
        <AdminBreadcrumb :breadcrumbs="breadcrumbs" />
        <form class="topbar-search" role="search" aria-label="顶部全局搜索" @submit.prevent="onGlobalSearch">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>
          <input ref="searchRef" v-model="keyword" type="search" maxlength="80" aria-label="搜索用户、内容或工单 ID"
            placeholder="用户、内容或工单 ID" title="Ctrl / ⌘ + K" />
          <button type="submit">搜索</button>
        </form>
        <div class="top-actions">
          <RouterLink :to="{ name: 'AdminSearch' }" class="icon-btn mobile-search" aria-label="全局搜索" title="全局搜索">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>
          </RouterLink>
          <button class="icon-btn" title="切换主题" @click="toggleTheme">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>
          </button>
          <el-dropdown trigger="click" placement="bottom-end" @command="openPendingQueue">
            <button type="button" class="icon-btn" aria-label="待办通知">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 0 4.6 0"/></svg>
              <span v-if="pending.failed" class="badge-dot retry" aria-label="待办数更新失败">!</span>
              <span v-else-if="pending.total !== null" class="badge-dot">{{ pending.total }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="queue in queues" :key="queue.key" :command="queue.to">
                  <span class="pending-menu-label">{{ queue.label }}</span>
                  <span class="pending-menu-count">{{ pending.counts ? pending.counts[queue.key] ?? 0 : '—' }}</span>
                </el-dropdown-item>
                <el-dropdown-item v-if="pending.failed" divided command="retry" :disabled="pending.loading">
                  重试更新待办数
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminStore } from '../store/admin'
import { useAdminPendingStore } from '../store/adminPending'
import { adminRoutes } from '../router'
import AdminBreadcrumb from './AdminBreadcrumb.vue'
import { adminBreadcrumbs, adminMenu, pendingQueues } from './adminNavigation.mjs'
import { searchKeyword } from '../utils/searchQuery.mjs'

const router = useRouter()
const route = useRoute()
const adminStore = useAdminStore()
const keyword = ref('')
const searchRef = ref()
const pending = useAdminPendingStore()
const queues = pendingQueues(adminRoutes)
let countTimer = null
const media = window.matchMedia('(max-width: 780px)')
const narrow = ref(media.matches)
const collapsed = ref(false)
const drawerOpen = ref(false)
const sidebar = ref()
const toggleButton = ref()
const closeButton = ref()

function toggleSidebar() {
  if (!narrow.value) {
    collapsed.value = !collapsed.value
    return
  }
  drawerOpen.value = true
  nextTick(() => closeButton.value?.focus())
}

function closeDrawer() {
  if (!drawerOpen.value) return
  drawerOpen.value = false
  nextTick(() => toggleButton.value?.focus())
}

function updateViewport(event) {
  narrow.value = event.matches
  if (!event.matches) closeDrawer()
}

watch(() => route.fullPath, closeDrawer)
watch(() => route.query.q, q => {
  if (route.name === 'AdminSearch') keyword.value = searchKeyword(q)
}, { immediate: true })

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

const visibleGroups = computed(() => adminMenu(adminRoutes, adminStore.isSuper, adminStore.can))
const breadcrumbs = computed(() => adminBreadcrumbs(route, adminRoutes))

const adminAccount = computed(() => adminStore.adminInfo?.username || adminStore.adminInfo?.nickname || '')

const roleText = computed(() => {
  const role = adminStore.adminInfo?.role
  // 后端角色定义：super=超级管理员 / audit=审核员（与 AdminSaveDTO 角色约束一致）
  return { super: '超级管理员', audit: '审核员' }[role] || role || '管理员'
})

function isActive(name) {
  return name === (route.meta.activeMenu || route.name)
}

function onGlobalSearch() {
  const q = searchKeyword(keyword.value)
  router.push({ name: 'AdminSearch', query: q ? { q } : {} })
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
  if (drawerOpen.value) {
    if (e.key === 'Escape') closeDrawer()
    if (e.key === 'Tab') {
      const links = sidebar.value.querySelectorAll('button, a[href]')
      const first = links[0]
      const last = links[links.length - 1]
      const boundary = e.shiftKey ? first : last
      if (document.activeElement === boundary) {
        e.preventDefault()
        const target = e.shiftKey ? last : first
        target.focus()
      }
    }
    return
  }
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    if (searchRef.value?.offsetParent) searchRef.value.focus()
    else if (route.name === 'AdminSearch') document.getElementById('admin-search-keyword')?.focus()
    else router.push({ name: 'AdminSearch' })
  }
}

function openPendingQueue(target) {
  if (target === 'retry') pending.refresh()
  else router.push(target)
}

onMounted(() => {
  const saved = localStorage.getItem('admin_theme')
  if (saved) document.documentElement.dataset.theme = saved
  adminStore.loadSession().catch(() => {})
  pending.refresh()
  media.addEventListener('change', updateViewport)
  window.addEventListener('keydown', onKeydown)
  // 定时同步其他管理员处理的待办。
  countTimer = window.setInterval(() => pending.refresh(), 30000)
})
onUnmounted(() => {
  media.removeEventListener('change', updateViewport)
  window.removeEventListener('keydown', onKeydown)
  if (countTimer) window.clearInterval(countTimer)
  pending.clear()
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
  min-height: 72px;
  padding: 0 20px;
  border-bottom: 1px solid var(--side-line);
}
.brand-mark { width: 33px; height: 33px; padding: 7px; border-radius: 9px; background: var(--side-active); flex: none; color: #fff; }
.brand-name { color: #fff; font-size: 16px; letter-spacing: .5px; white-space: nowrap; }
.brand-name b, .brand-name small { display: block; }
.brand-name small { color: var(--side-ink-3); font-size: 10px; letter-spacing: .08em; }
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
  min-height: 42px;
  padding: 9px 11px;
  border-radius: var(--r-sm);
  color: var(--side-ink);
  font-size: 14px;
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
  min-height: 64px;
  padding: 8px 28px;
  background: color-mix(in srgb, var(--surface) 88%, transparent);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid var(--line);
}
.topbar-search { display: flex; flex: 1; max-width: 330px; min-width: 0; margin-left: auto; position: relative; }
.topbar-search svg {
  position: absolute; left: 12px; top: 50%; width: 16px; height: 16px;
  transform: translateY(-50%); color: var(--ink-3); pointer-events: none;
}
.topbar-search input {
  flex: 1; min-width: 0; height: 38px; padding: 0 8px 0 36px;
  border: 1px solid var(--line); border-radius: var(--r-sm) 0 0 var(--r-sm);
  background: var(--surface-2); color: var(--ink); font-size: var(--fs-sm);
  transition: border-color .2s, box-shadow .2s, background .2s;
}
.topbar-search button { flex: none; border: 0; border-radius: 0 var(--r-sm) var(--r-sm) 0; padding-inline: 10px; background: var(--brand); color: #fff; cursor: pointer; }
.topbar-search input:focus { outline: none; border-color: var(--brand); background: var(--surface); box-shadow: 0 0 0 3px var(--brand-soft); }
.top-actions { display: flex; flex: none; align-items: center; gap: 12px; margin-left: auto; }
.topbar-search + .top-actions { margin-left: 0; }
.top-actions .mobile-search { display: none; }
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
.content { padding: 26px 28px 40px; max-width: 1540px; width: 100%; margin: 0 auto; }

/* —— 响应式 —— */
.pending-menu-label { min-width: 7em; }
.pending-menu-count { margin-inline-start: auto; padding-inline-start: 14px; font-variant-numeric: tabular-nums; }
.sidebar-backdrop { position: fixed; inset: 0; z-index: 29; background: rgb(0 0 0 / 45%); }
.drawer-close { align-self: flex-end; margin: 8px 12px 0; }
@media (min-width: 1700px) {
  .content { padding: 32px 36px 46px; }
}
@media (min-width: 781px) {
  .sidebar-collapsed { grid-template-columns: 64px minmax(0, 1fr); }
  .sidebar-collapsed .side-nav { padding: 4px 8px; }
  .sidebar-collapsed :is(.brand-name, .nav-group-label, .nav-item span, .nav-count, .side-user-meta) { display: none; }
  .sidebar-collapsed .nav-group { margin: 5px 0; }
  .sidebar-collapsed .nav-item { justify-content: center; padding: 10px 0; }
  .sidebar-collapsed :is(.brand, .sidebar-foot) { justify-content: center; padding: 12px 0; }
}
@media (max-width: 780px) {
  .admin-shell { grid-template-columns: minmax(0, 1fr); }
  .sidebar { display: none; position: fixed; inset: 0 auto 0 0; width: min(280px, 90vw); }
  .sidebar.drawer-open { display: flex; }
  .content { padding: var(--s-4); }
  .topbar { padding-inline: var(--s-4); }
  .topbar-search { display: none; }
  .topbar-search + .top-actions { margin-left: auto; }
  .top-actions .mobile-search { display: grid; }
}
@media (max-width: 480px) {
  .topbar { gap: 10px; padding-inline: 12px; }
  .top-actions { gap: 6px; }
  .top-actions .icon-btn { width: 30px; height: 32px; }
}
</style>
