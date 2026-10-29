<template>
  <div class="wrongbook">
    <!-- 渐变 Banner（对齐原型 wrong-banner，含统计 + 勋章） -->
    <div class="wrong-banner">
      <div class="banner-left">
        <h1>我的错题本</h1>
        <p>快速收录 · 智能复习 · AI 辅助，逐个击破</p>
        <div class="banner-stats">
          <div><b>{{ stats.total ?? 0 }}</b><span>错题总数</span></div>
          <div><b>{{ stats.pending ?? 0 }}</b><span>待复习</span></div>
          <div><b>{{ stats.mastered ?? 0 }}</b><span>已掌握</span></div>
          <div><b>{{ stats.weekReviewCount ?? 0 }}</b><span>本周复习</span></div>
        </div>
      </div>
      <div class="medal-row">
        <div class="medal" :class="{ on: (stats.total ?? 0) > 0 }">🏅 收录达人<small>累计错题 &gt; 0</small></div>
        <div class="medal" :class="{ on: (stats.weekReviewCount ?? 0) > 0 }">🎯 复习坚持<small>本周有复习</small></div>
        <div class="medal" :class="{ on: (stats.mastered ?? 0) > 0 }">🤖 AI 掌握<small>已掌握 &gt; 0</small></div>
      </div>
    </div>

    <!-- 中间操作区 -->
    <div class="actions">
      <el-button type="primary" @click="quickAddVisible = true">⚡ 快速收录</el-button>
      <el-button type="success" :disabled="!stats.todayPending" @click="openTodayReview">
        📖 开始今日复习<template v-if="stats.todayPending">（{{ stats.todayPending }}）</template>
      </el-button>
      <el-button @click="planVisible = true">🗓️ AI 生成复习计划</el-button>
      <el-button @click="openWeaknessReport">📊 生成薄弱点报告</el-button>
    </div>

    <!-- 薄弱知识点面板（统计型） -->
    <div v-if="weak.knowledgePoints.length || weak.errorReasons.length" class="weak-panel">
      <div class="weak-title">📉 薄弱知识点</div>
      <div class="weak-row">
        <span class="weak-label">高频知识点：</span>
        <el-tag
          v-for="kp in weak.knowledgePoints.slice(0, 6)"
          :key="kp.name"
          size="small"
          :type="kp.pendingCount > 0 ? 'danger' : 'info'"
          class="weak-tag"
        >
          {{ kp.name }}（{{ kp.wrongCount }} 题<template v-if="kp.pendingCount">，待复习 {{ kp.pendingCount }}</template>）
        </el-tag>
      </div>
      <div class="weak-row">
        <span class="weak-label">主要错因：</span>
        <span v-for="r in weak.errorReasons.slice(0, 5)" :key="r.reason" class="reason-item">
          {{ r.reason }} ×{{ r.count }}
        </span>
      </div>
    </div>

    <!-- 筛选与排序 -->
    <div class="filters">
      <el-radio-group v-model="statusFilter" size="small" @change="reload">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button v-for="s in WQ_STATUS" :key="s.value" :value="String(s.value)">{{ s.label }}</el-radio-button>
      </el-radio-group>
      <div class="subject-filter">
        <el-check-tag :checked="!subject" @change="selectSubject('')">全部学科</el-check-tag>
        <el-check-tag
          v-for="s in subjects"
          :key="s"
          :checked="subject === s"
          style="margin-left: 6px"
          @change="selectSubject(s)"
        >{{ s }}</el-check-tag>
      </div>
      <el-select v-model="sort" size="small" style="width: 150px" @change="reload">
        <el-option label="最近收录" value="create_desc" />
        <el-option label="最久未复习" value="last_review_asc" />
        <el-option label="错误次数最多" value="wrong_desc" />
        <el-option label="难度最高" value="difficulty_desc" />
      </el-select>
    </div>

    <!-- 批量操作条 -->
    <div v-if="selectedIds.length" class="batch-bar">
      <span>已选 {{ selectedIds.length }} 道错题</span>
      <el-button size="small" type="primary" @click="openOutlineFromSelected">🤖 根据选中错题生成提纲</el-button>
      <el-button size="small" @click="selectedIds = []">取消选择</el-button>
    </div>

    <!-- 错题卡片列表 -->
    <div class="cards" v-loading="loading">
      <el-card v-for="wq in list" :key="wq.id" class="wq-card" shadow="hover">
        <div class="wq-head">
          <el-checkbox
            :model-value="selectedIds.includes(wq.id)"
            @change="toggleSelect(wq.id)"
          />
          <el-tag size="small">{{ wq.subject || '待整理' }}</el-tag>
          <el-tag v-if="wq.tag" size="small" type="info" style="margin-left: 6px">{{ wq.tag }}</el-tag>
          <el-tag size="small" :type="statusOf(wq.status).type" style="margin-left: 6px">
            {{ statusOf(wq.status).label }}
          </el-tag>
          <span class="wq-source">{{ wq.source === 'ai' ? 'AI收录' : '手动收录' }}</span>
        </div>

        <div class="wq-q" :title="wq.question" @click="openReview(wq.id)">{{ wq.question }}</div>

        <div class="wq-meta">
          <span v-if="wq.questionType" class="meta-item">{{ wq.questionType }}</span>
          <span v-if="wq.chapter" class="meta-item">{{ wq.chapter }}</span>
          <span v-if="wq.difficulty" class="meta-item" :class="diffCls(wq.difficulty)">{{ wq.difficulty }}</span>
          <span v-if="wq.errorReason" class="meta-item err-reason">错因：{{ wq.errorReason }}</span>
          <span class="meta-item">错{{ wq.wrongCount ?? 1 }}次</span>
          <span v-if="wq.consecutiveCorrectCount" class="meta-item">连续答对 {{ wq.consecutiveCorrectCount }} 次</span>
          <span v-if="wq.analyzeStatus === 1" class="meta-item analyze-failed">AI 整理失败</span>
          <span class="meta-item due" :class="{ overdue: isDue(wq) }">下次：{{ formatDue(wq.nextReviewTime) }}</span>
        </div>

        <div class="wq-ops">
          <el-button size="small" type="success" @click="openReview(wq.id)">开始复习</el-button>
          <el-button size="small" type="primary" plain @click="openQuiz(wq)">AI 同类题</el-button>
          <el-button size="small" @click="openEdit(wq)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="remove(wq)">删除</el-button>
        </div>
      </el-card>
      <EmptyBox v-if="!loading && !list.length" description="暂无错题，点击「快速收录」收录第一道吧" />
    </div>

    <el-pagination
      v-model:current-page="pageNum"
      :total="total"
      :page-size="pageSize"
      layout="prev, pager, next"
      @current-change="load"
      class="pager"
    />

    <!-- 今日复习弹窗 -->
    <el-dialog v-model="todayVisible" title="📖 今日待复习" width="640px" append-to-body>
      <div v-if="!todayList.length" class="today-empty">
        <p>🎉 今日没有待复习的错题，很棒！</p>
        <el-button @click="todayVisible = false">收下夸奖</el-button>
      </div>
      <div v-else>
        <p class="today-hint">共 {{ todayList.length }} 道待复习，点击「开始复习」逐题攻克。</p>
        <div v-for="t in todayList" :key="t.id" class="today-item">
          <div class="today-q">{{ t.question }}</div>
          <div class="today-meta">
            <el-tag size="small">{{ t.subject || '待整理' }}</el-tag>
            <span class="meta-item">错{{ t.wrongCount ?? 1 }}次</span>
          </div>
          <el-button size="small" type="success" @click="startReviewFromToday(t.id)">开始复习</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑错题' : '收录错题'" width="620px" append-to-body>
      <el-form :model="editForm" label-width="76px">
        <el-form-item label="题目" required><el-input v-model="editForm.question" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="学科"><el-input v-model="editForm.subject" placeholder="留空=待整理" /></el-form-item>
        <el-form-item label="我的答案"><el-input v-model="editForm.myAnswer" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="正确答案"><el-input v-model="editForm.correctAnswer" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="解析"><el-input v-model="editForm.analysis" type="textarea" :rows="3" /></el-form-item>
        <el-row :gutter="8">
          <el-col :span="8"><el-form-item label="题型"><el-input v-model="editForm.questionType" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="章节"><el-input v-model="editForm.chapter" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="难度"><el-input v-model="editForm.difficulty" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="错误原因"><el-input v-model="editForm.errorReason" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="editForm.tag" /></el-form-item>
        <el-form-item label="知识点"><el-input v-model="editForm.knowledgePoints" /></el-form-item>
        <el-form-item label="笔记"><el-input v-model="editForm.note" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 子组件 -->
    <WrongQuickAdd v-model="quickAddVisible" :subjects="subjects" @saved="reload" />
    <WrongReviewDrawer
      v-model="reviewVisible"
      :question-id="reviewId"
      @done="onReviewDone"
      @quiz="openQuiz"
    />
    <WrongQuizDrawer v-model="quizVisible" :question="quizWq" @saved="reload" />
    <WrongOutlineDialog
      v-model="outlineVisible"
      :current-subject="subject"
      :selected-ids="selectedIds"
      :auto-mode="outlineAutoMode"
    />
    <WrongPlanDialog v-model="planVisible" :current-subject="subject" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import EmptyBox from '../../components/EmptyBox.vue'
import {
  listWrong, wrongSubjects, wrongStats, todayWrongs, weakPoints,
  createWrong, updateWrong, deleteWrong
} from '../../api/wrong'
import { WQ_STATUS, formatDue } from '../../utils/wrongbook'
import WrongQuickAdd from './components/WrongQuickAdd.vue'
import WrongReviewDrawer from './components/WrongReviewDrawer.vue'
import WrongQuizDrawer from './components/WrongQuizDrawer.vue'
import WrongOutlineDialog from './components/WrongOutlineDialog.vue'
import WrongPlanDialog from './components/WrongPlanDialog.vue'

const list = ref([])
const subjects = ref([])
const subject = ref('')
const statusFilter = ref('all')
const sort = ref('create_desc')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const stats = ref({ total: 0, pending: 0, mastered: 0, weekReviewCount: 0, todayPending: 0 })

const quickAddVisible = ref(false)
const reviewVisible = ref(false)
const reviewId = ref(null)
const quizVisible = ref(false)
const quizWq = ref(null)
const outlineVisible = ref(false)
const outlineAutoMode = ref('')
const planVisible = ref(false)

const weak = ref({ knowledgePoints: [], errorReasons: [], mostWrong: [] })

const selectedIds = ref([])

const todayVisible = ref(false)
const todayList = ref([])

const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({
  id: null, subject: '', tag: '', question: '', correctAnswer: '', analysis: '',
  myAnswer: '', errorReason: '', questionType: '', chapter: '',
  difficulty: '', knowledgePoints: '', note: ''
})

onMounted(() => {
  load()
  loadSubjects()
  loadStats()
  loadWeak()
})

function statusOf(s) {
  return WQ_STATUS.find((x) => x.value === s) || WQ_STATUS[0]
}

function isDue(wq) {
  if (wq.status === 0) return true
  if (!wq.nextReviewTime) return false
  return new Date(wq.nextReviewTime).getTime() <= Date.now()
}

function diffCls(d) {
  return d === '难' ? 'd-hard' : d === '中' ? 'd-mid' : ''
}

async function load() {
  loading.value = true
  try {
    const res = await listWrong({
      subject: subject.value || undefined,
      status: statusFilter.value === 'all' ? undefined : Number(statusFilter.value),
      sort: sort.value,
      pageNum: pageNum.value,
      pageSize
    })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadSubjects() {
  subjects.value = await wrongSubjects()
}

async function loadStats() {
  stats.value = await wrongStats()
}

async function loadWeak() {
  try {
    weak.value = await weakPoints()
  } catch (e) {
    // 统计失败不影响主流程
  }
}

function reload() {
  pageNum.value = 1
  load()
  loadSubjects()
  loadStats()
  loadWeak()
}

function selectSubject(s) {
  subject.value = s
  reload()
}

function toggleSelect(id) {
  const i = selectedIds.value.indexOf(id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(id)
}

// ---------- 今日复习 ----------
async function openTodayReview() {
  todayVisible.value = true
  todayList.value = await todayWrongs()
}

function startReviewFromToday(id) {
  todayVisible.value = false
  openReview(id)
}

function onReviewDone() {
  reload()
}

// ---------- 复习 / 同类题 ----------
function openReview(id) {
  reviewId.value = id
  reviewVisible.value = true
}

function openQuiz(wq) {
  quizWq.value = wq
  quizVisible.value = true
}

// ---------- AI 提纲 ----------
function openWeaknessReport() {
  outlineAutoMode.value = 'all'
  outlineVisible.value = true
}

function openOutlineFromSelected() {
  outlineAutoMode.value = 'selected'
  outlineVisible.value = true
}

// ---------- 编辑 / 删除 ----------
function openEdit(wq) {
  Object.assign(editForm, {
    id: wq.id, subject: wq.subject || '', tag: wq.tag || '',
    question: wq.question, correctAnswer: wq.correctAnswer || '',
    analysis: wq.analysis || '', myAnswer: wq.myAnswer || '',
    errorReason: wq.errorReason || '', questionType: wq.questionType || '',
    chapter: wq.chapter || '', difficulty: wq.difficulty || '',
    knowledgePoints: wq.knowledgePoints || '', note: wq.note || ''
  })
  editVisible.value = true
}

async function saveEdit() {
  if (!editForm.question || !editForm.question.trim()) {
    ElMessage.warning('题目不能为空')
    return
  }
  saving.value = true
  try {
    if (editForm.id) {
      await updateWrong(editForm.id, editForm)
    } else {
      await createWrong(editForm)
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function remove(wq) {
  await ElMessageBox.confirm('确定删除这道错题吗？删除后复习记录也会一并清除。', '提示', { type: 'warning' })
  await deleteWrong(wq.id)
  ElMessage.success('已删除')
  reload()
}
</script>

<style scoped>
.wrongbook {
  min-width: 0;
}

/* 渐变 Banner（对齐原型） */
.wrong-banner {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s-6);
  flex-wrap: wrap;
  padding: var(--s-7);
  border-radius: var(--r-xl);
  background: linear-gradient(120deg, oklch(36% 0.11 250) 0%, var(--info) 55%, var(--brand) 130%);
  color: #fff;
  margin-bottom: var(--s-5);
}
.wrong-banner::before {
  content: "";
  position: absolute;
  inset: 0;
  background: url('/images/wrong-bg.jpg') center/cover;
  opacity: 0.35;
  mix-blend-mode: overlay;
}
.banner-left {
  position: relative;
  z-index: 1;
}
.banner-left h1 {
  font-family: var(--font-display);
  font-size: var(--fs-h1);
  font-weight: 600;
  margin: 0 0 6px;
}
.banner-left p {
  color: rgba(255, 255, 255, 0.82);
  font-size: var(--fs-sm);
  margin-bottom: var(--s-5);
}
.banner-stats {
  display: flex;
  gap: var(--s-6);
  flex-wrap: wrap;
}
.banner-stats div b {
  font-family: var(--font-display);
  font-size: 1.7rem;
  font-weight: 600;
  display: block;
  line-height: 1.1;
}
.banner-stats div span {
  font-size: var(--fs-cap);
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 0.03em;
}

/* 勋章行 */
.medal-row {
  position: relative;
  z-index: 1;
  display: flex;
  gap: var(--s-3);
  flex-wrap: wrap;
}
.medal {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 108px;
  padding: var(--s-4) var(--s-3);
  border-radius: var(--r-lg);
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.22);
  backdrop-filter: blur(6px);
  font-weight: 700;
  font-size: var(--fs-sm);
  filter: grayscale(1);
  opacity: 0.65;
  transition: all .2s var(--ease-out);
}
.medal small {
  font-weight: 500;
  font-size: var(--fs-cap);
  color: rgba(255, 255, 255, 0.75);
}
.medal.on {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.4);
  filter: none;
  opacity: 1;
  transform: translateY(-2px);
}<style scoped>
.wrongbook {
  padding: 0 4px;
}
.stats {
  margin-bottom: 16px;
}
.stat-card {
  border-radius: 10px;
  padding: 14px 18px;
  color: #fff;
}
.stat-num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
}
.stat-label {
  font-size: 12px;
  opacity: 0.9;
  margin-top: 2px;
}
.c-total { background: linear-gradient(135deg, #409eff, #79bbff); }
.c-pending { background: linear-gradient(135deg, #f56c6c, #f89898); }
.c-mastered { background: linear-gradient(135deg, #67c23a, #95d475); }
.c-week { background: linear-gradient(135deg, #e6a23c, #ebb563); }
.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.weak-panel {
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 14px;
  font-size: 13px;
}
.weak-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #b88230;
}
.weak-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
}
.weak-row:last-child {
  margin-bottom: 0;
}
.weak-label {
  color: var(--ink-3, #909399);
  flex-shrink: 0;
}
.weak-tag {
  cursor: default;
}
.reason-item {
  background: #f4f4f5;
  border-radius: 4px;
  padding: 1px 8px;
  color: #606266;
}
.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.subject-filter {
  flex: 1;
  min-width: 200px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 14px;
  font-size: 13px;
}
.cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.wq-head {
  display: flex;
  align-items: center;
}
.wq-source {
  margin-left: auto;
  font-size: 12px;
  color: var(--ink-3, #909399);
}
.wq-q {
  margin: 10px 0 6px;
  font-size: 14px;
  color: var(--ink, #303133);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  cursor: pointer;
}
.wq-q:hover {
  color: #409eff;
}
.wq-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  color: var(--ink-3, #909399);
  margin-bottom: 8px;
}
.meta-item {
  background: #f4f4f5;
  border-radius: 4px;
  padding: 1px 6px;
}
.meta-item.due {
  background: transparent;
  padding: 0;
}
.meta-item.overdue {
  color: #f56c6c;
  font-weight: 600;
}
.err-reason {
  color: #e6a23c;
}
.meta-item.analyze-failed {
  color: #e6a23c;
  font-weight: 600;
}
.d-hard { color: #f56c6c; font-weight: 600; }
.d-mid { color: #e6a23c; }
.wq-ops {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.pager {
  display: flex;
  justify-content: center;
}
.today-empty {
  text-align: center;
  padding: 20px 0;
  color: var(--ink-3, #909399);
}
.today-hint {
  font-size: 13px;
  color: var(--ink-3, #909399);
  margin: 0 0 12px;
}
.today-item {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.today-q {
  flex: 1;
  font-size: 13px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.today-meta {
  display: flex;
  gap: 6px;
  align-items: center;
}
@media (max-width: 900px) {
  .cards {
    grid-template-columns: 1fr;
  }
}
</style>
