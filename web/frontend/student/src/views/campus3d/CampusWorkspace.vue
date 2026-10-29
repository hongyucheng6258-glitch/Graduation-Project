<template>
  <section ref="workspaceRoot" class="room-workspace" role="dialog" aria-modal="true" aria-labelledby="room-service-title">
    <header class="room-workspace-header">
      <div class="room-service-icon" v-html="icon(service.icon)"></div>
      <div class="room-service-heading"><small>{{ service.room }} · 学生端服务</small><h2 id="room-service-title">{{ service.name }}</h2></div>
      <span class="room-shared-data">与学生端共用数据和操作</span>
      <button ref="closeButton" class="room-return" @click="emit('close')">← 返回房间 <kbd>Esc</kbd></button>
    </header>
    <nav class="room-workspace-nav" aria-label="房间服务导航">
      <button :disabled="atLanding" @click="roomRouter?.back()">← 返回上一级</button>
      <button @click="roomRouter?.push(landing)">{{ service.name }}首页</button>
      <span>服务详情、发布和私信均在此办理</span>
    </nav>
    <div v-if="failure" class="room-workspace-error" role="alert"><h3>服务页面暂时无法打开</h3><p>{{ failure }}</p><button @click="mountPages">重新加载</button></div>
    <div v-show="!failure" ref="pageHost" class="room-page-host" tabindex="-1"></div>
  </section>
</template>

<script setup>
import { computed, createApp, h, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { RouterView } from 'vue-router'
import { getActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { routes } from '../../router/routes'
import { isLoggedIn } from '../../utils/auth'
import { useRequestRouter } from '../../utils/request-navigation'
import { SERVICES, icon } from '../../features/campus3d/campus-data'
import { createRoomRouter, SERVICE_PATHS } from '../../features/campus3d/room-router.mjs'

const props = defineProps({ serviceId: { type: String, required: true } })
const emit = defineEmits(['close'])
const service = computed(() => SERVICES.find(item => item.id === props.serviceId) || SERVICES[0])
const landing = SERVICE_PATHS[props.serviceId] || '/'
const pinia = getActivePinia()
const workspaceRoot = ref(null)
const pageHost = ref(null)
const closeButton = ref(null)
const roomRouter = shallowRef(null)
const atLanding = ref(true)
const failure = ref('')
let app, releaseRouter, scrollObserver, disposed = false, generation = 0
const scrollPositions = new Map()
const listState = new Map()
function cleanup() { scrollObserver?.disconnect(); app?.unmount(); app = null; releaseRouter?.(); releaseRouter = null }
async function mountPages() {
  const current = ++generation
  cleanup()
  failure.value = ''
  const router = createRoomRouter(routes, props.serviceId, isLoggedIn)
  roomRouter.value = router
  releaseRouter = useRequestRouter(router)
  router.onError(error => { failure.value = error.message || '请检查网络后重试' })
  router.beforeEach((to, from) => { scrollPositions.set(from.fullPath, pageHost.value?.scrollTop || 0) })
  router.afterEach(async (to, from, error) => {
    if (error) return
    atLanding.value = to.fullPath === landing
    await nextTick()
    scrollObserver?.disconnect()
    const scroll = scrollPositions.get(to.fullPath) || 0
    if (pageHost.value) {
      const restore = () => {
        pageHost.value.scrollTop = scroll
        if (pageHost.value.scrollTop >= scroll - 1) scrollObserver?.disconnect()
      }
      scrollObserver = new MutationObserver(restore)
      scrollObserver.observe(pageHost.value, { childList: true, subtree: true })
      restore()
    }
  })
  try {
    await router.replace(landing)
    await router.isReady()
    if (disposed || current !== generation) return
    // A separate app scopes both useRouter() and template $router to memory history.
    app = createApp({ render: () => h(RouterView, null, { default: ({ Component, route }) => Component && h(Component, { key: route.fullPath }) }) })
    app.provide('room-list-state', listState)
    app.use(pinia)
    app.use(router)
    app.use(ElementPlus, { locale: zhCn })
    app.config.errorHandler = (error, instance, info) => {
      if (error.code === 401 || error.code === 503) return // Request layer already redirects inside this room.
      if (error.code != null && /event handler/.test(info)) return // Keep failed mutation forms open; request layer shows the error.
      failure.value = error.message || '页面加载失败，请重试'
    }
    app.mount(pageHost.value)
  } catch (error) { if (!disposed) failure.value = error.message || '页面加载失败，请重试' }
}
function onKey(event) {
  if (event.defaultPrevented) return
  // Give inner dialogs, image previews and drawers their own focus/Escape handling.
  const overlay = [...document.querySelectorAll('.el-overlay, .el-image-viewer__wrapper')].some(el => el.getClientRects().length)
  if (overlay) return
  if (event.key === 'Escape') { event.preventDefault(); emit('close'); return }
  if (event.key !== 'Tab') return
  const items = [...workspaceRoot.value.querySelectorAll('button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), a[href], [tabindex="0"]')].filter(el => el.getClientRects().length)
  const first = items[0], last = items.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}

onMounted(() => { closeButton.value?.focus(); document.addEventListener('keydown', onKey); mountPages() })
onBeforeUnmount(() => { disposed = true; generation++; document.removeEventListener('keydown', onKey); cleanup() })
</script>

<style scoped>
.room-workspace { position: absolute; inset: 12px; z-index: 42; display: flex; flex-direction: column; min-width: 0; overflow: hidden; border: 1px solid var(--line); border-radius: 20px; background: var(--paper); box-shadow: 0 20px 70px #18345326; }
.room-workspace-header { flex: none; display: flex; align-items: center; gap: 14px; padding: 18px 24px; background: var(--surface); border-bottom: 1px solid var(--line); }
.room-service-icon { width: 44px; height: 44px; padding: 10px; background: var(--brand-soft); color: var(--brand); border-radius: 14px; flex: none; }
.room-service-icon :deep(svg) { width: 100%; height: 100%; }
.room-service-heading small { color: var(--ink-3); font-size: 12px; }
.room-service-heading h2 { margin: 3px 0 0; font-size: 21px; }
.room-shared-data { font-size: 12px; color: var(--ink-3); }
.room-return { margin-left: auto; color: var(--brand); border: 1px solid var(--brand-line); border-radius: 10px; background: var(--surface); padding: 10px 15px; font: inherit; font-weight: 600; cursor: pointer; white-space: nowrap; }
.room-return kbd { margin-left: 8px; font-size: 11px; color: var(--ink-3); }
.room-workspace-nav { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; padding: 10px 24px; border-bottom: 1px solid var(--line); background: var(--surface); }
.room-workspace-nav button, .room-workspace-error button { border: 0; border-radius: 6px; padding: 7px 10px; background: var(--brand-soft); color: var(--brand); cursor: pointer; }
.room-workspace-nav button:disabled { opacity: .45; cursor: default; }
.room-workspace-nav span { margin-left: auto; color: var(--ink-3); font-size: 12px; }
.room-page-host { flex: 1; min-height: 0; overflow: auto; overscroll-behavior: contain; scrollbar-gutter: stable; padding: clamp(16px, 2.3vw, 32px); }
.room-page-host :deep(> *) { max-width: 1440px; margin-inline: auto; min-width: 0; }
.room-page-host :deep(.el-pagination) { flex-wrap: wrap; gap: 8px; }
.room-page-host :deep(img) { max-width: 100%; }
.room-workspace-error { margin: auto; padding: 24px; text-align: center; }
button:focus-visible { outline: 3px solid var(--brand); outline-offset: 3px; }
@media(max-width: 700px) {
 .room-workspace { inset: 0; border-radius: 0; }
 .room-workspace-header { padding: 12px; gap: 9px; }
 .room-shared-data, .room-return kbd, .room-workspace-nav span { display: none; }
 .room-service-heading h2 { font-size: 17px; }
 .room-return { padding: 9px; font-size: 13px; }
 .room-workspace-nav { padding: 8px 12px; }
 .room-page-host :deep(.toolbar) { flex-wrap: wrap; }
 .room-page-host :deep(.grid) { grid-template-columns: minmax(0, 1fr); }
}
</style>
