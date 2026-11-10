<template>
  <div class="draw-guess-page">
    <header class="lobby-header">
      <h1>你画我猜</h1>
      <p>公开或私密房间、房间码加入，和同学实时画画、猜词聊天。</p>
      <div class="lobby-actions">
        <button type="button" class="entry-toggle primary-action" aria-controls="create-game"
          :aria-expanded="activeEntry === 'create'" @click="toggleEntry('create')">+ 创建房间</button>
        <button type="button" class="entry-toggle" aria-controls="join-game"
          :aria-expanded="activeEntry === 'join'" @click="toggleEntry('join')">输入房间码加入</button>
      </div>
    </header>

    <section v-show="activeEntry" class="entry-panel" aria-label="加入或创建游戏房间">
      <form v-show="activeEntry === 'join'" id="join-game" class="entry-card" @submit.prevent="joinByCode">
        <div class="entry-heading">
          <h2>加入同学的房间</h2>
          <p>输入六位房间码，和朋友汇合。</p>
        </div>
        <label class="field-label" for="room-code">房间码</label>
        <input
          id="room-code"
          v-model="roomCode"
          class="room-code-input"
          type="text"
          autocomplete="off"
          autocapitalize="characters"
          maxlength="6"
          placeholder="例如 ABCD23"
          aria-describedby="join-help"
          @input="roomCode = roomCode.replace(/[^a-z\d]/gi, '').toUpperCase()"
        />
        <span id="join-help" class="input-help">私密房间请一并填写房间密码</span>
        <input v-model="joinPassword" class="plain-input" type="password" autocomplete="off" maxlength="32" placeholder="房间密码（公开房间留空）" aria-label="房间密码" />
        <button class="action-button secondary-action" type="submit" :disabled="joining || roomCode.length !== 6">
          {{ joining ? '正在加入…' : '加入房间' }} <span aria-hidden="true">→</span>
        </button>
        <p v-if="joinError" class="form-error" role="alert">{{ joinError }}</p>
      </form>

      <form v-show="activeEntry === 'create'" id="create-game" class="entry-card" @submit.prevent="createRoom">
        <div class="entry-heading">
          <h2>发起一场速写局</h2>
          <p>设置人数和轮数，再把房间码分享给伙伴。</p>
        </div>
        <label class="field-label" for="room-title">房间名称</label>
        <input id="room-title" v-model.trim="form.title" class="plain-input" type="text" maxlength="40" placeholder="比如：周五下课来画画" />
        <div class="select-row">
          <label class="select-field">
            <span class="field-label">房间人数</span>
            <select v-model.number="form.maxPlayers" class="plain-input">
              <option v-for="count in [2, 3, 4, 5, 6]" :key="count" :value="count">{{ count }} 人</option>
            </select>
          </label>
          <label class="select-field">
            <span class="field-label">每人轮数</span>
            <select v-model.number="form.roundsPerPlayer" class="plain-input">
              <option v-for="count in [1, 2, 3, 4]" :key="count" :value="count">{{ count }} 轮</option>
            </select>
          </label>
        </div>
        <label class="private-toggle">
          <input v-model="form.privateRoom" type="checkbox" />
          <span class="toggle-mark" aria-hidden="true" />
          <span><b>设为私密房</b><small>需要房间码和密码才能加入</small></span>
        </label>
        <input v-if="form.privateRoom" v-model="form.password" class="plain-input" type="password" autocomplete="new-password" minlength="4" maxlength="32" placeholder="设置 4–32 位房间密码" aria-label="设置房间密码" />
        <button class="action-button primary-action" type="submit" :disabled="creating || (form.privateRoom && form.password.length < 4)">
          {{ creating ? '正在创建…' : '创建游戏房' }} <span aria-hidden="true">✦</span>
        </button>
        <p v-if="createError" class="form-error" role="alert">{{ createError }}</p>
      </form>
    </section>

    <section class="list-section" aria-labelledby="public-rooms-title">
      <header class="section-heading">
        <div>
          <span class="section-kicker">OPEN TABLES</span>
          <h2 id="public-rooms-title">正在等你加入</h2>
        </div>
        <button class="refresh-button" type="button" :disabled="loadingRooms" @click="loadRooms">
          <span aria-hidden="true">↻</span> {{ loadingRooms ? '刷新中' : '刷新房间' }}
        </button>
      </header>
      <div v-if="roomError" class="state-panel error-panel" role="alert">
        <span>{{ roomError }}</span><button class="text-action" type="button" @click="loadRooms">再试一次</button>
      </div>
      <div v-else-if="loadingRooms && !rooms.length" class="room-grid" aria-label="正在加载房间">
        <div v-for="item in 3" :key="item" class="room-skeleton" />
      </div>
      <div v-else-if="rooms.length" class="room-grid">
        <article v-for="room in rooms" :key="room.id" class="room-card">
          <div class="room-card-top">
            <span class="room-avatar" aria-hidden="true">{{ (room.title || '校园').slice(0, 1) }}</span>
            <div class="room-card-label">
              <h3>{{ room.title || '校园画画房' }}</h3>
              <span class="room-badge">公开</span>
              <span class="room-badge room-badge-neutral">等待玩家</span>
            </div>
          </div>
          <div class="room-card-meta">
            <span>玩家 {{ room.playerCount }}/{{ room.maxPlayers }}</span>
            <span>房间码 <b class="room-code">{{ room.roomCode }}</b></span>
            <span>{{ room.roundsPerPlayer }} 轮 / 人</span>
            <span>{{ room.onlineCount }} 人在线</span>
          </div>
          <button class="room-join primary-action" type="button" :disabled="joiningRoomId === room.id" @click="joinPublicRoom(room)">
            {{ joiningRoomId === room.id ? '加入中…' : '加入房间' }}
          </button>
        </article>
      </div>
      <div v-else class="state-panel empty-panel">
        <span class="empty-doodle" aria-hidden="true">✎</span>
        <div><b>现在还没有公开房间</b><p>创建一间，等同学来一起画。</p></div>
      </div>
    </section>

    <section v-if="recentRooms.length" class="list-section compact-section" aria-labelledby="recent-rooms-title">
      <header class="section-heading">
        <div><span class="section-kicker">PICK UP WHERE YOU LEFT</span><h2 id="recent-rooms-title">最近的房间</h2></div>
      </header>
      <div class="recent-strip">
        <button v-for="room in recentRooms" :key="room.id" class="recent-room" type="button" @click="openRoom(room)">
          <span class="recent-room-symbol" aria-hidden="true">✦</span>
          <span class="recent-room-text"><b>{{ room.title || '校园画画房' }}</b><small>{{ room.roomCode }} · {{ room.status === 'PLAYING' ? '游戏进行中' : room.status === 'FINISHED' ? '已结束' : '等待中' }}</small></span>
          <span class="recent-arrow" aria-hidden="true">→</span>
        </button>
      </div>
    </section>

    <section class="list-section gallery-section" aria-labelledby="gallery-title">
      <header class="section-heading">
        <div><span class="section-kicker">CAMPUS GALLERY</span><h2 id="gallery-title">同学们的灵感现场</h2></div>
        <span class="gallery-note">保存下来的每一张，都有一段故事</span>
      </header>
      <div v-if="galleryError" class="state-panel error-panel" role="alert">
        <span>{{ galleryError }}</span><button class="text-action" type="button" @click="loadGallery">再试一次</button>
      </div>
      <div v-else-if="gallery.length" class="gallery-grid">
        <article v-for="record in gallery" :key="record.roundId" class="art-card">
          <div class="art-image" :aria-label="`预览 ${record.drawerNickname} 保存的作品`">
            <el-image class="art-preview" :src="record.snapshotUrl" :preview-src-list="[record.snapshotUrl]" fit="cover" :alt="`${record.drawerNickname} 的你画我猜作品`" loading="lazy" />
            <span class="art-word">{{ record.word }}</span>
          </div>
          <div class="art-caption"><b>{{ record.drawerNickname }}</b><span>{{ record.title || '校园画画房' }} · 第 {{ record.turnNumber }} 题</span></div>
        </article>
      </div>
      <div v-else class="gallery-empty"><span aria-hidden="true">▧</span><p>画作保存后，会在这里和大家见面。</p></div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createDrawGuessRoom, joinDrawGuessRoom, listDrawGuessRooms, listRecentDrawGuessRooms, listDrawGuessRecords } from '../../api/drawGuess'

const router = useRouter()
const rooms = ref([])
const recentRooms = ref([])
const gallery = ref([])
const activeEntry = ref('')
const roomCode = ref('')
const joinPassword = ref('')
const loadingRooms = ref(false)
const joining = ref(false)
const creating = ref(false)
const joiningRoomId = ref(null)
const roomError = ref('')
const galleryError = ref('')
const joinError = ref('')
const createError = ref('')
const form = reactive({ title: '', maxPlayers: 6, roundsPerPlayer: 1, privateRoom: false, password: '' })

function toggleEntry(entry) {
  activeEntry.value = activeEntry.value === entry ? '' : entry
}

async function loadRooms() {
  loadingRooms.value = true
  roomError.value = ''
  try {
    const data = await listDrawGuessRooms()
    rooms.value = Array.isArray(data) ? data : []
  } catch (error) {
    roomError.value = error.message || '房间暂时无法加载，请稍后重试。'
  } finally {
    loadingRooms.value = false
  }
}

async function loadGallery() {
  galleryError.value = ''
  try {
    const data = await listDrawGuessRecords()
    gallery.value = Array.isArray(data) ? data : []
  } catch (error) {
    galleryError.value = error.message || '作品墙暂时无法加载。'
  }
}

async function loadRecent() {
  try {
    const data = await listRecentDrawGuessRooms()
    recentRooms.value = Array.isArray(data) ? data : []
  } catch {
    recentRooms.value = []
  }
}

async function joinByCode() {
  if (roomCode.value.length !== 6) return
  joining.value = true
  joinError.value = ''
  try {
    const room = await joinDrawGuessRoom(roomCode.value, { password: joinPassword.value || undefined })
    router.push(`/draw-guess/room/${room.id}`)
  } catch (error) {
    joinError.value = error.message || '没有加入成功，请检查房间码和密码。'
  } finally {
    joining.value = false
  }
}

async function joinPublicRoom(room) {
  joiningRoomId.value = room.id
  try {
    const joined = await joinDrawGuessRoom(room.roomCode)
    router.push(`/draw-guess/room/${joined.id}`)
  } catch (error) {
    ElMessage.error(error.message || '加入失败，房间可能已经开始或满员。')
    loadRooms()
  } finally {
    joiningRoomId.value = null
  }
}

async function createRoom() {
  creating.value = true
  createError.value = ''
  try {
    const room = await createDrawGuessRoom({
      title: form.title || undefined,
      maxPlayers: form.maxPlayers,
      roundsPerPlayer: form.roundsPerPlayer,
      privateRoom: form.privateRoom,
      password: form.privateRoom ? form.password : undefined
    })
    ElMessage.success(`房间已创建，房间码 ${room.roomCode}`)
    router.push(`/draw-guess/room/${room.id}`)
  } catch (error) {
    createError.value = error.message || '创建失败，请稍后再试。'
  } finally {
    creating.value = false
  }
}

function openRoom(room) {
  router.push(`/draw-guess/room/${room.id}`)
}

onMounted(() => {
  loadRooms()
  loadRecent()
  loadGallery()
})
</script>

<style scoped>
.draw-guess-page { max-width: 1240px; margin: 0 auto; padding: 4px 0 52px; }
.lobby-header h1 { font-size: var(--fs-h1); margin: 0 0 6px; color: var(--ink); }
.lobby-header p { color: var(--ink-3); font-size: var(--fs-sm); margin: 0; }
.lobby-actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 20px; }
.entry-toggle, .room-join {
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: var(--fs-sm);
  font-weight: 600;
  cursor: pointer;
}
.entry-toggle[aria-expanded="true"] { outline: 2px solid var(--brand-line); outline-offset: 2px; }
.entry-toggle:focus-visible, .room-join:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
.entry-panel { max-width: 560px; margin-top: 16px; }
.entry-card { display: flex; flex-direction: column; padding: 22px; border: 1px solid var(--line); border-radius: var(--r-md); background: var(--surface); }
.section-kicker { color: var(--brand); font-size: 10px; font-weight: 800; letter-spacing: .16em; }
.entry-heading h2, .section-heading h2 { margin: 0 0 4px; color: var(--ink); font-size: 19px; }
.entry-heading p { margin: 0; color: var(--ink-3); font-size: 12px; line-height: 1.7; }
.field-label { display: block; margin: 16px 0 7px; color: var(--ink-2); font-size: 11px; font-weight: 700; }
.plain-input, .room-code-input { width: 100%; min-height: 42px; padding: 0 12px; color: var(--ink); border: 1px solid var(--line-strong); border-radius: 10px; background: var(--surface); font: inherit; font-size: 13px; outline: 0; transition: border-color .16s, box-shadow .16s; }
.plain-input:focus, .room-code-input:focus { border-color: var(--brand); box-shadow: 0 0 0 3px color-mix(in oklab, var(--brand) 13%, transparent); }
.room-code-input { min-height: 50px; color: var(--brand-strong); font-family: var(--font-mono); font-size: 22px; font-weight: 800; letter-spacing: .23em; }
.room-code-input::placeholder { color: var(--ink-3); font: 600 14px var(--font-sans); letter-spacing: .03em; }
.input-help { margin: 5px 0 11px; color: var(--ink-3); font-size: 10px; }
.plain-input + .plain-input { margin-top: 10px; }
.action-button { min-height: 43px; margin-top: 15px; display: flex; align-items: center; justify-content: space-between; padding: 0 15px; border: 0; border-radius: 11px; font-size: 13px; font-weight: 750; cursor: pointer; transition: transform .16s, filter .16s; }
.action-button:not(:disabled):hover { transform: translateY(-1px); filter: brightness(1.04); }.action-button:disabled { opacity: .5; cursor: not-allowed; }
.secondary-action { background: var(--brand-soft); color: var(--brand-strong); }.primary-action { background: var(--brand); border-color: var(--brand); color: var(--brand-ink); }
.select-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }.select-field { min-width: 0; }.select-field .field-label { margin-top: 13px; }
.private-toggle { display: flex; align-items: center; gap: 10px; margin-top: 14px; cursor: pointer; }.private-toggle input { position: absolute; opacity: 0; }.toggle-mark { width: 17px; height: 17px; flex: none; border: 1px solid var(--line-strong); border-radius: 5px; background: var(--surface); }.private-toggle input:checked + .toggle-mark { border-color: var(--brand); background: var(--brand); box-shadow: inset 0 0 0 3px var(--surface); }.private-toggle input:focus-visible + .toggle-mark { outline: 2px solid var(--brand); outline-offset: 2px; }.private-toggle b, .private-toggle small { display: block; }.private-toggle b { color: var(--ink-2); font-size: 11px; }.private-toggle small { color: var(--ink-3); font-size: 10px; }
.form-error { margin-top: 8px; color: var(--error); font-size: 11px; }
.list-section { margin-top: 37px; }.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 18px; margin-bottom: 15px; }.section-heading h2 { font-size: 21px; margin-top: 4px; }.refresh-button { border: 0; padding: 7px 0; color: var(--brand); background: transparent; font: inherit; font-size: 12px; cursor: pointer; }.refresh-button:disabled { opacity: .55; }.refresh-button span { margin-right: 5px; font-size: 17px; vertical-align: -1px; }
.room-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; }
.room-card { min-width: 0; padding: 18px; border: 1px solid var(--line); border-radius: var(--r-md); background: var(--surface); }
.room-card:hover { border-color: var(--brand-line); }
.room-card-top { display: flex; align-items: center; gap: 12px; }
.room-avatar { display: grid; place-items: center; width: 38px; height: 38px; flex: none; border-radius: 50%; background: var(--brand-soft); color: var(--brand-strong); font-weight: 700; }
.room-card-label { min-width: 0; }
.room-card h3 { margin: 0 0 5px; font-size: var(--fs-body); overflow-wrap: anywhere; }
.room-badge { display: inline-block; margin-right: 4px; padding: 2px 8px; border-radius: var(--r-pill); color: var(--success); background: var(--success-soft); font-size: var(--fs-cap); }
.room-badge-neutral { color: var(--ink-2); background: var(--surface-2); }
.room-card-meta { display: flex; flex-wrap: wrap; gap: 6px 14px; margin: 16px 0; color: var(--ink-3); font-size: var(--fs-xs); }
.room-code { color: var(--ink); font-family: var(--font-mono); letter-spacing: .08em; }
.room-join { width: 100%; }
.room-join:disabled { opacity: .55; cursor: wait; }
.text-action { border: 0; padding: 3px 0; background: transparent; color: var(--brand); font: inherit; font-size: 11px; font-weight: 700; cursor: pointer; }
.state-panel { min-height: 92px; display: flex; align-items: center; justify-content: center; gap: 12px; padding: 18px; border: 1px dashed var(--line-strong); border-radius: 15px; color: var(--ink-3); font-size: 12px; }.error-panel { justify-content: space-between; border-color: var(--error); color: var(--error); }.empty-panel { justify-content: flex-start; padding-left: 24px; }.empty-panel b { color: var(--ink-2); }.empty-panel p { margin-top: 3px; font-size: 11px; }.empty-doodle { width: 42px; height: 42px; display: grid; place-items: center; border-radius: 12px; background: var(--brand-soft); color: var(--brand); font-size: 23px; }
.room-skeleton { height: 156px; border-radius: 15px; background: linear-gradient(100deg, var(--surface-2) 20%, var(--surface-3) 38%, var(--surface-2) 55%); background-size: 200% 100%; animation: shimmer 1.4s ease-in-out infinite; }.gallery-note { color: var(--ink-3); font-size: 11px; }.gallery-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 13px; }.art-card { overflow: hidden; border: 1px solid var(--line); border-radius: 14px; background: var(--surface); }.art-image { position: relative; display: block; height: 155px; overflow: hidden; background: var(--surface-2); }.art-preview { width: 100%; height: 100%; }.art-image img { width: 100%; height: 100%; object-fit: cover; transition: transform .3s var(--ease-out); }.art-image:hover img { transform: scale(1.035); }.art-word { position: absolute; left: 10px; bottom: 10px; padding: 4px 8px; border-radius: 999px; background: rgba(23, 37, 73, .78); color: #fff; font-size: 10px; font-weight: 700; }.art-caption { display: flex; justify-content: space-between; gap: 8px; padding: 11px 12px; color: var(--ink-2); font-size: 10px; }.art-caption b { overflow: hidden; color: var(--ink); text-overflow: ellipsis; white-space: nowrap; }.art-caption span { overflow: hidden; color: var(--ink-3); text-overflow: ellipsis; white-space: nowrap; }.gallery-empty { display: flex; align-items: center; gap: 10px; min-height: 64px; color: var(--ink-3); font-size: 11px; }.gallery-empty span { color: var(--brand); font-size: 21px; }
.recent-strip { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }.recent-room { min-width: 0; display: flex; align-items: center; gap: 12px; padding: 12px; border: 1px solid var(--line); border-radius: 13px; background: var(--surface); color: var(--ink); text-align: left; cursor: pointer; }.recent-room:hover { border-color: var(--brand-line); }.recent-room-symbol { width: 34px; height: 34px; display: grid; place-items: center; flex: none; border-radius: 11px; background: var(--accent-soft); color: var(--accent-ink); }.recent-room-text { min-width: 0; flex: 1; }.recent-room-text b, .recent-room-text small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.recent-room-text b { font-size: 11px; }.recent-room-text small { margin-top: 3px; color: var(--ink-3); font-size: 10px; }.recent-arrow { color: var(--brand); }
@keyframes shimmer { to { background-position-x: -200%; } }
@media (prefers-reduced-motion: reduce) { .room-skeleton { animation: none; background-position-x: 0; } }
@media (max-width: 900px) { .room-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.gallery-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 650px) { .draw-guess-page { padding-top: 0; }.entry-card { padding: 21px; }.list-section { margin-top: 29px; }.section-heading h2 { font-size: 18px; }.room-grid { grid-template-columns: 1fr; }.gallery-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; }.art-image { height: 120px; }.art-caption { flex-direction: column; gap: 2px; }.recent-strip { grid-template-columns: 1fr; }.gallery-note { max-width: 120px; text-align: right; font-size: 10px; } }
</style>
