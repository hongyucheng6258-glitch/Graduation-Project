<template>
  <div class="signup-conflict" aria-live="polite">
    <p v-if="checking">正在检查日程冲突…</p>
    <p v-else-if="failed">暂时无法检查日程，你仍可提交报名。</p>
    <p v-else-if="result && !result.timeKnown">活动时间待定，暂时无法检查冲突。</p>
    <el-alert v-else-if="result?.conflicts.length" type="warning" :closable="false" show-icon title="与已通过的报名时间重叠">
      <ul>
        <li v-for="conflict in result.conflicts" :key="conflict.activityId">
          {{ conflict.title }}（{{ overlapText(conflict.overlapSeconds) }}）
        </li>
      </ul>
      <p>你仍可确认提交报名。</p>
    </el-alert>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { signupConflicts } from '../../../api/activity'

const props = defineProps({ activityId: { type: [Number, String], required: true } })
const emit = defineEmits(['checking'])
const checking = ref(true)
const failed = ref(false)
const result = ref(null)

watch(() => props.activityId, async (activityId, _, onCleanup) => {
  const request = new AbortController()
  onCleanup(() => request.abort())
  checking.value = true
  failed.value = false
  result.value = null
  emit('checking', true)
  try {
    result.value = await signupConflicts(activityId, request.signal)
  } catch {
    if (!request.signal.aborted) failed.value = true
  } finally {
    if (!request.signal.aborted) {
      checking.value = false
      emit('checking', false)
    }
  }
}, { immediate: true })

function overlapText(seconds) {
  if (seconds < 60) return '重叠不足 1 分钟'
  return `重叠 ${Math.floor(seconds / 60)} 分钟`
}
</script>

<style scoped>
.signup-conflict { font-size: 0.875rem; }
.signup-conflict:not(:empty) { margin-bottom: 12px; }
.signup-conflict ul { margin: 4px 0; padding-inline-start: 1.25rem; }
.signup-conflict p { margin: 4px 0; }
</style>
