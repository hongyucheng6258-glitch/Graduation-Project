<template>
  <WtPageHeader title="举报处理" subtitle="查看与处置用户举报" eyebrow="管理后台" />

  <el-card>
    <template #header>举报处理</template>
    <div class="toolbar">
      <el-radio-group v-model="status" @change="search">
        <el-radio-button :value="undefined">全部</el-radio-button>
        <el-radio-button :value="0">待处理</el-radio-button>
        <el-radio-button :value="1">已处理</el-radio-button>
      </el-radio-group>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="reporterId" label="举报人ID" width="90" />
      <el-table-column label="举报对象" width="150">
        <template #default="{ row }">
          <el-tag size="small">{{ typeName(row.targetType) }}</el-tag> #{{ row.targetId }}
        </template>
      </el-table-column>
      <el-table-column prop="reasonType" label="类型" width="90" />
      <el-table-column prop="reason" label="举报理由" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'warning' : 'success'">
            {{ row.status === 0 ? '待处理' : '已处理' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="handleResult" label="处置结果" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" size="small" type="primary" @click="openHandle(row)">处置</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="pageNum" :total="total" :page-size="10"
                   layout="total, prev, pager, next" style="margin-top: 16px" @current-change="load" />

    <!-- 处置弹窗 -->
    <el-dialog v-model="handleVisible" title="举报处置" width="460px">
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
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { ElMessage } from 'element-plus'
import { reportList, handleReport } from '../../api/report'

const status = ref(undefined)
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
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
  loading.value = true
  try {
    const res = await reportList({ status: status.value, pageNum: pageNum.value, pageSize: 10 })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function openHandle(row) {
  currentRow.value = row
  handleForm.action = row.targetType === 'user' ? 'warn' : 'offline'
  handleForm.handleResult = ''
  handleVisible.value = true
}

async function doHandle() {
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

onMounted(load)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>
