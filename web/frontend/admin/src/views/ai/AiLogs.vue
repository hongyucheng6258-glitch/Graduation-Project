<template>
  <WtPageHeader title="AI 调用日志" subtitle="查询调用记录、场景、Token 用量、耗时与结果。" />

  <div class="admin-list-toolbar">
    <el-input-number v-model="userId" placeholder="用户 ID" aria-label="用户 ID"
                     :controls="false" style="width: 120px" />
    <el-select v-model="scene" placeholder="全部场景" aria-label="场景" clearable style="width: 150px" @change="search">
      <el-option label="答疑 chat" value="chat" />
      <el-option label="PDF问答 pdf" value="pdf" />
      <el-option label="代码纠错 code_fix" value="code_fix" />
      <el-option label="提纲 outline" value="outline" />
      <el-option label="习题 quiz" value="quiz" />
    </el-select>
    <el-select v-model="status" placeholder="全部结果" aria-label="结果" clearable style="width: 120px" @change="search">
      <el-option label="成功" :value="0" />
      <el-option label="失败" :value="1" />
    </el-select>
    <el-button type="primary" @click="search">查询</el-button>
  </div>
  <el-table class="admin-list-table" :data="list" v-loading="loading" empty-text="暂无调用日志">
    <el-table-column prop="id" label="ID" width="70" />
    <el-table-column prop="userId" label="用户 ID" width="85" />
    <el-table-column prop="scene" label="场景" min-width="110" show-overflow-tooltip />
    <el-table-column prop="model" label="模型" min-width="140" show-overflow-tooltip />
    <el-table-column label="Token 用量" width="115">
      <template #default="{ row }">{{ row.promptTokens }} + {{ row.completionTokens }}</template>
    </el-table-column>
    <el-table-column label="耗时" width="80">
      <template #default="{ row }">{{ (row.costMs / 1000).toFixed(1) }}s</template>
    </el-table-column>
    <el-table-column label="结果" width="80">
      <template #default="{ row }">
        <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
          {{ row.status === 0 ? '成功' : '失败' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="时间" width="168">
      <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
    </el-table-column>
    <el-table-column prop="errorMsg" label="错误信息" min-width="140" show-overflow-tooltip />
  </el-table>
  <el-pagination class="admin-list-pagination" v-model:current-page="pageNum" :total="total" :page-size="10"
                 layout="total, prev, pager, next" @current-change="load" />
</template>

<script setup>
import { onMounted, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { aiLogs } from '../../api/aiConfig'
import { formatTime } from '../../utils/date'

const userId = ref(undefined)
const scene = ref(undefined)
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
    const res = await aiLogs({
      userId: userId.value || undefined,
      scene: scene.value,
      status: status.value,
      pageNum: pageNum.value,
      pageSize: 10
    })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
