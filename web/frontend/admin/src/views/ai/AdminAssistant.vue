<template>
  <el-alert v-if="available === false" title="管理智能助手尚未启用" type="info" :closable="false" />
  <AssistantShell v-else ref="assistantShell" v-model:mode="workspace" v-model:question="question" class="admin-assistant"
    title="管理智能助手" subtitle="查看实时待办，从结果直接打开处理页面。" :tabs="workspaceTabs"
    tabs-label="管理助手功能" :sessions="sessions" :active-id="activeId"
    :prompts="showWelcome ? suggestions : []" :empty-title="showWelcome ? '从今天的待办开始' : ''"
    empty-description="可以查询待办数量、待审核活动或闲置，以及指定举报的证据。"
    :show-composer="workspace === 'tasks'" :busy="busy" :input-disabled="busy || !available" :sessions-disabled="busy"
    :max-length="1200" input-label="向管理助手提问" placeholder="例如，现在有多少待处理举报？"
    @new-session="newSession" @select-session="selectSession" @send="send(question)" @prompt="send">
    <template v-if="workspace === 'tasks'">
      <template v-if="error">
        <p class="assistant-error" role="alert">{{ error }}</p>
        <nav class="record-actions" aria-label="继续处理管理待办">
          <RouterLink :to="destinations.activity.route">待审活动</RouterLink>
          <RouterLink :to="destinations.idle.route">待审闲置</RouterLink>
          <RouterLink :to="destinations.report.route">待处理举报</RouterLink>
        </nav>
      </template>
      <template v-for="(turn, index) in turns" :key="index">
        <AssistantBubble role="user" :content="turn.question" :nickname="nickname" />
        <AssistantBubble role="assistant" :content="turn.answer" />
        <AssistantCard v-if="turn.tasks.length" wide>
          <section class="results" aria-label="实时待办结果">
            <header>
              <h3>待办快照</h3>
              <time :datetime="turn.queriedAt">{{ formatTime(turn.queriedAt) }}</time>
            </header>
            <div class="task-grid">
              <RouterLink v-for="task in turn.tasks" :key="task.key" :to="destinations[task.key].route" class="task-link">
                <span>{{ destinations[task.key].label }}</span>
                <strong>{{ task.count }} <small>项</small></strong>
                <span class="task-arrow" aria-hidden="true">↗</span>
              </RouterLink>
            </div>
            <p v-if="turn.tasks.some(task => task.key === 'ai')" class="risk-note">AI 预审风险提示是待审核内容的子集。</p>
          </section>
        </AssistantCard>
        <section v-if="turn.records.length" aria-label="业务证据">
          <template v-for="record in turn.records" :key="recordKey(record.kind, record.id)">
            <AssistantCard wide>
              <article class="evidence-card results">
                <header><strong>{{ record.title }}</strong><span class="record-status" :class="{ pending: record.status === 0 }">{{ recordStatus(record) }}</span></header>
                <p v-if="record.excerpt">内容节选　{{ record.excerpt }}</p>
                <div v-if="record.activity" class="record-details muted">
                  <span v-if="record.activity.location">地点 {{ record.activity.location }}</span>
                  <span v-if="record.activity.startTime">开始 {{ formatActivityTime(record.activity.startTime) }}</span>
                  <span v-if="record.activity.endTime">结束 {{ formatActivityTime(record.activity.endTime) }}</span>
                  <span v-if="record.activity.signupDeadline">报名截止 {{ formatActivityTime(record.activity.signupDeadline) }}</span>
                  <span v-if="record.activity.maxMembers != null">人数上限 {{ record.activity.maxMembers || '不限' }}</span>
                </div>
                <p v-if="record.riskReason" class="muted">预审提示　{{ record.riskReason }}</p>
                <template v-if="record.target">
                  <p class="muted">举报理由　{{ record.reason || '未填写' }}</p>
                  <p>被举报内容　{{ record.target.title || '原记录暂不可用' }}</p>
                  <p v-if="record.target.excerpt">被举报内容节选　{{ record.target.excerpt }}</p>
                </template>
                <footer class="record-actions">
                  <RouterLink :to="recordRoute(record)">{{ reviewKindNames[record.kind] ? `打开${reviewKindNames[record.kind]}审核记录` : '打开举报记录' }}</RouterLink>
                  <RouterLink v-if="record.target?.title && reviewKindNames[record.target.kind]"
                    :to="recordRoute(record.target)">查看被举报{{ reviewKindNames[record.target.kind] }}</RouterLink>
                  <el-button v-if="reviewKindNames[record.kind] && record.status === 0 && isLatestEvidence(record, index)"
                    plain type="primary" size="small" :disabled="busy" @click="openReview(record)">准备审核</el-button>
                </footer>
              </article>
            </AssistantCard>
            <AdminReviewCards v-if="reviewKindNames[record.kind] && isLatestEvidence(record, index)"
              :cards="reviewsByItem.get(recordKey(record.kind, record.id)) || []" :busy="busy" @confirm="confirmReview" />
          </template>
        </section>
      </template>
      <AdminReviewCards :cards="reviewsWithoutEvidence" :busy="busy" show-record-link @confirm="confirmReview" />
      <AssistantBubble v-if="sending" role="user" :content="pendingQuestion" :nickname="nickname" />
      <p v-if="initializing || loading || sending" class="muted" role="status">{{ sending ? '正在查询待办…' : '正在加载会话…' }}</p>
    </template>
    <AdminAssistantOperations v-else-if="available" :key="workspace" :mode="workspace"
      :can-manage-rules="canManageRules" :nickname="nickname" />
  </AssistantShell>
  <el-dialog v-model="reviewVisible" :title="`准备${reviewKindNames[reviewTarget?.kind] || '闲置'}审核`" width="min(460px, 92vw)">
    <p><strong>{{ reviewTarget?.title }}</strong></p>
    <el-radio-group v-model="reviewDecision" aria-label="审核决定">
      <el-radio value="PASS">通过</el-radio>
      <el-radio value="REJECT">驳回</el-radio>
    </el-radio-group>
    <el-input v-model="reviewReason" aria-label="审核理由" type="textarea" maxlength="255" :rows="3"
      :placeholder="reviewDecision === 'REJECT' ? '填写给发布者的驳回理由' : '可填写审核备注'" />
    <p v-if="reviewError" class="assistant-error" role="alert">{{ reviewError }}</p>
    <template #footer>
      <el-button @click="reviewVisible = false">取消</el-button>
      <el-button type="primary" :loading="reviewing" :disabled="reviewDecision === 'REJECT' && !reviewReason.trim()"
        @click="prepareReview">生成确认卡</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { useAdminPendingStore } from '../../store/adminPending'
import { computed, onMounted, ref } from 'vue'
import AssistantShell from '../../../../shared/assistant/AssistantShell.vue'
import AssistantBubble from '../../../../shared/assistant/AssistantBubble.vue'
import AssistantCard from '../../../../shared/assistant/AssistantCard.vue'
import AdminReviewCards from './AdminReviewCards.vue'
import { useAdminStore } from '../../store/admin'
import { formatTime as formatActivityTime } from '../../utils/date'
import AdminAssistantOperations from './AdminAssistantOperations.vue'
import { askAssistant, assistantCapabilities, assistantHistory, assistantReviews, assistantSessions,
  confirmAssistantReview, prepareAssistantReview } from '../../api/assistant'

const pending = useAdminPendingStore()

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
const suggestions = ['现在有哪些待办？', '找出待审核活动', '找出待审核闲置，优先显示有风险提示的记录', '有多少待处理举报？']
const reviewKindNames = { idle: '闲置', activity: '活动' }
const recordKey = (kind, id) => `${kind || 'idle'}-${id}`
const sessions = ref([])
const turns = ref([])
const reviews = ref([])
const reviewVisible = ref(false)
const reviewTarget = ref(null)
const reviewDecision = ref('PASS')
const reviewReason = ref('')
const reviewError = ref('')
const reviewing = ref(false)
const assistantShell = ref(null)
const pendingQuestion = ref('')
const question = ref('')
const activeId = ref(null)
const available = ref(null)
const workspace = ref('tasks')
const ragEnabled = ref(false)
const canManageRules = ref(false)
const initializing = ref(true)
const loading = ref(false)
const sending = ref(false)
const error = ref('')
const busy = computed(() => initializing.value || loading.value || sending.value || reviewing.value)
const adminStore = useAdminStore()
const nickname = computed(() => adminStore.adminInfo?.nickname || '我')
const workspaceTabs = computed(() => available.value ? [
  { value: 'tasks', label: '待办与审核' },
  ...(ragEnabled.value ? [{ value: 'rules', label: '内部规则' }] : []),
  { value: 'notice', label: '公告草稿' },
  { value: 'diagnostics', label: '运行诊断' }
] : [])
const showWelcome = computed(() => workspace.value === 'tasks' && available.value && !turns.value.length && !busy.value)
const lastEvidenceTurn = computed(() => {
  const placements = new Map()
  turns.value.forEach((turn, index) => {
    for (const record of turn.records) {
      if (reviewKindNames[record.kind]) placements.set(recordKey(record.kind, record.id), index)
    }
  })
  return placements
})
const isLatestEvidence = (record, index) => lastEvidenceTurn.value.get(recordKey(record.kind, record.id)) === index
const reviewsByItem = computed(() => {
  const grouped = new Map()
  for (const card of reviews.value) {
    const key = recordKey(card.kind, card.itemId)
    if (!grouped.has(key)) grouped.set(key, [])
    grouped.get(key).push(card)
  }
  return grouped
})
const reviewsWithoutEvidence = computed(() => reviews.value.filter(card => !lastEvidenceTurn.value.has(recordKey(card.kind, card.itemId))))
const scrollToBottom = () => assistantShell.value?.scrollToBottom()
const clock = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'medium' })
const formatTime = value => clock.format(new Date(value))
const recordStatus = record => record.kind === 'report'
  ? (record.status === 0 ? '待处理' : '已处理')
  : (['待审核', '已通过', '已驳回'][record.status] || '状态待确认')
const recordRoute = record => ({ path: reviewKindNames[record.kind] ? `/audit/${record.kind}` : '/report', query: { record: record.id } })

onMounted(async () => {
  try {
    const capabilities = await assistantCapabilities()
    available.value = capabilities.enabled
    ragEnabled.value = capabilities.ragEnabled
    canManageRules.value = capabilities.canManageRules
    if (!available.value) return
    sessions.value = await assistantSessions()
    if (sessions.value.length) await openSession(sessions.value[0].id)
  } catch (e) {
    error.value = e.message
  } finally {
    initializing.value = false
  }
})

function selectSession(session) {
  workspace.value = 'tasks'
  openSession(session.id)
  if (session.id === activeId.value) scrollToBottom()
}

async function openSession(id) {
  if (loading.value || sending.value || id === activeId.value) return
  loading.value = true
  error.value = ''
  try {
    const [history, cards] = await Promise.all([assistantHistory(id), assistantReviews(id)])
    turns.value = history
    reviews.value = cards
    activeId.value = id
    question.value = ''
    scrollToBottom()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function newSession() {
  workspace.value = 'tasks'
  activeId.value = null
  turns.value = []
  reviews.value = []
  question.value = ''
  error.value = ''
}

function openReview(record) {
  reviewTarget.value = record
  reviewDecision.value = 'PASS'
  reviewReason.value = ''
  reviewError.value = ''
  reviewVisible.value = true
}

async function prepareReview() {
  if (busy.value || !activeId.value || !reviewTarget.value) return
  reviewing.value = true
  reviewError.value = ''
  try {
    const card = await prepareAssistantReview({ sessionId: activeId.value, itemId: reviewTarget.value.id,
      kind: reviewTarget.value.kind, version: reviewTarget.value.activity?.version,
      decision: reviewDecision.value, reason: reviewReason.value.trim() })
    reviews.value.unshift(card)
    reviewVisible.value = false
  } catch (e) {
    reviewError.value = e.message
  } finally {
    reviewing.value = false
  }
}

async function confirmReview(card) {
  if (busy.value) return
  reviewing.value = true
  error.value = ''
  try {
    const receipt = await confirmAssistantReview(card.id)
    pending.refresh({ afterMutation: true })
    Object.assign(card, receipt)
    const key = recordKey(card.kind, card.itemId)
    for (const turn of turns.value) {
      for (const record of turn.records) {
        if (recordKey(record.kind, record.id) === key) record.status = receipt.auditStatus
      }
    }
  } catch (e) {
    error.value = e.message
  } finally {
    reviewing.value = false
  }
}

async function send(text) {
  const input = text.trim()
  if (busy.value || !available.value || !input) return
  sending.value = true
  pendingQuestion.value = input
  error.value = ''
  scrollToBottom()
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
    pendingQuestion.value = ''
    scrollToBottom()
  }
}
</script>

<style scoped>
.results header { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 8px; }
.results h3 { margin: 0; font-size: 15px; }
.muted, time, .risk-note { color: var(--ink-2); font-size: 13px; }
.task-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 144px), 1fr)); gap: 8px; margin-top: 11px; }
.task-link { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 4px 6px; padding: 9px 10px; border: 1px solid var(--line); border-radius: 9px; background: var(--surface-2); color: var(--ink-2); text-decoration: none; font-size: 12px; }
.task-link:hover, .task-link:focus-visible { color: var(--brand-strong); border-color: var(--brand-line); }
.task-link strong { grid-row: 2; color: var(--ink); font-size: 20px; line-height: 1.25; font-variant-numeric: tabular-nums; }
.task-link small { font-size: 11px; font-weight: 500; }
.task-arrow { grid-row: 1; grid-column: 2; color: var(--brand-strong); }
.risk-note { margin: 8px 0 0; font-size: 12px; }
.evidence-card p { margin: 6px 0; }
.record-status { padding: 2px 7px; border-radius: 6px; color: var(--ink-2); background: var(--surface-2); font-size: 12px; }
.record-status.pending { background: var(--warning-soft); color: var(--gold-strong); }
.record-details { display: flex; flex-wrap: wrap; gap: 4px 12px; margin: 6px 0; }
.record-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; padding-top: 9px; margin-top: 8px; border-top: 1px solid var(--line); }
.record-actions a { color: var(--brand-strong); font-size: 13px; }
.record-actions .el-button { margin-inline-start: auto; }
.assistant-error { color: var(--danger, #ae2929); }
@media (max-width: 420px) {
  .admin-assistant :deep(.ai-top__tools) { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .task-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .record-actions .el-button { margin-inline-start: 0; }
}
</style>
