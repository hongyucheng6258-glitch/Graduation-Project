<template>
  <WtPageHeader title="密码找回审核" subtitle="核实学生身份，处理密码找回申请。" />

  <div class="admin-list-toolbar">
    <el-button :disabled="loading" @click="load(currentCursor)">刷新</el-button>
    <span class="recovery-guidance">请通过校方可信渠道核实身份，申请人填写的联系方式仅供联系。</span>
  </div>
  <p v-if="loadError" class="recovery-error" role="alert">{{ loadError }}</p>

  <el-table class="admin-list-table recovery-table" v-loading="loading" :data="rows"
    :scrollbar-tabindex="0" :empty-text="loadError ? '申请加载失败' : '暂无找回申请'">
    <el-table-column prop="studentNo" label="学号" min-width="112" show-overflow-tooltip />
    <el-table-column prop="name" label="姓名" min-width="100" show-overflow-tooltip />
    <el-table-column prop="contact" label="联系方式" min-width="150" show-overflow-tooltip />
    <el-table-column label="状态" width="92">
      <template #default="{ row }">
        <el-tag size="small" round :type="statusTone[row.status]">{{ statusLabel[row.status] || row.status }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="申请时间" width="165">
      <template #default="{ row }">{{ formatTime(row.createdAt) || '—' }}</template>
    </el-table-column>
    <el-table-column label="操作" width="100">
      <template #default="{ row }">
        <el-button link type="primary" @click="openDetails(row)">{{ row.status === 'PENDING' ? '审核' : '查看详情' }}</el-button>
      </template>
    </el-table-column>
  </el-table>

  <div class="recovery-mobile" v-loading="loading">
    <AdminRecordCard v-for="row in rows" :key="row.id" :id="row.id" :title="row.name"
      :status="statusLabel[row.status] || row.status" :tone="statusTone[row.status]"
      :fields="[['学号', row.studentNo], ['申请时间', formatTime(row.createdAt) || '—']]">
      <template #actions>
        <el-button size="small" type="primary" plain @click="openDetails(row)">{{ row.status === 'PENDING' ? '审核' : '查看详情' }}</el-button>
      </template>
    </AdminRecordCard>
    <p v-if="!loading && !loadError && !rows.length" role="status">暂无找回申请</p>
  </div>

  <div v-if="previousCursors.length || rows.length === 30" class="admin-list-pagination recovery-pages">
    <el-button v-if="previousCursors.length" :disabled="loading" @click="previousPage">上一页</el-button>
    <el-button v-if="rows.length === 30" :disabled="loading" @click="nextPage">更早的申请</el-button>
  </div>

  <el-dialog v-model="detailVisible" title="找回申请详情" width="min(560px, calc(100vw - 32px))" destroy-on-close>
    <AdminRecordCard v-if="selected" :id="selected.id" :title="selected.name"
      :status="statusLabel[selected.status] || selected.status" :tone="statusTone[selected.status]"
      :fields="[['学号', selected.studentNo], ['联系方式', selected.contact], ['申请时间', formatTime(selected.createdAt) || '—'],
        ['找回说明', selected.explanation], ...(selected.reason ? [['驳回原因', selected.reason]] : [])]" />
    <template #footer>
      <el-button :disabled="reviewing" @click="detailVisible = false">关闭</el-button>
      <template v-if="selected?.status === 'PENDING'">
        <el-button type="danger" plain :disabled="reviewing" @click="review(selected, false)">驳回</el-button>
        <el-button type="primary" :loading="reviewing" @click="review(selected, true)">通过审核</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminRecordCard from '../../components/AdminRecordCard.vue'
import { formatTime } from '../../utils/date'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import request from '../../api/request'

const rows = ref([])
const loading = ref(false)
const loadError = ref('')
const currentCursor = ref()
const previousCursors = ref([])
const selected = ref(null)
const detailVisible = ref(false)
const reviewing = ref(false)
const statusTone = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', CLAIMED: 'info' }
function openDetails(row) {
  selected.value = row
  detailVisible.value = true
}
async function nextPage() {
  const previous = currentCursor.value
  if (await load(rows.value.at(-1).id)) previousCursors.value.push(previous)
}
async function previousPage() {
  if (await load(previousCursors.value.at(-1))) previousCursors.value.pop()
}
const statusLabel = { PENDING: '待审核', APPROVED: '待领取', REJECTED: '已驳回', CLAIMED: '已领取' }
async function load(before) {
  loading.value = true
  try {
    loadError.value = ''
    rows.value = await request.get('/password-recovery', { params: { before } })
    currentCursor.value = before
    return true
  } catch (error) {
    loadError.value = error.message || '加载申请失败，请刷新重试。'
    return false
  } finally {
    loading.value = false
  }
}
async function review(row, approve) {
  if (reviewing.value) return
  let reason = ''
  try {
    if (approve) {
      await ElMessageBox.confirm('确认已通过校方可信渠道核实该学生身份？通过后回执持有人可重置此账号密码。', '通过审核')
    } else {
      const result = await ElMessageBox.prompt('请填写学生可以看到的驳回原因。', '驳回申请', {
        inputValidator: value => !!value?.trim() && value.length <= 500 || '请填写 1 至 500 字'
      })
      reason = result.value.trim()
    }
  } catch {
    return
  }
  reviewing.value = true
  try {
    await request.put('/password-recovery/' + row.id, { approve, reason })
    detailVisible.value = false
    ElMessage.success('审核结果已保存')
    await load(currentCursor.value)
  } finally {
    reviewing.value = false
  }
}
onMounted(() => load())
</script>

<style scoped>
.recovery-guidance, .recovery-mobile > p { color: var(--ink-2); font-size: var(--fs-sm); }
.recovery-error { margin: 0 0 12px; color: var(--error); }
.recovery-mobile { display: none; }
.recovery-pages { display: flex; flex-wrap: wrap; gap: 8px; }
.recovery-pages :deep(.el-button + .el-button) { margin-left: 0; }
@media (max-width: 760px) {
  .recovery-table { display: none; }
  .recovery-mobile { display: grid; gap: 8px; }
  .recovery-guidance { flex-basis: 100%; }
}
</style>
