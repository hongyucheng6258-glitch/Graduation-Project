<template>
  <el-card class="agent-workspace" shadow="never">
    <template #header>
      <div class="agent-header">
        <div><strong>校园向导工作台</strong><p>发布校园资料，管理向导提示词和查看运行记录。</p></div>
        <el-button :loading="busy" @click="load">刷新</el-button>
      </div>
    </template>
    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane v-if="ragEnabled" name="knowledge" label="公共知识库">
        <el-upload accept=".pdf" :show-file-list="false" :http-request="upload">
          <el-button type="primary" :loading="busy">上传并发布 PDF</el-button>
        </el-upload>
        <el-table :data="documents" empty-text="暂无公共资料">
          <el-table-column prop="fileName" label="资料名称" min-width="180">
            <template #default="{ row }">
              <button type="button" class="knowledge-file" @click="openPreview(row)">{{ row.fileName }}</button>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="125">
            <template #default="{ row }">{{ states[row.state] || row.state }} {{ row.error || '' }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="205">
            <template #default="{ row }">
              <div class="knowledge-actions">
                <el-button text type="primary" @click="openPreview(row)">预览 PDF</el-button>
                <el-button v-if="['READY', 'FAILED'].includes(row.state)" text @click="reindex(row)">重建索引</el-button>
                <el-button text type="danger" @click="withdraw(row)">撤回</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane name="prompts" label="提示词版本">
        <div class="agent-header">
          <el-select v-model="scene" style="max-width: 220px" @change="load">
            <el-option label="校园向导" value="campus_agent" />
            <el-option label="资料问答" value="knowledge_answer" />
          </el-select>
          <el-button text @click="publish(0)">恢复内置版本</el-button>
        </div>
        <el-input v-model="draft" type="textarea" :rows="4" maxlength="4000" show-word-limit
          placeholder="填写这个场景的术语、风格或回答偏好" />
        <el-button :disabled="!draft.trim()" :loading="busy" class="agent-action" @click="create">保存草稿</el-button>
        <el-table :data="versions" empty-text="当前使用内置版本">
          <el-table-column prop="id" label="版本" width="90" />
          <el-table-column prop="content" label="内容" min-width="180" show-overflow-tooltip />
          <el-table-column label="发布" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.active" type="success">当前版本</el-tag>
              <el-button v-else text type="primary" @click="publish(row.id)">启用此版本</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane name="runs" label="运行记录">
        <el-table :data="runs" empty-text="暂无运行记录">
          <el-table-column prop="created_at" label="时间" min-width="155" />
          <el-table-column prop="model_name" label="模型" min-width="120" />
          <el-table-column prop="prompt_version" label="版本" width="90" />
          <el-table-column prop="tools" label="执行工具" min-width="150" show-overflow-tooltip />
          <el-table-column prop="elapsed_ms" label="耗时（毫秒）" width="115" />
          <el-table-column prop="status" label="结果" width="85" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="previewVisible" :title="previewDocument?.fileName || '预览 PDF'"
      width="min(1000px, calc(100vw - 24px))" top="5vh" append-to-body destroy-on-close @close="resetPreview">
      <div class="pdf-preview">
        <p v-if="previewLoading" class="preview-message" role="status">正在加载 PDF…</p>
        <div v-else-if="previewError" class="preview-message">
          <p role="alert">{{ previewError }}</p>
          <el-button @click="openPreview(previewDocument)">重试</el-button>
        </div>
        <iframe v-else-if="previewUrl" class="pdf-frame" :src="previewUrl" :title="previewDocument?.fileName" />
      </div>
      <template v-if="previewUrl" #footer>
        <a :href="previewUrl" :download="previewDocument?.fileName" class="pdf-download">下载原件</a>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { publicKnowledgeFile } from '../../api/assistant'

const root = '/ai/agent'
const states = { QUEUED: '等待索引', INDEXING: '索引中', READY: '已发布', FAILED: '索引失败' }
const props = defineProps({ ragEnabled: Boolean })
const tab = ref(props.ragEnabled ? 'knowledge' : 'prompts')
const scene = ref('campus_agent')
const draft = ref('')
const busy = ref(false)
const documents = ref([])
const versions = ref([])
const runs = ref([])
const previewVisible = ref(false)
const previewDocument = ref(null)
const previewUrl = ref('')
const previewLoading = ref(false)
const previewError = ref('')
let previewRequest
onMounted(load)
onBeforeUnmount(resetPreview)

function resetPreview() {
  previewRequest?.abort()
  previewRequest = undefined
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  previewError.value = ''
  previewLoading.value = false
}

async function openPreview(row) {
  resetPreview()
  previewDocument.value = row
  previewVisible.value = true
  previewLoading.value = true
  const controller = new AbortController()
  previewRequest = controller
  try {
    const pdf = await publicKnowledgeFile(row.id, controller.signal)
    if (previewRequest === controller) previewUrl.value = URL.createObjectURL(pdf)
  } catch {
    if (previewRequest === controller) previewError.value = '无法预览此资料，请确认文件仍可访问后重试。'
  } finally {
    if (previewRequest === controller) {
      previewRequest = undefined
      previewLoading.value = false
    }
  }
}

async function load() {
  busy.value = true
  try {
    if (tab.value === 'knowledge') documents.value = await request.get(`${root}/knowledge`)
    else if (tab.value === 'prompts') versions.value = await request.get(`${root}/prompts/${scene.value}`)
    else runs.value = await request.get(`${root}/runs`)
  } finally {
    busy.value = false
  }
}

async function upload({ file }) {
  const form = new FormData()
  form.append('file', file)
  busy.value = true
  try {
    await request.post(`${root}/knowledge`, form)
    ElMessage.success('已加入索引队列。索引完成后，学生可检索此资料。')
    await load()
  } finally {
    busy.value = false
  }
}

async function withdraw(row) {
  await ElMessageBox.confirm(`撤回后，学生将无法检索《${row.fileName}》。`, '撤回校园资料')
  await request.delete(`${root}/knowledge/${row.id}`)
  await load()
}

async function reindex(row) {
  await request.post(`${root}/knowledge/${row.id}/reindex`)
  await load()
}

async function create() {
  busy.value = true
  try {
    await request.post(`${root}/prompts/${scene.value}`, { content: draft.value })
    draft.value = ''
    await load()
  } finally {
    busy.value = false
  }
}

async function publish(id) {
  await ElMessageBox.confirm('新的提示词版本会用于之后的请求。', '切换提示词版本')
  await request.put(`${root}/prompts/${scene.value}/${id}`)
  await load()
}
</script>

<style scoped>
.agent-workspace { margin-block: 20px; }
.agent-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 8px; }
.agent-header p { margin: 5px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.agent-action { margin-block: 10px; }
.knowledge-actions { display: flex; flex-wrap: wrap; gap: 2px 4px; }
.knowledge-actions .el-button { margin: 0; padding-inline: 5px; }
.knowledge-file { padding: 0; border: 0; background: transparent; color: var(--brand-strong); text-align: start; font: inherit; cursor: pointer; overflow-wrap: anywhere; }
.knowledge-file:hover { text-decoration: underline; }
.pdf-preview { min-height: 140px; }
.pdf-frame { display: block; width: 100%; height: 68dvh; border: 1px solid var(--line); border-radius: 8px; }
.preview-message { padding: 22px 4px; color: var(--ink-2); text-align: center; }
.preview-message p { margin: 0 0 12px; }
.pdf-download { color: var(--brand-strong); }
</style>
