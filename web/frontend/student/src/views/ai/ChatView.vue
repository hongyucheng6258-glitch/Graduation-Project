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
            <div class="section-sub" style="font-size: var(--fs-cap)">{{ tab === 'guide' ? '实时校园数据驱动 · 活动 / 闲置 / 失物 / 公告' : 'DeepSeek 驱动 · 上下文已记忆' }}</div>
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
          <span v-if="pdfDoc" class="pdf-info">✅ {{ pdfDoc.fileName }}（{{ pdfDoc.pageCount }}页，已解析）</span>
          <span v-else class="pdf-tip">上传文本型PDF后可针对文档内容提问</span>
        </div>

        <!-- 快捷问题（对齐原型 quick-prompts，空会话时展示） -->
        <div v-if="!messages.length && tab !== 'pdf'" class="quick-prompts">
          <span v-for="q in quickPrompts" :key="q" class="quick-prompt" @click="askQuick(q)">{{ q }}</span>
        </div>

        <!-- 消息区 -->
        <div ref="msgBox" class="ai-chat">
          <ChatBubble
            v-for="(m, i) in messages"
            :key="i"
            :role="m.role"
            :content="m.content"
            :streaming="m.streaming"
            :nickname="userStore.userInfo?.nickname || '我'"
            :user-avatar="userStore.userInfo?.avatar"
          />
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
          <button class="send" :disabled="!question.trim() || asking" aria-label="发送" @click="send">
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
import WtButton from '../../components/wt/WtButton.vue'
import WtTabs from '../../components/wt/WtTabs.vue'
import WtEmptyState from '../../components/wt/WtEmptyState.vue'
import { useUserStore } from '../../store/user'
import * as aiApi from '../../api/ai'
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

const sceneTabs = [
  { value: 'chat', label: '💬 AI答疑' },
  { value: 'guide', label: '🏫 校园向导' },
  { value: 'pdf', label: '📄 PDF问答' }
]

const sceneOf = () => (tab.value === 'pdf' ? 'pdf' : 'chat')

onUnmounted(() => cancelStream())

onMounted(async () => {
  await loadSessions()
  const q = route.query.q
  if (q) {
    question.value = String(q)
    await send()
  }
})

watch(tab, () => {
  cancelStream()
  // 切换 Tab 重新加载对应场景会话，并清理另一场景的文档状态
  sessions.value = []
  currentSession.value = null
  messages.value = []
  pdfDoc.value = null
  if (tab.value !== 'guide') loadSessions()
})

async function loadSessions() {
  if (tab.value === 'guide') {
    sessions.value = []
    return
  }
  sessions.value = await aiApi.listSessions(sceneOf())
  if (sessions.value.length && !currentSession.value) {
    await switchSession(sessions.value[0])
  } else if (tab.value === 'pdf' && !currentSession.value) {
    pdfDoc.value = null
  }
}

async function restorePdfDoc(session) {
  if (tab.value !== 'pdf' || !session?.docId) {
    pdfDoc.value = null
    return
  }
  try {
    pdfDoc.value = await aiApi.pdfDoc(session.docId)
  } catch {
    pdfDoc.value = null
  }
}

async function newSession() {
  const s = await aiApi.createSession({ scene: sceneOf(), docId: pdfDoc.value?.docId })
  sessions.value.unshift(s)
  await switchSession(s)
}

async function switchSession(s) {
  cancelStream()
  currentSession.value = s
  await restorePdfDoc(s)
  const res = await aiApi.listMessages(s.id, 1, 50)
  // 接口倒序返回，翻转为正序展示
  messages.value = (res.list || []).reverse().map((m) => ({
    role: m.role,
    content: m.content,
    streaming: false
  }))
  scrollBottom()
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
    ? [
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
  if (!q || asking.value) return
  if (tab.value === 'pdf' && !pdfDoc.value) {
    ElMessage.warning('请先上传PDF课件')
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
      const answer = await aiApi.aiGuideAsk(q)
      if (!isCurrent()) return
      msg.content = answer || '⚠️ 暂时没有检索到相关内容'
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
      const res = await aiApi.pdfAsk({
        docId: pdfDoc.value.docId,
        sessionId: currentSession.value?.id,
        question: q
      })
      if (!isCurrent()) return
      msg.content = typeof res === 'string' ? res : res.answer
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
  pdfUploading.value = true
  try {
    pdfDoc.value = await aiApi.pdfUpload(file)
    // 上传成功后使用已有会话创建接口绑定文档，兼容尚未重启的旧后端版本。
    // 保留旧会话及其历史消息，当前文档使用新的 PDF 会话承载。
    const session = await aiApi.createSession({
      scene: 'pdf',
      docId: pdfDoc.value.docId,
      title: currentSession.value?.title || 'PDF问答'
    })
    sessions.value.unshift(session)
    currentSession.value = session
    messages.value = []
    ElMessage.success(`解析成功，共 ${pdfDoc.value.pageCount} 页，可以开始提问了`)
  } catch (e) {
    pdfDoc.value = null
    ElMessage.error(e.message || 'PDF上传或绑定失败')
  } finally {
    pdfUploading.value = false
  }
}

/** 首问后刷新会话列表（标题由后端自动更新） */
async function refreshSessionList() {
  const list = await aiApi.listSessions(sceneOf())
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
