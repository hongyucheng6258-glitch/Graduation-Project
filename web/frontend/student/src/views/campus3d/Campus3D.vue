<template>
  <main class="campus3d-page">
    <header class="campus3d-header">
      <button class="back-button" type="button" @click="goHome">
        <span aria-hidden="true">←</span>
        <span>返回学生端</span>
      </button>
      <div class="campus3d-brand">
        <span class="brand-mark" aria-hidden="true">◆</span>
        <span>
          <strong>3D校园</strong>
          <small>梧桐校园 · 空间服务中心</small>
        </span>
      </div>
      <div class="campus3d-header-actions">
        <span class="status-chip"><i></i>{{ locationName }}</span>
        <button class="toolbar-button" type="button" @click="mapOpen = true">
          <span aria-hidden="true">⌖</span>校园导览
        </button>
        <button class="toolbar-button" type="button" @click="toggleFullscreen">
          <span aria-hidden="true">⛶</span>{{ isFullscreen ? '退出全屏' : '全屏' }}
        </button>
      </div>
    </header>

    <section ref="sceneFrame" class="campus3d-frame" :class="{ 'scene-expanded': isFullscreen }" aria-label="3D校园场景">
      <div ref="sceneCanvas" class="scene-canvas" tabindex="0" aria-label="3D校园：拖动环视，WASD移动，点击服务门进入房间"></div>
      <div ref="sceneLabels" class="scene-labels"></div>

      <div v-if="loading" class="scene-state scene-loading">
        <span class="loader-ring"></span>
        <strong>正在打开3D校园</strong>
        <span>准备好，走进你的校园空间</span>
      </div>

      <div v-else-if="errorMessage" class="scene-state scene-error">
        <span class="error-icon" aria-hidden="true">!</span>
        <strong>3D场景暂时无法打开</strong>
        <p>{{ errorMessage }}</p>
        <button class="primary-button" type="button" @click="boot">重新加载场景</button>
      </div>

      <div v-if="!loading && !errorMessage && !explorationStarted" class="scene-intro">
        <span class="scene-kicker">你的校园，新的打开方式</span>
        <h1>在梧桐下，<br>遇见校园的每一面。</h1>
        <p>从这里出发，探索你的专属校园空间。</p>
        <button class="primary-button" type="button" @click="startExplore">开始探索 <span aria-hidden="true">→</span></button>
      </div>

      <section v-if="roomPrompt" class="room-prompt" aria-live="polite">
        <span class="room-prompt-kicker">{{ roomPrompt.profile.type }} · {{ roomPrompt.item.room }}</span>
        <h2>{{ roomPrompt.item.name }}</h2>
        <p>{{ roomPrompt.profile.prompt }} · {{ roomPrompt.profile.facilities.join(' · ') }}</p>
        <div class="room-prompt-actions">
          <button ref="roomOpenButton" class="primary-button" type="button" @click="openService(roomPrompt.item.id)">打开服务内容 <span aria-hidden="true">→</span></button>
          <button class="secondary-button" type="button" @click="exitRoom">回到门口</button>
        </div>
        <small>也可以点击房间里的屏幕或服务台</small>
      </section>

      <div v-if="traveling" class="travel-status" role="status">
        <span class="loader-ring small"></span>
        <span>{{ travelText }}</span>
        <button type="button" aria-label="取消前往" @click="cancelTravel">×</button>
        <span class="travel-progress" :style="{ width: `${travelProgress}%` }"></span>
      </div>

      <div class="scene-topline">
        <span class="location-chip location-chip-scene"><span class="pin-dot"></span>{{ locationName }}<em>3D实景</em></span>
        <button class="scene-tool" type="button" aria-label="切换舒适模式" :aria-pressed="comfort" @click="toggleComfort">◌ <span>{{ comfort ? '舒适模式已开' : '舒适模式' }}</span></button>
      </div>

      <div class="scene-bottomline">
        <label class="scene-tool">画质 <select v-model="quality" aria-label="场景画质" @change="setQuality"><option value="low">流畅</option><option value="balanced">均衡</option><option value="high">高清</option></select></label>
        <button v-if="isFullscreen" class="scene-tool" @click="toggleFullscreen">退出全屏</button>
        <span class="explore-hint"><span class="mouse-icon"></span>拖动环视 <b>W</b><b>A</b><b>S</b><b>D</b> 移动</span>
        <button class="scene-tool minimap-reopen-button" type="button" @click="mapOpen = true">⌖ 导览</button>
      </div>

      <CampusWorkspace v-if="workspaceOpen" :service-id="workspaceServiceId" @close="closeWorkspace" />
    </section>

    <footer class="campus3d-footer">
      <span><i></i>梧桐校园 · 连接每一种校园生活</span>
      <span>进入房间后，点击屏幕打开对应学生端功能</span>
    </footer>

    <div v-if="mapOpen" class="map-layer" @click.self="mapOpen = false">
      <section class="map-dialog" role="dialog" aria-modal="true" aria-labelledby="mapTitle">
        <header>
          <div>
            <small>从这里，找到你的目的地</small>
            <h2 id="mapTitle">校园导览</h2>
          </div>
          <button type="button" aria-label="关闭校园导览" @click="mapOpen = false">×</button>
        </header>
        <button class="map-hub" type="button" @click="goHall">
          <span class="map-hub-icon">◆</span>
          <span><strong>梧桐中庭</strong><small>你与校园生活的连接点</small></span>
          <b>回到大厅 →</b>
        </button>
        <div class="map-zones">
          <section v-for="group in mapGroups" :key="group.id" class="map-zone" :style="{ '--zone-color': group.color }">
            <h3>{{ group.name }}</h3>
            <p>{{ group.description }}</p>
            <button v-for="item in group.items" :key="item.id" type="button" @click="navigateToService(item.id)">
              <span class="map-dot" :style="{ background: item.color }"></span>
              <span>{{ item.name }}</span>
              <small>{{ item.room }}</small>
            </button>
          </section>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { createCampusScene } from '../../features/campus3d/campus-scene.js'
import { GROUPS, SERVICES, getRoomProfile } from '../../features/campus3d/campus-data.js'
import CampusWorkspace from './CampusWorkspace.vue'
import { useUserStore } from '../../store/user'
import { useChatStore } from '../../store/chat'

const router = useRouter()
const sceneFrame = ref(null)
const sceneCanvas = ref(null)
const sceneLabels = ref(null)
const roomOpenButton = ref(null)
const scene = shallowRef(null)
const loading = ref(true)
const errorMessage = ref('')
const explorationStarted = ref(false)
const roomPrompt = ref(null)
const workspaceOpen = ref(false)
const workspaceServiceId = ref(null)
const activeRoomId = ref(null)
const locationName = ref('梧桐中庭')
const traveling = ref(false)
const travelText = ref('正在前往…')
const travelProgress = ref(0)
const mapOpen = ref(false)
const comfort = ref(localStorage.getItem('wutong-campus-comfort') === 'true')
const isFullscreen = ref(false)
const quality = ref(localStorage.getItem('campus-quality') || 'balanced')
const userStore = useUserStore()
const chatStore = useChatStore()
watch(() => userStore.isLoggedIn, loggedIn => {
  if (loggedIn) chatStore.init(userStore.userInfo?.id)
  else chatStore.destroy()
}, { immediate: true })
function handleAuthExpired() { userStore.logout() }
function setQuality() {
  localStorage.setItem('campus-quality', quality.value)
  scene.value?.setQuality(quality.value)
}
watch([mapOpen, workspaceOpen, explorationStarted], ([map, workspace, started]) => scene.value?.setPaused(map || workspace || !started))
const serviceMap = new Map(SERVICES.map((item) => [item.id, item]))
const groupMap = new Map(GROUPS.map((group) => [group.id, group]))
const mapGroups = computed(() => GROUPS.filter((group) => group.id !== 'hub').map((group) => ({
  ...group,
  items: SERVICES.filter((item) => item.group === group.id)
})))

function showRoomPrompt(id) {
  const item = serviceMap.get(id)
  if (!item) return
  activeRoomId.value = id
  roomPrompt.value = { item, profile: getRoomProfile(id) }
  locationName.value = `${item.name} · ${item.room}`
  nextTick(() => roomOpenButton.value?.focus({ preventScroll: true }))
}

function hideRoomPrompt() {
  roomPrompt.value = null
}

function startExplore() {
  explorationStarted.value = true
  scene.value?.setPaused(false)
  scene.value?.goHall()
  sceneCanvas.value?.focus({ preventScroll: true })
}

function openService(id) {
  if (!serviceMap.has(id)) return
  activeRoomId.value = id
  hideRoomPrompt()
  workspaceServiceId.value = id
  workspaceOpen.value = true
  scene.value?.setPaused(true)
}

function closeWorkspace() {
  workspaceOpen.value = false
  scene.value?.setPaused(false)
  if (activeRoomId.value) showRoomPrompt(activeRoomId.value)
}

function exitRoom() {
  const id = activeRoomId.value || roomPrompt.value?.item.id || scene.value?.getDebug?.()?.insideId
  workspaceOpen.value = false
  scene.value?.setPaused(false)
  if (id) scene.value?.exit(id)
  activeRoomId.value = null
  hideRoomPrompt()
  locationName.value = '梧桐中庭'
}

function goHall() {
  mapOpen.value = false
  workspaceOpen.value = false
  scene.value?.setPaused(false)
  activeRoomId.value = null
  hideRoomPrompt()
  scene.value?.goHall()
  locationName.value = '梧桐中庭'
}

function navigateToService(id) {
  mapOpen.value = false
  if (!explorationStarted.value) startExplore()
  scene.value?.navigate(id, { instant: comfort.value })
}

function cancelTravel() {
  scene.value?.cancelNavigation()
  traveling.value = false
  hideRoomPrompt()
}

function toggleComfort() {
  comfort.value = !comfort.value
  localStorage.setItem('wutong-campus-comfort', String(comfort.value))
  scene.value?.setComfort(comfort.value)
}

async function toggleFullscreen() {
  if (document.fullscreenElement) { await document.exitFullscreen(); return }
  if (isFullscreen.value) { isFullscreen.value = false; return }
  try {
    // Include body-teleported Element Plus dialogs in the fullscreen subtree.
    await document.documentElement.requestFullscreen({ navigationUI: 'hide' })
  } catch { isFullscreen.value = true }
}

function goHome() {
  workspaceOpen.value = false
  router.push('/')
}

function handleNavigation({ phase, id, progress }) {
  const item = id ? serviceMap.get(id) : null
  if (phase === 'walking' || phase === 'opening' || phase === 'entering') {
    traveling.value = true
    travelText.value = `${phase === 'opening' || phase === 'entering' ? '正在打开' : '正在前往'}${item ? `「${item.name}」` : ''}`
    travelProgress.value = Math.round((progress || 0) * 100)
  } else {
    traveling.value = false
  }
}

function handlePosition({ zone }) {
  const group = groupMap.get(zone)
  if (!roomPrompt.value) locationName.value = zone === 'hub' ? '梧桐中庭' : `${group?.name || '校园空间'} · 3D实景`
}

async function boot() {
  loading.value = true
  errorMessage.value = ''
  scene.value?.destroy?.()
  scene.value = null
  if (import.meta.env.DEV) window.__campusScene = null
  explorationStarted.value = false
  hideRoomPrompt()
  try {
    scene.value = await createCampusScene({
      container: sceneCanvas.value,
      labels: sceneLabels.value,
      services: SERVICES,
      onEnter: showRoomPrompt,
      onRoomInteract: openService,
      onNavigate: handleNavigation,
      onPosition: handlePosition,
      onError: (error) => { errorMessage.value = error?.message || '无法初始化三维场景。' }
    })
    scene.value.setQuality(quality.value)
    scene.value.setComfort(comfort.value)
    scene.value.setPaused(true)
    // 仅开发环境暴露场景实例，供真实浏览器自动化驱动 navigate/exit 与诊断内存，生产构建不挂载。
    if (import.meta.env.DEV) window.__campusScene = scene.value
  } catch (error) {
    errorMessage.value = error?.message || '浏览器不支持当前三维场景。'
  } finally {
    loading.value = false
  }
}

function syncFullscreen() {
  isFullscreen.value = document.fullscreenElement === document.documentElement
}

onMounted(() => {
  window.addEventListener('auth-expired', handleAuthExpired)
  document.addEventListener('fullscreenchange', syncFullscreen)
  boot()
})

onBeforeUnmount(() => {
  window.removeEventListener('auth-expired', handleAuthExpired)
  chatStore.destroy()
  if (document.fullscreenElement) document.exitFullscreen().catch(() => {})
  document.removeEventListener('fullscreenchange', syncFullscreen)
  scene.value?.destroy?.()
  if (import.meta.env.DEV) window.__campusScene = null
})
</script>

<style scoped>
.campus3d-page { min-height: 100vh; display: flex; flex-direction: column; background: var(--paper); color: var(--ink); }
.campus3d-header { height: 72px; flex: none; display: flex; align-items: center; gap: 22px; padding: 0 34px; background: color-mix(in srgb, var(--surface) 92%, transparent); border-bottom: 1px solid var(--line); position: relative; z-index: 3; }
.back-button, .toolbar-button { border: 1px solid var(--line); background: var(--surface); color: var(--ink-2); border-radius: 10px; min-height: 38px; padding: 0 13px; display: inline-flex; align-items: center; gap: 7px; cursor: pointer; font: inherit; font-size: 13px; transition: .18s ease; }
.back-button:hover, .toolbar-button:hover { color: var(--brand); border-color: var(--brand-line); background: var(--brand-soft); }
.campus3d-brand { display: flex; align-items: center; gap: 10px; min-width: 210px; }
.brand-mark { width: 34px; height: 34px; display: grid; place-items: center; border-radius: 10px; background: var(--brand); color: #fff; font-size: 15px; box-shadow: 0 7px 18px oklch(56% .19 265 / .2); }
.campus3d-brand strong { display: block; font-size: 16px; color: var(--ink); }
.campus3d-brand small { display: block; color: var(--ink-3); font-size: 10px; margin-top: 2px; }
.campus3d-header-actions { margin-left: auto; display: flex; align-items: center; gap: 9px; }
.status-chip, .location-chip { display: inline-flex; align-items: center; gap: 7px; border: 1px solid var(--line); border-radius: 999px; padding: 8px 11px; color: var(--ink-2); background: color-mix(in srgb, var(--surface) 88%, transparent); font-size: 11px; white-space: nowrap; }
.status-chip i, .campus3d-footer i { width: 7px; height: 7px; border-radius: 50%; display: inline-block; background: var(--success); }
.campus3d-frame { flex: 1; position: relative; min-height: calc(100vh - 121px); margin: 22px 30px 0; overflow: hidden; border: 1px solid var(--brand-line); border-radius: 24px; background: #dcecfb; box-shadow: var(--shadow-lg); }
.campus3d-frame.scene-expanded { position: fixed; inset: 0; z-index: 50; width: 100vw; height: 100vh; min-height: 100vh; margin: 0; border-radius: 0; }
.scene-canvas { position: absolute; inset: 0; outline: none; }
.scene-labels { position: absolute; inset: 0; pointer-events: none; }
.scene-labels :deep(button) { pointer-events: auto; }
.scene-labels :deep(.scene-door-label), .scene-labels :deep(.scene-arch-label) { display: inline-flex; align-items: center; gap: 7px; padding: 7px 10px; border: 1px solid rgba(255,255,255,.86); border-radius: 9px; background: rgba(255,255,255,.92); box-shadow: 0 7px 20px rgba(27,69,120,.16); color: #24476c; font-size: 11px; font-weight: 700; white-space: nowrap; transform: translate(-50%, -100%); cursor: pointer; }
.scene-labels :deep(.scene-door-label:hover), .scene-labels :deep(.scene-door-label.is-active), .scene-labels :deep(.scene-arch-label:hover) { box-shadow: 0 0 0 3px rgba(40,100,234,.18), 0 9px 24px rgba(27,69,120,.22); color: var(--brand); }
.scene-labels :deep(.scene-label-dot) { width: 8px; height: 8px; border-radius: 50%; box-shadow: 0 0 0 4px color-mix(in srgb, var(--service-color, #2864ea) 16%, transparent); }
.scene-labels :deep(.scene-label-room) { color: #7890aa; font-size: 9px; font-weight: 500; }
.scene-topline, .scene-bottomline { position: absolute; z-index: 5; left: 20px; right: 20px; display: flex; align-items: center; justify-content: space-between; pointer-events: none; }
.scene-topline { top: 18px; }
.scene-bottomline { bottom: 18px; }
.scene-topline > *, .scene-bottomline > * { pointer-events: auto; }
.location-chip-scene { border-color: rgba(255,255,255,.8); background: rgba(255,255,255,.88); color: #24476c; box-shadow: 0 5px 15px rgba(25,69,119,.1); }
.location-chip-scene em { border-left: 1px solid var(--line); padding-left: 8px; color: var(--ink-3); font-style: normal; }
.pin-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--brand); }
.scene-tool { border: 1px solid rgba(255,255,255,.82); background: rgba(255,255,255,.88); color: #587592; border-radius: 9px; min-height: 34px; padding: 0 11px; cursor: pointer; font: inherit; font-size: 11px; box-shadow: 0 5px 15px rgba(25,69,119,.08); }
.scene-tool select { border: 0; background: transparent; color: inherit; font: inherit; padding: 4px; cursor: pointer; }
.scene-tool:hover { color: var(--brand); background: #fff; }
.scene-intro, .scene-state { position: absolute; inset: 0; z-index: 8; display: flex; flex-direction: column; justify-content: center; padding: 0 9%; }
.scene-intro { align-items: flex-start; background: linear-gradient(90deg, rgba(232,243,253,.95), rgba(232,243,253,.52) 54%, rgba(232,243,253,0)); color: #16395d; }
.scene-kicker { color: var(--brand); font-weight: 700; font-size: 12px; }
.scene-intro h1 { margin: 8px 0 10px; font-size: clamp(30px, 4vw, 48px); line-height: 1.25; letter-spacing: -1.5px; }
.scene-intro p { margin: 0 0 21px; color: #5f7898; font-size: 14px; }
.scene-state { align-items: center; text-align: center; background: rgba(234,244,253,.96); color: #16395d; }
.scene-state p { margin: 8px 0 17px; color: var(--ink-3); font-size: 13px; }
.scene-loading { gap: 9px; }
.scene-loading span:last-child { color: var(--ink-3); font-size: 12px; }
.scene-error .error-icon { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 50%; background: var(--brand-soft); color: var(--brand); font-size: 24px; font-weight: 700; margin-bottom: 10px; }
.loader-ring { width: 29px; height: 29px; display: inline-block; border: 3px solid rgba(40,100,234,.16); border-top-color: var(--brand); border-radius: 50%; animation: campus3d-spin .8s linear infinite; }
.loader-ring.small { width: 15px; height: 15px; border-width: 2px; }
@keyframes campus3d-spin { to { transform: rotate(360deg); } }
.primary-button, .secondary-button { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 40px; padding: 0 16px; border-radius: 9px; font: inherit; font-size: 13px; font-weight: 700; cursor: pointer; transition: .18s ease; }
.primary-button { border: 1px solid var(--brand); background: var(--brand); color: #fff; box-shadow: 0 7px 18px oklch(56% .19 265 / .25); }
.primary-button:hover { border-color: var(--brand-strong); background: var(--brand-strong); transform: translateY(-1px); }
.secondary-button { border: 1px solid var(--line); background: var(--surface); color: var(--brand); }
.secondary-button:hover { border-color: var(--brand-line); background: var(--brand-soft); }
.room-prompt { position: absolute; left: 24px; bottom: 23px; z-index: 8; width: min(390px, calc(100% - 48px)); padding: 17px 18px 15px; border: 1px solid rgba(255,255,255,.9); border-left: 3px solid var(--brand); border-radius: 15px; background: rgba(255,255,255,.94); box-shadow: 0 14px 34px rgba(25,69,119,.18); backdrop-filter: blur(14px); }
.room-prompt-kicker { display: block; color: var(--brand); font-size: 10px; font-weight: 750; letter-spacing: .06em; }
.room-prompt h2 { margin: 5px 0; color: #17395e; font-size: 20px; }
.room-prompt p { margin: 0; color: var(--ink-3); font-size: 11px; line-height: 1.65; }
.room-prompt-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 13px; }
.room-prompt-actions .primary-button, .room-prompt-actions .secondary-button { min-height: 36px; padding: 0 13px; font-size: 11px; }
.room-prompt small { display: block; margin-top: 10px; color: #8aa0b8; font-size: 10px; }
.travel-status { position: absolute; z-index: 7; left: 50%; top: 19px; transform: translateX(-50%); min-width: 230px; display: flex; align-items: center; gap: 8px; padding: 8px 12px; border: 1px solid rgba(255,255,255,.9); border-radius: 9px; background: rgba(255,255,255,.93); box-shadow: 0 7px 18px rgba(20,67,118,.12); color: #24476c; font-size: 11px; }
.travel-status button { margin-left: auto; border: 0; background: transparent; color: var(--ink-3); font-size: 17px; cursor: pointer; }
.travel-progress { position: absolute; left: 0; right: auto; bottom: 0; height: 2px; background: var(--brand); transition: width .2s; }
.explore-hint { display: flex; align-items: center; gap: 6px; color: #426587; font-size: 11px; text-shadow: 0 1px 1px #fff; }
.explore-hint b { padding: 1px 4px; border: 1px solid rgba(150,183,216,.8); border-radius: 3px; background: rgba(255,255,255,.7); font-size: 9px; font-weight: 500; }
.mouse-icon { width: 12px; height: 16px; border: 1.5px solid #426587; border-radius: 8px; position: relative; }
.mouse-icon::after { content: ''; position: absolute; width: 2px; height: 4px; left: 4px; top: 3px; border-radius: 2px; background: #426587; }
.campus3d-footer { display: flex; justify-content: space-between; gap: 20px; padding: 13px 34px; color: var(--ink-3); font-size: 11px; }
.campus3d-footer i { margin-right: 7px; }
.map-layer { position: fixed; inset: 0; z-index: 70; display: grid; place-items: center; padding: 24px; background: rgba(19,46,80,.35); backdrop-filter: blur(6px); }
.map-dialog { width: min(760px, 100%); max-height: min(82vh, 760px); overflow: auto; border: 1px solid var(--line); border-radius: 18px; background: var(--surface); box-shadow: 0 24px 80px rgba(19,54,100,.25); }
.map-dialog > header { display: flex; align-items: flex-start; justify-content: space-between; padding: 22px 24px 17px; border-bottom: 1px solid var(--line); }
.map-dialog header small { color: var(--ink-3); font-size: 11px; }
.map-dialog h2 { margin: 4px 0 0; color: var(--ink); font-size: 22px; }
.map-dialog header button { border: 0; background: transparent; color: var(--ink-3); font-size: 24px; cursor: pointer; }
.map-hub { width: calc(100% - 48px); margin: 18px 24px; display: flex; align-items: center; gap: 11px; padding: 16px; border: 0; border-radius: 13px; background: var(--brand-soft); text-align: left; color: var(--brand); cursor: pointer; }
.map-hub-icon { width: 28px; height: 28px; display: grid; place-items: center; border-radius: 9px; background: #fff; font-size: 11px; }
.map-hub strong, .map-hub small { display: block; }
.map-hub strong { color: var(--ink); font-size: 13px; }
.map-hub small { margin-top: 3px; color: var(--ink-3); font-size: 11px; }
.map-hub b { margin-left: auto; font-size: 11px; font-weight: 600; }
.map-zones { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; padding: 0 24px 24px; }
.map-zone { border: 1px solid var(--line); border-top: 3px solid var(--zone-color); border-radius: 12px; padding: 13px; }
.map-zone h3 { margin: 0; color: var(--ink); font-size: 14px; }
.map-zone p { margin: 4px 0 10px; color: var(--ink-3); font-size: 11px; }
.map-zone > button { width: 100%; display: grid; grid-template-columns: 9px 1fr auto; align-items: center; gap: 7px; padding: 8px 2px; border: 0; border-top: 1px solid var(--line); background: transparent; color: var(--brand); text-align: left; cursor: pointer; font: inherit; font-size: 11px; }
.map-zone > button:hover { background: var(--brand-soft); }
.map-zone > button small { color: var(--ink-3); font-size: 10px; }
.map-dot { width: 8px; height: 8px; border-radius: 50%; }
@media (max-width: 820px) {
  .campus3d-header { padding: 0 18px; gap: 12px; }
  .campus3d-brand { min-width: 0; }
  .campus3d-header-actions .status-chip { display: none; }
  .campus3d-frame { margin: 14px 14px 0; min-height: calc(100vh - 106px); border-radius: 18px; }
  .campus3d-footer { padding: 11px 18px; }
  .campus3d-footer span:last-child { display: none; }
  .map-zones { grid-template-columns: 1fr; }
}
@media (max-width: 560px) {
  .campus3d-header { height: 62px; }
  .back-button { padding: 0 9px; font-size: 12px; }
  .back-button span:last-child { display: none; }
  .campus3d-brand strong { font-size: 14px; }
  .campus3d-brand small { display: none; }
  .toolbar-button { min-height: 34px; padding: 0 9px; font-size: 0; }
  .toolbar-button span { font-size: 16px; }
  .campus3d-frame { margin: 9px 9px 0; min-height: calc(100vh - 87px); border-radius: 15px; }
  .scene-topline, .scene-bottomline { left: 12px; right: 12px; }
  .location-chip-scene em { display: none; }
  .explore-hint { font-size: 0; }
  .explore-hint b { font-size: 9px; }
  .room-prompt { left: 12px; right: 12px; bottom: 14px; width: auto; padding: 14px 15px; }
  .room-prompt h2 { font-size: 17px; }
  .room-prompt-actions .primary-button { flex: 1; min-width: 170px; }
  .map-layer { padding: 10px; }
  .map-dialog > header { padding: 17px; }
  .map-hub { width: calc(100% - 34px); margin: 14px 17px; flex-wrap: wrap; }
  .map-hub b { width: 100%; margin: 4px 0 0 39px; }
  .map-zones { padding: 0 17px 17px; }
}
</style>
