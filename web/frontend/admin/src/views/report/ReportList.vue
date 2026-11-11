<template>
  <WtPageHeader title="举报处理" subtitle="查看举报详情，处理内容及用户相关举报。" />

  <div class="admin-list-toolbar">
    <RouterLink v-if="focusedId" :to="route.query.searchQ ? '/report' : '/report?view=pending'">
      {{ route.query.searchQ ? '查看全部举报' : '返回待处理举报' }}
    </RouterLink>
    <el-select v-else v-model="status" aria-label="举报状态" placeholder="全部状态" style="width: 140px" @change="search">
      <el-option label="全部状态" value="" />
      <el-option label="待处理" :value="0" />
      <el-option label="已处理" :value="1" />
    </el-select>
  </div>
  <div v-if="focusedId" v-loading="loading">
    <AdminRecordCard v-if="list[0]" :id="focusedId" :title="list[0].reasonType || '举报工单'"
      :status="list[0].status === 0 ? '待处理' : '已处理'" :tone="list[0].status === 0 ? 'warning' : 'success'"
      :fields="[['举报对象', `${typeName(list[0].targetType)} #${list[0].targetId}`], ['举报人 ID', list[0].reporterId],
        ['举报时间', formatTime(list[0].createTime) || '—'], ...(list[0].handleResult ? [['处置结果', list[0].handleResult]] : [])]"
      :description="list[0].reason || ''">
      <template v-if="list[0].status === 0" #actions>
        <el-button type="primary" @click="openHandle(list[0])">处置</el-button>
      </template>
    </AdminRecordCard>
    <p v-else-if="!loading" role="status">{{ loadError || '没有找到该举报。' }}</p>
  </div>
  <el-table v-else class="admin-list-table" :data="list" :scrollbar-tabindex="0" v-loading="loading" empty-text="暂无举报">
    <el-table-column prop="id" label="ID" width="60" />
    <el-table-column prop="reporterId" label="举报人ID" width="90" />
    <el-table-column label="被举报对象" width="110">
      <template #default="{ row }">#{{ row.targetId }}</template>
    </el-table-column>
    <el-table-column label="对象类型" width="100">
      <template #default="{ row }">
        <el-tag size="small" round type="info">{{ typeName(row.targetType) }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="reasonType" label="举报类型" width="100" />
    <el-table-column prop="reason" label="举报原因" min-width="180" show-overflow-tooltip />
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <el-tag size="small" round :type="row.status === 0 ? 'warning' : 'success'">
          {{ row.status === 0 ? '待处理' : '已处理' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="举报时间" width="164">
      <template #default="{ row }">{{ formatTime(row.createTime) || '—' }}</template>
    </el-table-column>
    <el-table-column prop="handleResult" label="处置结果" min-width="140" show-overflow-tooltip />
    <el-table-column label="操作" width="74">
      <template #default="{ row }">
        <el-button v-if="row.status === 0" link type="primary" @click="openHandle(row)">处置</el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination v-if="!focusedId" class="admin-list-pagination" v-model:current-page="pageNum" :total="total" :page-size="10"
                 :pager-count="5" layout="total, prev, pager, next" @current-change="load" />

  <!-- 处置弹窗 -->
  <el-dialog v-model="handleVisible" title="举报处置" width="min(460px, calc(100vw - 32px))">
    <el-form label-width="90px">
      <el-form-item label="处置动作">
        <el-radio-group v-model="handleForm.action">
          <template v-if="currentRow?.targetType === 'user'">
            <el-radio value="warn">警告用户</el-radio>
            <el-radio value="ban">封禁用户</el-radio>
            <el-radio value="ignore">举报不成立</el-radio>
          </template>
          <template v-else>
            <el-radio value="offline">下架内容</el-radio>
            <el-radio value="warn">警告发布者</el-radio>
            <el-radio value="ban">封禁发布者</el-radio>
            <el-radio value="ignore">举报不成立</el-radio>
          </template>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="处置说明" required>
        <el-input v-model="handleForm.handleResult" type="textarea" :rows="3"
                  placeholder="必填，将通知举报人" maxlength="255" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleVisible = false">取消</el-button>
      <el-button type="primary" :loading="acting" @click="doHandle">确认处置</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import AdminRecordCard from '../../components/AdminRecordCard.vue'
import { ElMessage } from 'element-plus'
import { reportDetail, reportList, handleReport } from '../../api/report'
import { formatTime } from '../../utils/date'

const route = useRoute()
const focusedId = computed(() => typeof route.query.record === 'string' && /^[1-9]\d*$/.test(route.query.record)
  ? route.query.record : null)
let requestVersion = 0
const status = ref('')
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const acting = ref(false)
const handleVisible = ref(false)
const currentRow = ref(null)
const handleForm = reactive({ action: 'offline', handleResult: '' })

const typeName = (t) => ({ idle: '闲置', activity: '活动', lostfound: '失物', post: '动态', comment: '评论', user: '用户' }[t] || t)

function search() {
  pageNum.value = 1
  load()
}

async function load() {
  const version = ++requestVersion
  const recordId = focusedId.value
  loading.value = true
  loadError.value = ''
  if (recordId) list.value = []
  try {
    const res = recordId ? { list: [{ ...await reportDetail(recordId), id: recordId }], total: 1 }
      : await reportList({ status: status.value === '' ? undefined : status.value, pageNum: pageNum.value, pageSize: 10 })
    if (version !== requestVersion) return
    list.value = res.list
    total.value = res.total
  } catch (error) {
    if (version === requestVersion) {
      list.value = []
      total.value = 0
      loadError.value = error.message || '无法读取举报记录。'
    }
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function openHandle(row) {
  currentRow.value = row
  handleForm.action = row.targetType === 'user' ? 'warn' : 'offline'
  handleForm.handleResult = ''
  handleVisible.value = true
}

async function doHandle() {
  if (!currentRow.value) return
  if (!handleForm.handleResult.trim()) {
    ElMessage.warning('请填写处置说明')
    return
  }
  acting.value = true
  try {
    await handleReport(currentRow.value.id, handleForm)
    ElMessage.success('处置完成，举报人已收到通知')
    handleVisible.value = false
    load()
  } finally {
    acting.value = false
  }
}

watch([() => route.query.view, focusedId], ([view]) => {
  handleVisible.value = false
  currentRow.value = null
  handleForm.handleResult = ''
  status.value = view === 'pending' ? 0 : ''
  search()
}, { immediate: true })
</script>
