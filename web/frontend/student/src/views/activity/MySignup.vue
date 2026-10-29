<template>
  <WtPageHeader title="我的报名" subtitle="我参与和发起的活动" eyebrow="我的" />

  <div class="my-signup">
    <el-card>
      <template #header><h3>我的活动</h3></template>
      <el-tabs v-model="tab">
        <!-- 我的报名 -->
        <el-tab-pane label="我的报名" name="signup">
          <el-table :data="signups" v-loading="loading">
            <el-table-column prop="activityTitle" label="活动" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <router-link :to="`/activity/detail/${row.activityId}`">{{ row.activityTitle }}</router-link>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="报名说明" min-width="160" show-overflow-tooltip />
            <el-table-column label="报名时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="['warning','success','danger'][row.status]">
                  {{ ['待审批','已通过','已拒绝'][row.status] }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="signupPage" :total="signupTotal" :page-size="10"
                         layout="prev, pager, next" style="margin-top: 16px" @current-change="loadSignups" />
        </el-tab-pane>
        <!-- 我的发布 -->
        <el-tab-pane label="我的发布" name="published">
          <el-table :data="published" v-loading="loading">
            <el-table-column prop="title" label="活动" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <router-link :to="`/activity/detail/${row.id}`">{{ row.title }}</router-link>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.auditStatus === 2 ? 'danger' : row.status === 2 ? 'info' : row.status === 3 ? 'danger' : ['warning','success'][row.auditStatus]">
                  {{ row.auditStatus === 0 ? '待审核' : row.auditStatus === 2 ? '已驳回' : row.status === 2 ? '已结束' : row.status === 3 ? '已下架' : '已通过' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="auditReason" label="驳回理由" min-width="120" show-overflow-tooltip />
            <el-table-column label="报名数" width="90">
              <template #default="{ row }">{{ row.memberCount }}</template>
            </el-table-column>
            <el-table-column label="发布时间" width="150">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.status === 2 || row.status === 3" @click="editActivity(row)">编辑</el-button>
                <el-button size="small" type="primary" plain :disabled="row.auditStatus !== 1 || row.status === 2 || row.status === 3"
                           @click="openMembers(row)">报名管理</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="pubPage" :total="pubTotal" :page-size="10"
                         layout="prev, pager, next" style="margin-top: 16px" @current-change="loadPublished" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 报名管理弹窗 -->
    <el-dialog v-model="memberDialog" title="报名管理" width="640px" destroy-on-close>
      <template v-if="memberActivity">
        <div style="margin-bottom: 12px; font-weight: 600">{{ memberActivity.title }}</div>
        <el-table :data="members" v-loading="memberLoading" size="small">
          <el-table-column prop="nickname" label="报名人" width="110" />
          <el-table-column prop="remark" label="报名说明" min-width="140" show-overflow-tooltip />
          <el-table-column label="报名时间" width="150">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="['warning','success','danger'][row.status]">{{ ['待审批','已通过','已拒绝'][row.status] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <template v-if="row.status === 0">
                <el-button size="small" type="success" @click="handleMember(row, true)">同意</el-button>
                <el-button size="small" type="danger" @click="handleMember(row, false)">拒绝</el-button>
              </template>
              <span v-else style="color: var(--ink-3)">已处理</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!memberLoading && !members.length" description="暂无报名" />
        <div v-if="memberTotal > memberPageSize" class="member-pager">
          <el-pagination
            layout="prev, pager, next"
            :total="memberTotal"
            :page-size="memberPageSize"
            :current-page="memberPage"
            background
            small
            @current-change="onMemberPageChange"
          />
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { mySignups, myActivities, activityMembers, handleMember as handleMemberApi } from '../../api/activity'
import { formatTime } from '../../utils/date'

const router = useRouter()
const tab = ref('signup')
const loading = ref(false)
const signups = ref([])
const signupPage = ref(1)
const signupTotal = ref(0)
const published = ref([])
const pubPage = ref(1)
const pubTotal = ref(0)

const memberDialog = ref(false)
const memberLoading = ref(false)
const memberActivity = ref(null)
const members = ref([])
const memberTotal = ref(0)
const memberPage = ref(1)
const memberPageSize = 10

async function loadSignups() {
  loading.value = true
  try {
    const res = await mySignups({ pageNum: signupPage.value, pageSize: 10 })
    signups.value = res.list
    signupTotal.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadPublished() {
  loading.value = true
  try {
    const res = await myActivities({ pageNum: pubPage.value, pageSize: 10 })
    published.value = res.list
    pubTotal.value = res.total
  } finally {
    loading.value = false
  }
}

function editActivity(row) {
  router.push(`/activity/publish?id=${row.id}`)
}

async function loadMembers(page) {
  if (!memberActivity.value) return
  memberLoading.value = true
  try {
    const res = await activityMembers(memberActivity.value.id, { pageNum: page, pageSize: memberPageSize })
    members.value = res?.list || []
    memberTotal.value = res?.total || 0
    memberPage.value = page
  } finally {
    memberLoading.value = false
  }
}

function onMemberPageChange(page) {
  loadMembers(page)
}

async function openMembers(row) {
  memberActivity.value = row
  memberDialog.value = true
  await loadMembers(1)
}
async function handleMember(row, approve) {
  try {
    await handleMemberApi(row.id, approve)
    ElMessage.success(approve ? '已同意报名' : '已拒绝报名')
    if (memberActivity.value) {
      await loadMembers(memberPage.value)
    }
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

onMounted(() => {
  loadSignups()
  loadPublished()
})
</script>

<style scoped>
.member-pager { display: flex; justify-content: center; margin-top: 12px; }
</style>
