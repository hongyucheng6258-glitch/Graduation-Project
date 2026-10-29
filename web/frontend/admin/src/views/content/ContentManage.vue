<template>
  <div class="content-manage">
    <el-card>
      <template #header>
        <div class="head">
          <h3>内容管理</h3>
          <span class="sub">管理已发布内容：下架违规内容或恢复上架</span>
        </div>
      </template>
      <el-tabs v-model="type" @tab-change="load">
        <el-tab-pane label="活动" name="activity" />
        <el-tab-pane label="闲置" name="idle" />
        <el-tab-pane label="失物招领" name="lostfound" />
        <el-tab-pane label="学习搭子" name="partner" />
        <el-tab-pane label="互助问答" name="qa" />
      </el-tabs>
      <el-table :data="list" v-loading="loading">
        <el-table-column label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ titleOf(row) }}</template>
        </el-table-column>
        <el-table-column label="审核" width="90">
          <template #default="{ row }">
            <el-tag v-if="type === 'qa'" size="small" type="info">免审</el-tag>
            <el-tag v-else size="small" :type="['warning','success','danger'][row.auditStatus]">
              {{ ['待审核','已通过','已驳回'][row.auditStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ statusText(row) }}</template>
        </el-table-column>
        <el-table-column label="发布时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <template v-if="type === 'qa' || row.auditStatus === 1">
              <el-button v-if="!isOff(row)" size="small" type="danger" plain @click="doOff(row)">下架</el-button>
              <el-button v-else size="small" type="success" plain @click="doOn(row)">恢复上架</el-button>
              <el-button v-if="type === 'activity' && !isOff(row)" size="small" type="primary" plain @click="openReport(row)">签到报表</el-button>
            </template>
            <span v-else style="color: var(--ink-3); font-size: 12px">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="pageNum" :total="total" :page-size="10"
                     layout="prev, pager, next" style="margin-top: 16px" @current-change="load" />
    </el-card>

    <!-- 签到报表弹窗 -->
    <el-dialog v-model="reportVisible" title="签到报表" width="720px" destroy-on-close>
      <template #header>
        <span>签到报表</span>
        <span style="float:right">
          <el-button size="small" :loading="exporting" @click="doExport('members')">导出报名名单</el-button>
          <el-button size="small" type="primary" :loading="exporting" @click="doExport('signins')">导出签到名单</el-button>
        </span>
      </template>
      <template v-if="report">
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
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { auditAll } from '../../api/audit'
import { contentOff, contentOn, signinReport, exportMembers, exportSignins } from '../../api/content'
import { formatTime } from '../../utils/date'

const type = ref('activity')
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)

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

async function load() {
  loading.value = true
  try {
    const res = await auditAll({ type: type.value, pageNum: pageNum.value, pageSize: 10 })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
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

onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.head h3 {
  margin: 0;
}
.sub {
  font-size: 12px;
  color: var(--ink-3);
}
.report-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 14px;
}
.sum-item {
  flex: 1;
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
</style>
