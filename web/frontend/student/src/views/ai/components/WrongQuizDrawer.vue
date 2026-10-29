<template>
  <el-drawer v-model="visible" title="🤖 AI 同类题练习" size="600px" append-to-body>
    <template v-if="question">
      <!-- 原题参考 -->
      <div class="ref">
        <b>原题：</b>{{ question.question }}
      </div>
      <p v-if="!question.correctAnswer && !question.analysis" class="warn">
        ⚠️ 这道题还没有正确答案和解析，AI 生成的练习题质量可能较低，建议先补充。
      </p>
    </template>

    <!-- 加载中 -->
    <div v-if="loading" class="state" v-loading="true" element-loading-text="AI 正在出题，通常需要 10~30 秒…">
      <div class="state-box">
        <p>正在生成同类练习题…</p>
        <el-button @click="visible = false">取消</el-button>
      </div>
    </div>

    <!-- 失败：原因 + 重试 -->
    <div v-else-if="error" class="state">
      <div class="err-box">
        <div class="err-icon">{{ error.icon }}</div>
        <h3>{{ error.title }}</h3>
        <p class="err-desc">{{ error.desc }}</p>
        <div class="err-ops">
          <el-button @click="visible = false">返回错题详情</el-button>
          <el-button type="primary" :loading="regenerating" @click="generate">↻ {{ error.action }}</el-button>
        </div>
      </div>
    </div>

    <!-- 练习模式 -->
    <div v-else-if="practice" class="practice">
      <div class="q-head">
        <el-tag size="small" type="primary">练习题</el-tag>
        <span v-if="practice.options.length" class="q-type">选择题</span>
        <span v-else class="q-type">作答练习</span>
      </div>
      <div class="q-text">{{ practice.question }}</div>

      <!-- 选项作答 -->
      <div v-if="practice.options.length" class="options">
        <label
          v-for="opt in practice.options"
          :key="opt"
          class="option"
          :class="{ picked: myAnswer === optionKey(opt), disabled: submitted }"
        >
          <input
            type="radio"
            name="practice-option"
            :value="optionKey(opt)"
            :disabled="submitted"
            v-model="myAnswer"
          />
          <span>{{ opt }}</span>
        </label>
      </div>
      <!-- 填空/简答作答 -->
      <el-input
        v-else
        v-model="myAnswer"
        type="textarea"
        :rows="3"
        placeholder="输入你的答案…"
        :disabled="submitted"
      />

      <!-- 结果反馈 -->
      <el-alert
        v-if="submitted && judged !== null"
        :type="judged ? 'success' : 'error'"
        :closable="false"
        show-icon
        class="judge-alert"
      >
        <template #title>
          {{ judged ? '✅ 答对了，很棒！' : '❌ 答错了，看看解析再巩固一下' }}
        </template>
        <p v-if="!judged" class="correct-ans">正确答案：{{ practice.answer }}</p>
      </el-alert>
      <el-alert
        v-else-if="submitted && judged === null"
        type="info"
        :closable="false"
        show-icon
        class="judge-alert"
        title="AI 未提供标准答案，请结合下方解析自行核对"
      />

      <!-- 解析 -->
      <div v-if="showAnalysis && practice.analysis" class="analysis">
        <div class="analysis-title">📖 解析</div>
        <div class="md-body" v-html="renderMarkdown(practice.analysis)" />
      </div>

      <!-- 操作 -->
      <div class="ops">
        <template v-if="!submitted">
          <el-button type="success" :disabled="!myAnswer.trim()" @click="submit">提交答案</el-button>
          <el-button @click="visible = false">返回</el-button>
        </template>
        <template v-else>
          <el-button @click="showAnalysis = !showAnalysis">
            {{ showAnalysis ? '收起解析' : '查看解析' }}
          </el-button>
          <el-button type="primary" :loading="regenerating" @click="generate">再生成一题</el-button>
          <el-button type="warning" plain :loading="saving" @click="saveToWrongBook">
            📥 保存到错题本
          </el-button>
        </template>
      </div>
      <p class="hint">AI 生成的练习题会先记录为练习，不会自动加入错题本；点击「保存到错题本」才会正式收录。</p>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { generatePractice, savePractice } from '../../../api/wrong'
import { renderMarkdown } from '../../../utils/markdown'
import { aiErrorInfo } from '../../../utils/wrongbook'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  question: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const regenerating = ref(false)
const saving = ref(false)
const error = ref(null)
const practice = ref(null)
const myAnswer = ref('')
const submitted = ref(false)
const judged = ref(false)
const showAnalysis = ref(false)

watch(() => props.modelValue, (v) => {
  if (v && props.question) {
    generate()
  }
})

/** 生成练习题（结构化） */
async function generate() {
  if (!props.question) return
  loading.value = true
  regenerating.value = true
  error.value = null
  practice.value = null
  myAnswer.value = ''
  submitted.value = false
  judged.value = false
  showAnalysis.value = false
  try {
    const res = await generatePractice(props.question.id)
    practice.value = {
      ...res,
      options: Array.isArray(res.options) ? res.options : []
    }
  } catch (err) {
    error.value = aiErrorInfo(err)
  } finally {
    loading.value = false
    regenerating.value = false
  }
}

/** 选项 key：取 "A. xxx" 中的 A */
function optionKey(opt) {
  const m = /^\s*([A-Ha-h])\s*[.、:：)]/.exec(opt || '')
  return m ? m[1].toUpperCase() : (opt || '').trim()
}

/** 判对错归一化：纯字母答案取字母；"A. 内容" 提取字母；文本答案全文比较 */
function normalizeAnswer(s) {
  const t = (s || '').trim().toUpperCase().replace(/\.+$/, '')
  const m = /^([A-H])\s*[.、:：)]/.exec(t)
  return m ? m[1] : t
}

function submit() {
  if (!myAnswer.value.trim()) {
    ElMessage.warning('请先作答')
    return
  }
  if (!practice.value.answer) {
    // AI 未给标准答案：不判对错，直接展示解析
    submitted.value = true
    judged.value = null
    showAnalysis.value = true
    return
  }
  judged.value = normalizeAnswer(myAnswer.value) === normalizeAnswer(practice.value.answer)
  submitted.value = true
}

/** 保存练习题到错题本（幂等，source=ai） */
async function saveToWrongBook() {
  saving.value = true
  try {
    await savePractice(practice.value.id)
    ElMessage.success('已保存到错题本')
    emit('saved')
    visible.value = false
  } catch (e) {
    // 错误已由全局拦截提示（重复保存也会提示）
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.ref {
  background: #f7f8fa;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 8px;
}
.warn {
  color: #e6a23c;
  font-size: 12px;
  margin: 0 0 12px;
}
.state {
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.state-box {
  text-align: center;
  color: var(--ink-2, #606266);
}
.err-box {
  text-align: center;
  max-width: 420px;
}
.err-icon {
  font-size: 40px;
  margin-bottom: 8px;
}
.err-box h3 {
  margin: 0 0 8px;
}
.err-desc {
  color: var(--ink-3, #909399);
  font-size: 13px;
  line-height: 1.7;
  margin: 0 0 16px;
}
.err-ops {
  display: flex;
  justify-content: center;
  gap: 8px;
}
.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.q-type {
  font-size: 12px;
  color: var(--ink-3, #909399);
}
.q-text {
  font-size: 15px;
  line-height: 1.7;
  margin-bottom: 14px;
  white-space: pre-wrap;
}
.options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}
.option {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}
.option.picked {
  border-color: #409eff;
  background: #ecf5ff;
}
.option.disabled {
  cursor: default;
}
.judge-alert {
  margin-bottom: 12px;
}
.correct-ans {
  margin: 6px 0 0;
  font-size: 13px;
}
.analysis {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
  padding: 10px 12px;
  margin-bottom: 12px;
}
.analysis-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.md-body {
  line-height: 1.7;
  font-size: 13px;
}
.ops {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}
.hint {
  font-size: 12px;
  color: var(--ink-3, #909399);
  margin-top: 10px;
}
</style>
