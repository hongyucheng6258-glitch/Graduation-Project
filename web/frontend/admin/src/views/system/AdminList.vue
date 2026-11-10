<template>
  <WtPageHeader title="管理员账号" subtitle="查看管理员列表、新建账号和设置角色（仅超级管理员）。" />

  <div class="admin-list-toolbar">
    <el-button type="primary" @click="openEdit()">＋ 新建管理员</el-button>
  </div>
  <el-table class="admin-list-table" :data="list" v-loading="loading" empty-text="暂无管理员账号">
    <el-table-column prop="id" label="ID" width="70" />
    <el-table-column prop="username" label="账号" min-width="135" show-overflow-tooltip />
    <el-table-column prop="nickname" label="昵称" min-width="135" show-overflow-tooltip />
    <el-table-column label="角色" min-width="115">
      <template #default="{ row }">
        <el-tag :class="{ 'super-role': row.role === 'super' }" type="primary" size="small">
          {{ row.role === 'super' ? '超级管理员' : '审核员' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="86">
      <template #default="{ row }">
        <el-tag v-if="row.status === 0" type="success" size="small">正常</el-tag>
        <el-tag v-else-if="row.status === 1" type="danger" size="small">禁用</el-tag>
        <span v-else>—</span>
      </template>
    </el-table-column>
    <el-table-column label="创建时间" width="168">
      <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
    </el-table-column>
    <el-table-column label="操作" min-width="84">
      <template #default="{ row }">
        <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination class="admin-list-pagination" v-model:current-page="pageNum" :total="total" :page-size="10"
                 layout="total, prev, pager, next" @current-change="load" />

  <el-dialog v-model="editVisible" :title="editForm.id ? '编辑管理员' : '新建管理员'" width="min(440px, calc(100vw - 32px))">
    <el-form label-position="top">
      <el-form-item label="用户名" required>
        <el-input v-model="editForm.username" :disabled="!!editForm.id" />
      </el-form-item>
      <el-form-item :label="editForm.id ? '新密码' : '密码'" :required="!editForm.id">
        <el-input v-model="editForm.password" type="password" show-password
                  :placeholder="editForm.id ? '留空则不修改' : '6位以上'" />
      </el-form-item>
      <el-form-item label="昵称"><el-input v-model="editForm.nickname" /></el-form-item>
      <el-form-item label="角色">
        <el-select v-model="editForm.role">
          <el-option label="审核员 audit" value="audit" />
          <el-option label="超级管理员 super" value="super" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { ElMessage } from 'element-plus'
import { listAdmins, createAdmin, updateAdmin } from '../../api/user'
import { formatTime } from '../../utils/date'

const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const editVisible = ref(false)
const editForm = reactive({ id: null, username: '', password: '', nickname: '', role: 'audit' })

async function load() {
  loading.value = true
  try {
    const res = await listAdmins({ pageNum: pageNum.value, pageSize: 10 })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  if (row) {
    Object.assign(editForm, { id: row.id, username: row.username, password: '', nickname: row.nickname, role: row.role })
  } else {
    Object.assign(editForm, { id: null, username: '', password: '', nickname: '', role: 'audit' })
  }
  editVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (editForm.id) {
      await updateAdmin(editForm.id, editForm)
    } else {
      await createAdmin(editForm)
    }
    ElMessage.success('已保存')
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.super-role {
  --el-tag-bg-color: var(--purple-soft);
  --el-tag-text-color: var(--purple);
  --el-tag-border-color: var(--purple-line);
}
</style>
