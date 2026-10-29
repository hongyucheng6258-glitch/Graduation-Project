<template>
  <div class="app">
    <!-- ===================== 移动端抽屉导航 ===================== -->
    <aside class="sidebar student-drawer" :class="{ open: drawerOpen }" aria-hidden="!drawerOpen">
      <div class="drawer-brand">
        <a class="brand" @click="go('/')">
          <span class="brand-symbol" v-html="BRAND_MARK"></span>
          <span><b>梧桐校园</b></span>
        </a>
        <button class="icon-btn mobile-menu" aria-label="关闭导航" @click="drawerOpen = false">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg>
        </button>
      </div>
      <nav class="side-scroll" aria-label="全部功能">
        <template v-for="group in navGroups" :key="group.label">
          <div class="nav-group">{{ group.label }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            class="nav-link"
            :class="{ active: isActive(item.to) }"
            @click="drawerOpen = false"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" v-html="ICONS[item.icon]"></svg>
            {{ item.label }}
          </router-link>
        </template>
      </nav>
      <div class="sidebar-bottom">
        <router-link class="user-chip" :to="userStore.isLoggedIn ? '/profile' : '/login'">
          <WtAvatar :name="userStore.userInfo?.nickname || '梧'" :src="userStore.userInfo?.avatar" size="sm" />
          <span>
            <b>{{ userStore.isLoggedIn ? (userStore.userInfo?.nickname || '校园用户') : '未登录' }}</b>
            <small>{{ userStore.isLoggedIn ? '个人中心' : '点击登录' }}</small>
          </span>
        </router-link>
      </div>
    </aside>
    <button v-if="drawerOpen" class="drawer-scrim" aria-label="收起导航" @click="drawerOpen = false"></button>

    <!-- ===================== 主区 ===================== -->
    <div class="main">
      <!-- V2 顶部导航（88px） -->
      <header class="campus-header">
        <div class="header-inner">
          <button class="icon-btn mobile-menu" aria-label="切换导航" @click="drawerOpen = true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16"/></svg>
          </button>

          <a class="brand" @click="go('/')">
            <span class="brand-symbol" v-html="BRAND_MARK"></span>
            <span><b>梧桐校园</b></span>
          </a>

          <nav class="desktop-nav" aria-label="校园导航">
            <router-link
              v-for="item in desktopNav"
              :key="item.to"
              :to="item.to"
              class="desktop-link"
              :class="{ active: isActive(item.to) }"
            >
              {{ item.label }}
              <span v-if="item.ai" class="nav-ai-dot" aria-hidden="true"></span>
            </router-link>
          </nav>

          <div class="top-actions">
            <!-- 搜索（桌面显示完整表单，窄屏收为图标） -->
            <form class="header-search" role="search" @submit.prevent="onSearch">
              <label class="sr-only" for="campus-search-type">搜索范围</label>
              <select id="campus-search-type" v-model="searchType" aria-label="搜索范围">
                <option value="all">全部</option>
                <option value="activity">活动</option>
                <option value="idle">闲置</option>
                <option value="lost">失物</option>
                <option value="post">动态</option>
              </select>
              <div class="header-search-input">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>
                <input v-model="keyword" type="search" :placeholder="searchPlaceholder" aria-label="搜索关键词" />
              </div>
              <button class="header-search-submit search-submit" type="submit" aria-label="搜索">搜索</button>
            </form>
            <button class="icon-btn search-link" aria-label="综合搜索" @click="go('/search')">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>
            </button>

            <WtThemeToggle />

            <el-badge
              :value="messageStore.unread + chatStore.unreadTotal"
              :hidden="messageStore.unread + chatStore.unreadTotal === 0"
              class="bell-wrap"
            >
              <button class="icon-btn" aria-label="消息中心" @click="goMessage">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/></svg>
              </button>
            </el-badge>

            <el-dropdown v-if="userStore.isLoggedIn" trigger="click" @command="onPublishCommand">
              <button class="btn primary header-publish">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
                发布
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="activity">发布活动</el-dropdown-item>
                  <el-dropdown-item command="idle">发布闲置</el-dropdown-item>
                  <el-dropdown-item command="lostfound">发布失物信息</el-dropdown-item>
                  <el-dropdown-item command="partner">发布搭子帖</el-dropdown-item>
                  <el-dropdown-item command="qa">发布提问</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-dropdown v-if="userStore.isLoggedIn" trigger="click" @command="onCommand">
              <button class="header-avatar" aria-label="个人中心">
                <WtAvatar :name="userStore.userInfo?.nickname" :src="userStore.userInfo?.avatar" size="sm" />
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="wrong">错题本</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <button v-else class="btn small header-login" @click="go('/login')">登录</button>
          </div>
        </div>
      </header>

      <!-- V2 工具条：面包屑 + 快捷链接 -->
      <div class="utility-bar">
        <div class="utility-path">
          <a class="text-btn" @click="go('/')">我的校园</a>
          <span>/</span>
          <b>{{ pageTitle }}</b>
        </div>
        <div class="utility-links">
          <a class="text-btn" @click="go('/notice')">校园公告</a>
          <a class="text-btn" @click="go('/chat')">私信</a>
          <a class="text-btn" @click="go('/ai/wrong')">我的错题</a>
          <a class="text-btn" @click="go('/profile')">我的主页</a>
        </div>
      </div>

      <main class="content">
        <router-view />
      </main>

      <footer class="footer">
        <div class="footer-brand">
          <span class="brand-symbol" v-html="BRAND_MARK"></span>
          <span>梧桐校园 <small>让校园生活更有连接</small></span>
        </div>
        <div class="footer-links">
          <span>AI Campus · 毕业设计项目</span>
          <a class="text-btn" @click="go('/maintenance')">页面导航</a>
          <a class="text-btn" @click="go('/maintenance')">帮助与反馈</a>
        </div>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import { useMessageStore } from '../store/message'
import { useChatStore } from '../store/chat'
import WtThemeToggle from '../components/wt/WtThemeToggle.vue'
import WtAvatar from '../components/wt/WtAvatar.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const messageStore = useMessageStore()
const chatStore = useChatStore()
const drawerOpen = ref(false)
const keyword = ref('')
const searchType = ref('all')
const searchRoutes = {
  all: '/search',
  activity: '/activity',
  idle: '/idle',
  lost: '/lostfound',
  post: '/social'
}
const searchLabels = {
  all: '校园内容',
  activity: '校园活动',
  idle: '闲置物品',
  lost: '失物招领',
  post: '校园动态'
}
const searchPlaceholder = computed(() => `搜索${searchLabels[searchType.value]}`)

const BRAND_MARK = '<svg viewBox="0 0 40 40" aria-hidden="true"><path d="M4 5h13a3 3 0 0 1 3 3v12H8a4 4 0 0 1-4-4Z"/><path d="M23 4h13v12a4 4 0 0 1-4 4H23Z" opacity=".65"/><path d="M4 23h16v13H8a4 4 0 0 1-4-4Z" opacity=".65"/><path d="M23 23h13v13H23Z" opacity=".3"/></svg>'

const ICONS = {
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/>',
  calendar: '<rect x="3" y="4" width="18" height="17" rx="2"/><path d="M3 9h18M8 2v4M16 2v4"/>',
  bag: '<path d="M3 7h18l-2 13H5z"/><path d="M3 7l-1-3H0M6 11v6M10 11v6M14 11v6M18 11v6"/>',
  lost: '<circle cx="12" cy="8" r="5"/><path d="M9 13l-1.5 8L12 18l4.5 3L15 13"/>',
  chat: '<path d="M21 11.5a8.5 8.5 0 0 1-12.5 7.5L3 21l2-5.5A8.5 8.5 0 1 1 21 11.5z"/>',
  megaphone: '<path d="M3 11l18-5v12L3 13zM11.6 16.8a3 3 0 1 1-5.8-1.6"/>',
  spark: '<path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/><circle cx="12" cy="12" r="3"/>',
  code: '<path d="m8 9-4 3 4 3M16 9l4 3-4 3M13 6l-2 12"/>',
  book: '<path d="M4 19V5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2zM17 3v16"/>',
  bell: '<path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>',
  cube: '<path d="m12 2 9 5-9 5-9-5 9-5Z"/><path d="M3 7v10l9 5 9-5V7M12 12v10"/>'
}

const desktopNav = [
  { to: '/', label: '首页' },
  { to: '/campus-3d', label: '3D校园' },
  { to: '/activity', label: '校园活动' },
  { to: '/idle', label: '闲置互换' },
  { to: '/lostfound', label: '失物招领' },
  { to: '/social', label: '校园动态' },
  { to: '/draw-guess', label: '你画我猜' },
  { to: '/ai/chat', label: 'AI 学习', ai: true }
]

const navGroups = [
  {
    label: '校园服务',
    items: [
      { to: '/', label: '综合门户', icon: 'home' },
      { to: '/campus-3d', label: '3D校园', icon: 'cube' },
      { to: '/activity', label: '校园活动', icon: 'calendar' },
      { to: '/idle', label: '闲置互换', icon: 'bag' },
      { to: '/lostfound', label: '失物招领', icon: 'lost' },
      { to: '/partner', label: '学习搭子', icon: 'spark' },
      { to: '/qa', label: '互助问答', icon: 'chat' },
      { to: '/social', label: '动态广场', icon: 'chat' },
      { to: '/draw-guess', label: '你画我猜', icon: 'spark' },
      { to: '/notice', label: '校园公告', icon: 'megaphone' },
      { to: '/message', label: '消息中心', icon: 'bell' },
      { to: '/chat', label: '私信会话', icon: 'chat' }
    ]
  },
  {
    label: 'AI 学习',
    items: [
      { to: '/ai/chat', label: 'AI 答疑', icon: 'spark' },
      { to: '/ai/code', label: '代码纠错', icon: 'code' },
      { to: '/ai/wrong', label: '错题本', icon: 'book' }
    ]
  }
]

const PAGE_TITLES = {
  '/': '综合门户',
  '/campus-3d': '3D校园',
  '/search': '搜索结果',
  '/activity': '校园活动',
  '/activity/publish': '发布活动',
  '/idle': '闲置互换',
  '/idle/publish': '发布闲置',
  '/lostfound': '失物招领',
  '/lostfound/publish': '发布失物信息',
  '/partner': '学习搭子',
  '/partner/publish': '发布搭子帖',
  '/qa': '互助问答',
  '/qa/publish': '发布提问',
  '/social': '动态广场',
  '/draw-guess': '你画我猜',
  '/notice': '校园公告',
  '/message': '消息中心',
  '/chat': '私信',
  '/ai/chat': 'AI 答疑',
  '/ai/code': '代码纠错',
  '/ai/wrong': '错题本',
  '/profile': '个人中心'
}
const pageTitle = computed(() => {
  const p = route.path
  const matched = Object.keys(PAGE_TITLES).find((k) => p === k || p.startsWith(k + '/'))
  return (matched && PAGE_TITLES[matched]) || route.meta?.title || '梧桐校园'
})

function isActive(to) {
  if (to === '/') return route.path === '/'
  return route.path === to || route.path.startsWith(to + '/')
}

function go(path) {
  router.push(path)
}

function onSearch() {
  const q = keyword.value.trim()
  if (!q) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  router.push({ path: searchRoutes[searchType.value], query: { q } })
}

function goMessage() {
  if (router.currentRoute.value.path === '/message') return
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  router.push('/message')
}

// 事件委托：点击铃铛包裹区任意位置（含悬浮的未读角标）都进入消息中心
function onBellDocClick(e) {
  if (e.target.closest && e.target.closest('.bell-wrap')) goMessage()
}
onMounted(() => document.addEventListener('click', onBellDocClick))
onUnmounted(() => document.removeEventListener('click', onBellDocClick))

function onPublishCommand(cmd) {
  const map = {
    activity: '/activity/publish',
    idle: '/idle/publish',
    lostfound: '/lostfound/publish',
    partner: '/partner/publish',
    qa: '/qa/publish'
  }
  router.push(map[cmd])
}

function onCommand(cmd) {
  if (cmd === 'profile') router.push('/profile')
  else if (cmd === 'wrong') router.push('/ai/wrong')
  else if (cmd === 'logout') {
    userStore.logout()
    messageStore.stopPolling()
    chatStore.destroy()
    router.push('/login')
  }
}

function handleAuthExpired() {
  userStore.logout()
}

watch(
  () => userStore.isLoggedIn,
  (isLoggedIn) => {
    if (isLoggedIn) {
      messageStore.startPolling()
      chatStore.init(userStore.userInfo?.id)
    } else {
      messageStore.stopPolling()
      chatStore.destroy()
    }
  },
  { immediate: true }
)

window.addEventListener('auth-expired', handleAuthExpired)
onUnmounted(() => {
  window.removeEventListener('auth-expired', handleAuthExpired)
  window.removeEventListener('click', onBellDocClick)
  messageStore.stopPolling()
  chatStore.destroy()
})
</script>

<style scoped>
.app { min-height: 100vh; }
.main { min-width: 0; }

/* ===================== V2 顶部导航 ===================== */
.campus-header {
  height: 88px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  position: relative;
  z-index: 15;
}
.header-inner {
  height: 100%;
  max-width: 1440px;
  padding: 0 40px;
  margin: auto;
  display: flex;
  align-items: center;
  gap: 30px;
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  cursor: pointer;
}
.brand-symbol {
  width: 38px;
  height: 38px;
  color: var(--brand);
  display: inline-flex;
}
.brand-symbol :deep(svg) {
  width: 100%;
  height: 100%;
  fill: currentColor;
}
.brand b {
  font-size: 23px;
  letter-spacing: .5px;
  font-weight: 750;
  white-space: nowrap;
  color: var(--ink);
}
.desktop-nav {
  display: flex;
  align-items: center;
  gap: 28px;
  margin-left: 28px;
  height: 100%;
}
.desktop-link {
  font-size: 14px;
  white-space: nowrap;
  height: 100%;
  display: flex;
  align-items: center;
  position: relative;
  color: var(--ink-3);
  font-weight: 500;
  text-decoration: none;
}
.desktop-link.active { color: var(--brand); font-weight: 650; }
.desktop-link.active::after {
  content: "";
  position: absolute;
  height: 3px;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--brand);
  border-radius: 4px;
}
.nav-ai-dot {
  background: var(--brand);
  width: 5px;
  height: 5px;
  position: absolute;
  right: -8px;
  top: 28px;
  border-radius: 50%;
}
.top-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}
.icon-btn {
  border: 0;
  background: transparent;
  color: var(--ink-3);
  padding: 9px;
  display: inline-grid;
  place-items: center;
  border-radius: 10px;
  min-width: 38px;
  min-height: 38px;
  cursor: pointer;
}
.icon-btn:hover { color: var(--brand); background: var(--brand-soft); }
.icon-btn svg { width: 20px; height: 20px; }

/* 顶栏搜索 */
.header-search {
  display: grid;
  grid-template-columns: 80px minmax(140px, 220px) auto;
  align-items: center;
  height: 38px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface-2);
  overflow: hidden;
  transition: border-color .2s var(--ease-out), box-shadow .2s var(--ease-out), background .2s;
}
.header-search:focus-within {
  border-color: var(--brand);
  background: var(--surface);
  box-shadow: 0 0 0 3px var(--brand-soft);
}
.header-search select {
  min-width: 0;
  padding: 0 10px;
  border: 0;
  border-right: 1px solid var(--line);
  outline: none;
  background: transparent;
  color: var(--ink-2);
  font: 600 var(--fs-cap)/1 var(--font-sans);
  cursor: pointer;
  height: 100%;
}
.header-search-input { position: relative; min-width: 0; display: flex; align-items: center; }
.header-search-input svg {
  position: absolute;
  left: 11px;
  width: 15px;
  height: 15px;
  color: var(--ink-3);
  pointer-events: none;
}
.header-search input {
  width: 100%;
  height: 36px;
  padding: 0 8px 0 32px;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--ink);
  font: var(--fs-xs)/1 var(--font-sans);
}
.header-search input::placeholder { color: var(--ink-3); }
.header-search-submit {
  height: 100%;
  padding: 0 12px;
  border: 0;
  background: var(--brand);
  color: var(--brand-ink);
  font: 600 var(--fs-cap)/1 var(--font-sans);
  cursor: pointer;
  transition: background-color .18s ease;
}
.header-search-submit:hover { background: var(--brand-strong); }
.search-link { display: none; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }

.header-publish { font-size: 13px; min-height: 36px; padding: 0 13px; border-radius: 9px; }
.header-publish svg { width: 15px; height: 15px; }
.header-login { min-height: 34px; }
.header-avatar {
  padding-left: 9px;
  border-left: 1px solid var(--line);
  display: flex;
  background: none;
  border-right: 0;
  border-top: 0;
  border-bottom: 0;
  cursor: pointer;
  align-items: center;
}
.bell-wrap { line-height: 0; cursor: pointer; }
.bell-wrap :deep(.el-badge__content) { transform: translate(18%, -18%); }

/* ===================== 工具条 ===================== */
.utility-bar {
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  color: var(--ink-3);
  font-size: 12px;
}
.utility-path { display: flex; align-items: center; gap: 13px; }
.utility-path b { font-weight: 500; color: var(--ink); }
.utility-links { display: flex; gap: 25px; }
.utility-path .text-btn, .utility-links .text-btn { color: var(--ink-3); font-size: 12px; }
.utility-links .text-btn:hover { color: var(--brand); }

/* ===================== 内容与页脚 ===================== */
.content {
  max-width: 1376px;
  margin: 0 auto;
  padding: 0 40px 25px;
  min-height: calc(100vh - 260px);
}
.footer {
  margin-top: 54px;
  padding-top: 24px;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: space-between;
  gap: 25px;
  align-items: center;
  font-size: 11px;
  color: var(--ink-3);
}
.footer-brand { display: flex; gap: 10px; align-items: center; color: var(--ink); }
.footer-brand .brand-symbol { width: 30px; height: 30px; fill: var(--brand); }
.footer-brand > span { font-size: 14px; font-weight: 600; }
.footer-brand small { display: block; font-size: 10px; color: var(--ink-3); font-weight: 400; margin-top: 2px; }
.footer-links { display: flex; align-items: center; gap: 19px; font-size: 11px; flex-wrap: wrap; }
.footer-links .text-btn { font-size: 11px; color: var(--ink-3); }
.footer-links a:hover { color: var(--brand); }

/* ===================== 移动端抽屉 ===================== */
.sidebar {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: 252px;
  background: var(--surface);
  border-right: 1px solid var(--line);
  padding: 28px 16px 18px;
  display: flex;
  flex-direction: column;
  z-index: 40;
  transform: translateX(-100%);
  transition: transform .2s var(--ease-out);
  box-shadow: 15px 0 50px oklch(25% 0.04 265 / .12);
}
.student-drawer { display: flex; }
.sidebar.open { transform: translateX(0); }
.drawer-brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 22px;
}
.drawer-brand .brand b { font-size: 20px; }
.drawer-brand .brand-symbol { width: 31px; height: 31px; }
.side-scroll { overflow-y: auto; scrollbar-width: thin; min-height: 0; }
.nav-group { font-size: 10px; color: var(--ink-3); margin: 20px 14px 10px; letter-spacing: .08em; }
.nav-link {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 10px 13px;
  font-size: 13px;
  color: var(--ink-3);
  border-radius: 9px;
  margin: 3px 0;
  text-decoration: none;
  transition: background .18s, color .18s;
}
.nav-link svg { width: 17px; height: 17px; flex: none; }
.nav-link.active { background: var(--brand-soft); color: var(--brand); font-weight: 600; }
.nav-link:hover { background: var(--surface-2); color: var(--ink); }
.sidebar-bottom { margin-top: auto; border-top: 1px solid var(--line); padding-top: 14px; }
.user-chip {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 4px 7px;
  text-decoration: none;
  color: var(--ink);
}
.user-chip b { font-size: 12px; display: block; }
.user-chip small { font-size: 10px; color: var(--ink-3); display: block; margin-top: 3px; }
.drawer-scrim {
  position: fixed;
  inset: 0;
  background: oklch(20% 0.04 265 / .45);
  border: 0;
  z-index: 35;
  backdrop-filter: blur(3px);
}
.mobile-menu { display: none; }

/* ===================== 响应式 ===================== */
@media (min-width: 1600px) {
  .header-inner { max-width: 1540px; padding: 0 50px; }
  .content { max-width: 1480px; padding-left: 50px; padding-right: 50px; }
}
@media (max-width: 1250px) {
  .header-inner { gap: 20px; padding: 0 28px; }
  .desktop-nav { gap: 20px; margin-left: 14px; }
  .desktop-link { font-size: 13px; }
  .top-actions { gap: 7px; }
  .content { padding-left: 30px; padding-right: 30px; }
  .header-search { grid-template-columns: 70px minmax(110px, 170px) auto; }
}
@media (max-width: 1080px) {
  .brand b { font-size: 20px; }
  .brand-symbol { height: 33px; width: 33px; }
  .desktop-nav { gap: 18px; margin-left: 4px; }
  .header-inner { gap: 12px; padding: 0 22px; }
  .top-actions { gap: 5px; }
  .header-search { display: none; }
  .search-link { display: inline-grid; }
  .footer { align-items: flex-start; }
  .footer-links { gap: 13px; }
  .footer-links > span { width: 100%; }
}
@media (max-width: 760px) {
  .mobile-menu { display: inline-grid; }
  .campus-header { height: 72px; }
  .header-inner { padding: 0 17px; gap: 9px; }
  .header-inner > .brand { gap: 7px; }
  .header-inner .brand-symbol { height: 29px; width: 29px; }
  .header-inner .brand b { font-size: 20px; }
  .desktop-nav { display: none; }
  .header-inner > .mobile-menu { min-width: 25px; padding: 4px; margin-right: 2px; }
  .top-actions { gap: 6px; }
  .header-publish { min-height: 34px; font-size: 12px; padding: 0 11px; }
  .header-publish svg { width: 15px; }
  .top-actions .icon-btn { min-width: 30px; min-height: 32px; }
  .top-actions > .icon-btn:nth-child(2) { display: none; }
  .content { padding: 0 20px 24px; }
  .utility-bar { height: 54px; }
  .utility-links { gap: 15px; }
  .utility-links a:first-child, .utility-links a:last-child { display: none; }
  .utility-bar .text-btn { font-size: 11px; }
  .utility-path { font-size: 11px; gap: 10px; }
  .footer { flex-direction: column; gap: 21px; }
  .footer-links { font-size: 11px; gap: 14px; width: 100%; }
  .footer-links > span { font-size: 10px; }
}
@media (max-width: 370px) {
  .header-inner { padding: 0 12px; gap: 7px; }
  .header-inner .brand b { font-size: 18px; }
  .header-inner .brand-symbol { height: 26px; width: 26px; }
  .header-inner .top-actions { gap: 3px; }
}
</style>
