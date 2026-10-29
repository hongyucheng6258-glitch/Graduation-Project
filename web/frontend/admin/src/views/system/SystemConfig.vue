<template>
  <WtPageHeader title="系统配置" subtitle="在线修改系统参数，保存后立即生效，无需重启" eyebrow="管理后台" />

  <div v-loading="loading">
    <!-- 操作栏 -->
    <div class="toolbar">
      <el-radio-group v-model="activeCategory" @change="onCategoryChange">
        <el-radio-button label="">全部分组</el-radio-button>
        <el-radio-button v-for="c in categories" :key="c.key" :label="c.key">
          {{ c.label }}
        </el-radio-button>
      </el-radio-group>
      <div class="spacer" />
      <el-button @click="loadData">刷新</el-button>
      <el-button type="primary" :loading="saving" @click="saveAll">保存全部（即时生效）</el-button>
    </div>

    <!-- 配置表单：按分组渲染 -->
    <el-card v-for="group in groupedConfigs" :key="group.category" class="config-card">
      <template #header>
        <div class="card-header">
          <span class="group-title">{{ group.label }}</span>
          <el-tag size="small" type="info">{{ group.items.length }} 项</el-tag>
        </div>
      </template>
      <el-form label-width="200px" label-position="right">
        <el-form-item
          v-for="item in group.items"
          :key="item.configKey"
          :label="item.description || item.configKey"
        >
          <!-- bool 类型：开关 -->
          <el-switch
            v-if="item.valueType === 'bool'"
            v-model="formData[item.configKey]"
            active-value="true"
            inactive-value="false"
          />
          <!-- int/long 类型：数字输入 -->
          <el-input-number
            v-else-if="item.valueType === 'int' || item.valueType === 'long'"
            v-model="formData[item.configKey]"
            :controls-position="'right'"
            style="width: 220px"
          />
          <!-- double 类型：小数输入 -->
          <el-input-number
            v-else-if="item.valueType === 'double'"
            v-model="formData[item.configKey]"
            :precision="2"
            :step="0.1"
            :controls-position="'right'"
            style="width: 220px"
          />
          <!-- list 类型：文本域（逗号分隔） -->
          <el-input
            v-else-if="item.valueType === 'list'"
            v-model="formData[item.configKey]"
            type="textarea"
            :rows="2"
            placeholder="多个值用英文逗号分隔"
          />
          <!-- json 类型：文本域 -->
          <el-input
            v-else-if="item.valueType === 'json'"
            v-model="formData[item.configKey]"
            type="textarea"
            :rows="4"
            placeholder='JSON 格式，如 {"key":"value"}'
          />
          <!-- string 类型（默认）：单行输入 -->
          <el-input
            v-else
            v-model="formData[item.configKey]"
            :placeholder="`配置键：${item.configKey}`"
            style="max-width: 480px"
          />
          <div class="config-key-hint">键：{{ item.configKey }}</div>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 无数据提示 -->
    <el-empty v-if="!loading && filteredConfigs.length === 0" description="暂无配置项" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { getSystemConfig, updateSystemConfig } from '../../api/systemConfig'

const loading = ref(false)
const saving = ref(false)
const allConfigs = ref([])
const formData = reactive({})
const activeCategory = ref('')

// 分组定义（与后端 category 对应）
const categories = [
  { key: 'basic', label: '基础功能' },
  { key: 'site', label: '站点信息' },
  { key: 'audit', label: '内容审核' },
  { key: 'chat', label: '私信聊天' },
  { key: 'upload', label: '上传设置' },
  { key: 'security', label: '安全设置' }
]

const categoryLabel = (key) => {
  const found = categories.find((c) => c.key === key)
  return found ? found.label : key
}

// 当前筛选后的配置列表
const filteredConfigs = computed(() => {
  if (!activeCategory.value) return allConfigs.value
  return allConfigs.value.filter((c) => c.category === activeCategory.value)
})

// 按分组聚合（按 categories 定义的逻辑顺序排序，组内按 sort 排序）
const groupedConfigs = computed(() => {
  const map = new Map()
  for (const item of filteredConfigs.value) {
    if (!map.has(item.category)) {
      map.set(item.category, { category: item.category, label: categoryLabel(item.category), items: [] })
    }
    map.get(item.category).items.push(item)
  }
  // 组内按 sort 排序
  for (const group of map.values()) {
    group.items.sort((a, b) => (a.sort || 0) - (b.sort || 0))
  }
  // 按 categories 定义的顺序排序，未定义的分组排最后
  const orderMap = new Map(categories.map((c, i) => [c.key, i]))
  return Array.from(map.values()).sort((a, b) => {
    const ia = orderMap.has(a.category) ? orderMap.get(a.category) : 999
    const ib = orderMap.has(b.category) ? orderMap.get(b.category) : 999
    return ia - ib
  })
})

function onCategoryChange() {
  // 切换分组时不需要重新加载，computed 自动过滤
}

async function loadData() {
  loading.value = true
  try {
    const list = await getSystemConfig()
    allConfigs.value = list || []
    // 填充表单
    list.forEach((item) => {
      formData[item.configKey] = item.configValue ?? ''
    })
  } finally {
    loading.value = false
  }
}

async function saveAll() {
  // 只提交有变化的配置（简化：提交全部非空键）
  const payload = {}
  for (const item of allConfigs.value) {
    payload[item.configKey] = formData[item.configKey] ?? ''
  }
  saving.value = true
  try {
    await updateSystemConfig(payload)
    ElMessage.success('配置已保存并即时生效')
    await loadData()
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.spacer {
  flex: 1;
}
.config-card {
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.group-title {
  font-weight: 600;
  font-size: 15px;
  color: var(--ink);
}
.config-key-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-3);
  font-family: monospace;
}
</style>
