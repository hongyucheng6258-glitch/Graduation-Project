<template>
  <WtPageHeader title="闲置详情" subtitle="查看物品详情与交换条件" eyebrow="校园服务" />

  <div class="detail" v-loading="loading">
    <el-card v-if="item">
      <div class="layout">
        <!-- 图左 -->
        <div class="gallery">
          <el-carousel v-if="detailImages.length" height="360px" :arrow="detailImages.length > 1 ? 'hover' : 'never'">
          <span v-if="isSchematic" class="schematic-flag">示意图</span>
            <el-carousel-item v-for="img in detailImages" :key="img">
              <el-image :src="img" fit="contain" style="width:100%;height:100%" :preview-src-list="detailImages" lazy @error="onImgError($event, img)" />
            </el-carousel-item>
          </el-carousel>
          <el-empty v-else description="暂无图片" :image-size="80" />
        </div>
        <!-- 右侧信息 -->
        <div class="info">
          <h2>{{ item.title }}</h2>
          <div class="meta">
            <el-tag v-if="item.category">{{ item.category }}</el-tag>
            <el-tag :type="statusType">{{ statusText }}</el-tag>
            <span class="views">👁 {{ item.viewCount }} 次浏览</span>
          </div>
          <div class="expect">期望换物：<b>{{ item.expectItem || '面议' }}</b></div>
          <div class="desc">{{ item.description }}</div>
          <div class="publisher" style="cursor:pointer" @click="goUser(item.userId)">
            <el-avatar :size="36" :src="item.publisherAvatar">{{ item.publisherNickname?.charAt(0) }}</el-avatar>
            <div>
              <div style="color:var(--brand-strong);font-weight:600">{{ item.publisherNickname }}</div>
              <div class="score" v-if="item.sellerAvgScore">历史评分 ⭐ {{ item.sellerAvgScore.toFixed(1) }}</div>
            </div>
          </div>
          <div class="actions">
            <template v-if="!item.isOwner">
              <el-button v-if="item.reviewAppointmentId && !item.reviewed" type="warning" size="large" @click="openReview">
                评价
              </el-button>
              <el-button v-else-if="item.reviewAppointmentId && item.reviewed" disabled>已评价</el-button>
              <el-button v-else-if="item.myAppointmentId" disabled>已预约，等待卖家处理</el-button>
              <el-button v-else type="primary" size="large" :disabled="item.status !== 0" @click="appointVisible = true">
                {{ item.status === 0 ? '发起预约互换' : '该物品暂不可预约' }}
              </el-button>
              <el-button size="large" @click="contactPublisher">私信卖家</el-button>
            </template>
            <template v-else>
              <el-button type="danger" plain @click="offline">下架</el-button>
              <el-button v-if="item.reviewAppointmentId && !item.reviewed" type="warning" size="large" @click="openReview">
                评价
              </el-button>
              <el-button v-else-if="item.reviewAppointmentId && item.reviewed" disabled>已评价</el-button>
            </template>
            <el-button text type="warning" @click="reportVisible = true">举报</el-button>
            <el-button text :type="favorited ? 'warning' : 'default'" @click="toggleFavorite">
              {{ favorited ? '★ 已收藏' : '☆ 收藏' }}
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 预约弹窗 -->
    <el-dialog v-model="appointVisible" title="发起预约" width="440px">
      <el-input
        v-model="appointMsg"
        type="textarea"
        :rows="3"
        placeholder="给卖家留言（交换方式、时间地点等）"
        maxlength="255"
      />
      <template #footer>
        <el-button @click="appointVisible = false">取消</el-button>
        <el-button type="primary" :loading="appointing" @click="doAppoint">确认预约</el-button>
      </template>
    </el-dialog>

    <!-- 交易互评弹窗 -->
    <el-dialog v-model="reviewVisible" title="交易互评" width="420px">
      <div class="rate-row">
        <span>评分：</span>
        <el-rate v-model="reviewForm.score" />
      </div>
      <el-input
        v-model="reviewForm.content"
        type="textarea"
        :rows="3"
        placeholder="说说这次互换体验…"
        maxlength="255"
      />
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewing" @click="doReview">提交评价</el-button>
      </template>
    </el-dialog>

    <!-- 举报弹窗 -->
    <el-dialog v-model="reportVisible" title="举报该内容" width="440px">
      <el-form label-width="80px">
        <el-form-item label="举报类型">
          <el-select v-model="reportForm.reasonType">
            <el-option label="虚假/欺诈信息" value="欺诈" />
            <el-option label="违规内容" value="违规" />
            <el-option label="广告骚扰" value="广告" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="补充说明">
          <el-input v-model="reportForm.reason" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" @click="doReport">提交举报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { idleDetail, appoint, offlineIdle, reviewAppoint } from '../../api/idle'
import { submitReport } from '../../api/report'
import { favoriteStatus, favorite, unfavorite } from '../../api/favorite'
import { useUserStore } from '../../store/user'
import { startChat } from '../../utils/startChat'
import { firstContentImage } from '../../utils/content-assets.mjs'
import { onImageError, isFallbackSrc } from '../../utils/content-assets.mjs'
import { normalizeImages } from '../../utils/image'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const id = Number(route.params.id)
const item = ref(null)
const loading = ref(false)
const detailImages = computed(() => {
  if (!item.value) return []
  const images = normalizeImages(item.value)
  return images.length ? images : [firstContentImage(item.value, 'idle', item.value.category)].filter(Boolean)
})
const isSchematic = computed(() => detailImages.value.length === 1 && isFallbackSrc(detailImages.value[0], 'idle', item.value?.category))
function onImgError(event, img) {
  if (isSchematic) return
  onImageError(event, 'idle', item.value?.category)
}
const appointVisible = ref(false)
const appointMsg = ref('')
const appointing = ref(false)
const reportVisible = ref(false)
const reportForm = reactive({ reasonType: '违规', reason: '' })
const reviewVisible = ref(false)
const reviewing = ref(false)
const reviewForm = reactive({ score: 5, content: '' })
const favorited = ref(false)

const statusText = computed(() => ['在架', '已预约', '已完成', '已下架'][item.value?.status] ?? '')
const statusType = computed(() => ['success', 'warning', 'info', 'danger'][item.value?.status] ?? 'info')

async function load() {
  loading.value = true
  try {
    item.value = await idleDetail(id)
    if (userStore.isLoggedIn) {
      favorited.value = await favoriteStatus('idle', id)
    }
  } finally {
    loading.value = false
  }
}

async function toggleFavorite() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  if (favorited.value) {
    await unfavorite('idle', id)
    favorited.value = false
    ElMessage.success('已取消收藏')
  } else {
    await favorite('idle', id)
    favorited.value = true
    ElMessage.success('收藏成功')
  }
}

async function doAppoint() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  appointing.value = true
  try {
    await appoint(id, { message: appointMsg.value })
    ElMessage.success('预约已发起，等待卖家确认')
    appointVisible.value = false
    await load()
  } finally {
    appointing.value = false
  }
}

async function offline() {
  try {
    await ElMessageBox.confirm('确定下架该物品吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await offlineIdle(id)
  ElMessage.success('已下架')
  router.push('/idle')
}

function goUser(id) {
  if (!id) return
  if (Number(id) === Number(userStore.userInfo?.id)) router.push('/profile')
  else router.push(`/user/${id}`)
}

async function contactPublisher() {
  await startChat(router, userStore, item.value?.userId, { type: 'idle', id, title: item.value?.title })
}

function openReview() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  reviewForm.score = 5
  reviewForm.content = ''
  reviewVisible.value = true
}

async function doReview() {
  const appointmentId = item.value?.reviewAppointmentId || item.value?.myAppointmentId
  if (!appointmentId) return
  reviewing.value = true
  try {
    await reviewAppoint(appointmentId, reviewForm)
    ElMessage.success('评价成功')
    reviewVisible.value = false
    await load()
  } finally {
    reviewing.value = false
  }
}

async function doReport() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  await submitReport({
    targetType: 'idle',
    targetId: id,
    reasonType: reportForm.reasonType,
    reason: reportForm.reason
  })
  ElMessage.success('举报已提交，管理员会尽快处理')
  reportVisible.value = false
}

onMounted(load)
</script>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 480px 1fr;
  gap: 24px;
}
.gallery {
  background: var(--surface-2);
  border-radius: 8px;
}
.info h2 {
  margin-bottom: 12px;
}
.meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.views {
  font-size: 12px;
  color: var(--ink-3);
}
.expect {
  color: var(--warning);
  margin-bottom: 12px;
}
.desc {
  color: var(--ink-2);
  line-height: 1.8;
  white-space: pre-wrap;
  margin-bottom: 16px;
}
.publisher {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 12px;
  background: var(--surface-2);
  border-radius: 8px;
  margin-bottom: 16px;
}
.score {
  font-size: 12px;
  color: var(--warning);
}
.actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.rate-row {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
</style>

<style scoped>
.schematic-flag {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 3;
  font-size: 12px;
  padding: 3px 8px;
  border-radius: 4px;
  color: #fff;
  background: rgba(15, 23, 42, 0.62);
}
</style>
