<template>
  <el-dialog v-model="visible" title="🤖 AI 生成今日复习计划" width="680px" append-to-body>
    <div v-if="!loading && !error && !result" class="empty">
      <p>将基于今日待复习错题生成个性化复习计划，点击开始生成。</p>
      <el-button type="primary" @click="run">开始生成</el-button>
    </div>

    <div v-else-if="loading" v-loading="true" class="loading-box" element-loading-text="AI 正在制定复习计划，通常需要 20~40 秒…">
      <p>正在分析今日待复习错题…</p>
      <el-button size="small" @click="visible = false">取消</el-button>
    </div>

    <div v-else-if="error" class="err-box">
      <div class="err-icon">{{ error.icon }}</div>
      <h3>{{ error.title }}</h3>
      <p class="err-desc">{{ error.desc }}</p>
      <div class="err-ops">
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" @click="run">↻ {{ error.action }}</el-button>
      </div>
    </div>

    <template v-else>
      <div class="result-head">
        <el-tag size="small" type="success">已生成 · {{ subjectLabel }}</el-tag>
        <el-button size="small" text type="primary" @click="run">重新生成</el-button>
      </div>
      <div class="md-body" v-html="renderMarkdown(result)" />
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { reviewPlan } from '../../../api/wrong'
import { renderMarkdown } from '../../../utils/markdown'
import { aiErrorInfo } from '../../../utils/wrongbook'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  currentSubject: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const error = ref(null)
const result = ref('')

const subjectLabel = computed(() => (props.currentSubject ? `学科：${props.currentSubject}` : '全部学科'))

watch(() => props.modelValue, (v) => {
  if (v) {
    error.value = null
    result.value = ''
    run()
  }
})

async function run() {
  if (loading.value) return
  loading.value = true
  error.value = null
  result.value = ''
  try {
    const res = await reviewPlan(props.currentSubject)
    result.value = res
  } catch (err) {
    error.value = aiErrorInfo(err)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.empty {
  text-align: center;
  padding: 24px 0;
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
