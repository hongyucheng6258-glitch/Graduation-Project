<template>
  <div class="ai-assist">
    <div class="ai-assist-head">
      <span class="ai-badge">✨ AI 辅助发布</span>
      <span class="ai-sub">{{ typeName }}内容智能生成 / 润色</span>
    </div>

    <el-tabs v-model="tab" class="ai-tabs">
      <!-- ① 生成草稿 -->
      <el-tab-pane label="AI 生成草稿" name="compose">
        <div class="ai-row">
          <el-input
            v-model="topic"
            type="textarea"
            :rows="2"
            placeholder="一句话描述主题，如：周末图书馆读书分享会 / 出九成新教材 + 篮球"
          />
        </div>
        <div class="ai-row ai-extra">
          <el-input
            v-model="extra"
            placeholder="补充信息（时间地点价格等，可选）"
            clearable
          />
          <el-button type="primary" :loading="composing" @click="doCompose">生成</el-button>
        </div>

        <div v-if="draft" class="ai-result">
          <div class="ai-result-title"><b>标题：</b>{{ draft.title || '（未生成标题）' }}</div>
          <div class="ai-result-content">{{ draft.content }}</div>
          <div v-if="draft.tags && draft.tags.length" class="ai-tags">
            <el-tag v-for="t in draft.tags" :key="t" size="small" effect="plain">{{ t }}</el-tag>
          </div>
          <div class="ai-actions">
            <el-button size="small" type="success" @click="fillDraft">填入表单</el-button>
            <el-button size="small" text @click="draft = null">放弃</el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- ② 润色 -->
      <el-tab-pane label="AI 润色 / 扩写 / 精简" name="polish">
        <div class="ai-row">
          <el-input
            v-model="source"
            type="textarea"
            :rows="3"
            placeholder="粘贴已写好的内容，交给 AI 优化"
          />
        </div>
        <div class="ai-row ai-extra">
          <el-radio-group v-model="action" size="small">
            <el-radio-button value="polish">润色</el-radio-button>
            <el-radio-button value="expand">扩写</el-radio-button>
            <el-radio-button value="shorten">精简</el-radio-button>
          </el-radio-group>
          <el-button type="primary" :loading="polishing" @click="doPolish">开始</el-button>
        </div>

        <div v-if="polished" class="ai-result">
          <div class="ai-result-content">{{ polished }}</div>
          <div class="ai-actions">
            <el-button size="small" type="success" @click="fillPolish">填入表单</el-button>
            <el-button size="small" text @click="polished = ''">放弃</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiAssistCompose, aiAssistPolish } from '../api/ai'

const props = defineProps({
  type: { type: String, required: true },
  typeName: { type: String, default: '' }
})
const emit = defineEmits(['fill'])

const tab = ref('compose')
const topic = ref('')
const extra = ref('')
const composing = ref(false)
const draft = ref(null)
const source = ref('')
const action = ref('polish')
const polishing = ref(false)
const polished = ref('')

async function doCompose() {
  if (!topic.value.trim()) {
    ElMessage.warning('请先描述主题')
    return
  }
  composing.value = true
  draft.value = null
  try {
    const data = await aiAssistCompose({
      type: props.type,
      topic: topic.value.trim(),
      extra: extra.value.trim()
    })
    draft.value = data || null
    if (!draft.value) ElMessage.warning('AI 未生成内容，请重试')
  } catch (e) {
    ElMessage.error(e?.message || 'AI 生成失败，请稍后重试')
  } finally {
    composing.value = false
  }
}

function fillDraft() {
  if (!draft.value) return
  emit('fill', {
    title: draft.value.title || '',
    content: draft.value.content || '',
    tags: draft.value.tags || []
  })
  ElMessage.success('已填入表单，可继续微调')
}

async function doPolish() {
  if (!source.value.trim()) {
    ElMessage.warning('请先粘贴需要优化的内容')
    return
  }
  polishing.value = true
  polished.value = ''
  try {
    const text = await aiAssistPolish({
      type: props.type,
      content: source.value.trim(),
      action: action.value
    })
    polished.value = text || ''
    if (!polished.value) ElMessage.warning('AI 未返回内容，请重试')
  } catch (e) {
    ElMessage.error(e?.message || 'AI 润色失败，请稍后重试')
  } finally {
    polishing.value = false
  }
}

function fillPolish() {
  if (!polished.value) return
  emit('fill', { title: '', content: polished.value, tags: [] })
  ElMessage.success('已填入表单，可继续微调')
}
</script>

<style scoped>
.ai-assist {
  border: 1px dashed var(--brand, #00b578);
  border-radius: 12px;
  padding: 12px 14px 6px;
  margin-bottom: 16px;
  background: linear-gradient(120deg, rgba(0, 181, 120, 0.06), rgba(0, 181, 120, 0.02));
}
.ai-assist-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 4px;
}
.ai-badge {
  font-weight: 700;
  color: var(--brand, #00b578);
}
.ai-sub {
  font-size: 12px;
  color: var(--ink-3, #888);
}
.ai-row {
  margin-bottom: 8px;
}
.ai-extra {
  display: flex;
  gap: 8px;
  align-items: center;
}
.ai-result {
  border: 1px solid var(--surface-3, #eee);
  border-radius: 8px;
  padding: 10px 12px;
  background: #fff;
  margin-top: 4px;
}
.ai-result-title {
  margin-bottom: 6px;
}
.ai-result-content {
  font-size: 13px;
  color: var(--ink-2, #444);
  white-space: pre-wrap;
  line-height: 1.7;
}
.ai-tags {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.ai-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}
</style>
