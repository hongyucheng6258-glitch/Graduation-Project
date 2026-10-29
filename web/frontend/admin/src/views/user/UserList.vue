<template>
  <WtPageHeader title="用户管理" subtitle="查看与管理平台用户" eyebrow="管理后台" />

  <el-card>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="学号/昵称" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 130px" @change="search">
        <el-option label="正常" :value="0" />
        <el-option label="已禁用" :value="1" />
      </el-select>
      <el-button type="primary" @click="search">搜索</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="studentNo" label="学号" width="120">
        <template #default="{ row }">{{ row.studentNo || '—' }}</template>
      </el-table-column>
      <el-table-column prop="nickname" label="昵称" width="130" />
      <el-table-column prop="phone" label="手机号" width="120" />
      <el-table-column label="最近登录" width="170">
        <template #default="{ row }">{{ formatTime(row.lastLoginTime) || '从未登录' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'">
            {{ row.status === 0 ? '正常' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" size="small" type="danger" @click="toggle(row, 1)">禁用</el-button>
          <el-button v-else size="small" type="success" @click="toggle(row, 0)">解封</el-button>
          <el-button size="small" @click="resetPwd(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="pageNum" :total="total" :page-size="10"
                   layout="total, prev, pager, next" style="margin-top: 16px" @current-change="load" />
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, updateUserStatus, resetPassword } from '../../api/user'
import { formatTime } from '../../utils/date'

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
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
