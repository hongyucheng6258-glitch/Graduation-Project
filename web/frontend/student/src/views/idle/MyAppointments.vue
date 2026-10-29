<template>
  <WtPageHeader title="我的预约" subtitle="我申请交换的闲置物品" eyebrow="我的" />

  <div class="my-appointments">
    <el-card>
      <template #header><h3>我的预约（闲置互换）</h3></template>
      <el-tabs v-model="role" @tab-change="load">
        <el-tab-pane label="我发起的（买家）" name="buyer" />
        <el-tab-pane label="我收到的（卖家）" name="seller" />
      </el-tabs>
      <el-table :data="list" v-loading="loading">
        <el-table-column label="物品" min-width="180">
          <template #default="{ row }">
            <div class="item-cell">
              <el-image v-if="row.itemImage" :src="row.itemImage" fit="cover" class="thumb" />
              <span>{{ row.itemTitle }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="role === 'buyer' ? '卖家' : '买家'" width="120">
          <template #default="{ row }">{{ role === 'buyer' ? row.sellerNickname : row.buyerNickname }}</template>
        </el-table-column>
        <el-table-column prop="message" label="留言" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <!-- 卖家处理待确认预约 -->
            <template v-if="role === 'seller' && row.status === 0">
              <el-button size="small" type="success" @click="handle(row, true)">接受</el-button>
              <el-button size="small" type="danger" @click="handle(row, false)">拒绝</el-button>
            </template>
            <!-- 已接受 → 确认完成 -->
            <el-button v-if="row.status === 1" size="small" type="primary" @click="finish(row)">确认完成</el-button>
            <!-- 已完成 → 互评 -->
            <el-button v-if="row.status === 3 && !row.reviewed" size="small" type="warning" @click="openReview(row)">评价</el-button>
            <span v-if="row.status === 3 && row.reviewed" class="done-text">已评价</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pageNum"
        :total="total"
        :page-size="10"
        layout="prev, pager, next"
        style="margin-top: 16px"
        @current-change="load"
      />
    </el-card>

    <!-- 互评弹窗 -->
    <el-dialog v-model="reviewVisible" title="交易互评" width="420px">
      <div class="rate-row">
        <span>评分：</span>
        <el-rate v-model="reviewForm.score" />
      </div>
      <el-input v-model="reviewForm.content" type="textarea" :rows="3" placeholder="说说这次互换体验…" maxlength="255" />
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewing" @click="doReview">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { ElMessage } from 'element-plus'
import { myAppointments, handleAppoint, finishAppoint, reviewAppoint } from '../../api/idle'

const route = useRoute()
// 从消息中心跳转时通过 ?role=buyer/seller 指定初始标签
const role = ref(route.query.role === 'seller' ? 'seller' : 'buyer')
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const reviewVisible = ref(false)
const reviewing = ref(false)
const currentRow = ref(null)
const reviewForm = reactive({ score: 5, content: '' })

const STATUS = ['待确认', '已接受', '已拒绝', '已完成', '已取消']
const TYPES = ['warning', 'success', 'danger', 'info', 'info']
const statusText = (s) => STATUS[s] ?? ''
const statusType = (s) => TYPES[s] ?? 'info'

async function load() {
  loading.value = true
  try {
    const res = await myAppointments({ role: role.value, pageNum: pageNum.value, pageSize: 10 })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handle(row, accept) {
  await handleAppoint(row.id, accept)
  ElMessage.success(accept ? '已接受预约' : '已拒绝预约')
  load()
}

async function finish(row) {
  await finishAppoint(row.id)
  ElMessage.success('交易已完成，快去互评吧')
  load()
}

function openReview(row) {
  currentRow.value = row
  reviewForm.score = 5
  reviewForm.content = ''
  reviewVisible.value = true
}

async function doReview() {
  reviewing.value = true
  try {
    await reviewAppoint(currentRow.value.id, reviewForm)
    ElMessage.success('评价成功')
    reviewVisible.value = false
    load()
  } finally {
    reviewing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.item-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.thumb {
  width: 40px;
  height: 40px;
  border-radius: 4px;
}
.rate-row {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
.done-text {
  font-size: 12px;
  color: var(--ink-3);
}
</style>
