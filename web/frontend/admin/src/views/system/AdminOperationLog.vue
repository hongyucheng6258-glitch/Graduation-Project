<template>
  <WtPageHeader title="操作日志" />
  <form class="admin-list-toolbar" aria-label="筛选操作日志" @submit.prevent="search">
    <el-input v-model="filters.username" aria-label="操作账号" placeholder="操作账号" clearable maxlength="64" />
    <el-select v-model="filters.module" aria-label="业务模块" placeholder="全部模块" clearable>
      <el-option v-for="(label, key) in modules" :key="key" :label="label" :value="key" />
    </el-select>
    <el-input v-model="filters.targetId" aria-label="记录 ID" placeholder="记录 ID" clearable maxlength="19" />
    <el-select v-model="filters.succeeded" aria-label="执行结果" placeholder="全部结果" clearable>
      <el-option label="成功" value="true" />
      <el-option label="失败" value="false" />
    </el-select>
    <el-date-picker v-model="filters.period" type="datetimerange" start-placeholder="开始时间"
      end-placeholder="结束时间" value-format="YYYY-MM-DDTHH:mm:ss"
      :aria-label="['开始时间', '结束时间']" />
    <el-button type="primary" native-type="submit">查询</el-button>
    <el-button @click="reset">重置</el-button>
    <el-button :disabled="loading" @click="load">刷新</el-button>
  </form>
  <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon class="load-error" />
  <el-table class="admin-list-table" :data="rows" v-loading="loading"
    :empty-text="loadError ? '日志加载失败' : '暂无操作日志'">
    <el-table-column label="时间" width="170">
      <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
    </el-table-column>
    <el-table-column prop="adminUsername" label="操作账号" min-width="115" show-overflow-tooltip />
    <el-table-column label="模块" width="100">
      <template #default="{ row }">{{ modules[row.module] || row.module }}</template>
    </el-table-column>
    <el-table-column label="操作" min-width="135">
      <template #default="{ row }">{{ actions[row.action] || row.action }}</template>
    </el-table-column>
    <el-table-column label="记录" min-width="135">
      <template #default="{ row }">
        {{ row.targetId ? [types[row.targetType], row.targetId].filter(Boolean).join(' · ') : '—' }}
      </template>
    </el-table-column>
    <el-table-column label="来源" width="100">
      <template #default="{ row }">{{ row.source === 'assistant' ? '管理助手' : '管理端' }}</template>
    </el-table-column>
    <el-table-column label="结果" width="108">
      <template #default="{ row }">
        <el-tag size="small" :type="row.succeeded ? 'success' : 'danger'">
          {{ row.succeeded ? '成功' : `失败 (${row.resultCode})` }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="耗时" width="92">
      <template #default="{ row }">{{ row.durationMs }} ms</template>
    </el-table-column>
  </el-table>
  <el-pagination v-if="total > pageSize" class="admin-list-pagination" v-model:current-page="page"
    :total="total" :page-size="pageSize" :pager-count="5" layout="total, prev, pager, next"
    @current-change="load" />
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../api/request'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { formatTime } from '../../utils/date'
import { searchRecordId } from '../../utils/searchQuery.mjs'

const modules = { audit: '内容审核', report: '举报', user: '用户', notice: '公告', ai: 'AI 配置', activity: '活动', system: '系统配置' }
const actions = {
  pass: '审核通过', reject: '审核驳回', 'confirm-review': '确认审核', handle: '处置举报',
  'update-status': '更新账号状态', 'reset-password': '重置密码', create: '新建公告', update: '编辑公告',
  publish: '发布公告', offline: '下线公告', delete: '删除公告', 'confirm-draft': '保存公告草稿',
  'update-config': '修改配置', 'create-config': '新建配置', 'delete-config': '删除配置', 'refresh-config': '刷新配置',
  'create-prompt': '新建提示词', 'update-prompt': '编辑提示词', 'export-members': '导出报名名单',
  'export-signins': '导出签到名单'
}
const types = { activity: '活动', idle: '闲置', post: '动态', lostfound: '失物招领', partner: '搭子' }
const initialFilters = { username: '', module: '', targetId: '', succeeded: '', period: null }
const filters = reactive({ ...initialFilters })
const rows = ref([])
const page = ref(1)
const total = ref(0)
const pageSize = 10
const loading = ref(false)
const loadError = ref('')
let applied = {}
let version = 0

function search() {
  const rawId = filters.targetId.trim()
  const targetId = searchRecordId(rawId)
  if (rawId && !targetId) {
    ElMessage.warning('请输入有效的记录 ID')
    return
  }
  applied = {
    username: filters.username.trim() || undefined,
    module: filters.module || undefined,
    targetId,
    succeeded: filters.succeeded || undefined,
    from: filters.period?.[0],
    to: filters.period?.[1]
  }
  page.value = 1
  load()
}

function reset() {
  Object.assign(filters, initialFilters)
  search()
}

async function load() {
  const current = ++version
  loading.value = true
  loadError.value = ''
  try {
    const result = await request.get('/system/operation-log', { params: { ...applied, pageNum: page.value, pageSize } })
    if (current !== version) return
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    if (current !== version) return
    rows.value = []
    loadError.value = error.message || '暂时无法读取操作日志'
  } finally {
    if (current === version) loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.admin-list-toolbar > .el-input,
.admin-list-toolbar > .el-select { width: 140px; }
.load-error { margin-bottom: 12px; }
</style>
