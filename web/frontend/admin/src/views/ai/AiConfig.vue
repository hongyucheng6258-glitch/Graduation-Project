<template>
  <WtPageHeader title="AI 配置" subtitle="服务地址、密钥、模型参数与提示词模板。" />

  <div>
    <el-form v-if="admin.permissions !== null" v-loading="loading" :disabled="!canEditConfig" class="model-form" label-position="top">
      <el-form-item label="服务地址">
        <el-input v-model="configs.base_url" placeholder="https://api.deepseek.com" />
      </el-form-item>
      <el-form-item label="API 密钥">
        <el-input v-model="newApiKey" type="password" autocomplete="new-password"
                  :disabled="loading || saving"
                  :placeholder="apiKeyConfigured ? '********' : '未配置'" />
        <small v-if="canEditConfig" class="api-key-hint">输入新密钥后保存，留空则保留当前配置。</small>
      </el-form-item>
      <el-form-item label="模型名称">
        <el-input v-model="configs.model_name" placeholder="deepseek-chat" />
      </el-form-item>
      <el-form-item label="温度 Temperature">
        <el-input-number v-model="temperatureNum" :min="0" :max="2" :step="0.1" />
      </el-form-item>
      <el-form-item label="最大输出 Token">
        <el-input-number v-model="maxTokensNum" :min="256" :max="8192" :step="256" />
      </el-form-item>
      <el-form-item label="超时时间（ms）">
        <el-input-number v-model="timeoutNum" :min="5000" :max="300000" :step="5000" />
      </el-form-item>
      <el-form-item label="失败重试次数">
        <el-input-number v-model="retryNum" :min="0" :max="5" />
      </el-form-item>
      <el-form-item label="每用户每日限流">
        <el-input-number v-model="rateLimitNum" :min="1" :max="1000" />
      </el-form-item>
    </el-form>
    <p v-else role="status">正在读取管理员权限…</p>

    <el-card class="prompt-panel" shadow="never">
      <div class="head">
        <h2>提示词模板</h2>
        <el-button v-if="canEditPrompt" size="small" @click="openEdit()">新建模板</el-button>
      </div>
      <el-table class="admin-list-table" :data="prompts">
        <el-table-column prop="scene" label="场景" width="110">
          <template #default="{ row }"><el-tag size="small">{{ row.scene }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="name" label="模板名" width="160" />
        <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canEditPrompt" label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div v-if="canEditConfig" class="config-actions">
      <el-button type="primary" :disabled="loading || !configReady" :loading="saving" @click="save">保存配置</el-button>
    </div>

    <el-dialog v-if="canEditPrompt" v-model="editVisible" :title="editForm.id ? '编辑模板' : '新建模板'" width="min(560px, calc(100vw - 32px))">
      <el-form label-position="top">
        <el-form-item label="场景">
          <el-select v-model="editForm.scene">
            <el-option label="答疑 chat" value="chat" />
            <el-option label="代码纠错 code_fix" value="code_fix" />
            <el-option label="PDF问答 pdf" value="pdf" />
            <el-option label="提纲 outline" value="outline" />
            <el-option label="习题 quiz" value="quiz" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板名"><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="内容">
          <el-input v-model="editForm.content" type="textarea" :rows="8"
                    placeholder="支持占位符：{question} {code} {language} {context} {subject} {topic} {answer}" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="editForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="savePrompt">保存</el-button>
      </template>
    </el-dialog>
    <AgentWorkspace v-if="agentEnabled" :rag-enabled="ragEnabled" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import AgentWorkspace from './AgentWorkspace.vue'
import request from '../../api/request'
import { useAdminStore } from '../../store/admin'
import { ElMessage } from 'element-plus'
import { getAiConfig, updateAiConfig, listPrompts, createPrompt, updatePrompt } from '../../api/aiConfig'

const admin = useAdminStore()
const canEditConfig = computed(() => admin.can('ai:config:edit'))
const canEditPrompt = computed(() => admin.can('ai:prompt:edit'))
const loading = ref(false)
const configReady = ref(false)
const agentEnabled = ref(false)
const ragEnabled = ref(false)
onMounted(async () => {
  try {
    const features = await request.get('/ai/agent/capabilities', { silent: true })
    agentEnabled.value = features.enabled
    ragEnabled.value = features.ragEnabled
  } catch { /* 旧版后端无需加载向导面板 */ }
})
const saving = ref(false)
const configs = reactive({})
const apiKeyConfigured = ref(false)
const newApiKey = ref('')
const prompts = ref([])
const editVisible = ref(false)
const editForm = reactive({ id: null, scene: 'chat', name: '', content: '', enabled: 1 })

// 数值型配置与字符串存储互转
const num = (key) => computed({
  get: () => Number(configs[key] || 0),
  set: (v) => { configs[key] = String(v) }
})
const temperatureNum = num('temperature')
const maxTokensNum = num('max_tokens')
const timeoutNum = num('timeout_ms')
const retryNum = num('retry_times')
const rateLimitNum = num('rate_limit_per_day')

onMounted(async () => {
  loading.value = true
  try {
    const list = await getAiConfig()
    for (const { configKey, configValue } of list) {
      if (configKey === 'api_key') apiKeyConfigured.value = Boolean(configValue)
      else configs[configKey] = configValue
    }
    configReady.value = true
    prompts.value = await listPrompts()
  } finally {
    loading.value = false
  }
})

async function save() {
  if (loading.value || !configReady.value || saving.value) return
  saving.value = true
  try {
    const key = newApiKey.value.trim()
    const updateKey = key && !/^\*+$/.test(key)
    await updateAiConfig({ ...configs, ...(updateKey ? { api_key: key } : {}) })
    if (updateKey) {
      newApiKey.value = ''
      const list = await getAiConfig()
      apiKeyConfigured.value = Boolean(list.find(config => config.configKey === 'api_key')?.configValue)
    }
    ElMessage.success('配置已保存并即时生效')
  } finally {
    saving.value = false
  }
}

function openEdit(row) {
  if (row) {
    Object.assign(editForm, row)
  } else {
    Object.assign(editForm, { id: null, scene: 'chat', name: '', content: '', enabled: 1 })
  }
  editVisible.value = true
}

async function savePrompt() {
  if (editForm.id) {
    await updatePrompt(editForm.id, editForm)
  } else {
    await createPrompt(editForm)
  }
  ElMessage.success('已保存')
  editVisible.value = false
  prompts.value = await listPrompts()
}
</script>

<style scoped>
.model-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  max-width: 820px;
}
.model-form :deep(.el-form-item) {
  min-width: 0;
  margin: 0;
}
.model-form :deep(.el-form-item__label) {
  color: var(--ink);
  font-size: var(--fs-sm);
  font-weight: 600;
}
.model-form :deep(.el-input-number) {
  width: 100%;
}
.model-form :deep(.el-input-number .el-input__inner) {
  text-align: start;
}
.api-key-hint {
  color: var(--ink-3);
}
.prompt-panel {
  margin-top: var(--s-5);
  border-radius: var(--r-md);
}
.prompt-panel .admin-list-table {
  border: 0;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--s-3);
  margin-bottom: var(--s-4);
}
.head h2 {
  font-size: var(--fs-body);
  font-weight: 650;
}
.config-actions {
  margin: 22px 0;
}
@media (max-width: 640px) {
  .model-form {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--s-4);
  }
}
</style>
