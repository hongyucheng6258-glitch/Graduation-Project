<template>
  <div>
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h1>{{ cfg.title }}</h1>
        <p class="desc">{{ cfg.desc }}</p>
      </div>
      <div class="page-head-actions">
        <RouterLink v-if="focusedId" class="btn btn-ghost btn-sm" :to="{ path: `/audit/${props.type}`, query: { view: 'pending' } }">返回待审列表</RouterLink>
        <el-radio-group v-else v-model="viewMode" @change="handleViewChange">
          <el-radio-button value="all">全部内容</el-radio-button>
          <el-radio-button value="pending">待人工审核</el-radio-button>
        </el-radio-group>
        <button class="btn btn-ghost btn-sm" :disabled="loading" @click="load">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/></svg>
          刷新
        </button>
      </div>
    </div>

    <nav class="review-type-nav" aria-label="审核分类">
      <RouterLink v-for="(category, key) in CFG" :key="key" class="review-type"
        :class="{ selected: props.type === key }"
        :to="{ path: `/audit/${key}`, query: viewMode === 'pending' ? { view: 'pending' } : {} }">
        {{ category.label }}
      </RouterLink>
    </nav>

    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon class="audit-error" />

    <!-- 审核卡片列表 -->
    <div v-loading="loading" class="review-list" :class="{ 'review-list--focused': focusedId }">
      <article v-for="row in list" :key="row.id" class="review-card"
        :class="{ 'has-cover': firstValidImage(row) && !imageErrors[row.id] }">
        <div class="review-body">
          <div class="review-tags">
            <span class="tag tag-brand">{{ cfg.label }}</span>
            <span class="tag" :class="statusClass(row.auditStatus)">{{ statusText(row.auditStatus) }}</span>
            <span class="tag" :class="riskClass(row.aiRiskLevel)">{{ riskText(row.aiRiskLevel) }}</span>
          </div>
          <h2>{{ displayTitle(row) }}</h2>
          <div class="review-meta">
            <span>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
              {{ formatTime(row.createTime) }}
            </span>
            <span>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
              {{ sourceText[row.auditSource] || '人工' }}
            </span>
            <span>#{{ row.id }}</span>
          </div>
          <div v-if="props.type === 'activity'" class="review-meta">
            <span v-if="row.category">分类 {{ row.category }}</span>
            <span v-if="row.location">地点 {{ row.location }}</span>
            <span v-if="row.startTime">开始 {{ formatTime(row.startTime) }}</span>
            <span v-if="row.endTime">结束 {{ formatTime(row.endTime) }}</span>
            <span v-if="row.signupDeadline">报名截止 {{ formatTime(row.signupDeadline) }}</span>
            <span v-if="row.maxMembers != null">人数上限 {{ row.maxMembers || '不限' }}</span>
          </div>
          <p v-if="displayDesc(row)" class="review-desc">{{ displayDesc(row) }}</p>

          <!-- AI 预审建议 -->
          <div v-if="row.aiRiskLevel !== null && row.aiRiskLevel !== undefined && row.aiAuditReason" class="ai-pre">
            <div class="ai-pre-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/><circle cx="12" cy="12" r="3"/></svg>
            </div>
            <div class="ai-pre-body">
              <b>AI 预审：{{ riskText(row.aiRiskLevel) }}</b>
              {{ row.aiAuditReason }}
            </div>
          </div>
        </div>

        <div v-if="firstValidImage(row) && !imageErrors[row.id]" class="review-cover">
          <el-image :src="firstValidImage(row)" fit="cover"
                    :preview-src-list="normalizeImages(row)" @error="imageErrors[row.id] = true" />
        </div>

        <!-- 操作 -->
        <div class="review-actions">
          <button v-if="row.auditStatus !== 1" class="btn btn-primary btn-sm" :disabled="acting" @click="pass(row)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 6 9 17l-5-5"/></svg>
            通过
          </button>
          <button v-if="row.auditStatus !== 2" class="btn btn-ghost-danger btn-sm" :disabled="acting" @click="openReject(row)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6 6 18M6 6l12 12"/></svg>
            驳回
          </button>
        </div>
      </article>

      <div v-if="!loading && !loadError && list.length === 0" class="empty">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9 12l2 2 4-4"/><path d="M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6z"/></svg>
        <b>暂无内容</b>
        <p>当前视图下没有待处理的数据</p>
      </div>
    </div>

    <el-pagination v-if="total > pageSize" v-model:current-page="pageNum" :total="total" :page-size="pageSize"
                   layout="total, prev, pager, next" style="margin-top: 16px" @current-change="load" />

    <!-- 驳回理由弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回原因" width="420px">
      <el-input v-model="rejectReason" type="textarea" :rows="3"
                placeholder="必填，将随消息通知作者" maxlength="255" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="acting" @click="reject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { auditAll, auditList, auditPass, auditReject, auditRecordDetail } from '../../api/audit'
import { formatTime } from '../../utils/date'
import { normalizeImages, firstValidImage } from '../../utils/image'

const props = defineProps({
  type: { type: String, required: true }
})
const route = useRoute()
const focusedId = computed(() => ['idle', 'activity'].includes(props.type) && typeof route.query.record === 'string'
  && /^[1-9]\d*$/.test(route.query.record) ? route.query.record : null)
let requestVersion = 0

const CFG = {
  activity: { label: '活动', title: '活动审核', desc: '校园活动发布审核 · 内容安全与合规校验' },
  idle: { label: '闲置', title: '闲置审核', desc: '闲置物品发布审核 · 交易信息与违规校验' },
  lostfound: { label: '失物', title: '失物招领审核', desc: '失物招领信息审核 · 真实性校验' },
  post: { label: '动态', title: '动态审核', desc: '校园动态发布审核 · 社区内容治理' },
  partner: { label: '搭子', title: '搭子审核', desc: '学习搭子发布审核 · 信息真实性与合规校验' }
}
const cfg = computed(() => CFG[props.type] || CFG.idle)
const sourceText = { manual: '人工', ai: 'AI 自动', ai_manual: 'AI+人工' }

const viewMode = ref('all')
const list = ref([])
const pageNum = ref(1)
const pageSize = 8
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const acting = ref(false)
const rejectVisible = ref(false)
const rejectReason = ref('')
const currentRow = ref(null)
const imageErrors = ref({})

function statusText(s) {
  return ['待审核', '已通过', '已驳回'][s] || '未知'
}
function statusClass(s) {
  return ['tag-warning', 'tag-success', 'tag-error'][s] || 'tag-neutral'
}
function riskText(r) {
  if (r === null || r === undefined) return '未评估'
  return ['低风险', '中风险', '高风险'][r] || '未评估'
}
function riskClass(r) {
  if (r === null || r === undefined) return 'tag-neutral'
  return ['tag-success', 'tag-warning', 'tag-error'][r] || 'tag-neutral'
}

function displayTitle(row) {
  if (props.type === 'partner') return row.subject || '未填写科目'
  return row.title || row.content || ''
}
function displayDesc(row) {
  if (props.type === 'partner') {
    const parts = []
    if (row.goal) parts.push('目标：' + row.goal)
    if (row.schedule) parts.push('时间：' + row.schedule)
    if (row.intro) parts.push(row.intro)
    if (row.contact) parts.push('联系：' + row.contact)
    return parts.join(' ｜ ')
  }
  return row.description || row.content || ''
}

function handleViewChange() {
  resetAndLoad()
}

async function load() {
  const version = ++requestVersion
  const recordId = focusedId.value
  loading.value = true
  loadError.value = ''
  if (recordId) list.value = []
  try {
    const requestFn = viewMode.value === 'all' ? auditAll : auditList
    const res = recordId ? { list: [{ ...await auditRecordDetail(props.type, recordId), id: recordId }], total: 1 }
      : await requestFn({ type: props.type, pageNum: pageNum.value, pageSize })
    if (version !== requestVersion) return
    list.value = res.list
    total.value = res.total
  } catch (error) {
    if (version !== requestVersion) return
    list.value = []
    total.value = 0
    loadError.value = error.message || '无法读取审核记录，请刷新重试'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

async function pass(row) {
  acting.value = true
  try {
    await auditPass(props.type, row.id, row.auditStatus === 0)
    ElMessage.success('已通过，作者将收到通知')
    load()
  } finally {
    acting.value = false
  }
}

function openReject(row) {
  currentRow.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function reject() {
  if (!currentRow.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回理由')
    return
  }
  acting.value = true
  try {
    await auditReject(props.type, currentRow.value.id, rejectReason.value.trim(), currentRow.value.auditStatus === 0)
    ElMessage.success('已驳回，作者将收到通知')
    rejectVisible.value = false
    load()
  } finally {
    acting.value = false
  }
}

function resetAndLoad() {
  list.value = []
  total.value = 0
  pageNum.value = 1
  imageErrors.value = {}
  load()
}
watch([() => props.type, () => route.query.view, focusedId], ([, view], [, previousView]) => {
  rejectVisible.value = false
  currentRow.value = null
  rejectReason.value = ''
  if (view !== previousView) viewMode.value = view === 'pending' ? 'pending' : 'all'
  resetAndLoad()
}, { immediate: true })
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--s-4);
  flex-wrap: wrap;
  margin-bottom: var(--s-5);
}
.page-head h1 {
  font-family: var(--font-display);
  font-size: var(--fs-h1);
  font-weight: 600;
  margin-bottom: 4px;
  letter-spacing: -.01em;
}
.page-head .desc { color: var(--ink-3); font-size: var(--fs-sm); }
.page-head-actions { display: flex; flex-wrap: wrap; gap: var(--s-2); align-items: center; }

.review-type-nav { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 18px; }
.review-type {
  padding: 5px 13px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 600;
}
.review-type:hover { border-color: var(--brand-line); }
.review-type.selected { background: var(--brand); border-color: var(--brand); color: var(--brand-ink); }
.audit-error { margin-bottom: 16px; }
.review-list { display: flex; flex-direction: column; gap: 16px; }
.review-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  padding: 22px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
}
.review-card.has-cover { grid-template-columns: minmax(0, 1fr) 148px; }
.review-cover { width: 100%; height: 104px; border-radius: var(--r-sm); overflow: hidden; background: var(--surface-2); }
.review-cover :deep(.el-image) { width: 100%; height: 100%; }
.review-body { min-width: 0; display: flex; flex-direction: column; gap: 8px; overflow-wrap: anywhere; }
.review-body h2 { font-size: var(--fs-body); font-weight: 650; }
.review-tags { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.review-meta { display: flex; gap: var(--s-3); flex-wrap: wrap; font-size: var(--fs-cap); color: var(--ink-3); }
.review-meta span { display: inline-flex; align-items: center; gap: 4px; }
.review-meta svg { width: 12px; height: 12px; }
.review-desc {
  font-size: var(--fs-sm);
  color: var(--ink-2);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.review-list--focused .review-desc { display: block; white-space: pre-wrap; overflow-wrap: anywhere; }
.review-actions { grid-column: 1 / -1; display: flex; flex-wrap: wrap; gap: var(--s-2); }

.ai-pre {
  display: flex;
  gap: var(--s-2);
  align-items: flex-start;
  padding: var(--s-2) var(--s-3);
  border-radius: var(--r-sm);
  background: var(--surface-2);
}
.ai-pre-icon {
  width: 26px;
  height: 26px;
  border-radius: var(--r-xs);
  background: var(--info);
  color: var(--info-ink);
  display: grid;
  place-items: center;
  flex: none;
}
.ai-pre-icon svg { width: 14px; height: 14px; }
.ai-pre-body { font-size: var(--fs-xs); color: var(--info-strong); line-height: 1.5; }
.ai-pre-body b { display: block; }

.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 9px;
  border-radius: var(--r-pill);
  font-size: var(--fs-cap);
  font-weight: 600;
  white-space: nowrap;
}
.tag::before { content: ""; width: 5px; height: 5px; border-radius: 50%; background: currentColor; opacity: .85; }
.tag-brand { background: var(--brand-soft); color: var(--brand-strong); }
.tag-success { background: var(--success-soft); color: var(--success); }
.tag-warning { background: var(--warning-soft); color: var(--gold-strong); }
.tag-error { background: var(--error-soft); color: var(--error); }
.tag-neutral { background: var(--surface-3); color: var(--ink-2); }

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 9px 16px;
  border-radius: var(--r-sm);
  font-weight: 600;
  font-size: var(--fs-sm);
  transition: background .18s var(--ease-out), border-color .18s, color .18s, transform .12s, box-shadow .18s;
  white-space: nowrap;
  cursor: pointer;
  border: none;
}
.btn:active { transform: translateY(1px); }
.btn-primary { background: var(--brand); color: var(--brand-ink); }
.btn-primary:hover { filter: brightness(1.08); box-shadow: var(--shadow-sm); }
.btn-ghost-danger { background: var(--surface); color: var(--error); border: 1px solid var(--line-strong); }
.btn-ghost-danger:hover { border-color: var(--error); background: var(--error-soft); }
.btn-sm { padding: 5px 10px; font-size: var(--fs-xs); border-radius: var(--r-xs); }
.btn svg { width: 14px; height: 14px; }
.btn:disabled { opacity: .55; cursor: not-allowed; }
.btn-ghost { background: var(--surface); color: var(--ink-2); border: 1px solid var(--line-strong); }
.btn-ghost:hover { border-color: var(--brand-line); color: var(--ink); background: var(--surface-2); }

.empty { text-align: center; padding: var(--s-7) var(--s-5); color: var(--ink-3); background: var(--surface); border: 1px solid var(--line); border-radius: var(--r-lg); }
.empty svg { width: 44px; height: 44px; margin: 0 auto var(--s-2); opacity: .35; }
.empty b { display: block; color: var(--ink-2); font-size: var(--fs-sm); margin-bottom: 3px; }
.empty p { font-size: var(--fs-xs); }

@media (max-width: 780px) {
  .review-card { padding: 16px; }
  .review-card.has-cover { grid-template-columns: minmax(0, 1fr); }
  .review-cover { height: 140px; }
}
</style>
