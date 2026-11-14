<template>
  <WtPageHeader title="举报处理" subtitle="查看举报详情，处理内容及用户相关举报。" />

  <div class="admin-list-toolbar">
    <RouterLink v-if="focusedId" :to="route.query.searchQ ? '/report' : '/report?view=pending'">
      {{ route.query.searchQ ? '查看全部举报' : '返回待处理举报' }}
    </RouterLink>
    <el-select v-else :model-value="status" aria-label="举报状态" placeholder="全部状态" style="width: 140px" @change="changeStatus">
      <el-option label="全部状态" value="" />
      <el-option label="待处理" :value="0" />
      <el-option label="已处理" :value="1" />
    </el-select>
    <el-button v-if="!focusedId" @click="changeStatus('')">重置</el-button>
    <el-button :disabled="loading" @click="load">刷新</el-button>
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
  <el-table v-else class="admin-list-table" :data="list" :scrollbar-tabindex="0" v-loading="loading" :empty-text="loadError || '暂无举报'">
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
  <el-pagination v-if="!focusedId && total > 0" class="admin-list-pagination" :current-page="pageNum" :total="total" :page-size="10"
                 :pager-count="5" layout="total, prev, pager, next" @update:current-page="changePage" />

  <!-- 处置弹窗 -->
  <el-dialog v-model="handleVisible" title="举报处置" width="min(460px, calc(100vw - 32px))">
    <div v-if="currentRow?.targetType === 'chat_message'" v-loading="evidenceLoading" class="chat-evidence">
      <template v-if="evidence">
        <strong>被举报消息 · 发送人 #{{ evidence.senderId }}</strong>
        <img v-if="evidence.messageType === 'image' && /^(?:data:image\/(?:png|jpeg|gif|webp);base64,|\/api\/assets\/)/.test(evidence.text)" :src="evidence.text" alt="被举报图片" />
        <p v-else>{{ evidence.messageType === 'image' ? '图片无法预览' : evidence.text }}</p>
      </template>
      <p v-else-if="!evidenceLoading">{{ evidenceError }}</p>
    </div>
    <el-form label-width="90px">
      <el-form-item label="处置动作">
        <el-radio-group v-model="handleForm.action">
          <template v-if="currentRow?.targetType === 'user'">
            <el-radio value="warn">警告用户</el-radio>
            <el-radio value="ban">封禁用户</el-radio>
            <el-radio value="ignore">举报不成立</el-radio>
          </template>
          <template v-else>
            <el-radio v-if="currentRow?.targetType !== 'chat_message'" value="offline">下架内容</el-radio>
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
import { listPage } from '../../layout/adminNavigation.mjs'
import { useAdminPendingStore } from '../../store/adminPending'
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import AdminRecordCard from '../../components/AdminRecordCard.vue'
import { ElMessage } from 'element-plus'
import { reportDetail, reportList, handleReport, reportChatEvidence } from '../../api/report'
import { formatTime } from '../../utils/date'

const pending = useAdminPendingStore()

const route = useRoute()
const router = useRouter()
const focusedId = computed(() => typeof route.query.record === 'string' && /^[1-9]\d*$/.test(route.query.record)
  ? route.query.record : null)
let requestVersion = 0
const status = computed(() => route.query.view === 'pending' ? 0 : route.query.view === 'handled' ? 1 : '')
const list = ref([])
const pageNum = computed(() => listPage(route.query.page))
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const acting = ref(false)
const handleVisible = ref(false)
const currentRow = ref(null)
const evidence = ref(null)
const evidenceLoading = ref(false)
const evidenceError = ref('')
watch(currentRow, async (row, _, onCleanup) => {
  let active = true
  onCleanup(() => { active = false })
  evidence.value = null
  evidenceError.value = ''
  evidenceLoading.value = row?.targetType === 'chat_message'
  if (!evidenceLoading.value) return
  try {
    const result = await reportChatEvidence(row.id)
    if (active) evidence.value = result
  } catch (error) {
    if (active) evidenceError.value = error.message || '无法加载被举报消息'
  } finally {
    if (active) evidenceLoading.value = false
  }
})
const handleForm = reactive({ action: 'offline', handleResult: '' })

const typeName = (t) => ({ idle: '闲置', activity: '活动', lostfound: '失物', post: '动态', comment: '评论', chat_message: '聊天消息', user: '用户' }[t] || t)

function changeStatus(value) {
  const view = value === 0 ? 'pending' : value === 1 ? 'handled' : undefined
  router.replace({ query: { ...route.query, view, page: undefined } })
}

function changePage(value) {
  router.replace({ query: { ...route.query, page: value > 1 ? String(value) : undefined } })
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
    const lastPage = Math.max(1, Math.ceil(res.total / 10))
    if (!recordId && pageNum.value > lastPage) {
      changePage(lastPage)
      return
    }
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
  handleForm.action = ['user', 'chat_message'].includes(row.targetType) ? 'warn' : 'offline'
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
    pending.refresh({ afterMutation: true })
    ElMessage.success('处置完成，举报人已收到通知')
    handleVisible.value = false
    load()
  } finally {
    acting.value = false
  }
}

watch([status, focusedId, pageNum], () => {
  handleVisible.value = false
  currentRow.value = null
  handleForm.handleResult = ''
  load()
}, { immediate: true })
</script>

<style scoped>
.chat-evidence { min-height: 40px; margin-bottom: 16px; padding: 12px; border: 1px solid var(--line); overflow-wrap: anywhere; white-space: pre-wrap; }
.chat-evidence img { display: block; max-width: 100%; max-height: 220px; margin-top: 8px; }
</style>
