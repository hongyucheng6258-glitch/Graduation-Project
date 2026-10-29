<template>
  <WtPageHeader title="招领详情" subtitle="查看失物信息并联系失主" eyebrow="校园服务" />

  <div class="detail" v-loading="loading">
    <el-card v-if="lf">
      <div class="layout">
        <div class="gallery">
          <el-carousel v-if="detailImages.length" height="340px" :arrow="detailImages.length > 1 ? 'hover' : 'never'">
            <el-carousel-item v-for="img in detailImages" :key="img">
              <el-image :src="img" fit="contain" style="width:100%;height:100%" :preview-src-list="detailImages" />
            </el-carousel-item>
          </el-carousel>
          <el-empty v-else description="无图片" :image-size="80" />
        </div>
        <div class="info">
          <h2>
            <el-tag :type="lf.type === 0 ? 'danger' : 'success'" style="margin-right: 8px">
              {{ lf.type === 0 ? '失物' : '招领' }}
            </el-tag>
            {{ lf.title }}
          </h2>
          <el-tag v-if="lf.status === 1" type="info">✅ 已完成</el-tag>
          <div class="kv">📍 地点：{{ lf.location || '未填写' }}</div>
          <div class="kv">🕐 时间：{{ formatTime(lf.happenTime) || '未填写' }}</div>
          <div class="desc">{{ lf.description }}</div>
          <div class="contact">
            <b>联系方式：</b>{{ lf.contact || '请通过消息联系发布者' }}
          </div>
          <div class="publisher" style="cursor:pointer" @click="goUser(lf.userId)">
            <el-avatar :size="36" :src="lf.publisherAvatar">{{ lf.publisherNickname?.charAt(0) }}</el-avatar>
            <span style="color:var(--brand-strong);font-weight:600">{{ lf.publisherNickname }}</span>
          </div>

          <!-- 我的认领状态（非发布者） -->
          <el-alert v-if="!lf.isOwner && myClaim" :type="myClaim.status === 1 ? 'success' : 'warning'"
                    :closable="false" style="margin-bottom: 12px">
            <template #title>
              {{ claimStatusText(myClaim.status) }}
            </template>
          </el-alert>

          <div class="actions">
            <el-button v-if="lf.isOwner && lf.status === 0" type="success" @click="finish">标记为已完成</el-button>
            <el-button v-if="lf.isOwner && lf.status === 0" type="primary" plain @click="editInfo">编辑信息</el-button>
            <el-button v-if="lf.isOwner && lf.type === 1 && lf.status === 0" type="warning" plain @click="openClaims">
              认领申请<el-badge v-if="claims.length" :value="claims.filter(c => c.status === 0).length" style="margin-left: 6px" />
            </el-button>
            <el-button v-if="!lf.isOwner && lf.type === 1 && lf.status === 0 && !myClaim"
                       type="primary" @click="claimVisible = true">申请认领</el-button>
            <el-button v-if="!lf.isOwner && myClaim && myClaim.status === 1" type="success" @click="confirmReturn">确认已找回</el-button>
            <el-button v-if="!lf.isOwner" type="primary" plain @click="contactPublisher">私信发布者</el-button>
            <el-button v-if="!lf.isOwner" text type="warning" @click="reportVisible = true">举报</el-button>
            <el-button text :type="favorited ? 'warning' : 'default'" @click="toggleFavorite">
              {{ favorited ? '★ 已收藏' : '☆ 收藏' }}
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 申请认领弹窗 -->
    <el-dialog v-model="claimVisible" title="申请认领" width="440px">
      <p style="margin-top:0;color:var(--ink-2)">请填写物品特征说明（如卡号、证件信息等）和联系方式，发布者核实后将与你联系。</p>
      <el-input v-model="claimForm.message" type="textarea" :rows="3" maxlength="255" placeholder="物品特征/证明信息…" />
      <el-input v-model="claimForm.contact" style="margin-top:10px" maxlength="64" placeholder="联系方式（手机/微信/QQ）" />
      <template #footer>
        <el-button @click="claimVisible = false">取消</el-button>
        <el-button type="primary" :loading="claiming" @click="doClaim">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 认领申请管理弹窗（发布者） -->
    <el-dialog v-model="claimsVisible" title="认领申请管理" width="680px" destroy-on-close>
      <el-table :data="claims" v-loading="claimsLoading" size="small">
        <el-table-column prop="claimNickname" label="申请人" width="100" />
        <el-table-column prop="message" label="认领说明" min-width="160" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系方式" width="120" show-overflow-tooltip />
        <el-table-column label="申请时间" width="150">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="['warning','success','danger','info'][row.status]">{{ claimStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button size="small" type="success" @click="doHandleClaim(row, true)">同意</el-button>
              <el-button size="small" type="danger" @click="doHandleClaim(row, false)">拒绝</el-button>
            </template>
            <span v-else-if="row.status === 1" style="color:var(--ink-3);font-size:12px">等待失主确认</span>
            <span v-else style="color:var(--ink-3);font-size:12px">已处理</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!claimsLoading && !claims.length" description="暂无认领申请" />
      <div v-if="claimTotal > claimPageSize" class="claim-pager">
        <el-pagination
          layout="prev, pager, next"
          :total="claimTotal"
          :page-size="claimPageSize"
          :current-page="claimPage"
          background
          small
          @current-change="onClaimPageChange"
        />
      </div>
    </el-dialog>

    <el-dialog v-model="reportVisible" title="举报该信息" width="440px">
      <el-select v-model="reasonType" style="width: 100%; margin-bottom: 10px">
        <el-option label="虚假/欺诈信息" value="欺诈" />
        <el-option label="违规内容" value="违规" />
        <el-option label="广告骚扰" value="广告" />
        <el-option label="其他" value="其他" />
      </el-select>
      <el-input v-model="reason" type="textarea" :rows="3" maxlength="500" />
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
import { ElMessage } from 'element-plus'
import { lostFoundDetail, finishLostFound, updateLostFound, claimLostFound,
         lostFoundClaims, myClaim as fetchMyClaim, handleClaim, confirmClaim } from '../../api/lostfound'
import { submitReport } from '../../api/report'
import { favoriteStatus, favorite, unfavorite } from '../../api/favorite'
import { formatTime } from '../../utils/date'
import { useUserStore } from '../../store/user'
import { startChat } from '../../utils/startChat'
import { firstContentImage } from '../../utils/content-assets.mjs'
import { normalizeImages } from '../../utils/image'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const id = Number(route.params.id)
const lf = ref(null)
const detailImages = computed(() => {
  if (!lf.value) return []
  const images = normalizeImages(lf.value)
  return images.length ? images : [firstContentImage(lf.value, 'lost')].filter(Boolean)
})
const loading = ref(false)
const reportVisible = ref(false)
const reasonType = ref('违规')
const reason = ref('')
const favorited = ref(false)

const claimVisible = ref(false)
const claiming = ref(false)
const claimForm = reactive({ message: '', contact: '' })
const claimsVisible = ref(false)
const claimsLoading = ref(false)
const claims = ref([])
const claimTotal = ref(0)
const claimPage = ref(1)
const claimPageSize = 10
const myClaim = ref(null)

const claimStatusText = (s) => ['待确认', '已同意（待归还）', '已拒绝', '已归还'][s] ?? ''

async function load() {
  loading.value = true
  try {
    lf.value = await lostFoundDetail(id)
    if (!lf.value.isOwner) {
      try {
        myClaim.value = await fetchMyClaim(id)
      } catch { myClaim.value = null }
    }
    if (userStore.isLoggedIn) {
      favorited.value = await favoriteStatus('lostfound', id)
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
    await unfavorite('lostfound', id)
    favorited.value = false
    ElMessage.success('已取消收藏')
  } else {
    await favorite('lostfound', id)
    favorited.value = true
    ElMessage.success('收藏成功')
  }
}

async function finish() {
  await finishLostFound(id)
  ElMessage.success('已标记完成')
  load()
}

function editInfo() {
  router.push(`/lostfound/publish?id=${id}`)
}

async function loadClaims(page = claimPage.value) {
  claimsLoading.value = true
  try {
    const res = await lostFoundClaims(id, { pageNum: page, pageSize: claimPageSize })
    claims.value = res?.list || []
    claimTotal.value = res?.total || 0
    claimPage.value = page
  } finally {
    claimsLoading.value = false
  }
}

function onClaimPageChange(page) {
  loadClaims(page)
}

async function openClaims() {
  claimsVisible.value = true
  await loadClaims(1)
}

async function doClaim() {
  if (!claimForm.message.trim() && !claimForm.contact.trim()) {
    ElMessage.warning('请填写认领说明或联系方式')
    return
  }
  claiming.value = true
  try {
    await claimLostFound(id, claimForm)
    ElMessage.success('认领申请已提交，等待发布者核实')
    claimVisible.value = false
    load()
  } finally {
    claiming.value = false
  }
}

async function doHandleClaim(row, accept) {
  await handleClaim(row.id, accept)
  ElMessage.success(accept ? '已同意认领' : '已拒绝认领')
  openClaims()
}

async function confirmReturn() {
  await confirmClaim(myClaim.value.id)
  ElMessage.success('已确认找回，感谢发布者！')
  load()
}

function goUser(id) {
  if (!id) return
  if (Number(id) === Number(userStore.userInfo?.id)) router.push('/profile')
  else router.push(`/user/${id}`)
}

async function contactPublisher() {
  await startChat(router, userStore, lf.value?.userId, { type: 'lostfound', id, title: lf.value?.title })
}

async function doReport() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  await submitReport({ targetType: 'lostfound', targetId: id, reasonType: reasonType.value, reason: reason.value })
  ElMessage.success('举报已提交')
  reportVisible.value = false
}

onMounted(load)
</script>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 440px 1fr;
  gap: 24px;
}
.gallery {
  background: var(--surface-2);
  border-radius: 8px;
}
.kv {
  color: var(--ink-2);
  font-size: 14px;
  margin: 8px 0;
}
.desc {
  color: var(--ink-2);
  line-height: 1.8;
  white-space: pre-wrap;
  margin: 12px 0;
}
.contact {
  background: #fdf6ec;
  padding: 10px 14px;
  border-radius: 6px;
  color: #b88230;
  margin-bottom: 14px;
}
.publisher {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
}
.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
</style>

<style scoped>
.claim-pager { display: flex; justify-content: center; margin-top: 12px; }
</style>
