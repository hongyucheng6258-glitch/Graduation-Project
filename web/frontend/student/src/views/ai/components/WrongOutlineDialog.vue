<template>
  <el-dialog v-model="visible" title="🤖 AI 生成复习提纲" width="680px" append-to-body>
    <!-- 三种生成方式 -->
    <div v-if="!loading && !error && !result" class="modes">
      <button
        class="mode-btn"
        :disabled="!currentSubject"
        @click="run({ mode: 'subject', subject: currentSubject })"
      >
        <span class="m-icon">📚</span>
        <span class="m-title">根据本学科错题生成</span>
        <span class="m-desc">
          {{ currentSubject ? `按当前筛选的「${currentSubject}」错题生成` : '请先在上方筛选一个学科' }}
        </span>
      </button>

      <button
        class="mode-btn"
        :disabled="!selectedIds.length"
        @click="run({ mode: 'selected', wrongQuestionIds: selectedIds })"
      >
        <span class="m-icon">✅</span>
        <span class="m-title">根据选中错题生成</span>
        <span class="m-desc">
          {{ selectedIds.length ? `已选中 ${selectedIds.length} 道错题` : '请先在列表中勾选错题' }}
        </span>
      </button>

      <button class="mode-btn" @click="run({ mode: 'all' })">
        <span class="m-icon">📊</span>
        <span class="m-title">生成全部错题薄弱点报告</span>
        <span class="m-desc">基于全部错题归纳高频知识点与复习重点</span>
      </button>
    </div>

    <!-- 加载中 -->
    <div v-else-if="loading" v-loading="true" class="loading-box" element-loading-text="AI 正在分析错题生成提纲，通常需要 20~40 秒…">
      <p>正在归纳错题中的高频知识点…</p>
      <el-button size="small" @click="cancel">取消</el-button>
    </div>

    <!-- 失败 -->
    <div v-else-if="error" class="err-box">
      <div class="err-icon">{{ error.icon }}</div>
      <h3>{{ error.title }}</h3>
      <p class="err-desc">{{ error.desc }}</p>
      <div class="err-ops">
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" @click="retry">↻ {{ error.action }}</el-button>
      </div>
    </div>

    <!-- 结果 -->
    <template v-else>
      <div class="result-head">
        <el-tag size="small" type="success">已生成</el-tag>
        <el-button size="small" text type="primary" @click="reset">重新选择生成方式</el-button>
      </div>
      <div class="md-body" v-html="renderMarkdown(result)" />
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { generateOutline } from '../../../api/ai'
import { renderMarkdown } from '../../../utils/markdown'
import { aiErrorInfo } from '../../../utils/wrongbook'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  currentSubject: { type: String, default: '' },
  selectedIds: { type: Array, default: () => [] },
  /** 打开时自动执行的模式（all=薄弱点报告），用于快捷入口 */
  autoMode: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const error = ref(null)
const result = ref('')
const lastPayload = ref(null)

watch(() => props.modelValue, (v) => {
  if (v && props.autoMode) {
    reset()
    if (props.autoMode === 'all') {
      run({ mode: 'all' })
    } else if (props.autoMode === 'selected') {
      run({ mode: 'selected', wrongQuestionIds: props.selectedIds })
    } else {
      run({ mode: props.autoMode })
    }
  }
})

async function run(payload) {
  if (loading.value) return
  lastPayload.value = payload
  loading.value = true
  error.value = null
  result.value = ''
  try {
    const res = await generateOutline(payload)
    result.value = res.answer
  } catch (err) {
    error.value = aiErrorInfo(err)
  } finally {
    loading.value = false
  }
}

function retry() {
  if (lastPayload.value) {
    run(lastPayload.value)
  }
}

function cancel() {
  visible.value = false
}

function reset() {
  error.value = null
  result.value = ''
  lastPayload.value = null
}
</script>

<style scoped>
.modes {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mode-btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;
}
.mode-btn:hover:not(:disabled) {
  border-color: #409eff;
  background: #ecf5ff;
}
.mode-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.m-icon {
  font-size: 22px;
}
.m-title {
  font-weight: 600;
  font-size: 14px;
}
.m-desc {
  font-size: 12px;
  color: var(--ink-3, #909399);
}
.loading-box {
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--ink-2, #606266);
}
.err-box {
  text-align: center;
  padding: 24px 0;
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
.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.md-body {
  max-height: 460px;
  overflow-y: auto;
  line-height: 1.7;
}
</style>
