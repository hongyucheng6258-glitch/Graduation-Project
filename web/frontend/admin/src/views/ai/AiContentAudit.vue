<template>
  <div>
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h1>AI 内容审核</h1>
        <p class="desc">AI 智能预审结果复核 · 拦截内容人工裁决，保障社区内容安全</p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-ghost btn-sm" :disabled="loading" @click="loadAll">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/></svg>
          刷新数据
        </button>
      </div>
    </div>

    <!-- KPI -->
    <div class="kpi-grid">
      <div class="kpi">
        <div class="kpi-icon accent">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 8v4M12 16h.01"/><circle cx="12" cy="12" r="10"/></svg>
        </div>
        <div class="kpi-body">
          <div class="kpi-label">待人工复核</div>
          <div class="kpi-value">{{ pendingCount }}<small> 条</small></div>
        </div>
      </div>
      <div class="kpi">
        <div class="kpi-icon gold">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3l2.9 5.9 6.5.9-4.7 4.6 1.1 6.5L12 17.8l-5.8 3.1 1.1-6.5L2.6 9.8l6.5-.9z"/></svg>
        </div>
        <div class="kpi-body">
          <div class="kpi-label">AI 预审拦截</div>
          <div class="kpi-value">{{ blockedCount }}<small> 条</small></div>
        </div>
      </div>
      <div class="kpi">
        <div class="kpi-icon info">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 12l2 2 4-4"/><path d="M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6z"/></svg>
        </div>
        <div class="kpi-body">
          <div class="kpi-label">自动放行</div>
          <div class="kpi-value">{{ passedCount }}<small> 条</small></div>
        </div>
      </div>
      <div class="kpi">
        <div class="kpi-icon brand">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12a9 9 0 1 1 18 0 9 9 0 0 1-18 0z"/><path d="M12 7v5l3 3"/></svg>
        </div>
        <div class="kpi-body">
          <div class="kpi-label">AI 拦截率</div>
          <div class="kpi-value">{{ blockRate }}<small> %</small></div>
        </div>
      </div>
    </div>

    <!-- Tab -->
    <div class="tabs" style="margin-bottom: var(--s-4)">
      <button v-for="t in tabs" :key="t.key" class="tab" :class="{ active: tab === t.key }" @click="tab = t.key">
        {{ t.label }}
        <span v-if="t.key === 'pending'" class="tab-count">{{ pendingCount }}</span>
        <span v-else-if="t.key === 'log'" class="tab-count">{{ blockedCount }}</span>
      </button>
    </div>

    <!-- 待复核 -->
    <div v-show="tab === 'pending'" v-loading="loading">
      <el-table :data="pendingList" :row-key="(r) => r.type + '-' + r.id">
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <span class="tag" :class="typeTag(row.type)">{{ typeText(row.type) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="内容" min-width="220">
          <template #default="{ row }">{{ row.title || row.content }}</template>
        </el-table-column>
        <el-table-column label="风险" width="90">
          <template #default="{ row }">
            <span class="tag" :class="riskClass(row.aiRiskLevel)">{{ riskText(row.aiRiskLevel) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="aiAuditReason" label="AI 判断原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" :disabled="acting" @click="restore(row)">恢复展示</el-button>
            <el-button size="small" type="danger" :disabled="acting" @click="openBlock(row)">维持拦截</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && pendingList.length === 0" class="empty">
        <b>没有待复核内容</b>
        <p>AI 预审命中高风险的内容会出现在这里</p>
      </div>
    </div>

    <!-- 拦截日志 -->
    <div v-show="tab === 'log'" v-loading="loading">
      <el-table :data="blockedList" :row-key="(r) => r.type + '-' + r.id">
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <span class="tag" :class="typeTag(row.type)">{{ typeText(row.type) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="内容" min-width="220">
          <template #default="{ row }">{{ row.title || row.content }}</template>
        </el-table-column>
        <el-table-column label="风险" width="90">
          <template #default="{ row }">
            <span class="tag" :class="riskClass(row.aiRiskLevel)">{{ riskText(row.aiRiskLevel) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="aiAuditReason" label="命中规则" min-width="220" show-overflow-tooltip />
        <el-table-column label="处理状态" width="110">
          <template #default="{ row }">
            <span class="tag" :class="statusClass(row.auditStatus)">{{ statusText(row.auditStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && blockedList.length === 0" class="empty">
        <b>暂无拦截记录</b>
        <p>近期的 AI 高风险内容会显示在这里</p>
      </div>
    </div>

    <!-- 规则命中排行 -->
    <div v-show="tab === 'rules'" class="card">
      <div class="card-head">
        <div>
          <div class="card-title">规则命中排行</div>
          <div class="card-sub">按 AI 判断原因聚合的高风险内容分布（当前数据范围）</div>
        </div>
      </div>
      <div v-if="ruleRows.length" class="rule-list">
        <div v-for="r in ruleRows" :key="r.rule" class="rule-row">
          <div class="rule-name">{{ r.rule }}</div>
          <div class="rule-bar"><span :style="{ width: r.pct + '%' }"></span></div>
          <div class="rule-num">{{ r.count }} 条</div>
        </div>
      </div>
      <div v-else class="empty">
        <b>暂无规则数据</b>
        <p>AI 预审命中规则后将在此聚合统计</p>
      </div>
    </div>

    <!-- 维持拦截理由弹窗 -->
    <el-dialog v-model="blockVisible" title="维持拦截" width="420px">
      <p class="dialog-tip">确认维持拦截？该内容将保持不可见，作者会收到驳回通知。</p>
      <el-input v-model="blockReason" type="textarea" :rows="3" placeholder="理由（选填，将随消息通知作者）" maxlength="255" />
      <template #footer>
        <el-button @click="blockVisible = false">取消</el-button>
        <el-button type="danger" :loading="acting" @click="confirmBlock">确认拦截</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditAll, auditList, auditPass, auditReject } from '../../api/audit'
import { formatTime } from '../../utils/date'

const TYPES = ['activity', 'idle', 'lostfound', 'post', 'partner']
const TYPE_TEXT = { activity: '活动', idle: '闲置', lostfound: '失物', post: '动态', partner: '搭子' }
const TYPE_TAG = { activity: 'tag-brand', idle: 'tag-gold', lostfound: 'tag-info', post: 'tag-accent', partner: 'tag-brand' }

const tab = ref('pending')
const loading = ref(false)
const acting = ref(false)
const pendingList = ref([])
const blockedList = ref([])
const allRows = ref([])
const blockVisible = ref(false)
const blockReason = ref('')
const currentRow = ref(null)

const tabs = [
  { key: 'pending', label: '待复核' },
  { key: 'log', label: '拦截日志' },
  { key: 'rules', label: '规则命中' }
]

const pendingCount = computed(() => pendingList.value.length)
const blockedCount = computed(() => blockedList.value.length)
const passedCount = computed(() => allRows.value.filter((r) => (r.aiRiskLevel ?? 0) === 0 && r.auditStatus === 1).length)
const blockRate = computed(() => {
  const total = blockedCount.value + passedCount.value
  return total === 0 ? 0 : Math.round((blockedCount.value / total) * 100)
})

function typeText(t) { return TYPE_TEXT[t] || t }
function typeTag(t) { return TYPE_TAG[t] || 'tag-neutral' }
function riskText(r) {
  if (r === null || r === undefined) return '未评估'
  return ['低风险', '中风险', '高风险'][r] || '未评估'
}
function riskClass(r) {
  if (r === null || r === undefined) return 'tag-neutral'
  return ['tag-success', 'tag-warning', 'tag-error'][r] || 'tag-neutral'
}
function statusText(s) { return ['待审核', '已通过', '已驳回'][s] || '未知' }
function statusClass(s) { return ['tag-warning', 'tag-success', 'tag-error'][s] || 'tag-neutral' }

const ruleRows = computed(() => {
  const map = new Map()
  for (const r of allRows.value) {
    if ((r.aiRiskLevel ?? 0) < 1 || !r.aiAuditReason) continue
    const key = r.aiAuditReason
    map.set(key, (map.get(key) || 0) + 1)
  }
  const rows = [...map.entries()]
    .map(([rule, count]) => ({ rule, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 8)
  const max = rows[0]?.count || 1
  return rows.map((r) => ({ ...r, pct: Math.round((r.count / max) * 100) }))
})

async function loadAll() {
  loading.value = true
  try {
    const [pends, alls] = await Promise.all([
      Promise.all(TYPES.map((t) => auditList({ type: t, pageNum: 1, pageSize: 100 }).catch(() => ({ list: [], total: 0 })))),
      Promise.all(TYPES.map((t) => auditAll({ type: t, pageNum: 1, pageSize: 100 }).catch(() => ({ list: [], total: 0 }))))
    ])
    const pending = pends.flatMap((res, i) => (res.list || []).map((r) => ({ ...r, type: TYPES[i] })))
    const all = alls.flatMap((res, i) => (res.list || []).map((r) => ({ ...r, type: TYPES[i] })))
    pendingList.value = pending.filter((r) => (r.aiRiskLevel ?? 0) >= 1)
    allRows.value = all
    blockedList.value = all.filter((r) => (r.aiRiskLevel ?? 0) >= 2).sort((a, b) => (b.createTime || '').localeCompare(a.createTime || ''))
  } finally {
    loading.value = false
  }
}

async function restore(row) {
  try {
    await ElMessageBox.confirm('确认恢复展示该内容？作者将收到通过通知。', '恢复展示', { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    await auditPass(row.type, row.id)
    ElMessage.success('已恢复展示')
    loadAll()
  } finally {
    acting.value = false
  }
}

function openBlock(row) {
  currentRow.value = row
  blockReason.value = ''
  blockVisible.value = true
}

async function confirmBlock() {
  acting.value = true
  try {
    await auditReject(currentRow.value.type, currentRow.value.id, blockReason.value.trim() || 'AI 预审判定为高风险内容，维持拦截')
    ElMessage.success('已维持拦截')
    blockVisible.value = false
    loadAll()
  } finally {
    acting.value = false
  }
}

onMounted(loadAll)
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
.page-head-actions { display: flex; gap: var(--s-2); flex: none; }

.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--s-4); margin-bottom: var(--s-4); }
.kpi {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: var(--s-4) var(--s-5);
  display: flex;
  gap: var(--s-4);
  align-items: center;
  transition: box-shadow .25s, border-color .25s;
}
.kpi:hover { box-shadow: var(--shadow-md); border-color: var(--brand-line); }
.kpi-icon {
  width: 46px;
  height: 46px;
  border-radius: var(--r-md);
  display: grid;
  place-items: center;
  flex: none;
}
.kpi-icon svg { width: 22px; height: 22px; }
.kpi-icon.accent { background: var(--accent-soft); color: var(--accent-strong); }
.kpi-icon.gold { background: var(--gold-soft); color: var(--gold-strong); }
.kpi-icon.info { background: var(--info-soft); color: var(--info-strong); }
.kpi-icon.brand { background: var(--brand-soft); color: var(--brand-strong); }
.kpi-body { min-width: 0; flex: 1; }
.kpi-label { font-size: var(--fs-xs); color: var(--ink-3); font-weight: 500; }
.kpi-value { font-family: var(--font-display); font-size: 1.55rem; font-weight: 600; line-height: 1.2; letter-spacing: -.01em; }
.kpi-value small { font-size: var(--fs-sm); color: var(--ink-3); font-weight: 500; }

.tabs { display: inline-flex; background: var(--surface-2); border: 1px solid var(--line); border-radius: var(--r-pill); padding: 3px; gap: 2px; }
.tab {
  padding: 6px 14px;
  border-radius: var(--r-pill);
  font-size: var(--fs-xs);
  font-weight: 600;
  color: var(--ink-2);
  cursor: pointer;
  transition: background .2s, color .2s;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: none;
}
.tab.active { background: var(--surface); color: var(--brand-strong); box-shadow: var(--shadow-sm); }
.tab-count {
  background: var(--accent);
  color: var(--accent-ink);
  font-size: 10px;
  font-weight: 700;
  min-width: 17px;
  height: 16px;
  padding: 0 4px;
  border-radius: var(--r-pill);
  display: grid;
  place-items: center;
}
.tab.active .tab-count { background: var(--brand); color: var(--brand-ink); }

.card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: var(--s-5);
}
.card-head { display: flex; align-items: center; justify-content: space-between; gap: var(--s-3); margin-bottom: var(--s-4); }
.card-title { font-family: var(--font-display); font-weight: 600; font-size: var(--fs-h3); }
.card-sub { font-size: var(--fs-xs); color: var(--ink-3); margin-top: 2px; }

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
.tag-accent { background: var(--accent-soft); color: var(--accent-strong); }
.tag-info { background: var(--info-soft); color: var(--info-strong); }
.tag-gold { background: var(--gold-soft); color: var(--gold-strong); }
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
.btn-ghost { background: var(--surface); color: var(--ink-2); border: 1px solid var(--line-strong); }
.btn-ghost:hover { border-color: var(--brand-line); color: var(--ink); background: var(--surface-2); }
.btn-sm { padding: 5px 10px; font-size: var(--fs-xs); border-radius: var(--r-xs); }
.btn svg { width: 14px; height: 14px; }
.btn:disabled { opacity: .55; cursor: not-allowed; }

.rule-list { display: flex; flex-direction: column; gap: var(--s-3); }
.rule-row { display: flex; align-items: center; gap: var(--s-3); }
.rule-name { width: 280px; font-size: var(--fs-sm); color: var(--ink-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: none; }
.rule-bar { flex: 1; height: 8px; border-radius: var(--r-pill); background: var(--surface-3); overflow: hidden; }
.rule-bar span { display: block; height: 100%; border-radius: var(--r-pill); background: linear-gradient(90deg, var(--gold), var(--accent)); }
.rule-num { width: 56px; text-align: right; font-size: var(--fs-xs); font-family: var(--font-mono); color: var(--ink-2); flex: none; }

.empty { text-align: center; padding: var(--s-7) var(--s-5); color: var(--ink-3); }
.empty b { display: block; color: var(--ink-2); font-size: var(--fs-sm); margin-bottom: 3px; }
.empty p { font-size: var(--fs-xs); }
.dialog-tip { color: var(--ink-2); font-size: var(--fs-sm); line-height: 1.6; margin-bottom: var(--s-3); }

@media (max-width: 780px) {
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
