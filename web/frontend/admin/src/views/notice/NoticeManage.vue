<template>
  <WtPageHeader title="系统公告" subtitle="公告的新建、编辑、发布、下线与删除。">
    <el-button type="primary" @click="openEditor()">新建公告</el-button>
  </WtPageHeader>

  <div class="admin-list-toolbar">
    <el-select :model-value="status" aria-label="公告状态" style="width: 140px" @change="changeStatus">
      <el-option label="全部状态" value="" />
      <el-option label="草稿" value="0" />
      <el-option label="已发布" value="1" />
      <el-option label="已下线" value="2" />
    </el-select>
    <el-button @click="changeStatus('')">重置</el-button>
    <el-button :disabled="loading" @click="load">刷新</el-button>
  </div>
  <el-table class="admin-list-table" :data="list" :scrollbar-tabindex="0" v-loading="loading" :empty-text="loadError || '暂无公告'">
    <el-table-column prop="id" label="ID" width="60" />
    <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
    <el-table-column label="状态" width="100">
      <template #default="{ row }">
        <el-tag size="small" round :type="['warning', 'success', 'info'][row.status]">
          {{ ['草稿','已发布','已下线'][row.status] }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="发布时间" width="170">
      <template #default="{ row }">{{ formatTime(row.publishTime) || '—' }}</template>
    </el-table-column>
    <el-table-column label="创建时间" width="170">
      <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
    </el-table-column>
    <el-table-column label="操作" width="160">
      <template #default="{ row }">
        <el-button link type="primary" @click="openEditor(row.id)">编辑</el-button>
        <el-button v-if="row.status !== 1" link type="primary" @click="publish(row)">发布</el-button>
        <el-button v-else link type="warning" @click="offline(row)">下线</el-button>
        <el-button link type="danger" @click="remove(row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination v-if="total > 0" class="admin-list-pagination" :current-page="pageNum" :total="total" :page-size="pageSize"
                 :pager-count="5" layout="total, prev, pager, next" @update:current-page="changePage" />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listPage, noticeListLocation } from '../../layout/adminNavigation.mjs'
import { formatTime } from '../../utils/date'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { noticeList, publishNotice, offlineNotice, deleteNotice } from '../../api/notice'

const route = useRoute()
const router = useRouter()
const list = ref([])
const pageNum = computed(() => listPage(route.query.page))
const status = computed(() => ['0', '1', '2'].includes(route.query.status) ? route.query.status : '')
const loadError = ref('')
const total = ref(0)
const loading = ref(false)
const pageSize = 10
let requestVersion = 0

function openEditor(id) {
  const { query } = noticeListLocation(String(pageNum.value), status.value)
  router.push({ name: 'NoticeEdit', params: { id: id || '' }, query })
}

function changeStatus(value) {
  router.replace(noticeListLocation('1', value))
}

function changePage(value) {
  router.replace(noticeListLocation(String(value), status.value))
}

async function load() {
  const version = ++requestVersion
  const requestedPage = pageNum.value
  loading.value = true
  loadError.value = ''
  try {
    const result = await noticeList({ pageNum: requestedPage, pageSize, status: status.value || undefined })
    if (version !== requestVersion) return
    const lastPage = Math.max(1, Math.ceil(result.total / pageSize))
    if (requestedPage > lastPage) {
      changePage(lastPage)
      return
    }
    list.value = result.list
    total.value = result.total
  } catch (error) {
    if (version === requestVersion) {
      list.value = []
      total.value = 0
      loadError.value = error.message || '无法读取公告。'
    }
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

async function publish(row) {
  await publishNotice(row.id)
  ElMessage.success('已发布，三端即时可见')
  load()
}

async function offline(row) {
  await offlineNotice(row.id)
  ElMessage.success('已下线')
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？`, '提示', { type: 'warning' })
  await deleteNotice(row.id)
  ElMessage.success('已删除')
  load()
}

watch([pageNum, status], load, { immediate: true })
</script>
