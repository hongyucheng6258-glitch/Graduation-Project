<template>
  <div>
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h1>{{ cfg.title }}</h1>
        <p class="desc">{{ cfg.desc }}</p>
      </div>
      <div class="page-head-actions">
        <el-radio-group v-model="viewMode" @change="handleViewChange">
          <el-radio-button value="all">全部内容</el-radio-button>
          <el-radio-button value="pending">待人工审核</el-radio-button>
        </el-radio-group>
        <button class="btn btn-ghost btn-sm" :disabled="loading" @click="load">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/></svg>
          刷新
        </button>
      </div>
    </div>

    <!-- 审核卡片列表 -->
    <div v-loading="loading" class="review-list">
      <div v-for="row in list" :key="row.id" class="review-card" :class="{ 'no-cover': props.type === 'partner' }">
        <!-- 封面（搭子无图片，不展示封面区） -->
        <div v-if="props.type !== 'partner'" class="review-cover">
          <el-image v-if="firstValidImage(row) && !imageErrors[row.id]" :src="firstValidImage(row)" fit="cover"
                    :preview-src-list="normalizeImages(row)" @error="imageErrors[row.id] = true" />
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" v-html="cfg.icon"></svg>
        </div>

        <!-- 主体 -->
        <div class="review-body">
          <div class="review-title">
            <span class="ellipsis">{{ displayTitle(row) }}</span>
            <span class="tag" :class="statusClass(row.auditStatus)">{{ statusText(row.auditStatus) }}</span>
            <span class="tag" :class="riskClass(row.aiRiskLevel)">{{ riskText(row.aiRiskLevel) }}</span>
          </div>
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

        <!-- 操作 -->
        <div class="review-actions">
          <button v-if="row.auditStatus !== 1" class="btn btn-success btn-sm" :disabled="acting" @click="pass(row)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 6 9 17l-5-5"/></svg>
            通过
          </button>
          <button v-if="row.auditStatus !== 2" class="btn btn-ghost-danger btn-sm" :disabled="acting" @click="openReject(row)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6 6 18M6 6l12 12"/></svg>
            驳回
          </button>
        </div>
      </div>

      <div v-if="!loading && list.length === 0" class="empty">
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
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { auditAll, auditList, auditPass, auditReject } from '../../api/audit'
import { formatTime } from '../../utils/date'
import { normalizeImages, firstValidImage } from '../../utils/image'

const props = defineProps({
  type: { type: String, required: true }
})

const CFG = {
  activity: { title: '活动审核', desc: '校园活动发布审核 · 内容安全与合规校验', icon: '<rect x="3" y="4" width="18" height="17" rx="2"/><path d="M3 9h18M8 2v4M16 2v4"/>' },
  idle: { title: '闲置审核', desc: '闲置物品发布审核 · 交易信息与违规校验', icon: '<path d="M3 7h18l-2 13H5z"/><path d="M8 11v6M12 11v6M16 11v6"/>' },
  lostfound: { title: '失物招领审核', desc: '失物招领信息审核 · 真实性校验', icon: '<circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/>' },
  post: { title: '动态审核', desc: '校园动态发布审核 · 社区内容治理', icon: '<path d="M21 11.5a8.5 8.5 0 0 1-12.5 7.5L3 21l2-5.5A8.5 8.5 0 1 1 21 11.5z"/>' },
  partner: { title: '搭子审核', desc: '学习搭子发布审核 · 信息真实性与合规校验', icon: '<circle cx="9" cy="7" r="4"/><path d="M2 21v-2a4 4 0 0 1 4-4h6a4 4 0 0 1 4 4v2"/><path d="M17 11l2 2 4-4"/>' }
}
const cfg = computed(() => CFG[props.type] || CFG.idle)
const sourceText = { manual: '人工', ai: 'AI 自动', ai_manual: 'AI+人工' }

const viewMode = ref('all')
const list = ref([])
const pageNum = ref(1)
const pageSize = 8
const total = ref(0)
const loading = ref(false)
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
  pageNum.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    const requestFn = viewMode.value === 'all' ? auditAll : auditList
    const res = await requestFn({ type: props.type, pageNum: pageNum.value, pageSize })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function pass(row) {
  acting.value = true
  try {
    await auditPass(props.type, row.id)
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
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回理由')
    return
  }
  acting.value = true
  try {
    await auditReject(props.type, currentRow.value.id, rejectReason.value.trim())
    ElMessage.success('已驳回，作者将收到通知')
    rejectVisible.value = false
    load()
  } finally {
    acting.value = false
  }
}

function resetAndLoad() {
  pageNum.value = 1
  imageErrors.value = {}
  load()
}
onMounted(resetAndLoad)
watch(() => props.type, resetAndLoad)
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--s-4);
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
.page-head-actions { display: flex; gap: var(--s-2); flex: none; align-items: center; }

.review-list { display: flex; flex-direction: column; gap: var(--s-3); }
.review-card {
  display: grid;
  grid-template-columns: 148px 1fr auto;
  gap: var(--s-5);
  padding: var(--s-4);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  transition: box-shadow .25s, border-color .25s;
}
.review-card:hover { box-shadow: var(--shadow-md); border-color: var(--brand-line); }
/* 无封面类型（搭子）：去掉 148px 封面空列，主体顶格、按钮贴近内容 */
.review-card.no-cover { grid-template-columns: 1fr auto; gap: var(--s-4); }
.review-cover {
  width: 148px;
  height: 104px;
  border-radius: var(--r-md);
  background: var(--surface-3);
  overflow: hidden;
  display: grid;
  place-items: center;
  color: var(--ink-3);
}
.review-cover img { width: 100%; height: 100%; object-fit: cover; }
.review-cover svg { width: 26px; height: 26px; }
.review-body { min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.review-title { font-weight: 600; font-size: var(--fs-sm); color: var(--ink); display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ellipsis { max-width: 420px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
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
.review-actions { display: flex; flex-direction: column; gap: var(--s-2); justify-content: center; }
.review-actions .btn { min-width: 92px; }

.ai-pre {
  display: flex;
  gap: var(--s-2);
  align-items: flex-start;
  padding: var(--s-2) var(--s-3);
  border-radius: var(--r-sm);
  background: var(--info-soft);
  border: 1px solid var(--info-line);
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
.btn-success { background: var(--success); color: #fff; }
.btn-success:hover { filter: brightness(1.08); box-shadow: var(--shadow-sm); }
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
  .review-card { grid-template-columns: 1fr; }
  .review-cover { width: 100%; height: 140px; }
  .review-actions { flex-direction: row; flex-wrap: wrap; }
  .review-actions .btn { flex: 1; }
}
</style>
