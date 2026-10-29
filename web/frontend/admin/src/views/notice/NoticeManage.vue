<template>
  <WtPageHeader title="公告管理" subtitle="发布与维护校园公告" eyebrow="管理后台" />

  <el-card>
    <template #header>
      <div class="head">
        <span>公告管理</span>
        <el-button type="primary" @click="$router.push('/notice/edit')">＋ 新建公告</el-button>
      </div>
    </template>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="['info','success','warning'][row.status]">
            {{ ['草稿','已发布','已下线'][row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" width="170">
        <template #default="{ row }">{{ formatTime(row.publishTime) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="$router.push(`/notice/edit/${row.id}`)">编辑</el-button>
          <el-button v-if="row.status !== 1" size="small" type="success" @click="publish(row)">发布</el-button>
          <el-button v-if="row.status === 1" size="small" type="warning" @click="offline(row)">下线</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="pageNum" :total="total" :page-size="10"
                   layout="total, prev, pager, next" style="margin-top: 16px" @current-change="load" />
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { formatTime } from '../../utils/date'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { noticeList, publishNotice, offlineNotice, deleteNotice } from '../../api/notice'

const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await noticeList({ pageNum: pageNum.value, pageSize: 10 })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function publish(row) {
  await publishNotice(row.id)
  ElMessage.success('已发布，三端即时可见')
  load()
}

async function offline(row) {
  await offlineNotice(row.id)
  ElMessage.success('已下线')
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？`, '提示', { type: 'warning' })
  await deleteNotice(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
