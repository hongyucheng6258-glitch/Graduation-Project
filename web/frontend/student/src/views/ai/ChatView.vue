<template>
  <div class="ai-page">
    <div class="ai-layout">
      <!-- 左栏：会话列表 -->
      <aside class="ai-side">
        <WtButton type="soft" block class="new-btn" @click="newSession">＋ 新建会话</WtButton>
        <div class="session-list">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="ai-session"
            :class="{ active: s.id === currentSession?.id }"
            @click="switchSession(s)"
          >
            <svg class="ai-session__ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M21 11.5a8.5 8.5 0 0 1-12.5 7.5L3 21l2-5.5A8.5 8.5 0 1 1 21 11.5z" />
            </svg>
            <span class="s-title">{{ s.title }}</span>
            <el-dropdown size="small" @command="(cmd) => onSessionCmd(cmd, s)" @click.stop>
              <el-icon class="s-more"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename">重命名</el-dropdown-item>
                  <el-dropdown-item command="delete">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <WtEmptyState v-if="!sessions.length" type="empty" title="暂无会话" description="点击上方新建一个对话" />
        </div>
      </aside>

      <!-- 右栏：对话窗 -->
      <div class="ai-main">
        <div class="ai-top">
          <div class="ai-orb">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/><circle cx="12" cy="12" r="3"/>
            </svg>
          </div>
          <div class="ai-top__meta">
            <b>{{ tab === 'pdf' ? 'PDF 问答' : (tab === 'guide' ? '🏫 AI 校园向导' : 'AI 自由对话') }}</b>
            <div class="section-sub" style="font-size: var(--fs-cap)">{{ tab === 'guide' ? (capabilities.enabled ? '查活动、校园资料和闲置，生成发布草稿' : '实时校园数据问答') : '学习助手' }}</div>
          </div>
          <div class="ai-top__tools">
            <WtTabs v-model="tab" :options="sceneTabs" />
          </div>
        </div>

        <!-- PDF 问答：文档上传区 -->
        <div v-if="tab === 'pdf'" class="pdf-bar">
          <el-upload :show-file-list="false" :http-request="doPdfUpload" accept=".pdf">
            <el-button :loading="pdfUploading">上传PDF课件</el-button>
          </el-upload>
          <span v-if="pdfDoc" class="pdf-info">{{ pdfDoc.fileName }}（{{ pdfDoc.pageCount }}页，{{ pdfIndexLabel }}）</span>
          <span v-else class="pdf-tip">上传文本型PDF后可针对文档内容提问</span>
          <el-button v-if="pdfIndex?.state === 'FAILED'" text @click="startPdfIndex(pdfDoc)">重试索引</el-button>
          <el-button v-else-if="pdfIndex?.state === 'READY'" text @click="startPdfIndex(pdfDoc, true)">重建索引</el-button>
        </div>

        <!-- 快捷问题（对齐原型 quick-prompts，空会话时展示） -->
        <div v-if="!messages.length && tab !== 'pdf'" class="quick-prompts">
          <span v-for="q in quickPrompts" :key="q" class="quick-prompt" @click="askQuick(q)">{{ q }}</span>
        </div>

        <!-- 消息区 -->
        <div ref="msgBox" class="ai-chat">
          <template v-for="(m, i) in messages" :key="i">
            <ChatBubble :role="m.role" :content="m.content" :streaming="m.streaming"
              :nickname="userStore.userInfo?.nickname || '我'" :user-avatar="userStore.userInfo?.avatar" />
            <IdleGuideCards v-if="m.idleItems?.length" :items="m.idleItems" :contacting="contactingIdle" :numbered="m.idleSearch"
              @contact="contactIdle" />
            <div v-for="resource in m.resources || []" :key="`${resource.kind}-${resource.id}`" class="ai-confirm resource-card">
              <strong>{{ resource.title }}</strong>
              <p class="section-sub">{{ resourceLabel(resource) }}<template v-if="resource.subtitle"> · {{ resource.subtitle }}</template></p>
              <RouterLink :to="resourceRoute(resource)">{{ resource.kind === 'partner' ? '查看发布者主页' : '打开原帖' }}</RouterLink>
            </div>
            <div v-for="draft in m.idleDrafts || []" :key="draft.id" class="ai-confirm idle-draft-card">
              <strong>闲置发布草稿</strong>
              <p>{{ draft.title }}</p>
              <p v-if="draft.expectItem" class="section-sub">想换：{{ draft.expectItem }}</p>
              <p v-if="draft.state === 'SUBMITTED'" role="status">已提交，可在我的闲置查看审核进度。</p>
              <p v-else-if="draft.state === 'EXPIRED'" role="status">草稿已过期，请重新生成。</p>
              <RouterLink v-else :to="{ name: 'IdlePublish', query: { draft: draft.id } }">编辑并确认发布</RouterLink>
            </div>
            <div v-for="appointment in m.idleAppointments || []" :key="appointment.id" class="ai-confirm idle-appointment-card">
              <strong>确认预约互换</strong>
              <p>{{ appointment.itemTitle }}</p>
              <p v-if="appointment.remark" class="section-sub">留言：{{ appointment.remark }}</p>
              <p v-if="appointment.state === 'SUBMITTED'" role="status">{{ appointmentStatus(appointment.appointmentStatus) }}</p>
              <p v-else-if="appointment.state === 'EXPIRED'" role="status">预约确认卡已过期，请重新发起。</p>
              <el-button v-else type="primary" :loading="appointment.sending" @click="confirmAppointment(appointment)">
                确认预约
              </el-button>
            </div>
            <div v-if="m.citations?.length" class="ai-sources" aria-label="检索到的资料">
              <span>资料来源</span>
              <button v-for="source in m.citations" :key="`${source.documentId}-${source.page}`"
                type="button" @click="openCitation(source)">
                {{ source.fileName }} · 第 {{ source.page }} 页
              </button>
            </div>
            <div v-for="action in m.actions || []" :key="action.id" class="ai-confirm">
              <strong>确认活动报名</strong>
              <p>{{ action.activityTitle }}</p>
              <p v-if="action.remark" class="section-sub">备注：{{ action.remark }}</p>
              <p v-if="action.result === 'SUBMITTED'" role="status">报名已提交，等待审核。</p>
              <p v-else-if="action.result === 'EXPIRED'" role="status">确认卡已过期，请重新发起报名。</p>
              <el-button v-else type="primary" :loading="action.sending" @click="confirmAction(action)">
                确认提交报名
              </el-button>
            </div>
          </template>
          <WtEmptyState v-if="!messages.length" type="empty" title="开始提问吧" description="支持多轮上下文追问，AI 会记得聊过的内容" />
        </div>

        <!-- 输入区 -->
        <div class="ai-input">
          <input
            v-model="question"
            type="text"
            :placeholder="tab === 'pdf' ? '针对PDF文档内容提问…' : (tab === 'guide' ? '问问校园：周末有什么活动 / 我报名的活动 / 有失物招领吗' : '输入你的问题，回车发送，Shift+Enter 换行')"
            @keydown.enter.exact.prevent="send"
          />
          <button class="send" :disabled="!question.trim() || asking || switching" aria-label="发送" @click="send">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M22 2 11 13M22 2l-7 20-4-9-9-4z" />
            </svg>
          </button>
        </div>
        <div class="ai-footnote">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <circle cx="12" cy="12" r="9" /><path d="M12 8h.01M11 12h1v4h1" />
          </svg>
          AI 回答由 DeepSeek 生成，关键信息请自行核对；调用全程留痕可观测。
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import ChatBubble from '../../components/ChatBubble.vue'
import IdleGuideCards from '../../components/IdleGuideCards.vue'
import WtButton from '../../components/wt/WtButton.vue'
import WtTabs from '../../components/wt/WtTabs.vue'
import WtEmptyState from '../../components/wt/WtEmptyState.vue'
import { useUserStore } from '../../store/user'
import * as aiApi from '../../api/ai'
import { idleDetail } from '../../api/idle'
import { startChat } from '../../utils/startChat'
import { responseAction } from '../../utils/request-policy.mjs'
import { handleAuthExpired } from '../../utils/request-navigation'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const tab = ref('chat')
const sessions = ref([])
const currentSession = ref(null)
const messages = ref([])
const question = ref('')
const asking = ref(false)
// 第11项：流式取消与请求标识隔离——迟到/串会话响应一律丢弃
const streamAbort = ref(null)
let requestSeq = 0
function cancelStream() {
  if (streamAbort.value) {
    streamAbort.value.abort()
    streamAbort.value = null
  }
  requestSeq += 1
}
const msgBox = ref()
// PDF 问答状态
const pdfUploading = ref(false)
const pdfDoc = ref(null)
const pdfIndex = ref(null)
const capabilities = ref({ enabled: false, ragEnabled: false })
const contactingIdle = ref(null)
const switching = ref(false)
let viewRequest = 0
let indexRequest = 0
const pdfIndexLabel = computed(() => {
  if (!capabilities.value.ragEnabled) return '已解析'
  if (pdfIndex.value?.state === 'READY') return '可问答'
  if (pdfIndex.value?.state === 'FAILED') return pdfIndex.value.error || '索引失败'
  return '正在建立索引'
})

const sceneTabs = [
  { value: 'chat', label: '💬 AI答疑' },
  { value: 'guide', label: '🏫 校园向导' },
  { value: 'pdf', label: '📄 PDF问答' }
]

const sceneOf = () => (tab.value === 'pdf' ? 'pdf' : tab.value === 'guide' && capabilities.value.enabled ? 'agent' : 'chat')

onUnmounted(() => {
  viewRequest += 1
  indexRequest += 1
  cancelStream()
})

onMounted(async () => {
  try { capabilities.value = await aiApi.agentCapabilities() } catch { /* 旧版后端继续使用原问答 */ }
  await loadSessions()
  const q = route.query.q
  if (q) {
    question.value = String(q)
    await send()
  }
})

watch(tab, () => {
  cancelStream()
  viewRequest += 1
  switching.value = false
  indexRequest += 1
  // 切换 Tab 重新加载对应场景会话，并清理另一场景的文档状态
  sessions.value = []
  currentSession.value = null
  messages.value = []
  pdfDoc.value = null
  pdfIndex.value = null
  loadSessions()
})

async function loadSessions() {
  const view = viewRequest
  if (tab.value === 'guide' && !capabilities.value.enabled) {
    sessions.value = []
    return
  }
  const list = await aiApi.listSessions(sceneOf())
  if (view !== viewRequest) return
  sessions.value = list
  if (sessions.value.length && !currentSession.value) {
    await switchSession(sessions.value[0])
  } else if (tab.value === 'pdf' && !currentSession.value) {
    pdfDoc.value = null
  }
}

async function restorePdfDoc(session, view) {
  if (tab.value !== 'pdf' || !session?.docId) {
    pdfDoc.value = null
    return
  }
  try {
    const doc = await aiApi.pdfDoc(session.docId)
    if (view !== viewRequest) return
    pdfDoc.value = doc
    startPdfIndex(pdfDoc.value)
  } catch {
    if (view === viewRequest) pdfDoc.value = null
  }
}

async function newSession() {
  if (tab.value === 'guide' && !capabilities.value.enabled) {
    cancelStream()
    messages.value = []
    return
  }
  const view = viewRequest
  const s = await aiApi.createSession({ scene: sceneOf(), docId: pdfDoc.value?.docId })
  if (view !== viewRequest) return
  sessions.value.unshift(s)
  await switchSession(s)
}

async function switchSession(s) {
  cancelStream()
  const view = ++viewRequest
  indexRequest += 1
  pdfDoc.value = null
  pdfIndex.value = null
  messages.value = []
  currentSession.value = s
  switching.value = true
  try {
    await restorePdfDoc(s, view)
    if (view !== viewRequest) return
    const res = await aiApi.listMessages(s.id, 1, 50)
    if (view !== viewRequest) return
    messages.value = (res.list || []).reverse().filter((m) => ['user', 'assistant'].includes(m.role)).map((m) => ({
      role: m.role,
      content: m.content,
      citations: m.attachments?.citations,
      idleItems: m.attachments?.idleItems,
      idleSearch: m.attachments?.idleSearch,
      idleDrafts: m.attachments?.idleDrafts,
      idleAppointments: m.attachments?.idleAppointments,
      resources: m.attachments?.resources,
      actions: m.attachments?.actions,
      streaming: false
    }))
    scrollBottom()
  } finally {
    if (view === viewRequest) switching.value = false
  }
}

async function onSessionCmd(cmd, s) {
  if (cmd === 'rename') {
    const { value } = await ElMessageBox.prompt('请输入新名称', '重命名会话', {
      inputValue: s.title
    })
    if (value) {
      await aiApi.renameSession(s.id, value)
      s.title = value
    }
  } else if (cmd === 'delete') {
    await ElMessageBox.confirm('删除后将清空该会话的全部消息', '确认删除', { type: 'warning' })
    await aiApi.deleteSession(s.id)
    sessions.value = sessions.value.filter((x) => x.id !== s.id)
    if (currentSession.value?.id === s.id) {
      currentSession.value = null
      messages.value = []
    }
  }
}

/** 发送问题：chat/pdf 共用；chat 走 SSE 流式，pdf 走一次性返回 */
const quickPrompts = computed(() =>
  tab.value === 'guide'
    ? capabilities.value.enabled ? [
        '周末有哪些校园活动？',
        '我报名的活动有哪些？',
        '找找可以用书交换的键盘',
        '我想发布一件闲置',
        '请查资料：活动报名有哪些要求？'
      ] : [
        '周末有哪些活动？',
        '我报名的活动有哪些？',
        '最近有失物招领吗？',
        '校园里有什么二手闲置？',
        '食堂和图书馆几点开放？'
      ]
    : [
        '帮我梳理一下高数期末的复习重点',
        '这道编程题为什么会报错？帮我看看',
        '怎么规划一周的英语四六级备考？',
        '帮我生成一份论文写作大纲',
        '解释一下 TCP 三次握手的过程'
      ]
)

function askQuick(q) {
  question.value = q
  send()
}

async function send() {
  const q = question.value.trim()
  if (!q || asking.value || switching.value) return
  if (tab.value === 'pdf' && !pdfDoc.value) {
    ElMessage.warning('请先上传PDF课件')
    return
  }
  if (tab.value === 'pdf' && capabilities.value.ragEnabled && pdfIndex.value?.state !== 'READY') {
    ElMessage.warning('请等待文档索引就绪')
    return
  }
  asking.value = true
  question.value = ''
  const seq = ++requestSeq
  if (streamAbort.value) streamAbort.value.abort()
  const controller = new AbortController()
  streamAbort.value = controller
  messages.value.push({ role: 'user', content: q, streaming: false })
  // R4 修复：普通对象 push 进 ref 数组后，Vue 只在「经数组代理访问」时把它包成响应式代理；
  // 持原始对象引用改属性不会触发更新。这里取出数组代理上的同一消息对象，所有回调都改它。
  const raw = { role: 'assistant', content: '', streaming: true }
  messages.value.push(raw)
  const msg = messages.value[messages.value.length - 1]
  scrollBottom()

  // 仅当仍是最新一次发送时回调才生效（防迟到/串会话响应）
  const isCurrent = () => seq === requestSeq
  // R4 修复：统一业务错误策略——401 跳登录、503 跳维护页，与 request.js 拦截器一致；
  // 其余错误才在页内弹提示。chat/guide/pdf 三个分支共用。
  const onError = (code, message) => {
    if (!isCurrent()) return
    msg.streaming = false
    msg.content = msg.content || `⚠️ ${message}`
    const action = responseAction(code)
    if (action === 'login') {
      handleAuthExpired(router, router.currentRoute.value.fullPath)
    } else if (action === 'maintenance' && router.currentRoute.value.path !== '/maintenance') {
      router.replace('/maintenance')
    } else {
      ElMessage.error(message)
    }
    asking.value = false
  }

  if (tab.value === 'chat') {
    // SSE 流式
    try {
      await aiApi.chatStream(
        { sessionId: currentSession.value?.id, question: q },
        {
          onDelta: (delta) => {
            if (!isCurrent()) return
            msg.content += delta
            scrollBottom()
          },
          onDone: () => {
            if (!isCurrent()) return
            msg.streaming = false
            asking.value = false
            refreshSessionList()
          },
          onError
        },
        { signal: controller.signal }
      )
    } catch (e) {
      onError(1002, e.message || 'AI服务调用失败')
    } finally {
      if (isCurrent()) {
        msg.streaming = false
        asking.value = false
        streamAbort.value = null
      }
    }
  } else if (tab.value === 'guide') {
    // 校园向导：实时业务数据问答（复用 aiGuideAsk 统一错误策略，401/403/503 走 request 封装处理，避免裸 fetch）
    try {
      const answer = capabilities.value.enabled
        ? await aiApi.agentAsk(q, currentSession.value?.id, controller.signal)
        : await aiApi.aiGuideAsk(q)
      if (!isCurrent()) return
      msg.content = (typeof answer === 'string' ? answer : answer.answer) || '暂时没有查到相关内容'
      msg.citations = answer?.citations
      msg.idleItems = answer?.idleItems
      msg.idleSearch = answer?.idleSearch
      msg.idleDrafts = answer?.idleDrafts
      msg.idleAppointments = answer?.idleAppointments
      msg.resources = answer?.resources
      msg.actions = answer?.actions
      if (answer?.sessionId) {
        currentSession.value = { id: answer.sessionId }
        refreshSessionList().catch(() => {})
      }
    } catch (e) {
      onError(e?.code || 1002, e?.message || 'AI服务调用失败')
    } finally {
      if (!isCurrent()) return
      msg.streaming = false
      asking.value = false
    }
    scrollBottom()
  } else {
    // PDF 一次性返回
    try {
      const res = capabilities.value.ragEnabled
        ? await aiApi.knowledgeAsk(pdfIndex.value.id, q, currentSession.value?.id, controller.signal)
        : await aiApi.pdfAsk({
        docId: pdfDoc.value.docId,
        sessionId: currentSession.value?.id,
        question: q
      })
      if (!isCurrent()) return
      msg.content = typeof res === 'string' ? res : res.answer
      msg.citations = res?.citations
      msg.streaming = false
      if (res?.sessionId) {
        currentSession.value = { ...currentSession.value, id: res.sessionId, docId: pdfDoc.value.docId }
      }
      refreshSessionList()
    } catch (e) {
      onError(1002, e.message || 'AI服务调用失败')
    } finally {
      if (isCurrent()) {
        asking.value = false
        streamAbort.value = null
      }
    }
    scrollBottom()
  }
}

/** 上传 PDF 并解析 */
async function doPdfUpload({ file }) {
  cancelStream()
  const view = ++viewRequest
  indexRequest += 1
  switching.value = false
  pdfDoc.value = null
  pdfIndex.value = null
  pdfUploading.value = true
  try {
    const doc = await aiApi.pdfUpload(file)
    if (view !== viewRequest) return
    // 上传成功后使用已有会话创建接口绑定文档，兼容尚未重启的旧后端版本。
    // 保留旧会话及其历史消息，当前文档使用新的 PDF 会话承载。
    const session = await aiApi.createSession({
      scene: 'pdf',
      docId: doc.docId,
      title: currentSession.value?.title || 'PDF问答'
    })
    if (view !== viewRequest) return
    pdfDoc.value = doc
    startPdfIndex(doc)
    sessions.value.unshift(session)
    currentSession.value = session
    messages.value = []
    ElMessage.success(capabilities.value.ragEnabled ? 'PDF 已上传，正在建立索引' : '解析成功，可以开始提问')
  } catch (e) {
    if (view !== viewRequest) return
    pdfDoc.value = null
    ElMessage.error(e.message || 'PDF上传或绑定失败')
  } finally {
    pdfUploading.value = false
  }
}

async function startPdfIndex(doc, rebuild = false) {
  if (!capabilities.value.ragEnabled || !doc) return
  const ticket = ++indexRequest
  pdfIndex.value = null
  try {
    let status = await aiApi.indexPdf(doc.docId, rebuild)
    for (let i = 0; i < 90 && ticket === indexRequest; i += 1) {
      pdfIndex.value = status
      if (['READY', 'FAILED'].includes(status.state)) return
      await new Promise((resolve) => setTimeout(resolve, 1200))
      if (ticket !== indexRequest) return
      status = await aiApi.knowledgeStatus(status.id)
    }
    if (ticket === indexRequest) pdfIndex.value = { state: 'FAILED', error: '索引仍在处理中，可稍后重试' }
  } catch (e) {
    if (ticket === indexRequest) pdfIndex.value = { state: 'FAILED', error: e.message }
  }
}

async function openCitation(source) {
  const tab = window.open('about:blank', '_blank')
  if (!tab) {
    ElMessage.warning('请允许浏览器打开资料窗口')
    return
  }
  tab.opener = null
  try {
    const url = URL.createObjectURL(await aiApi.knowledgeFile(source.documentId))
    tab.location.replace(`${url}#page=${source.page}`)
    setTimeout(() => URL.revokeObjectURL(url), 120000)
  } catch (e) {
    tab.close()
    ElMessage.error(e.message)
  }
}

async function confirmAction(action) {
  if (action.sending || action.result === 'SUBMITTED') return
  action.sending = true
  try {
    const result = await aiApi.confirmAgentAction(action.id)
    action.result = result.status
  } catch (e) {
    ElMessage.error(e.message || '报名未能提交')
  } finally {
    action.sending = false
  }
}

function resourceLabel(resource) {
  if (resource.kind === 'lost') return resource.status === 1 ? '拾物招领' : '寻物启事'
  if (resource.kind === 'partner') return '学习搭子'
  return resource.status === 1 ? '互助问答 · 已解决' : '互助问答 · 待回答'
}

function resourceRoute(resource) {
  if (resource.kind === 'lost') return { name: 'LostFoundDetail', params: { id: resource.id } }
  if (resource.kind === 'partner') return { name: 'UserHome', params: { id: resource.profileId } }
  return { name: 'QaDetail', params: { id: resource.id } }
}

function appointmentStatus(status) {
  return ['预约已提交，等待发布者处理。', '发布者已接受预约。', '发布者已拒绝预约。', '互换已完成。', '预约已取消。'][status]
    || '预约已提交，可在闲置详情查看进度。'
}

async function confirmAppointment(appointment) {
  if (appointment.sending) return
  appointment.sending = true
  try {
    Object.assign(appointment, await aiApi.confirmIdleAppointment(appointment.id))
  } catch (e) {
    ElMessage.error(e.message || '预约未能提交')
  } finally {
    appointment.sending = false
  }
}

async function contactIdle(card) {
  if (contactingIdle.value !== null) return
  contactingIdle.value = card.itemId
  const view = viewRequest
  try {
    const item = await idleDetail(card.itemId)
    if (view !== viewRequest) return
    if (item.status !== 0 || item.auditStatus !== 1 || item.isOwner) {
      ElMessage.warning('物品状态已变化，请打开详情查看')
      return
    }
    await startChat(router, userStore, item.userId, { type: 'idle', id: card.itemId, title: item.title })
  } catch (e) {
    ElMessage.error(e.message || '暂时无法联系发布者')
  } finally {
    contactingIdle.value = null
  }
}

/** 首问后刷新会话列表（标题由后端自动更新） */
async function refreshSessionList() {
  const view = viewRequest
  const list = await aiApi.listSessions(sceneOf())
  if (view !== viewRequest) return
  sessions.value = list
  if (currentSession.value) {
    const cur = list.find((x) => x.id === currentSession.value.id)
    if (cur) currentSession.value = cur
  } else if (list.length) {
    currentSession.value = list[0]
  }
}

function scrollBottom() {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}
</script>

<style scoped>
.ai-sources {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 9px;
  margin: 0 0 12px 42px;
  font-size: 12px;
}
.ai-sources button {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 4px 8px;
  background: var(--el-bg-color);
  color: var(--el-color-primary);
  cursor: pointer;
  overflow-wrap: anywhere;
}
.ai-confirm {
  width: min(100% - 44px, 350px);
  box-sizing: border-box;
  margin: 0 0 12px 42px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
}
.ai-confirm p { margin: 6px 0; overflow-wrap: anywhere; }
.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s-2);
  margin-bottom: var(--s-4);
}
.quick-prompt {
  padding: 8px 14px;
  border-radius: var(--r-pill);
  background: var(--info-soft);
  color: var(--info-strong);
  border: 1px solid var(--info-line);
  font-size: var(--fs-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all .15s var(--ease-out);
}
.quick-prompt:hover {
  background: var(--info);
  color: var(--info-ink);
  border-color: var(--info);
  transform: translateY(-1px);
}<style scoped>
.ai-page { height: 100%; }
.ai-layout {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: var(--s-5);
  height: calc(100vh - 220px);
  min-height: 480px;
}
.ai-side {
  display: flex;
  flex-direction: column;
  gap: var(--s-2);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: var(--s-4);
  overflow: auto;
}
.new-btn { margin-bottom: var(--s-2); }
.session-list { display: flex; flex-direction: column; gap: 4px; }
.ai-session {
  display: flex;
  align-items: center;
  gap: var(--s-3);
  padding: var(--s-3);
  border-radius: var(--r-sm);
  font-size: var(--fs-sm);
  color: var(--ink-2);
  background: transparent;
  border: none;
  width: 100%;
  text-align: left;
  cursor: pointer;
  font-family: var(--font-sans);
  transition: background 0.2s, color 0.2s;
}
.ai-session__ico { width: 18px; height: 18px; flex: none; color: var(--brand); }
.ai-session:hover { background: var(--surface-2); }
.ai-session.active { background: var(--brand-soft); color: var(--brand-strong); font-weight: 600; }
.ai-session.active .ai-session__ico { color: var(--brand-strong); }
.s-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.s-more { color: var(--ink-3); cursor: pointer; }
.s-more:hover { color: var(--ink); }

.ai-main {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  overflow: hidden;
  min-width: 0;
}
.ai-top {
  display: flex;
  align-items: center;
  gap: var(--s-3);
  padding: var(--s-4) var(--s-5);
  border-bottom: 1px solid var(--line);
}
.ai-orb {
  width: 34px;
  height: 34px;
  border-radius: var(--r-pill);
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(140deg, var(--brand), var(--accent));
  box-shadow: var(--shadow-sm);
  flex: none;
}
.ai-orb svg { width: 18px; height: 18px; }
.ai-top__meta { min-width: 0; }
.ai-top__meta b { font-size: var(--fs-body); }
.ai-top__tools { margin-left: auto; }

.pdf-bar {
  padding: 10px 16px;
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  gap: 12px;
}
.pdf-info { font-size: 13px; color: var(--success); }
.pdf-tip { font-size: 13px; color: var(--ink-3); }

.ai-chat {
  flex: 1;
  overflow-y: auto;
  padding: var(--s-5);
  display: flex;
  flex-direction: column;
  gap: var(--s-4);
  background: var(--surface-2);
}
.ai-input {
  display: flex;
  gap: var(--s-3);
  padding: var(--s-4);
  border-top: 1px solid var(--line);
  background: var(--surface);
}
.ai-input input {
  flex: 1;
  padding: var(--s-3) var(--s-4);
  border-radius: var(--r-pill);
  border: 1px solid var(--line);
  background: var(--surface-2);
  font-size: var(--fs-sm);
  font-family: var(--font-sans);
  color: var(--ink);
}
.ai-input input::placeholder { color: var(--ink-3); }
.ai-input input:focus { outline: none; border-color: var(--brand); background: var(--surface); box-shadow: 0 0 0 4px var(--brand-soft); }
.send {
  width: 48px;
  height: 48px;
  border-radius: var(--r-pill);
  display: grid;
  place-items: center;
  background: linear-gradient(140deg, var(--brand), var(--brand-strong));
  color: var(--brand-ink);
  border: none;
  cursor: pointer;
  flex: none;
  transition: transform 0.15s var(--ease-out), box-shadow 0.2s, opacity 0.2s;
}
.send:hover:not(:disabled) { transform: translateY(-2px); box-shadow: var(--shadow-md); }
.send:disabled { opacity: 0.5; cursor: not-allowed; }
.send svg { width: 20px; height: 20px; }
.ai-footnote {
  display: flex;
  align-items: center;
  gap: var(--s-2);
  font-size: var(--fs-cap);
  color: var(--ink-3);
  padding: var(--s-3) var(--s-5);
  border-top: 1px solid var(--line);
  background: var(--surface-2);
}
.ai-footnote svg { width: 14px; height: 14px; color: var(--brand); flex: none; }

@media (max-width: 820px) {
  .ai-layout { grid-template-columns: 1fr; height: auto; }
  .ai-side { flex-direction: row; overflow-x: auto; }
  .session-list { flex-direction: row; }
}
</style>
