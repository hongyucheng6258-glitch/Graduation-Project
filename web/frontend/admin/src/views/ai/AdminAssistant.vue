<template>
  <WtPageHeader title="管理智能助手" subtitle="查看实时待办，从结果直接打开处理页面。" />

  <el-alert v-if="available === false" title="管理智能助手尚未启用" type="info" :closable="false" />
  <div v-else class="assistant-workspace" :aria-busy="initializing">
    <aside class="session-panel" aria-label="管理助手会话">
      <div class="session-heading">
        <h2>会话</h2>
        <el-button size="small" :disabled="busy" @click="newSession">新建会话</el-button>
      </div>
      <p v-if="!sessions.length" class="muted">查询后会在这里保存会话。</p>
      <button v-for="session in sessions" :key="session.id" type="button" class="session-item"
        :aria-pressed="session.id === activeId" :disabled="busy" @click="openSession(session.id)">
        {{ session.title }}
      </button>
    </aside>

    <section class="conversation" aria-label="管理助手对话">
      <p v-if="error" class="assistant-error" role="alert">{{ error }}</p>
      <div v-if="!turns.length && !busy" class="starter">
        <h2>从今天的待办开始</h2>
        <p class="muted">可以查询待办数量、待审核闲置和指定举报的证据。</p>
        <div class="suggestions">
          <el-button v-for="suggestion in suggestions" :key="suggestion" @click="send(suggestion)">{{ suggestion }}</el-button>
        </div>
      </div>

      <ol v-if="turns.length" class="turns">
        <li v-for="(turn, index) in turns" :key="index" class="turn">
          <p class="question">{{ turn.question }}</p>
          <p>{{ turn.answer }}</p>
          <section v-if="turn.tasks.length" class="results" aria-label="实时待办结果">
            <header>
              <h3>待办快照</h3>
              <time :datetime="turn.queriedAt">{{ formatTime(turn.queriedAt) }}</time>
            </header>
            <RouterLink v-for="task in turn.tasks" :key="task.key" :to="destinations[task.key].route" class="task-link">
              <span>{{ destinations[task.key].label }}</span>
              <strong>{{ task.count }} 项</strong>
              <span class="muted" aria-hidden="true">查看 →</span>
            </RouterLink>
            <p v-if="turn.tasks.some(task => task.key === 'ai')" class="risk-note">AI 预审风险提示是待审核内容的子集。</p>
          </section>
          <section v-if="turn.records.length" class="results" aria-label="业务证据">
            <h3>业务记录</h3>
            <article v-for="record in turn.records" :key="`${record.kind}-${record.id}`" class="evidence-card">
              <header><strong>{{ record.title }}</strong><span class="muted">{{ recordStatus(record) }}</span></header>
              <p v-if="record.excerpt">内容节选　{{ record.excerpt }}</p>
              <p v-if="record.riskReason" class="muted">预审提示　{{ record.riskReason }}</p>
              <template v-if="record.target">
                <p class="muted">举报理由　{{ record.reason || '未填写' }}</p>
                <p>被举报内容　{{ record.target.title || '原记录暂不可用' }}</p>
                <p v-if="record.target.excerpt">被举报内容节选　{{ record.target.excerpt }}</p>
              </template>
              <RouterLink :to="recordRoute(record)">{{ record.kind === 'idle' ? '打开闲置审核记录' : '打开举报记录' }}</RouterLink>
              <RouterLink v-if="record.target?.kind === 'idle' && record.target.title" class="target-link"
                :to="recordRoute(record.target)">查看被举报闲置</RouterLink>
            </article>
          </section>
        </li>
      </ol>
      <p v-if="busy" class="muted" role="status">{{ sending ? '正在查询待办…' : '正在加载会话…' }}</p>

      <form class="composer" @submit.prevent="send(question)">
        <label for="assistant-question">向管理助手提问</label>
        <div>
          <el-input id="assistant-question" v-model="question" maxlength="1200" :disabled="busy || !available"
            placeholder="例如，现在有多少待处理举报？" />
          <el-button native-type="submit" type="primary" :disabled="busy || !available || !question.trim()">发送</el-button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { askAssistant, assistantCapabilities, assistantHistory, assistantSessions } from '../../api/assistant'

const audit = (type, label) => ({ label, route: { path: `/audit/${type}`, query: { view: 'pending' } } })
const destinations = {
  idle: audit('idle', '闲置审核'),
  activity: audit('activity', '活动审核'),
  lostfound: audit('lostfound', '失物招领审核'),
  post: audit('post', '动态审核'),
  partner: audit('partner', '学习搭子审核'),
  report: { label: '待处理举报', route: { path: '/report', query: { view: 'pending' } } },
  ai: { label: 'AI 预审风险提示', route: '/ai/audit' }
}
const suggestions = ['现在有哪些待办？', '找出待审核闲置，优先显示有风险提示的记录', '有多少待处理举报？']
const sessions = ref([])
const turns = ref([])
const question = ref('')
const activeId = ref(null)
const available = ref(null)
const initializing = ref(true)
const loading = ref(false)
const sending = ref(false)
const error = ref('')
const busy = computed(() => initializing.value || loading.value || sending.value)
const clock = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'medium' })
const formatTime = value => clock.format(new Date(value))
const recordStatus = record => record.kind === 'report'
  ? (record.status === 0 ? '待处理' : '已处理')
  : (['待审核', '已通过', '已驳回'][record.status] || '状态待确认')
const recordRoute = record => ({ path: record.kind === 'idle' ? '/audit/idle' : '/report', query: { record: record.id } })

onMounted(async () => {
  try {
    available.value = (await assistantCapabilities()).enabled
    if (!available.value) return
    sessions.value = await assistantSessions()
    if (sessions.value.length) await openSession(sessions.value[0].id)
  } catch (e) {
    error.value = e.message
  } finally {
    initializing.value = false
  }
})

async function openSession(id) {
  if (loading.value || sending.value || id === activeId.value) return
  loading.value = true
  error.value = ''
  try {
    turns.value = await assistantHistory(id)
    activeId.value = id
    question.value = ''
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function newSession() {
  activeId.value = null
  turns.value = []
  question.value = ''
  error.value = ''
}

async function send(text) {
  const input = text.trim()
  if (busy.value || !available.value || !input) return
  sending.value = true
  error.value = ''
  try {
    const result = await askAssistant({ question: input, sessionId: activeId.value })
    activeId.value = result.sessionId
    turns.value.push(result.turn)
    question.value = ''
    sessions.value = await assistantSessions()
  } catch (e) {
    error.value = e.message
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.assistant-workspace { display: grid; grid-template-columns: minmax(165px, 220px) minmax(0, 1fr); gap: 18px; align-items: start; }
.session-panel, .conversation { background: var(--surface, #fff); border: 1px solid var(--line); border-radius: 12px; padding: 18px; }
.session-heading, .results header { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.session-heading h2, .starter h2 { margin: 0; font-size: 17px; }
.muted, time, .risk-note { color: var(--ink-2); font-size: 13px; }
.session-item { display: block; width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-align: left; padding: 9px 8px; border: 0; background: transparent; cursor: pointer; color: inherit; border-radius: 6px; }
.session-item[aria-pressed='true'] { background: var(--brand-soft); color: var(--brand-strong); }
.session-item:disabled { cursor: default; }
.starter { padding: 10px 0 16px; }
.suggestions { display: flex; flex-wrap: wrap; gap: 8px; }
.suggestions :deep(.el-button) { margin-left: 0; }
.turns { list-style: none; margin: 0; padding: 0; }
.turn { border-bottom: 1px solid var(--line); padding: 0 0 12px; margin-bottom: 14px; overflow-wrap: anywhere; }
.question { font-weight: 650; color: var(--brand-strong); }
.results { max-width: 620px; }
.results h3 { font-size: 14px; margin: 0 0 6px; }
.task-link { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 12px; padding: 9px 0; border-top: 1px solid var(--line); color: inherit; text-decoration: none; }
.task-link:hover { color: var(--brand-strong); }
.task-link strong { font-variant-numeric: tabular-nums; }
.evidence-card { border-top: 1px solid var(--line); padding: 10px 0; }
.evidence-card p { margin: 6px 0; }
.evidence-card a { color: var(--brand-strong); }
.target-link { margin-left: 12px; }
.composer { display: grid; gap: 6px; margin-top: 12px; }
.composer > div { display: flex; gap: 10px; }
.assistant-error { color: var(--danger, #ae2929); }
@media (max-width: 760px) {
  .assistant-workspace { grid-template-columns: minmax(0, 1fr); }
}
</style>
