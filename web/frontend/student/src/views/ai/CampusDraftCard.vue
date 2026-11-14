<template>
  <AssistantCard wide>
    <div class="draft-head">
      <h3>{{ config.title }}</h3>
      <el-tag size="small" round :type="draft.state === 'SUBMITTED' ? 'success' : 'info'">
        {{ draft.state === 'SUBMITTED' ? '已提交' : draft.state === 'EXPIRED' ? '已过期' : '可编辑' }}
      </el-tag>
    </div>
    <p v-if="draft.targetTitle">回复问题：{{ draft.targetTitle }}</p>
    <template v-if="!editing">
      <dl class="draft-fields">
        <div v-for="field in config.fields" :key="field.key">
          <dt>{{ field.label }}</dt>
          <dd>{{ display(field) }}</dd>
        </div>
      </dl>
      <p v-if="draft.state === 'SUBMITTED'" role="status">
        {{ statusText }}
        <RouterLink :to="resultRoute">查看{{ config.result }}</RouterLink>
      </p>
      <p v-else-if="draft.state === 'EXPIRED'" role="status">请在对话中重新生成草稿。</p>
      <el-button v-else type="primary" @click="edit">在卡片内编辑</el-button>
    </template>
    <el-form v-else label-position="top" class="draft-fields" @submit.prevent="submit">
      <el-form-item v-for="field in config.fields" :key="field.key" :label="field.label"
        :required="field.required" :class="{ wide: field.multiline }">
        <el-select v-if="field.options" v-model="form[field.key]" placeholder="请选择" :clearable="!field.required">
          <el-option v-for="[value, label] in field.options" :key="value" :label="label" :value="value" />
        </el-select>
        <el-date-picker v-else-if="field.date" v-model="form[field.key]" type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择时间" />
        <el-input v-else v-model.trim="form[field.key]" :type="field.multiline ? 'textarea' : 'text'"
          :rows="3" :maxlength="field.max" :required="field.required" />
      </el-form-item>
      <el-form-item v-if="draft.kind === 'lost'" label="物品图片" class="wide">
        <UploadImg v-model="images" :max="6" @uploaded="rememberImage" @uploading="uploading = $event" />
      </el-form-item>
      <div class="draft-actions wide">
        <el-button type="primary" native-type="submit" :loading="saving" :disabled="uploading">
          {{ config.audited ? '确认提交审核' : '确认发布' }}
        </el-button>
        <el-button :disabled="saving || uploading" @click="editing = false">取消编辑</el-button>
      </div>
    </el-form>
  </AssistantCard>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AssistantCard from '../../../../shared/assistant/AssistantCard.vue'
import UploadImg from '../../components/UploadImg.vue'
import { confirmCampusDraft } from '../../api/ai'

const props = defineProps({ draft: { type: Object, required: true } })
const emit = defineEmits(['submitted'])
const kinds = {
  lost: {
    title: '失物招领发布草稿', result: '失物招领', audited: true,
    fields: [
      { key: 'type', label: '类型', required: true, options: [[0, '寻物启事'], [1, '拾物招领']] },
      { key: 'title', label: '物品标题', required: true, max: 64 },
      { key: 'location', label: '地点', max: 128 },
      { key: 'happenTime', label: '丢失或拾获时间', date: true },
      { key: 'contact', label: '联系方式', max: 64 },
      { key: 'description', label: '物品描述', max: 2000, multiline: true }
    ]
  },
  partner: {
    title: '学习搭子发布草稿', result: '学习搭子', audited: true,
    fields: [
      { key: 'subject', label: '科目或领域', required: true, max: 64 },
      { key: 'goal', label: '学习目标', max: 128 },
      { key: 'schedule', label: '可搭时间', max: 255 },
      { key: 'contact', label: '联系方式', max: 64 },
      { key: 'intro', label: '自我介绍', max: 500, multiline: true }
    ]
  },
  question: {
    title: '互助提问草稿', result: '问题',
    fields: [
      { key: 'title', label: '问题标题', required: true, max: 128 },
      { key: 'category', label: '分类', options: ['课程', '考试', '技术', '生活', '其他'].map(name => [name, name]) },
      { key: 'content', label: '详细说明', max: 4000, multiline: true }
    ]
  },
  answer: {
    title: '互助回答草稿', result: '问题与回答',
    fields: [{ key: 'content', label: '回答内容', required: true, max: 4000, multiline: true }]
  }
}
const config = computed(() => kinds[props.draft.kind])
const statusText = computed(() => {
  if (!config.value.audited) return '已发布。'
  if (props.draft.auditStatus === 1) return '审核已通过。'
  if (props.draft.auditStatus === 2) return '审核未通过。'
  return '已提交，等待审核。'
})
const resultRoute = computed(() => {
  const d = props.draft
  if (d.kind === 'lost') return { name: 'LostFoundDetail', params: { id: d.submittedId } }
  if (d.kind === 'partner') return { name: 'PartnerList' }
  return { name: 'QaDetail', params: { id: d.targetId || d.submittedId } }
})
const form = ref({})
const editing = ref(false)
const saving = ref(false)
const uploading = ref(false)
const images = ref([])
const imageIds = new Map()

function display(field) {
  const value = props.draft.form[field.key]
  return field.options?.find(([option]) => option === value)?.[1] ?? (value || '待补充')
}

function edit() {
  form.value = { ...props.draft.form }
  images.value = []
  imageIds.clear()
  editing.value = true
}

function rememberImage({ url, resourceId }) {
  imageIds.set(url, resourceId)
}

async function submit() {
  if (saving.value || uploading.value) return
  const missing = config.value.fields.find(field => field.required && (form.value[field.key] == null || form.value[field.key] === ''))
  if (missing) {
    ElMessage.warning(`请填写或选择${missing.label}`)
    return
  }
  const data = { ...form.value }
  if (props.draft.kind === 'lost') data.imageIds = images.value.map(url => imageIds.get(url))
  saving.value = true
  try {
    emit('submitted', await confirmCampusDraft(props.draft.id, data))
    editing.value = false
  } catch (error) {
    ElMessage.error(error.message || '提交失败，请重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.draft-head, .draft-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.draft-head { justify-content: space-between; }
.draft-head h3 { margin: 4px 0; font-size: var(--fs-h3, 18px); }
.draft-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 14px; margin: 12px 0; }
dl.draft-fields { background: var(--surface-2); border-radius: 8px; padding: 10px 12px; }
dt { color: var(--ink-3); font-size: 12px; }
dd { margin: 3px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.wide { grid-column: 1 / -1; }
.draft-fields :deep(.el-date-editor) { width: 100%; min-width: 0; }
@media (max-width: 600px) {
  .draft-fields { grid-template-columns: minmax(0, 1fr); }
}
</style>
