<template>
  <div class="content-manage">
    <WtPageHeader title="内容管理" subtitle="管理已发布内容的下架与恢复。" />

    <div class="admin-list-toolbar">
      <RouterLink v-if="record" :to="{ name: 'ContentManage', query: { type } }">查看全部{{ contentTypes[type] }}</RouterLink>
      <el-select v-else v-model="type" aria-label="内容类型" style="width: 160px" @change="resetType">
        <el-option v-for="(label, value) in contentTypes" :key="value" :label="label" :value="value" />
      </el-select>
    </div>
    <div v-if="record" v-loading="loading">
      <AdminRecordCard v-if="list[0]" :id="record" :title="titleOf(list[0]) || contentTypes[type]"
        :status="statusText(list[0])" :tone="isOff(list[0]) ? 'info' : 'success'"
        :fields="[['类型', contentTypes[type]], ['作者 ID', list[0].userId], ['发布时间', formatTime(list[0].createTime) || '—']]"
        :description="list[0].description || list[0].intro || list[0].content || ''">
        <template v-if="canManage(list[0])" #actions>
          <el-button v-if="list[0].status === 0" type="danger" plain @click="doOff(list[0])">下架</el-button>
          <el-button v-else-if="isOff(list[0])" type="primary" @click="doOn(list[0])">恢复上架</el-button>
          <el-button v-if="type === 'activity' && !isOff(list[0])" @click="openReport(list[0])">签到报表</el-button>
        </template>
      </AdminRecordCard>
      <p v-else-if="!loading" role="status">{{ loadError || '没有找到该内容。' }}</p>
    </div>
    <el-table v-else class="admin-list-table" :data="list" :scrollbar-tabindex="0" v-loading="loading" empty-text="暂无此类内容">
      <el-table-column label="类型" width="110">
        <template #default>
          <el-tag size="small" round type="info">{{ contentTypes[type] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标题" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ titleOf(row) || '—' }}</template>
      </el-table-column>
      <el-table-column prop="userId" label="作者 ID" width="100" />
      <el-table-column label="审核" width="90">
        <template #default="{ row }">
          <el-tag v-if="type === 'qa'" size="small" round type="info">免审</el-tag>
          <el-tag v-else size="small" round :type="['warning','success','danger'][row.auditStatus]">
            {{ ['待审核','已通过','已驳回'][row.auditStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" round :type="canManage(row) && !isOff(row) ? 'success' : 'info'">
            {{ statusText(row) || '—' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="145">
        <template #default="{ row }">
          <template v-if="canManage(row)">
            <el-button v-if="row.status === 0" link type="danger" @click="doOff(row)">下架</el-button>
            <el-button v-else-if="isOff(row)" link type="primary" @click="doOn(row)">恢复上架</el-button>
            <el-button v-if="type === 'activity' && !isOff(row)" link type="primary" @click="openReport(row)">签到报表</el-button>
          </template>
          <span v-else style="color: var(--ink-3); font-size: 12px">—</span>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="!record" class="admin-list-pagination" v-model:current-page="pageNum" :total="total" :page-size="10"
                   :pager-count="5" layout="total, prev, pager, next" />

    <!-- 签到报表弹窗 -->
    <el-dialog v-model="reportVisible" title="签到报表" width="min(720px, calc(100vw - 32px))" destroy-on-close>
      <template v-if="report">
        <div class="report-actions">
          <el-button v-if="canExport" size="small" :loading="exporting" @click="doExport('members')">导出报名名单</el-button>
          <el-button v-if="canExport" size="small" type="primary" :loading="exporting" @click="doExport('signins')">导出签到名单</el-button>
        </div>
        <div class="report-summary">
          <div class="sum-item"><b>{{ report.joinedCount }}</b><span>已通过报名</span></div>
          <div class="sum-item"><b>{{ report.signinCount }}</b><span>已签到</span></div>
          <div class="sum-item"><b>{{ report.signinRate }}%</b><span>签到率</span></div>
        </div>
        <el-table :data="report.members" size="small" max-height="360">
          <el-table-column prop="nickname" label="学生" width="120" />
          <el-table-column label="报名状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="['warning','success','danger'][row.memberStatus]">
                {{ ['待审批','已通过','已拒绝'][row.memberStatus] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="签到" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.signed ? 'success' : 'info'">{{ row.signed ? '已签到' : '未签到' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="签到时间" width="170">
            <template #default="{ row }">{{ formatTime(row.signTime) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAdminStore } from '../../store/admin'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import AdminRecordCard from '../../components/AdminRecordCard.vue'
import { ElMessage } from 'element-plus'
import { auditAll } from '../../api/audit'
import { contentOff, contentOn, signinReport, exportMembers, exportSignins } from '../../api/content'
import { formatTime } from '../../utils/date'
import { searchRecordId } from '../../utils/searchQuery.mjs'

const admin = useAdminStore()
const canExport = computed(() => admin.can('activity:export'))
const contentTypes = {
  activity: '活动',
  idle: '闲置',
  lostfound: '失物招领',
  partner: '学习搭子',
  qa: '互助问答',
}

const route = useRoute()
const record = computed(() => searchRecordId(route.query.record))
const readType = value => typeof value === 'string' && Object.hasOwn(contentTypes, value) ? value : 'activity'
const type = ref(readType(route.query.type))
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const loadError = ref('')
let requestVersion = 0

const reportVisible = ref(false)
const report = ref(null)
const exporting = ref(false)

async function doExport(kind) {
  if (!report.value) return
  exporting.value = true
  try {
    if (kind === 'members') {
      await exportMembers(report.value.activityId)
    } else {
      await exportSignins(report.value.activityId)
    }
    ElMessage.success('导出成功，请查看下载文件')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

const isOff = (row) => {
  if (type.value === 'activity') return row.status === 3
  if (type.value === 'idle') return row.status === 3
  if (type.value === 'partner' || type.value === 'qa') return row.status === 9
  return row.status === 2
}
const canManage = (row) => type.value === 'qa' || row.auditStatus === 1
const titleOf = (row) => {
  if (type.value === 'partner') return `${row.subject || ''}${row.goal ? ' · ' + row.goal : ''}`
  return row.title || row.content || ''
}
const statusText = (row) => {
  if (type.value === 'activity') return ['报名中', '已报满', '已结束', '已下架'][row.status] ?? ''
  if (type.value === 'idle') return ['在架', '已预约', '已完成', '已下架'][row.status] ?? ''
  if (type.value === 'partner') return ['匹配中', '已找到', '', '', '', '', '', '', '', '已下架'][row.status] ?? ''
  if (type.value === 'qa') return ['待答', '已解决', '', '', '', '', '', '', '', '已下架'][row.status] ?? ''
  return ['进行中', '已完成', '已下架'][row.status] ?? ''
}

function resetType() {
  list.value = []
  total.value = 0
  pageNum.value = 1
}

async function load() {
  const version = ++requestVersion
  loading.value = true
  loadError.value = ''
  try {
    const res = await auditAll({ type: type.value, record: record.value, pageNum: pageNum.value, pageSize: 10 })
    if (version !== requestVersion) return
    list.value = res.list
    total.value = res.total
  } catch (error) {
    if (version === requestVersion) {
      list.value = []
      total.value = 0
      loadError.value = error.message || '无法读取内容记录。'
    }
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

async function doOff(row) {
  await contentOff(type.value, row.id)
  ElMessage.success('已下架')
  load()
}

async function doOn(row) {
  await contentOn(type.value, row.id)
  ElMessage.success('已恢复上架')
  load()
}

async function openReport(row) {
  report.value = null
  reportVisible.value = true
  try {
    report.value = await signinReport(row.id)
  } catch (e) {
    ElMessage.error(e.message || '加载报表失败')
  }
}

watch([() => route.query.type, record], ([value]) => {
  type.value = readType(value)
  resetType()
})
watch([type, pageNum, record], load, { immediate: true })
</script>

<style scoped>
.report-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}
.report-actions .el-button + .el-button { margin-left: 0; }
.report-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 14px;
}
.sum-item {
  flex: 1;
  min-width: 0;
  text-align: center;
  padding: 14px;
  border-radius: 8px;
  background: var(--surface-2);
}
.sum-item b {
  display: block;
  font-size: 26px;
  color: var(--brand-strong);
}
.sum-item span {
  font-size: 12px;
  color: var(--ink-3);
}
@media (max-width: 540px) {
  .report-summary { gap: 8px; }
  .sum-item { padding: 8px; }
}
</style>
