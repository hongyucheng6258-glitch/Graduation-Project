<template>
  <div class="my-appointments record-page">
    <WtPageHeader title="我的预约" subtitle="查看发起和收到的闲置互换预约" eyebrow="我的" />
    <el-card>
      <el-tabs v-model="role" @tab-change="changeRole">
        <el-tab-pane label="我发起的" name="buyer" />
        <el-tab-pane label="我收到的" name="seller" />
      </el-tabs>
      <el-button v-if="linkedId" size="small" text @click="linkedId = null; load()">查看全部预约</el-button>
      <div v-loading="loading" class="record-list">
        <article v-for="row in list" :key="row.id" class="record-row">
          <header class="record-head">
            <div class="item-cell">
              <el-image v-if="row.itemImage" :src="row.itemImage" fit="contain" class="thumb" />
              <strong>{{ row.itemTitle }}</strong>
            </div>
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </header>
          <div class="record-meta">
            <span>{{ role === 'buyer' ? '卖家' : '买家' }} {{ (role === 'buyer' ? row.sellerNickname : row.buyerNickname) || '校园同学' }}</span>
          </div>
          <p v-if="row.message" class="record-note">{{ row.message }}</p>
          <div class="record-actions">
            <template v-if="role === 'seller' && row.status === 0">
              <el-button size="small" type="success" @click="handle(row, true)">接受</el-button>
              <el-button size="small" type="danger" @click="handle(row, false)">拒绝</el-button>
            </template>
            <el-button v-if="row.status === 1" size="small" type="primary" :aria-expanded="handoffId === row.id"
              @click="handoffId = handoffId === row.id ? null : row.id">交接安排</el-button>
            <el-button v-if="row.status === 1 || (role === 'buyer' && row.status === 0)"
              size="small" :loading="cancellingId === row.id" :disabled="cancellingId !== null"
              @click="cancel(row)">取消预约</el-button>
            <el-button v-if="row.status === 3 && !row.reviewed" size="small" type="warning" @click="openReview(row)">评价</el-button>
            <span v-if="row.status === 3 && row.reviewed" class="done-text">已评价</span>
          </div>
          <IdleHandoffPanel v-if="row.status === 1 && handoffId === row.id" :appointment-id="row.id" :role="role" @updated="load" />
        </article>
        <EmptyBox v-if="!loading && !list.length" description="暂无预约记录" />
      </div>
      <el-pagination v-if="total > 10" v-model:current-page="pageNum" :total="total" :page-size="10"
        class="record-pager" layout="prev, pager, next" :pager-count="5" small @current-change="load" />
    </el-card>

    <!-- 互评弹窗 -->
    <el-dialog v-model="reviewVisible" title="交易互评" width="min(420px, calc(100vw - 24px))">
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
import EmptyBox from '../../components/EmptyBox.vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import IdleHandoffPanel from './IdleHandoffPanel.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myAppointments, handleAppoint, cancelAppoint, reviewAppoint } from '../../api/idle'

const route = useRoute()
// 从消息中心跳转时通过 ?role=buyer/seller 指定初始标签
const role = ref(route.query.role === 'seller' ? 'seller' : 'buyer')
const list = ref([])
const handoffId = ref(Number(route.query.appointmentId) || null)
const linkedId = ref(handoffId.value)
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const reviewVisible = ref(false)
const reviewing = ref(false)
const cancellingId = ref(null)
const currentRow = ref(null)
const reviewForm = reactive({ score: 5, content: '' })

const STATUS = ['待确认', '已接受', '已拒绝', '已完成', '已取消']
const TYPES = ['warning', 'success', 'danger', 'info', 'info']
const statusText = (s) => STATUS[s] ?? ''
const statusType = (s) => TYPES[s] ?? 'info'

function changeRole() {
  pageNum.value = 1
  handoffId.value = null
  linkedId.value = null
  load()
}

async function load() {
  loading.value = true
  try {
    const res = await myAppointments({ role: role.value, appointmentId: linkedId.value, pageNum: pageNum.value, pageSize: 10 })
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

async function cancel(row) {
  cancellingId.value = row.id
  try {
    const confirmed = await ElMessageBox.confirm('取消后原交接安排将失效，并通知对方。', '取消预约', {
      confirmButtonText: '确认取消', cancelButtonText: '保留预约', type: 'warning'
    }).then(() => true, () => false)
    if (!confirmed) return
    await cancelAppoint(row.id)
    if (handoffId.value === row.id) handoffId.value = null
    ElMessage.success('预约已取消')
    await load()
  } finally {
    cancellingId.value = null
  }
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

<style scoped src="../../styles/records.css" />

<style scoped>
.item-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.thumb {
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 4px;
  background: var(--surface-2);
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
