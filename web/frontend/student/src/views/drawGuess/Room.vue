<template>
  <div class="draw-room-page" :aria-busy="loading">
    <header class="room-page-head">
      <button class="back-link" type="button" @click="leaveRoom">
        <span aria-hidden="true">←</span> 返回游戏大厅
      </button>
      <div class="room-head-title">
        <span class="room-eyebrow">CAMPUS DRAW & GUESS</span>
        <h1>{{ room?.title || '你画我猜房间' }}</h1>
      </div>
      <div class="connection-state" :class="`state-${socketState}`" role="status" aria-live="polite">
        <i /> {{ socketLabel }}
      </div>
    </header>

    <div v-if="loadError" class="room-error" role="alert">
      <span>{{ loadError }}</span><button class="text-action" type="button" @click="loadRoom">重新加载</button>
    </div>

    <template v-else-if="room">
      <section class="room-status-bar" aria-label="游戏状态">
        <div class="round-status">
          <span class="round-icon" aria-hidden="true">✦</span>
          <span><b>{{ room.status === 'PLAYING' ? `第 ${room.currentTurn} / ${room.totalTurns} 题` : room.status === 'FINISHED' ? '本局已结束' : '房间等待中' }}</b><small>{{ room.status === 'PLAYING' ? (room.isDrawer ? '轮到你来画' : '仔细看画，猜猜是什么') : `${room.playerCount} 位同学已加入` }}</small></span>
        </div>
        <div v-if="room.status === 'PLAYING'" class="timer" :class="{ urgent: remainingSeconds <= 10 }" aria-live="off">
          <span aria-hidden="true">◷</span><b>{{ remainingSeconds }}</b><small>秒</small>
        </div>
        <div v-else class="room-invite">
          <span>房间码 <b>{{ room.roomCode }}</b></span>
          <button type="button" aria-label="复制房间码" @click="copyRoomCode">复制邀请</button>
        </div>
      </section>

      <div v-if="turnNotice" class="turn-notice" role="status">
        <span>{{ turnNotice }}</span>
      </div>
      <div v-if="pendingCompletedArtworks.length" class="completed-artwork-actions" aria-label="已结束回合的待保存作品">
        <span>本局有 {{ pendingCompletedArtworks.length }} 张画作待保存</span>
        <button v-for="artwork in pendingCompletedArtworks" :key="artwork.roundId" type="button" class="notice-save"
          :disabled="savingSnapshot" @click="saveSnapshot(artwork.roundId)">
          {{ savingSnapshot ? '保存中…' : `保存第 ${artwork.turnNumber} 题的画` }}
        </button>
      </div>

      <div class="room-layout">
        <section class="board-panel" aria-label="实时画板">
          <div class="board-toolbar">
            <div class="drawer-prompt" aria-live="polite">
              <span class="prompt-marker" :class="{ active: room.isDrawer }" aria-hidden="true">✎</span>
              <span v-if="room.isDrawer"><b>你的题目</b><strong>{{ privateAnswer || `${room.answerLength || '？'} 个字` }}</strong></span>
              <span v-else-if="room.status === 'PLAYING'"><b>{{ drawerName || '画手' }}正在作画</b><small>猜词请在右侧发送</small></span>
              <span v-else><b>{{ room.status === 'FINISHED' ? '游戏结束' : '等待房主开始' }}</b><small>{{ room.status === 'WAITING' ? '至少两位同学加入后就能开局' : '可以返回大厅再开一局' }}</small></span>
            </div>
            <div v-if="room.isDrawer && room.status === 'PLAYING'" class="drawing-tools" aria-label="画笔工具">
              <div class="color-tools" role="group" aria-label="画笔颜色">
                <button v-for="color in colors" :key="color" class="color-swatch" :class="{ selected: selectedColor === color }" :style="{ '--swatch': color }" type="button" :aria-label="`选择颜色 ${color}`" :aria-pressed="selectedColor === color" @click="selectedColor = color" />
              </div>
              <button class="tool-button" :class="{ selected: tool === 'eraser' }" type="button" :aria-pressed="tool === 'eraser'" @click="tool = tool === 'eraser' ? 'pen' : 'eraser'">{{ tool === 'eraser' ? '橡皮擦' : '橡皮' }}</button>
              <button class="tool-button clear-tool" type="button" @click="clearCanvas">清空</button>
              <button class="tool-button skip-tool" type="button" @click="skipTurn">跳过本题</button>
            </div>
            <div v-else-if="room.isOwner && room.status === 'WAITING'" class="owner-tools">
              <button class="start-button" type="button" :disabled="room.playerCount < 2 || starting" @click="startGame">
                {{ starting ? '准备开局…' : room.playerCount < 2 ? '再等一位同学' : '开始游戏' }} <span aria-hidden="true">→</span>
              </button>
            </div>
            <button v-if="room.isDrawer && room.status === 'PLAYING' && room.currentRoundId" class="save-button" type="button" :disabled="savingSnapshot || isRoundSaved(room.currentRoundId)" @click="saveSnapshot(room.currentRoundId)">
              {{ isRoundSaved(room.currentRoundId) ? '✓ 已保存' : savingSnapshot ? '保存中…' : '保存画作' }}
            </button>
          </div>

          <div class="canvas-frame" :class="{ 'canvas-locked': !room.isDrawer || room.status !== 'PLAYING' }">
            <canvas
              ref="canvasRef"
              class="draw-canvas"
              tabindex="0"
              role="img"
              aria-label="你画我猜共享画板。画手可以使用触控、鼠标或触控笔绘画。"
              @pointerdown="beginStroke"
              @pointermove="continueStroke"
              @pointerup="endStroke"
              @pointercancel="endStroke"
              @lostpointercapture="endStroke"
            />
            <div v-if="room.status === 'WAITING'" class="canvas-empty-hint">
              <span aria-hidden="true">✧</span><b>画板准备好了</b><small>把房间码发给同学，人齐就能开始。</small>
            </div>
            <div v-else-if="room.status === 'FINISHED'" class="canvas-empty-hint finished-hint">
              <span aria-hidden="true">✦</span><b>本局画作都在这里</b><small>返回大厅看看同学们保存的作品。</small>
            </div>
          </div>
          <div class="board-footer">
            <span><i class="lock-indicator" /> {{ room.isDrawer && room.status === 'PLAYING' ? '只有当前画手可以落笔' : '画板由当前画手操作' }}</span>
            <span>房间码 <b>{{ room.roomCode }}</b></span>
          </div>
        </section>

        <aside class="side-panel">
          <section class="players-panel" aria-labelledby="players-heading">
            <header class="panel-heading"><div><span class="panel-kicker">PLAYERS</span><h2 id="players-heading">同桌玩家 <small>{{ room.playerCount }}/{{ room.maxPlayers }}</small></h2></div><span class="online-count"><i /> {{ room.onlineCount }} 在线</span></header>
            <ol class="player-list">
              <li v-for="(player, index) in room.players" :key="player.userId" class="player-row">
                <span class="rank-number" :class="{ winner: index === 0 && player.score > 0 }">{{ index + 1 }}</span>
                <WtAvatar class="player-avatar" :class="`avatar-tone-${index % 4}`" :name="player.nickname || '校园同学'" :src="player.avatar" :size="30" />
                <span class="player-details"><b>{{ player.nickname }} <small v-if="player.owner">房主</small><small v-if="Number(player.userId) === myUserId">我</small></b><span><i :class="{ online: player.online }" />{{ player.online ? '在线' : '暂时离开' }}</span></span>
                <strong class="player-score">{{ player.score }}<small>分</small></strong>
              </li>
            </ol>
            <div v-if="room.status === 'WAITING'" class="waiting-note">再来 {{ Math.max(0, 2 - room.playerCount) }} 位同学就可以开始。</div>
          </section>

          <section class="chat-panel" aria-labelledby="chat-heading">
            <header class="panel-heading chat-heading"><div><span class="panel-kicker">TABLE TALK</span><h2 id="chat-heading">猜词与聊天</h2></div><span class="chat-count">{{ messages.length }} 条</span></header>
            <div ref="messagesRef" class="message-list" aria-live="polite" aria-relevant="additions text" aria-label="房间聊天记录">
              <p v-if="!messages.length" class="chat-empty">打个招呼，或者准备好猜词吧。</p>
              <article v-for="message in messages" :key="message.id" class="chat-message" :class="{ correct: message.correct, mine: Number(message.userId) === myUserId }">
                <span class="message-avatar">{{ (message.nickname || '同').slice(0, 1) }}</span>
                <div class="message-body"><header><b>{{ message.nickname || '校园同学' }}</b><time>{{ formatTime(message.createdAt) }}</time></header><p>{{ message.content }}</p></div>
              </article>
            </div>
            <div class="chat-composer">
              <div class="composer-modes" role="group" aria-label="发送方式">
                <button type="button" :class="{ active: composerMode === 'guess' }" :disabled="room.status !== 'PLAYING' || room.isDrawer" @click="composerMode = 'guess'">猜词</button>
                <button type="button" :class="{ active: composerMode === 'chat' }" @click="composerMode = 'chat'">聊天</button>
              </div>
              <form class="composer-form" @submit.prevent="sendComposerMessage">
                <input ref="composerInput" v-model="composerText" type="text" maxlength="240" :disabled="room.status === 'FINISHED'" :placeholder="composerMode === 'guess' ? '你觉得画的是什么？' : '说点什么…'" aria-label="输入猜词或聊天内容" @keydown.enter.prevent="sendComposerMessage" />
                <button type="submit" :disabled="!composerText.trim() || room.status === 'FINISHED'" aria-label="发送消息">发送</button>
              </form>
              <p class="composer-help">{{ composerMode === 'guess' ? '答错会作为消息发送，猜中后一起得分。' : '友善交流，不要在聊天里直接透露题目。' }}</p>
            </div>
          </section>
        </aside>
      </div>

      <footer class="room-footer">
        <span>游戏状态由房间服务器同步；网络恢复后会自动重新连接。</span>
        <button class="leave-button" type="button" @click="leaveRoom">离开房间</button>
      </footer>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import WtAvatar from '../../components/wt/WtAvatar.vue'
import { createDrawGuessWsTicket, getDrawGuessRoom, leaveDrawGuessRoom, startDrawGuessRoom, uploadDrawGuessSnapshot } from '../../api/drawGuess'
import { useUserStore } from '../../store/user'
import { DrawGuessSocketClient } from '../../features/drawGuess/drawGuessSocket.mjs'
import { buildDrawMessage, limitStrokePoints, normalizeCanvasPoint } from '../../features/drawGuess/drawGuessProtocol.mjs'
import { captureCompletedArtwork, dataUrlToBlob, renderStoredArtwork, restorePendingArtworks } from '../../features/drawGuess/completedArtwork.mjs'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const room = ref(null)
const canvasRef = ref(null)
const messagesRef = ref(null)
const composerInput = ref(null)
const loading = ref(false)
const starting = ref(false)
const savingSnapshot = ref(false)
const loadError = ref('')
const socketState = ref('connecting')
const privateAnswer = ref('')
const turnNotice = ref('')
const pendingCompletedArtworks = ref([])
const savedRoundIds = ref(new Set())
const remainingSeconds = ref(0)
const composerText = ref('')
const composerMode = ref('guess')
const selectedColor = ref('#304d99')
const tool = ref('pen')
const colors = ['#304d99', '#e35b57', '#e4a93f', '#2c9b7b', '#212735', '#a96ac4']
const roomId = computed(() => String(route.params.roomId || ''))
const myUserId = computed(() => Number(userStore.userInfo?.id || 0))
const messages = computed(() => room.value?.messages || [])
const drawerName = computed(() => room.value?.players?.find(player => Number(player.userId) === Number(room.value?.drawerUserId))?.nickname)
const socketLabel = computed(() => ({ connected: '实时已连接', connecting: '正在连接', disconnected: '正在重连', error: '连接中断' })[socketState.value] || '连接中')

let socketClient = null
let resizeObserver = null
let resizeHandler = null
let drawing = false
let activePoints = []
let activePointerId = null
let currentLoad = 0

async function loadRoom() {
  const requestId = ++currentLoad
  socketClient?.close()
  socketClient = null
  room.value = null
  privateAnswer.value = ''
  turnNotice.value = ''
  pendingCompletedArtworks.value = []
  savedRoundIds.value = new Set()
  loading.value = true
  loadError.value = ''
  try {
    const data = await getDrawGuessRoom(roomId.value)
    if (requestId !== currentLoad) return
    room.value = data
    remainingSeconds.value = data.remainingSeconds || 0
    privateAnswer.value = ''
    await nextTick()
    redrawCanvas()
    const board = canvasRef.value
    const pixelRatio = Math.max(1, window.devicePixelRatio || 1)
    pendingCompletedArtworks.value = restorePendingArtworks(
      data.pendingArtworks,
      strokes => renderStoredArtwork(strokes, board?.clientWidth, board?.clientHeight, pixelRatio,
        () => document.createElement('canvas')),
      savedRoundIds.value
    )
    attachResizeObserver()
    connectSocket(roomId.value)
  } catch (error) {
    if (requestId === currentLoad) loadError.value = error.message || '房间加载失败，可能已经关闭。'
  } finally {
    if (requestId === currentLoad) loading.value = false
  }
}

function connectSocket(id) {
  socketClient?.close()
  socketClient = new DrawGuessSocketClient({
    roomId: Number(id),
    getTicket: async (activeRoomId) => {
      const result = await createDrawGuessWsTicket(activeRoomId)
      return result.ticket
    },
    onEvent: handleEvent,
    onState: (state) => { socketState.value = state }
  })
  socketClient.connect()
}

function handleEvent(event) {
  switch (event.type) {
    case 'room_state':
      if (event.room) {
        const previousTurn = room.value?.currentTurn
        room.value = event.room
        remainingSeconds.value = event.room.remainingSeconds || 0
        if (previousTurn != null && previousTurn !== event.room.currentTurn) privateAnswer.value = ''
        composerMode.value = event.room.status === 'PLAYING' && !event.room.isDrawer ? 'guess' : 'chat'
        nextTick(redrawCanvas)
      }
      break
    case 'private_turn':
      privateAnswer.value = event.word || ''
      turnNotice.value = ''
      break
    case 'round_started':
      privateAnswer.value = ''
      turnNotice.value = ''
      if (room.value) {
        room.value = { ...room.value, currentTurn: event.turnNumber, totalTurns: event.totalTurns, drawerUserId: event.drawerUserId,
          isDrawer: Number(event.drawerUserId) === myUserId.value, status: 'PLAYING', strokes: [], currentRoundId: null }
      }
      composerMode.value = Number(event.drawerUserId) === myUserId.value ? 'chat' : 'guess'
      redrawCanvas()
      break
    case 'timer':
      remainingSeconds.value = Math.max(0, Number(event.remainingSeconds) || 0)
      break
    case 'stroke':
      if (room.value) {
        room.value = { ...room.value, strokes: [...(room.value.strokes || []), { userId: event.userId, stroke: event.stroke }] }
        redrawCanvas()
      }
      break
    case 'canvas_cleared':
      if (room.value) room.value = { ...room.value, strokes: [] }
      clearCanvasPixels()
      break
    case 'chat_message':
      addMessage(event.message)
      break
    case 'guess_result':
      if (event.message) addMessage(event.message)
      if (event.correct) ElMessage.success(`${event.nickname || '同学'} 猜对了，+${event.pointsAwarded || 1} 分`)
      break
    case 'advance_scheduled':
      turnNotice.value = '大家都猜到了，5 秒后进入下一题。'
      break
    case 'round_ended':
      turnNotice.value = `第 ${event.turnNumber} 题答案：${event.answer}`
      {
        const artwork = captureCompletedArtwork(event, myUserId.value, canvasRef.value)
        if (artwork && !isRoundSaved(artwork.roundId)
            && !pendingCompletedArtworks.value.some(item => String(item.roundId) === String(artwork.roundId))) {
          pendingCompletedArtworks.value = [...pendingCompletedArtworks.value, { ...artwork, turnNumber: event.turnNumber }]
        }
      }
      privateAnswer.value = ''
      break
    case 'room_ended':
      if (event.room) room.value = event.room
      turnNotice.value = '本局结束，感谢一起作画！'
      break
    case 'error':
      ElMessage.warning(event.message || '这次操作没有成功。')
      break
    case 'pong':
      break
  }
  if (event.type === 'chat_message' || event.type === 'guess_result' || event.type === 'room_state') scrollMessagesToEnd()
}

function addMessage(message) {
  if (!room.value || !message) return
  const existing = room.value.messages || []
  if (existing.some(item => item.id === message.id)) return
  room.value = { ...room.value, messages: [...existing, message].slice(-100) }
  scrollMessagesToEnd()
}

function scrollMessagesToEnd() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

function sendComposerMessage() {
  const text = composerText.value.trim()
  if (!text || !socketClient || room.value?.status === 'FINISHED') return
  const type = composerMode.value === 'guess' && room.value.status === 'PLAYING' && !room.value.isDrawer ? 'guess' : 'chat'
  if (socketClient.send({ type, text })) composerText.value = ''
  else ElMessage.warning('正在连接房间，请连接恢复后再发送。')
}

async function startGame() {
  if (!room.value?.isOwner || room.value.playerCount < 2) return
  starting.value = true
  privateAnswer.value = ''
  try {
    room.value = await startDrawGuessRoom(room.value.id)
    ElMessage.success('游戏开始，准备作画！')
  } catch (error) {
    ElMessage.error(error.message || '现在还不能开始。')
  } finally {
    starting.value = false
  }
}

function clearCanvas() {
  if (room.value?.isDrawer) socketClient?.send({ type: 'clear' })
}

function skipTurn() {
  if (room.value?.isDrawer) socketClient?.send({ type: 'skip' })
}

function getCanvasContext() {
  return canvasRef.value?.getContext('2d') || null
}

function clearCanvasPixels() {
  const canvas = canvasRef.value
  const context = getCanvasContext()
  if (canvas && context) context.clearRect(0, 0, canvas.width, canvas.height)
}

function redrawCanvas() {
  const canvas = canvasRef.value
  const context = getCanvasContext()
  if (!canvas || !context) return
  const rect = canvas.getBoundingClientRect()
  const ratio = Math.max(1, window.devicePixelRatio || 1)
  canvas.width = Math.round(rect.width * ratio)
  canvas.height = Math.round(rect.height * ratio)
  context.setTransform(ratio, 0, 0, ratio, 0, 0)
  context.clearRect(0, 0, rect.width, rect.height)
  for (const item of room.value?.strokes || []) drawStroke(context, rect.width, rect.height, item.stroke)
}

function drawStroke(context, width, height, stroke) {
  if (!stroke?.points?.length) return
  context.save()
  context.globalCompositeOperation = stroke.tool === 'eraser' ? 'destination-out' : 'source-over'
  context.strokeStyle = stroke.color || '#304d99'
  context.lineWidth = Number(stroke.width) || 4
  context.lineCap = 'round'
  context.lineJoin = 'round'
  const points = stroke.points
  context.beginPath()
  context.moveTo(points[0].x * width, points[0].y * height)
  if (points.length === 1) {
    context.lineTo(points[0].x * width + .1, points[0].y * height + .1)
  } else {
    for (let index = 1; index < points.length; index++) context.lineTo(points[index].x * width, points[index].y * height)
  }
  context.stroke()
  context.restore()
}

function beginStroke(event) {
  if (!room.value?.isDrawer || room.value.status !== 'PLAYING' || !canvasRef.value) return
  const point = normalizeCanvasPoint(canvasRef.value.getBoundingClientRect(), event.clientX, event.clientY)
  if (!point) return
  event.preventDefault()
  drawing = true
  activePointerId = event.pointerId
  activePoints = [point]
  canvasRef.value.setPointerCapture?.(event.pointerId)
}

function continueStroke(event) {
  if (!drawing || event.pointerId !== activePointerId || !canvasRef.value) return
  const point = normalizeCanvasPoint(canvasRef.value.getBoundingClientRect(), event.clientX, event.clientY)
  if (!point) return
  event.preventDefault()
  const previous = activePoints.at(-1)
  activePoints.push(point)
  const context = getCanvasContext()
  const rect = canvasRef.value.getBoundingClientRect()
  if (!context) return
  context.save()
  context.globalCompositeOperation = tool.value === 'eraser' ? 'destination-out' : 'source-over'
  context.strokeStyle = tool.value === 'eraser' ? '#ffffff' : selectedColor.value
  context.lineWidth = tool.value === 'eraser' ? 18 : 4
  context.lineCap = 'round'
  context.lineJoin = 'round'
  context.beginPath()
  context.moveTo(previous.x * rect.width, previous.y * rect.height)
  context.lineTo(point.x * rect.width, point.y * rect.height)
  context.stroke()
  context.restore()
}

function endStroke(event) {
  if (!drawing || (event?.pointerId != null && event.pointerId !== activePointerId)) return
  drawing = false
  activePointerId = null
  const points = limitStrokePoints(activePoints)
  activePoints = []
  const stroke = {
    points,
    color: tool.value === 'eraser' ? '#ffffff' : selectedColor.value,
    width: tool.value === 'eraser' ? 18 : 4,
    tool: tool.value
  }
  if (!socketClient?.send(buildDrawMessage(stroke))) redrawCanvas()
}

function isRoundSaved(id) {
  return id != null && savedRoundIds.value.has(String(id))
}

async function saveSnapshot(roundId) {
  if (roundId == null || savingSnapshot.value || isRoundSaved(roundId)) return
  const canvas = canvasRef.value
  if (!canvas) return
  savingSnapshot.value = true
  try {
    const preservedArtwork = pendingCompletedArtworks.value.find(item => String(item.roundId) === String(roundId))
    const blob = preservedArtwork
      ? dataUrlToBlob(preservedArtwork.imageDataUrl)
      : await new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
    if (!blob) throw new Error('画作生成失败，请再试一次。')
    const file = new File([blob], `draw-guess-${room.value.id}-${roundId}.png`, { type: 'image/png' })
    await uploadDrawGuessSnapshot(room.value.id, roundId, file)
    savedRoundIds.value = new Set([...savedRoundIds.value, String(roundId)])
    pendingCompletedArtworks.value = pendingCompletedArtworks.value.filter(item => String(item.roundId) !== String(roundId))
    ElMessage.success('作品已保存到校园画廊。')
  } catch (error) {
    ElMessage.error(error.message || '作品保存失败。')
  } finally {
    savingSnapshot.value = false
  }
}

async function copyRoomCode() {
  try {
    await navigator.clipboard.writeText(room.value.roomCode)
    ElMessage.success('房间码已复制，分享给同学吧。')
  } catch {
    ElMessage.info(`房间码：${room.value.roomCode}`)
  }
}

async function leaveRoom() {
  const id = room.value?.id || roomId.value
  socketClient?.close()
  socketClient = null
  if (room.value && room.value.status !== 'PLAYING') {
    try { await leaveDrawGuessRoom(id) } catch { /* 已关闭的等待房不需要额外清理 */ }
  }
  router.push('/draw-guess')
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(date)
}

function attachResizeObserver() {
  if (!canvasRef.value) return
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver?.disconnect()
    resizeObserver = new ResizeObserver(redrawCanvas)
    resizeObserver.observe(canvasRef.value.parentElement)
    return
  }
  if (!resizeHandler) {
    resizeHandler = redrawCanvas
    window.addEventListener('resize', resizeHandler)
  }
}

watch(roomId, (id) => { if (id) loadRoom() }, { immediate: true })
watch(() => messages.value.length, scrollMessagesToEnd)

onBeforeUnmount(() => {
  ++currentLoad
  socketClient?.close()
  resizeObserver?.disconnect()
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
})
</script>

<style scoped>
.draw-room-page { max-width: 1320px; margin: 0 auto; padding: 4px 0 35px; }
.room-page-head { display: flex; align-items: center; gap: 20px; min-height: 61px; margin-bottom: 14px; }.back-link { display: inline-flex; align-items: center; gap: 8px; flex: none; border: 0; padding: 8px 0; color: var(--ink-3); background: transparent; font: inherit; font-size: 12px; cursor: pointer; }.back-link:hover { color: var(--brand); }.back-link span { font-size: 19px; }.room-head-title { flex: 1; min-width: 0; }.room-eyebrow, .panel-kicker { color: var(--brand); font-size: 9px; font-weight: 800; letter-spacing: .14em; }.room-head-title h1 { overflow: hidden; margin-top: 2px; color: var(--ink); font-size: 19px; font-weight: 750; text-overflow: ellipsis; white-space: nowrap; }
.connection-state { display: inline-flex; align-items: center; gap: 7px; flex: none; padding: 7px 10px; border: 1px solid var(--line); border-radius: 999px; background: var(--surface); color: var(--ink-3); font-size: 10px; }.connection-state i, .online-count i { width: 6px; height: 6px; border-radius: 50%; background: var(--ink-3); }.state-connected { color: var(--success); border-color: color-mix(in oklab, var(--success) 25%, var(--line)); }.state-connected i { background: var(--success); }.state-error { color: var(--error); }.state-error i { background: var(--error); }.room-error { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 17px; border: 1px solid var(--error); border-radius: 12px; color: var(--error); background: var(--surface); font-size: 12px; }
.room-status-bar { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 74px; margin-bottom: 13px; padding: 12px 19px; border: 1px solid var(--line); border-radius: 15px; background: var(--surface); }.round-status { display: flex; align-items: center; gap: 12px; }.round-icon { width: 38px; height: 38px; display: grid; place-items: center; border-radius: 12px; background: var(--brand-soft); color: var(--brand); font-size: 18px; }.round-status b, .round-status small { display: block; }.round-status b { color: var(--ink); font-size: 13px; }.round-status small { margin-top: 2px; color: var(--ink-3); font-size: 10px; }.timer { min-width: 99px; display: flex; align-items: baseline; justify-content: center; gap: 5px; padding: 6px 12px; border: 1px solid var(--accent-line); border-radius: 11px; background: var(--accent-soft); color: var(--accent-ink); }.timer > span { font-size: 16px; }.timer b { font: 750 25px var(--font-mono); line-height: 1; }.timer small { font-size: 10px; }.timer.urgent { border-color: var(--error); background: var(--error-soft); color: var(--error); }.room-invite { display: flex; align-items: center; gap: 10px; color: var(--ink-3); font-size: 10px; }.room-invite b { margin-left: 4px; color: var(--brand-strong); font: 750 14px var(--font-mono); letter-spacing: .08em; }.room-invite button { min-height: 31px; padding: 0 10px; border: 1px solid var(--brand-line); border-radius: 8px; background: var(--brand-soft); color: var(--brand-strong); font: inherit; font-weight: 700; cursor: pointer; }
.turn-notice { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 13px; padding: 10px 15px; border: 1px solid var(--accent-line); border-radius: 11px; background: var(--accent-soft); color: var(--accent-ink); font-size: 11px; font-weight: 650; }.notice-save { border: 0; background: transparent; color: var(--brand-strong); font: inherit; font-size: 10px; font-weight: 750; cursor: pointer; }
.completed-artwork-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 15px; margin: -5px 0 13px; padding: 9px 13px; border: 1px solid var(--brand-line); border-radius: 10px; background: var(--brand-soft); color: var(--ink-2); font-size: 10px; }.completed-artwork-actions .notice-save { padding: 2px 0; }.completed-artwork-actions .notice-save:disabled { opacity: .6; cursor: wait; }
.room-layout { display: grid; grid-template-columns: minmax(0, 1fr) 325px; align-items: start; gap: 14px; }.board-panel, .players-panel, .chat-panel { min-width: 0; overflow: hidden; border: 1px solid var(--line); border-radius: 15px; background: var(--surface); }.board-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 11px; min-height: 68px; padding: 11px 15px; border-bottom: 1px solid var(--line); }.drawer-prompt { display: flex; align-items: center; gap: 10px; min-width: 160px; }.prompt-marker { width: 34px; height: 34px; display: grid; place-items: center; flex: none; border-radius: 10px; background: var(--surface-2); color: var(--ink-3); font-size: 17px; }.prompt-marker.active { background: var(--brand-soft); color: var(--brand); }.drawer-prompt b, .drawer-prompt strong, .drawer-prompt small { display: block; }.drawer-prompt b { color: var(--ink-2); font-size: 11px; }.drawer-prompt strong { margin-top: 1px; color: var(--brand-strong); font-size: 16px; letter-spacing: .1em; }.drawer-prompt small { margin-top: 2px; color: var(--ink-3); font-size: 9px; }.drawing-tools, .color-tools, .owner-tools { display: flex; align-items: center; gap: 7px; }.color-tools { gap: 5px; }.color-swatch { width: 19px; height: 19px; padding: 0; border: 2px solid var(--surface); border-radius: 50%; outline: 1px solid var(--line-strong); background: var(--swatch); cursor: pointer; }.color-swatch.selected { outline: 2px solid var(--brand); outline-offset: 2px; }.tool-button { min-height: 30px; padding: 0 8px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); color: var(--ink-2); font: inherit; font-size: 10px; cursor: pointer; }.tool-button.selected { border-color: var(--brand-line); background: var(--brand-soft); color: var(--brand-strong); }.clear-tool:hover { color: var(--error); border-color: var(--error); }.skip-tool { color: var(--ink-3); }.save-button { min-height: 30px; padding: 0 9px; border: 1px solid var(--brand-line); border-radius: 8px; background: var(--brand-soft); color: var(--brand-strong); font: inherit; font-size: 10px; font-weight: 700; cursor: pointer; }.save-button:disabled, .notice-save:disabled { opacity: .5; cursor: default; }.start-button { min-height: 35px; padding: 0 12px; border: 0; border-radius: 9px; background: var(--brand); color: #fff; font: inherit; font-size: 11px; font-weight: 750; cursor: pointer; }.start-button:disabled { opacity: .55; cursor: not-allowed; }.start-button span { padding-left: 7px; }
.canvas-frame { position: relative; min-height: 455px; background-color: #fffdf8; background-image: radial-gradient(#e6e4de .65px, transparent .65px); background-size: 19px 19px; touch-action: none; }.draw-canvas { position: relative; z-index: 1; display: block; width: 100%; height: 455px; touch-action: none; outline: 0; }.draw-canvas:focus-visible { outline: 2px solid var(--brand); outline-offset: -3px; }.canvas-locked .draw-canvas { cursor: default; }.canvas-frame:not(.canvas-locked) .draw-canvas { cursor: crosshair; }.canvas-empty-hint { position: absolute; inset: 0; z-index: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 5px; color: #81889c; pointer-events: none; }.canvas-empty-hint > span { display: grid; width: 50px; height: 50px; place-items: center; margin-bottom: 4px; border-radius: 16px; background: #edf1fb; color: #4968ac; font-size: 24px; }.canvas-empty-hint b { color: #596177; font-size: 12px; }.canvas-empty-hint small { color: #858b9a; font-size: 10px; }.finished-hint > span { background: #fff3d7; color: #a37d28; }.board-footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 14px; border-top: 1px solid var(--line); color: var(--ink-3); font-size: 9px; }.board-footer > span:first-child { display: inline-flex; align-items: center; gap: 6px; }.board-footer b { color: var(--ink-2); font: 700 10px var(--font-mono); letter-spacing: .08em; }.lock-indicator { width: 6px; height: 6px; border-radius: 50%; background: var(--success); }
.side-panel { display: flex; flex-direction: column; gap: 13px; }.players-panel { padding: 15px 15px 12px; }.panel-heading { display: flex; align-items: end; justify-content: space-between; gap: 8px; }.panel-heading h2 { margin-top: 3px; color: var(--ink); font-size: 14px; }.panel-heading h2 small { margin-left: 3px; color: var(--ink-3); font-size: 9px; font-weight: 600; }.online-count { display: inline-flex; align-items: center; gap: 5px; color: var(--success); font-size: 9px; }.online-count i { width: 5px; height: 5px; background: var(--success); }.player-list { margin-top: 12px; }.player-row { display: flex; align-items: center; gap: 9px; padding: 8px 0; border-top: 1px solid var(--line); }.rank-number { width: 17px; color: var(--ink-3); font: 600 10px var(--font-mono); text-align: center; }.rank-number.winner { color: var(--gold-strong); }.player-avatar, .message-avatar { display: grid; width: 30px; height: 30px; place-items: center; flex: none; border-radius: 10px; background: var(--brand-soft); color: var(--brand-strong); font-size: 11px; font-weight: 750; }.avatar-tone-1 { background: var(--sage); color: var(--success); }.avatar-tone-2 { background: var(--accent-soft); color: var(--accent-ink); }.avatar-tone-3 { background: var(--purple-soft); color: var(--purple); }.player-details { min-width: 0; flex: 1; }.player-details b, .player-details > span { display: flex; align-items: center; gap: 5px; overflow: hidden; color: var(--ink-2); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }.player-details b { color: var(--ink); font-size: 11px; }.player-details b small { padding: 1px 5px; border-radius: 999px; background: var(--brand-soft); color: var(--brand-strong); font-size: 8px; font-weight: 700; }.player-details > span { margin-top: 2px; color: var(--ink-3); font-size: 9px; }.player-details > span i { width: 5px; height: 5px; border-radius: 50%; background: var(--line-strong); }.player-details > span i.online { background: var(--success); }.player-score { color: var(--brand-strong); font: 750 14px var(--font-mono); }.player-score small { margin-left: 2px; color: var(--ink-3); font: 500 8px var(--font-sans); }.waiting-note { margin-top: 7px; padding: 8px 9px; border-radius: 8px; background: var(--brand-soft); color: var(--brand-strong); font-size: 9px; }
.chat-panel { display: flex; min-height: 385px; flex-direction: column; }.chat-heading { padding: 14px 15px 11px; border-bottom: 1px solid var(--line); }.chat-count { color: var(--ink-3); font-size: 9px; }.message-list { display: flex; min-height: 150px; max-height: 290px; flex: 1; flex-direction: column; gap: 9px; overflow-y: auto; padding: 12px; }.chat-empty { margin: auto; color: var(--ink-3); font-size: 10px; text-align: center; }.chat-message { display: flex; align-items: flex-start; gap: 7px; }.message-avatar { width: 24px; height: 24px; border-radius: 8px; background: var(--surface-3); color: var(--ink-2); font-size: 9px; }.message-body { min-width: 0; flex: 1; }.message-body header { display: flex; align-items: baseline; gap: 6px; }.message-body header b { overflow: hidden; color: var(--ink-2); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }.message-body time { color: var(--ink-3); font-size: 8px; }.message-body p { margin-top: 2px; color: var(--ink); font-size: 10px; line-height: 1.5; overflow-wrap: anywhere; }.chat-message.correct .message-body p { display: inline-block; padding: 4px 7px; border-radius: 7px; background: var(--success-soft); color: var(--success); font-weight: 750; }.chat-composer { padding: 9px 11px 11px; border-top: 1px solid var(--line); }.composer-modes { display: flex; gap: 5px; margin-bottom: 7px; }.composer-modes button { border: 0; padding: 4px 7px; border-radius: 6px; background: transparent; color: var(--ink-3); font: inherit; font-size: 9px; cursor: pointer; }.composer-modes button.active { background: var(--brand-soft); color: var(--brand-strong); font-weight: 750; }.composer-modes button:disabled { cursor: not-allowed; opacity: .5; }.composer-form { display: flex; align-items: center; gap: 5px; }.composer-form input { min-width: 0; min-height: 37px; flex: 1; padding: 0 10px; border: 1px solid var(--line); border-radius: 9px; outline: 0; background: var(--surface-2); color: var(--ink); font: inherit; font-size: 10px; }.composer-form input:focus { border-color: var(--brand); }.composer-form button { min-height: 37px; padding: 0 12px; border: 0; border-radius: 9px; background: var(--brand); color: #fff; font: inherit; font-size: 10px; font-weight: 700; cursor: pointer; }.composer-form button:disabled { opacity: .45; cursor: not-allowed; }.composer-help { margin-top: 5px; color: var(--ink-3); font-size: 8px; }
.room-footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 13px; color: var(--ink-3); font-size: 9px; }.leave-button { border: 0; background: transparent; color: var(--ink-3); font: inherit; font-size: 10px; cursor: pointer; }.leave-button:hover { color: var(--error); }
@media (max-width: 980px) { .room-layout { grid-template-columns: minmax(0, 1fr) 285px; }.board-toolbar { align-items: flex-start; }.drawing-tools { flex-wrap: wrap; }.canvas-frame { min-height: 400px; }.draw-canvas { height: 400px; } }
@media (max-width: 760px) { .draw-room-page { padding-top: 0; }.room-page-head { flex-wrap: wrap; gap: 8px 14px; }.back-link { order: 0; width: 100%; }.room-head-title { order: 1; }.connection-state { order: 2; }.room-layout { grid-template-columns: 1fr; }.side-panel { display: grid; grid-template-columns: 1fr; }.players-panel { order: 0; }.chat-panel { order: 1; min-height: 350px; }.message-list { max-height: 235px; }.canvas-frame { min-height: 360px; }.draw-canvas { height: 360px; }.room-status-bar { min-height: 65px; padding: 10px 12px; }.round-status { gap: 8px; }.round-icon { width: 33px; height: 33px; }.timer { min-width: 83px; }.timer b { font-size: 22px; } }
@media (max-width: 480px) { .room-head-title h1 { font-size: 17px; }.connection-state { padding: 6px 8px; font-size: 9px; }.room-status-bar { align-items: flex-start; }.room-invite { align-items: flex-end; flex-direction: column; gap: 5px; }.room-invite b { font-size: 12px; }.board-toolbar { padding: 10px; }.drawer-prompt { width: 100%; }.drawing-tools { width: 100%; justify-content: flex-start; }.owner-tools { margin-left: auto; }.save-button { margin-left: auto; }.canvas-frame { min-height: 300px; }.draw-canvas { height: 300px; }.board-footer { align-items: flex-start; flex-direction: column; gap: 4px; }.turn-notice { align-items: flex-start; flex-direction: column; }.room-footer { align-items: flex-start; }.room-footer span { max-width: 75%; } }
</style>
