<template>
  <AssistantCard wide :class="activity ? 'activity-draft-card' : 'idle-draft-card'">
    <div class="draft-head">
      <div>
        <span class="draft-label">{{ activity ? '活动发布草稿' : '闲置发布草稿' }}</span>
        <h3>{{ draft.title }}</h3>
      </div>
      <el-tag size="small" round :type="draft.state === 'SUBMITTED' ? 'success' : 'info'">
        {{ draft.state === 'SUBMITTED' ? '已提交' : draft.state === 'EXPIRED' ? '已过期' : '可编辑' }}
      </el-tag>
    </div>
    <template v-if="!editing">
      <dl class="draft-fields">
        <div v-for="[label, value] in details" :key="label"><dt>{{ label }}</dt><dd>{{ value || '待补充' }}</dd></div>
      </dl>
      <p v-if="draft.description" class="draft-description">{{ draft.description }}</p>
      <p v-if="draft.state === 'SUBMITTED'" role="status">
        {{ draft.auditStatus === 2 ? '审核未通过' : draft.auditStatus === 1 ? '审核通过' : '已提交，可查看审核进度' }}
        <template v-if="draft.auditStatus === 2 && draft.auditReason"> · {{ draft.auditReason }}</template>
        <RouterLink v-if="activity" :to="{ name: 'MySignup', query: { tab: 'published' } }">查看我的活动发布</RouterLink>
      </p>
      <p v-else-if="draft.state === 'EXPIRED'" role="status">请在对话中重新生成草稿。</p>
      <div v-else class="draft-actions">
        <el-button type="primary" @click="edit">在卡片内编辑</el-button>
        <RouterLink :to="fullEditor">{{ activity ? '编辑并提交活动' : '编辑并确认发布' }}</RouterLink>
      </div>
    </template>
    <el-form v-else label-position="top" class="draft-form" @submit.prevent="submit">
      <el-form-item label="标题"><el-input v-model.trim="form.title" maxlength="64" required /></el-form-item>
      <el-form-item label="分类">
        <el-select v-model="form.category" clearable placeholder="选择分类">
          <el-option v-for="category in categories" :key="category" :label="category" :value="category" />
        </el-select>
      </el-form-item>
      <el-form-item label="详细说明" class="wide-field">
        <el-input v-model="form.description" type="textarea" :rows="3" :maxlength="activity ? 4000 : 2000" />
      </el-form-item>
      <template v-if="activity">
        <el-form-item label="地点"><el-input v-model.trim="form.location" maxlength="128" required /></el-form-item>
        <el-form-item label="人数上限（0 为不限）"><el-input-number v-model="form.maxMembers" :min="0" :max="500" /></el-form-item>
        <el-form-item v-for="[key, label] in dateFields" :key="key" :label="label">
          <el-date-picker v-model="form[key]" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
      </template>
      <el-form-item v-else label="期望交换物品" class="wide-field">
        <el-input v-model.trim="form.expectItem" maxlength="128" />
      </el-form-item>
      <div class="draft-actions wide-field">
        <el-button type="primary" native-type="submit" :loading="saving">确认提交审核</el-button>
        <el-button :disabled="saving" @click="editing = false">取消编辑</el-button>
      </div>
    </el-form>
  </AssistantCard>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AssistantCard from '../../../../shared/assistant/AssistantCard.vue'
import { confirmActivityDraft, confirmIdleDraft } from '../../api/ai'

const props = defineProps({ draft: { type: Object, required: true }, kind: { type: String, required: true } })
const emit = defineEmits(['submitted'])
const activity = computed(() => props.kind === 'activity')
const fullEditor = computed(() => ({ name: activity.value ? 'ActivityPublish' : 'IdlePublish', query: { draft: props.draft.id } }))
const categories = computed(() => activity.value
  ? ['学习交流', '体育运动', '文艺娱乐', '志愿服务', '竞赛组队', '其他']
  : ['教材书籍', '数码电子', '生活用品', '运动器材', '服饰鞋包', '其他'])
const dateFields = [['startTime', '开始时间'], ['endTime', '结束时间'], ['signupDeadline', '报名截止']]
const displayTime = value => value?.replace('T', ' ').slice(0, 16)
const details = computed(() => {
  const d = props.draft
  return activity.value
    ? [['分类', d.category], ['地点', d.location], ...dateFields.map(([key, label]) => [label, displayTime(d[key])]),
        ['人数上限', d.maxMembers === 0 ? '不限' : d.maxMembers]]
    : [['分类', d.category], ['期望交换', d.expectItem]]
})
const editing = ref(false)
const saving = ref(false)
const form = reactive({})
function edit() {
  const d = props.draft
  Object.assign(form, { title: d.title, category: d.category || '', description: d.description || '' })
  if (activity.value) {
    Object.assign(form, { location: d.location || '', maxMembers: d.maxMembers ?? undefined })
    for (const [key] of dateFields) form[key] = d[key] ? d[key].replace('T', ' ').slice(0, 19) : null
  } else {
    form.expectItem = d.expectItem || ''
  }
  editing.value = true
}
async function submit() {
  if (saving.value || !form.title) return
  if (activity.value && (!form.location || !form.startTime || !form.endTime)) {
    ElMessage.warning('请补充活动地点、开始时间和结束时间')
    return
  }
  saving.value = true
  try {
    const result = activity.value
      ? await confirmActivityDraft(props.draft.id, { ...form, images: [] })
      : await confirmIdleDraft(props.draft.id, { ...form, imageIds: [] })
    emit('submitted', result)
    editing.value = false
    ElMessage.success('已提交审核')
  } catch (error) {
    ElMessage.error(error.message || '提交失败，请重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.draft-head, .draft-actions { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px 12px; }
.draft-head h3 { margin: 4px 0; font-size: var(--fs-h3, 18px); }
.draft-label, dt { color: var(--ink-3); font-size: 12px; }
.draft-fields, .draft-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 14px; margin: 12px 0; }
.draft-fields { padding: 10px 12px; border-radius: 8px; background: var(--surface-2); }
dd { margin: 3px 0 0; }
.draft-description { white-space: pre-wrap; }
.draft-actions { justify-content: flex-start; margin-top: 10px; }
.wide-field { grid-column: 1 / -1; }
.draft-form :deep(.el-form-item) { margin-bottom: 0; min-width: 0; }
.draft-form :deep(.el-date-editor), .draft-form :deep(.el-input-number) { width: 100%; }
@media (max-width: 520px) { .draft-form { grid-template-columns: 1fr; } }
</style>
