<template>
  <WtPageHeader title="AI 配置" subtitle="配置模型与调用参数" eyebrow="管理后台" />

  <div>
    <!-- 模型参数配置 -->
    <el-card v-loading="loading">
      <template #header>AI 参数配置</template>
      <el-form label-width="160px" style="max-width: 640px">
        <el-form-item label="模型服务地址">
          <el-input v-model="configs.base_url" placeholder="https://api.deepseek.com" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="configs.api_key" type="password" show-password
                    placeholder="留空则使用服务端 application.yml 配置" />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="configs.model_name" placeholder="deepseek-chat" />
        </el-form-item>
        <el-form-item label="temperature">
          <el-input-number v-model="temperatureNum" :min="0" :max="2" :step="0.1" />
        </el-form-item>
        <el-form-item label="最大输出 token">
          <el-input-number v-model="maxTokensNum" :min="256" :max="8192" :step="256" />
        </el-form-item>
        <el-form-item label="超时时间(ms)">
          <el-input-number v-model="timeoutNum" :min="5000" :max="300000" :step="5000" />
        </el-form-item>
        <el-form-item label="失败重试次数">
          <el-input-number v-model="retryNum" :min="0" :max="5" />
        </el-form-item>
        <el-form-item label="每用户每日限流">
          <el-input-number v-model="rateLimitNum" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 提示词模板管理 -->
    <el-card style="margin-top: 16px">
      <template #header>
        <div class="head">
          <span>提示词模板</span>
          <el-button type="primary" size="small" @click="openEdit()">＋ 新建模板</el-button>
        </div>
      </template>
      <el-table :data="prompts">
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
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 模板编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑模板' : '新建模板'" width="560px">
      <el-form label-width="80px">
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { ElMessage } from 'element-plus'
import { getAiConfig, updateAiConfig, listPrompts, createPrompt, updatePrompt } from '../../api/aiConfig'

const loading = ref(false)
const saving = ref(false)
const configs = reactive({})
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
    list.forEach((c) => { configs[c.configKey] = c.configValue })
    prompts.value = await listPrompts()
  } finally {
    loading.value = false
  }
})

async function save() {
  saving.value = true
  try {
    await updateAiConfig({ ...configs })
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
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
