<template>
  <WtPageHeader title="用户管理" subtitle="查询、启用或禁用账号，重置密码。" />

  <form class="admin-list-toolbar" @submit.prevent="search">
    <el-input v-model="keyword" aria-label="学号或昵称" placeholder="学号 / 昵称搜索" clearable style="width: 220px" @clear="search" />
    <el-select v-model="status" aria-label="账号状态" placeholder="全部状态" clearable style="width: 130px" @change="search">
      <el-option label="正常" :value="0" />
      <el-option label="已禁用" :value="1" />
    </el-select>
    <el-button type="primary" native-type="submit">搜索</el-button>
  </form>
  <el-table class="admin-list-table" :data="list" :scrollbar-tabindex="0" v-loading="loading" empty-text="暂无符合条件的用户">
    <el-table-column prop="id" label="ID" width="70" />
    <el-table-column prop="studentNo" label="学号" width="120">
      <template #default="{ row }">{{ row.studentNo || '—' }}</template>
    </el-table-column>
    <el-table-column prop="nickname" label="昵称" min-width="150">
      <template #default="{ row }">
        <div class="user-name">
          <el-avatar :size="32" :src="resolveAssetUrl(row.avatar)" aria-hidden="true">
            {{ row.nickname?.charAt(0) || '用' }}
          </el-avatar>
          <span>{{ row.nickname || '—' }}</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column prop="phone" label="手机号" width="120" />
    <el-table-column label="最近登录" width="170">
      <template #default="{ row }">{{ formatTime(row.lastLoginTime) || '从未登录' }}</template>
    </el-table-column>
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <el-tag size="small" round :type="row.status === 0 ? 'success' : 'danger'">
          {{ row.status === 0 ? '正常' : '已禁用' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="150">
      <template #default="{ row }">
        <el-button link type="primary" @click="resetPwd(row)">重置密码</el-button>
        <el-button v-if="row.status === 0" link type="danger" @click="toggle(row, 1)">禁用</el-button>
        <el-button v-else link type="primary" @click="toggle(row, 0)">启用</el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination class="admin-list-pagination" v-model:current-page="pageNum" :total="total" :page-size="10"
                 :pager-count="5" layout="total, prev, pager, next" @current-change="load" />
</template>

<script setup>
import { onMounted, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, updateUserStatus, resetPassword } from '../../api/user'
import { formatTime } from '../../utils/date'
import { resolveAssetUrl } from '../../utils/image'

const keyword = ref('')
const status = ref(undefined)
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)

function search() {
  pageNum.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    const res = await listUsers({ keyword: keyword.value || undefined, status: status.value, pageNum: pageNum.value, pageSize: 10 })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function toggle(row, s) {
  await ElMessageBox.confirm(
    s === 1 ? `确定禁用用户「${row.nickname}」吗？禁用后无法登录与发布。` : `确定解封用户「${row.nickname}」吗？`,
    '提示', { type: 'warning' }
  )
  await updateUserStatus(row.id, s)
  ElMessage.success('操作成功')
  load()
}

async function resetPwd(row) {
  await ElMessageBox.confirm(`确定将「${row.nickname}」的密码重置为 123456 吗？`, '提示', { type: 'warning' })
  await resetPassword(row.id)
  ElMessage.success('已重置为 123456')
}

onMounted(load)
</script>

<style scoped>
.user-name {
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-name .el-avatar {
  flex: none;
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
}
</style>
