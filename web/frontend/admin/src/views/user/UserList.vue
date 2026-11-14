<template>
  <WtPageHeader title="用户管理" subtitle="查询、启用或禁用账号，重置密码。" />

  <div v-if="record" class="admin-list-toolbar">
    <span>用户 #{{ record }}</span>
    <RouterLink :to="listRoute">查看全部用户</RouterLink>
    <el-button :disabled="loading" @click="load">刷新</el-button>
  </div>
  <form v-else class="admin-list-toolbar" @submit.prevent="search">
    <el-input v-model="keyword" aria-label="学号或昵称" placeholder="学号 / 昵称搜索" clearable style="width: 220px" @clear="search" />
    <el-select v-model="status" aria-label="账号状态" placeholder="全部状态" clearable style="width: 130px" @change="search">
      <el-option label="正常" :value="0" />
      <el-option label="已禁用" :value="1" />
    </el-select>
    <el-button type="primary" native-type="submit">搜索</el-button>
    <el-button @click="resetSearch">重置</el-button>
    <el-button :disabled="loading" @click="load">刷新</el-button>
  </form>
  <div v-if="record" v-loading="loading">
    <AdminRecordCard v-if="list[0]" :id="record" :title="list[0].nickname || list[0].studentNo || '用户'"
      :status="list[0].status === 0 ? '正常' : '已禁用'" :tone="list[0].status === 0 ? 'success' : 'danger'"
      :fields="[['学号', list[0].studentNo], ['手机号', list[0].phone], ['最近登录', formatTime(list[0].lastLoginTime) || '从未登录']]">
      <template #actions>
        <el-button v-if="canResetPassword" @click="resetPwd(list[0])">重置密码</el-button>
        <el-button v-if="list[0].status === 0" type="danger" plain @click="toggle(list[0], 1)">禁用</el-button>
        <el-button v-else type="primary" @click="toggle(list[0], 0)">启用</el-button>
      </template>
    </AdminRecordCard>
    <p v-else-if="!loading" role="status">{{ loadError || '没有找到该用户。' }}</p>
  </div>
  <template v-else>
  <div class="mobile-users" v-loading="loading">
    <ul v-if="list.length" aria-label="用户列表">
      <li v-for="row in list" :key="row.id" class="mobile-user">
        <el-avatar :size="32" :src="resolveAssetUrl(row.avatar)" aria-hidden="true">{{ row.nickname?.charAt(0) || '用' }}</el-avatar>
        <div class="mobile-user-info">
          <strong>{{ row.nickname || '未设置昵称' }}</strong>
          <small>学号 {{ row.studentNo || '—' }}</small>
        </div>
        <el-tag size="small" round :type="row.status === 0 ? 'success' : 'danger'">{{ row.status === 0 ? '正常' : '已禁用' }}</el-tag>
        <RouterLink class="mobile-user-detail" :to="{ name: 'UserList', query: { ...route.query, record: String(row.id) } }"
          :aria-label="`查看 ${row.nickname || row.studentNo || row.id} 的详情`">查看详情 ›</RouterLink>
      </li>
    </ul>
    <p v-else-if="!loading" role="status">{{ loadError || '暂无符合条件的用户' }}</p>
  </div>
  <el-table class="admin-list-table" :data="list" :scrollbar-tabindex="0" v-loading="loading" :empty-text="loadError || '暂无符合条件的用户'">
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
        <el-button v-if="canResetPassword" link type="primary" @click="resetPwd(row)">重置密码</el-button>
        <el-button v-if="row.status === 0" link type="danger" @click="toggle(row, 1)">禁用</el-button>
        <el-button v-else link type="primary" @click="toggle(row, 0)">启用</el-button>
      </template>
    </el-table-column>
  </el-table>
  </template>
  <el-pagination v-if="!record && total > 0" class="admin-list-pagination" :current-page="pageNum" :total="total" :page-size="10"
                 :pager-count="5" layout="total, prev, pager, next" @update:current-page="changePage" />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useAdminStore } from '../../store/admin'
import { useRoute, useRouter } from 'vue-router'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import AdminRecordCard from '../../components/AdminRecordCard.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, updateUserStatus, resetPassword } from '../../api/user'
import { formatTime } from '../../utils/date'
import { resolveAssetUrl } from '../../utils/image'
import { listPage } from '../../layout/adminNavigation.mjs'
import { searchKeyword, searchRecordId } from '../../utils/searchQuery.mjs'

const admin = useAdminStore()
const canResetPassword = computed(() => admin.can('user:password:reset'))
const route = useRoute()
const router = useRouter()
const record = computed(() => searchRecordId(route.query.record))
const listRoute = computed(() => {
  const { record, ...query } = route.query
  return { name: 'UserList', query }
})
const keyword = ref('')
const status = ref(undefined)
const list = ref([])
const pageNum = computed(() => listPage(route.query.page))
const appliedKeyword = computed(() => searchKeyword(route.query.q) || undefined)
const appliedStatus = computed(() => ['0', '1'].includes(route.query.status) ? Number(route.query.status) : undefined)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
let requestVersion = 0

function search() {
  const q = searchKeyword(keyword.value) || undefined
  const selected = [0, 1].includes(status.value) ? status.value : undefined
  if (q === appliedKeyword.value && selected === appliedStatus.value && pageNum.value === 1) {
    load()
    return
  }
  router.replace({ query: { ...route.query, q, status: selected?.toString(), page: undefined } })
}

function resetSearch() {
  keyword.value = ''
  status.value = undefined
  search()
}

function changePage(value) {
  router.replace({ query: { ...route.query, page: value > 1 ? String(value) : undefined } })
}

async function load() {
  const version = ++requestVersion
  loading.value = true
  loadError.value = ''
  const recordId = record.value
  if (recordId) list.value = []
  try {
    const res = await listUsers({ record: recordId, keyword: recordId ? undefined : appliedKeyword.value,
      status: recordId ? undefined : appliedStatus.value, pageNum: pageNum.value, pageSize: 10 })
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
      loadError.value = error.message || '无法读取用户记录。'
    }
  } finally {
    if (version === requestVersion) loading.value = false
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
  await ElMessageBox.confirm(`确定将「${row.nickname}」的密码并撤销已有会话吗？`, '提示', { type: 'warning' })
  const { temporaryPassword } = await resetPassword(row.id)
  await ElMessageBox.alert(temporaryPassword, '临时密码（24 小时内有效），请安全转交')
}

watch([record, appliedKeyword, appliedStatus], ([, q, selected]) => {
  keyword.value = q || ''
  status.value = selected
}, { immediate: true })

watch([record, appliedKeyword, appliedStatus, pageNum], load, { immediate: true })
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
.mobile-users { display: none; }
@media (max-width: 680px) {
  .admin-list-table { display: none; }
  .mobile-users { display: block; }
  .mobile-users ul { display: grid; gap: 8px; padding: 0; margin: 0; list-style: none; }
  .mobile-user {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto;
    align-items: center;
    gap: 6px 10px;
    padding: 10px 12px;
    border: 1px solid var(--line);
    border-radius: 10px;
    background: var(--surface);
  }
  .mobile-user-info { overflow-wrap: anywhere; }
  .mobile-user-info small { display: block; color: var(--ink-2); }
  .mobile-user-detail {
    grid-column: 2 / -1;
    justify-self: end;
    display: flex;
    align-items: center;
    min-height: 32px;
    color: var(--brand-strong);
  }
}
</style>
