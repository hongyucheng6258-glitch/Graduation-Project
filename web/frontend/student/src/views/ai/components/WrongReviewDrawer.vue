<template>
  <el-drawer
    v-model="visible"
    :title="title"
    size="560px"
    append-to-body
    :destroy-on-close="false"
  >
    <div v-loading="loading">
      <template v-if="wq">
        <!-- 题目区 -->
        <div class="sec">
          <div class="sec-title">📌 题目</div>
          <div class="q-text">{{ wq.question }}</div>
          <img v-if="wq.questionImage" :src="wq.questionImage" class="q-img" alt="题目图片" />
          <div class="q-tags">
            <el-tag v-if="wq.subject" size="small">{{ wq.subject }}</el-tag>
            <el-tag v-if="wq.questionType" size="small" type="info">{{ wq.questionType }}</el-tag>
            <el-tag v-if="wq.chapter" size="small" type="info">{{ wq.chapter }}</el-tag>
            <el-tag v-if="wq.difficulty" size="small" type="warning">{{ wq.difficulty }}</el-tag>
            <el-tag v-if="wq.errorReason" size="small" type="danger">{{ wq.errorReason }}</el-tag>
          </div>
        </div>

        <!-- 我的错误 -->
        <div class="sec">
          <div class="sec-title">✍️ 我的错误</div>
          <template v-if="wq.myAnswer || wq.correctAnswer || wq.analysis">
            <p class="kv"><b>我的答案：</b>{{ wq.myAnswer || '（未填写）' }}</p>
            <p class="kv"><b>正确答案：</b>{{ wq.correctAnswer || '（未填写）' }}</p>
            <p class="kv"><b>解析：</b>{{ wq.analysis || '（未填写）' }}</p>
          </template>
          <el-empty v-else :image-size="60" description="还未补充答案与解析" />
        </div>

        <!-- AI 辅助：智能整理状态 + 讲解 -->
        <div class="sec">
          <div class="sec-title">🤖 AI 辅助</div>
          <div v-if="wq.analyzeStatus === 1" class="ai-line">
            <span class="ai-status failed">暂未完成智能整理</span>
            <el-button size="small" link type="primary" :loading="analyzing" @click="retryAnalyze">重新整理</el-button>
          </div>
          <div v-else-if="wq.analyzeStatus === 0" class="ai-line">
            <span class="ai-status">未智能整理</span>
            <el-button size="small" link type="primary" :loading="analyzing" @click="retryAnalyze">AI 整理</el-button>
          </div>
          <div class="ai-line">
            <el-button size="small" type="primary" plain :loading="explaining" @click="explain">
              🤖 AI 讲解这道题
            </el-button>
            <el-button size="small" plain @click="emit('quiz', wq)">生成同类题</el-button>
          </div>
          <div v-if="explainResult" class="explain-body" v-html="renderMarkdown(explainResult)" />
          <div v-else-if="explainError" class="explain-err">
            <p><b>{{ explainError.title }}</b>：{{ explainError.desc }}</p>
            <el-button size="small" type="primary" @click="explain">↻ {{ explainError.action }}</el-button>
          </div>
        </div>

        <!-- 复习反馈 -->
        <div class="sec">
          <div class="sec-title">🔁 本次复习反馈</div>
          <p class="hint">可以看着正确答案回忆，然后选择掌握程度；系统将自动安排下次复习。</p>
          <el-input
            v-model="userAnswer"
            type="textarea"
            :rows="2"
            placeholder="本次作答（可选）：把答案写下来印象更深"
            style="margin-bottom: 12px"
          />
          <div class="levels">
            <button
              v-for="lv in MASTERY_LEVELS"
              :key="lv.value"
              class="level-btn"
              :class="{ active: level === lv.value }"
              @click="level = lv.value"
            >
              <span class="lv-icon">{{ lv.icon }}</span>
              <span class="lv-label">{{ lv.label }}</span>
              <span class="lv-desc">{{ lv.desc }}</span>
            </button>
          </div>
        </div>

        <!-- 提交结果 -->
        <el-alert
          v-if="result"
          :type="result.isCorrect ? 'success' : 'warning'"
          :closable="false"
          show-icon
          class="result-alert"
        >
          <template #title>
            {{ result.isCorrect ? '复习完成，状态已更新' : '已记录，这道题还需要再巩固' }}
            · {{ result.nextText }}
          </template>
        </el-alert>

        <div class="drawer-footer">
          <el-button @click="visible = false">关闭</el-button>
          <el-button v-if="!result" type="success" :loading="submitting" @click="submit">提交复习反馈</el-button>
          <el-button v-else type="success" @click="finish">完成</el-button>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getWrong, submitReview, explainWrong, analyzeWrong } from '../../../api/wrong'
import { renderMarkdown } from '../../../utils/markdown'
import { MASTERY_LEVELS, formatDue, aiErrorInfo } from '../../../utils/wrongbook'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  questionId: { type: Number, default: null }
})
const emit = defineEmits(['update:modelValue', 'done', 'quiz'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const wq = ref(null)
const loading = ref(false)
const submitting = ref(false)
const userAnswer = ref('')
const level = ref(null)
const result = ref(null)

// AI 讲解
const explaining = ref(false)
const explainResult = ref('')
const explainError = ref(null)
const analyzing = ref(false)

const title = computed(() => (wq.value ? '错题详情 · 复习' : '错题详情'))

watch(() => props.modelValue, (v) => {
  if (v && props.questionId) {
    load()
  }
})

async function load() {
  loading.value = true
  level.value = null
  userAnswer.value = ''
  result.value = null
  explainResult.value = ''
  explainError.value = null
  try {
    wq.value = await getWrong(props.questionId)
  } finally {
    loading.value = false
  }
}

/** AI 讲解这道题（错因分析 + 知识点讲解），失败可重试 */
async function explain() {
  if (!wq.value) return
  explaining.value = true
  explainError.value = null
  explainResult.value = ''
  try {
    const res = await explainWrong(wq.value.id)
    explainResult.value = res
  } catch (err) {
    explainError.value = aiErrorInfo(err)
  } finally {
    explaining.value = false
  }
}

/** 重试/触发 AI 智能整理，成功后刷新详情 */
async function retryAnalyze() {
  if (!wq.value) return
  analyzing.value = true
  try {
    const updated = await analyzeWrong(wq.value.id)
    wq.value = updated
    ElMessage.success('智能整理完成')
    emit('done', updated)
  } catch (err) {
    ElMessage.warning('暂未完成智能整理，可稍后重试')
    wq.value = await getWrong(wq.value.id)
  } finally {
    analyzing.value = false
  }
}

async function submit() {
  if (level.value === null) {
    ElMessage.warning('请选择掌握程度')
    return
  }
  submitting.value = true
  try {
    const updated = await submitReview({
      wrongQuestionId: wq.value.id,
      userAnswer: userAnswer.value || undefined,
      masteryLevel: level.value,
      isCorrect: level.value >= 2 ? 1 : 0
    })
    result.value = {
      isCorrect: level.value >= 2,
      nextText: formatDue(updated.nextReviewTime)
    }
    ElMessage.success('复习反馈已记录')
    emit('done', updated)
  } finally {
    submitting.value = false
  }
}

function finish() {
  visible.value = false
}
</script>

<style scoped>
.sec {
  margin-bottom: 20px;
}
.sec-title {
  font-weight: 600;
  margin-bottom: 8px;
  font-size: 14px;
}
.q-text {
  white-space: pre-wrap;
  line-height: 1.7;
  margin-bottom: 8px;
}
.q-img {
  max-width: 100%;
  max-height: 220px;
  border-radius: 6px;
  margin-bottom: 8px;
}
.q-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.kv {
  margin: 4px 0;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.hint {
  font-size: 12px;
  color: var(--ink-3, #909399);
  margin: 0 0 10px;
}
.ai-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.ai-status {
  font-size: 12px;
  color: var(--ink-3, #909399);
}
.ai-status.failed {
  color: #e6a23c;
}
.explain-body {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
  background: #fafafa;
  line-height: 1.7;
  font-size: 13px;
}
.explain-err {
  border: 1px solid #fde2e2;
  border-radius: 8px;
  background: #fef0f0;
  padding: 10px 12px;
  color: #f56c6c;
  font-size: 13px;
}
.explain-err p {
  margin: 0 0 8px;
}
.levels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.level-btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 10px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;
}
.level-btn:hover {
  border-color: #409eff;
}
.level-btn.active {
  border-color: #409eff;
  background: #ecf5ff;
}
.lv-icon {
  font-size: 18px;
}
.lv-label {
  font-weight: 600;
  font-size: 13px;
}
.lv-desc {
  font-size: 11px;
  color: var(--ink-3, #909399);
}
.result-alert {
  margin-bottom: 16px;
}
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
</style>
