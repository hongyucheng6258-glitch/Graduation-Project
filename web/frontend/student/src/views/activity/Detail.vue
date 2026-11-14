<template>
  <div class="event-page" v-loading="loading">
    <template v-if="act">
      <nav class="detail-breadcrumb" aria-label="面包屑">
        <RouterLink to="/activity">校园活动</RouterLink>
        <span aria-hidden="true">›</span>
        <span aria-current="page">活动详情</span>
      </nav>

      <div class="event-detail-layout">
        <article class="event-overview">
          <div class="event-detail-heading">
            <div class="head-tags">
              <span class="tag" :class="statusTagCls(act.displayStatus)">{{ statusText }}</span>
              <span v-if="act.category" class="tag tag-category">{{ act.category }}</span>
            </div>
            <h1>{{ act.title }}</h1>
            <p v-if="act.subtitle">{{ act.subtitle }}</p>
          </div>
          <div class="detail-cover content-photo">
            <el-image v-if="detailImages.length" :src="detailImages[0]" fit="contain" class="detail-cover-img"
              :alt="act.title" :preview-src-list="detailImages" preview-teleported />
            <WtEventArt v-else :item="act" aria-hidden="true" />
            <span v-if="detailImages.length" class="cover-preview-label" aria-hidden="true">查看原图</span>
          </div>
          <dl class="event-facts">
            <div class="event-fact">
              <el-icon aria-hidden="true"><Calendar /></el-icon>
              <dt>活动时间</dt>
              <dd>
                <time v-if="act.startTime" :datetime="act.startTime">{{ eventTime(act.startTime) }}</time>
                <span v-else>时间待定</span>
                <span v-if="act.endTime" class="event-end">至 <time :datetime="act.endTime">{{ endTime }}</time></span>
              </dd>
            </div>
            <div class="event-fact">
              <el-icon aria-hidden="true"><Location /></el-icon>
              <dt>活动地点</dt>
              <dd>{{ act.location || '地点待定' }}</dd>
            </div>
          </dl>
          <div class="event-host">
            <el-avatar :size="36" :src="act.publisherAvatar">{{ act.publisherNickname?.charAt(0) || '梧' }}</el-avatar>
            <span class="host-identity">
              <b>{{ act.publisherNickname || '校园同学' }}</b>
              <small>活动发起方</small>
            </span>
            <button type="button" class="text-btn" @click="goUser(act.userId)">查看主页</button>
          </div>
        </article>

        <aside class="event-registration" aria-labelledby="registration-title">
          <div class="event-ticket">
            <h2 id="registration-title">{{ act.isOwner ? '活动管理' : '报名参与' }}</h2>
            <div class="registration-count">
              <span>已通过报名</span>
              <span><strong>{{ act.memberCount || 0 }}</strong><template v-if="act.maxMembers"> / {{ act.maxMembers }}</template> 人</span>
            </div>
            <div v-if="act.maxMembers" class="progress" aria-hidden="true">
              <i :style="{ width: capacityPercent + '%' }"></i>
            </div>
            <p v-if="ticketHint" class="ticket-hint">{{ ticketHint }}</p>
            <div v-if="act.signupDeadline" class="registration-deadline">
              <span>报名截止</span>
              <time :datetime="act.signupDeadline">{{ eventTime(act.signupDeadline) }}</time>
            </div>

            <!-- 报名操作区（保留全部原有逻辑） -->
            <div class="stack">
              <!-- 发布者视角：名单管理 + 签到二维码 -->
              <template v-if="act.isOwner">
                <button type="button" class="btn primary" @click="loadMembers()">报名名单管理</button>
                <button type="button" class="btn" @click="showQrcode">签到二维码</button>
              </template>
              <!-- 参与者视角 -->
              <template v-else>
                <button
                  v-if="act.mySignupStatus == null && !waitlistPriority"
                  type="button"
                  class="btn primary"
                  :disabled="!act.canSignup"
                  @click="signupVisible = true"
                >
                  {{ act.canSignup ? '立即报名' : (act.signupDisabledReason || '不可报名') }}
                </button>
                <span v-else-if="act.mySignupStatus === 0" class="ticket-tag warning">报名待审批</span>
                <span v-else-if="act.mySignupStatus === 1" class="ticket-tag success">
                  已通过报名{{ act.signedIn ? '（已签到）' : act.signinOpen ? '（可在活动现场签到）' : '（签到未开放）' }}
                </span>
                <span v-else-if="act.mySignupStatus != null" class="ticket-tag error">报名未通过</span>
                <button v-if="act.mySignupStatus === 1 && !act.signedIn && act.signinOpen" type="button" class="btn primary"
                  @click="signinVisible = true">活动签到</button>
                <ActivityWaitlist v-if="userStore.isLoggedIn" :activity-id="id" :signup-status="act.mySignupStatus" :occupied-seats="act.occupiedSeats"
                  @changed="load" @priority="waitlistPriority = $event" />
                <button
                  v-if="act.mySignupStatus === 0 || act.mySignupStatus === 1"
                  type="button"
                  class="btn"
                  :loading="canceling"
                  :disabled="canceling || act.signedIn"
                  @click="doCancelSignup"
                >
                  {{ act.signedIn ? '已签到不可取消' : '取消报名' }}
                </button>
                <button type="button" class="btn" @click="contactPublisher">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M21 11.5a8.5 8.5 0 0 1-12.5 7.5L3 21l2-5.5A8.5 8.5 0 1 1 21 11.5z"/></svg>
                  联系发布者
                </button>
              </template>
            </div>
          </div>
        </aside>
        <section class="event-description">
          <h2>活动介绍</h2>
          <p>{{ act.description?.trim() || '发起方暂未填写活动介绍。' }}</p>
          <div class="detail-actions">
            <button type="button" class="btn" :class="{ favorited }" @click="toggleFavorite">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 17.3 5.8 21l1.6-6.8L2 9.2l7-.6L12 2l3 6.6 7 .6-5.4 5 1.6 6.8z"/></svg>
              {{ favorited ? '已收藏' : '收藏' }}
            </button>
            <button type="button" class="text-btn" @click="reportVisible = true">举报此内容</button>
          </div>
        </section>
      </div>

      <!-- ===== 保留全部弹窗 ===== -->
      <!-- 报名弹窗 -->
      <el-dialog v-model="signupVisible" title="报名活动" width="min(440px, calc(100vw - 24px))">
        <SignupConflictNotice v-if="signupVisible && userStore.isLoggedIn" :activity-id="id"
          @checking="checkingConflicts = $event" />
        <el-input v-model="remark" type="textarea" :rows="3"
                  placeholder="报名说明/组队信息（如：计科2201张三，求组队）" maxlength="255" />
        <template #footer>
          <el-button @click="signupVisible = false">取消</el-button>
          <el-button type="primary" :loading="signing" :disabled="checkingConflicts" @click="doSignup">确认报名</el-button>
        </template>
      </el-dialog>

      <!-- 名单管理弹窗（发布者） -->
      <el-dialog v-model="membersVisible" title="报名名单" width="min(640px, calc(100vw - 24px))">
        <el-table :data="members" size="small">
          <el-table-column prop="nickname" label="昵称" width="100" />
          <el-table-column prop="studentNo" label="学号" width="110" />
          <el-table-column prop="remark" label="报名说明" min-width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="['warning','success','danger'][row.status]">
                {{ ['待审批','已通过','已拒绝'][row.status] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="签到" width="70">
            <template #default="{ row }">
              <el-tag v-if="row.signedIn" size="small" type="success">已签</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <template v-if="row.status === 0">
                <el-button size="small" type="success" link @click="approve(row, true)">通过</el-button>
                <el-button size="small" type="danger" link @click="approve(row, false)">拒绝</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-dialog>
        <div v-if="membersTotal > membersPageSize" class="members-pager">
          <el-pagination
            layout="prev, pager, next"
            :total="membersTotal"
            :page-size="membersPageSize"
            :current-page="membersPage"
            background
            small
            @current-change="onMembersPageChange"
          />
        </div>

      <!-- 签到二维码弹窗 -->
      <el-dialog v-model="qrVisible" title="活动签到" width="min(420px, calc(100vw - 24px))">
        <el-alert type="success" :closable="false" title="参与者可扫码，或复制签到内容到活动详情页提交。" />
        <div class="qr-code-wrap">
          <img v-if="qrImage" :src="qrImage" class="qr-code" alt="活动签到二维码" />
        </div>
        <div class="qr-content">{{ qrContent }}</div>
        <p class="qr-tip">如扫码不便，可复制上方签到内容手动录入。</p>
      </el-dialog>

      <el-dialog v-model="signinVisible" title="提交活动签到" width="min(420px, calc(100vw - 24px))">
        <form @submit.prevent="submitSignin">
          <label for="activity-signin-code">签到内容</label>
          <el-input id="activity-signin-code" v-model.trim="signinCode" placeholder="粘贴发布者提供的签到内容" />
          <el-button type="primary" native-type="submit" :loading="submittingSignin" style="margin-top: 12px">确认签到</el-button>
        </form>
      </el-dialog>

      <!-- 举报弹窗 -->
      <el-dialog v-model="reportVisible" title="举报该活动" width="min(440px, calc(100vw - 24px))">
        <el-select v-model="reportReasonType" style="width: 100%; margin-bottom: 10px">
          <el-option label="虚假/欺诈信息" value="欺诈" />
          <el-option label="违规内容" value="违规" />
          <el-option label="广告骚扰" value="广告" />
          <el-option label="其他" value="其他" />
        </el-select>
        <el-input v-model="reportReason" type="textarea" :rows="3" maxlength="500" placeholder="补充说明（可空）" />
        <template #footer>
          <el-button @click="reportVisible = false">取消</el-button>
          <el-button type="primary" @click="doReport">提交举报</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import QRCode from 'qrcode'
import dayjs from 'dayjs'
import { Calendar, Location } from '@element-plus/icons-vue'
import WtEventArt from '../../components/wt/WtEventArt.vue'
import SignupConflictNotice from './components/SignupConflictNotice.vue'
import ActivityWaitlist from './components/ActivityWaitlist.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { activityDetail, signupActivity, cancelActivitySignup, activityMembers, handleMember, signinQrcode, signinActivity } from '../../api/activity'
import { submitReport } from '../../api/report'
import { favoriteStatus, favorite, unfavorite } from '../../api/favorite'
import { normalizeImages } from '../../utils/image'
import { normalizeSigninQrContent, parseSigninQrContent } from '../../utils/signinQr.mjs'
import { useUserStore } from '../../store/user'
import { startChat } from '../../utils/startChat'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const id = Number(route.params.id)
const act = ref(null)
const detailImages = computed(() => normalizeImages(act.value))
const loading = ref(false)
const signupVisible = ref(false)
const remark = ref('')
const signing = ref(false)
const checkingConflicts = ref(false)
const waitlistPriority = ref(false)
const canceling = ref(false)
const membersVisible = ref(false)
const members = ref([])
const membersTotal = ref(0)
const membersPage = ref(1)
const membersPageSize = 10
const qrVisible = ref(false)
const signinVisible = ref(false)
const signinCode = ref('')
const submittingSignin = ref(false)
const qrContent = ref('')
const qrImage = ref('')
const reportVisible = ref(false)
const reportReasonType = ref('违规')
const reportReason = ref('')
const favorited = ref(false)

const STATUS_TEXT = ['报名中', '已满', '已结束', '已下架']
const statusText = computed(() => act.value?.displayStatusText || STATUS_TEXT[act.value?.status] || '报名中')

function statusTagCls(s) {
  return ['tag-success', 'tag-warning', 'tag-muted', 'tag-muted', 'tag-muted', 'tag-error'][s ?? 0] || 'tag-muted'
}

function eventTime(value) {
  return dayjs(value).format('YYYY年M月D日 HH:mm')
}

const endTime = computed(() => {
  const end = dayjs(act.value.endTime)
  return act.value.startTime && end.isSame(dayjs(act.value.startTime), 'day')
    ? end.format('HH:mm')
    : eventTime(act.value.endTime)
})
const occupiedSeats = computed(() => Number(act.value?.occupiedSeats ?? act.value?.memberCount) || 0)
const capacityPercent = computed(() => {
  const max = Number(act.value?.maxMembers)
  if (!max) return 0
  return Math.min(Math.round(occupiedSeats.value / max * 100), 100)
})
const ticketHint = computed(() => {
  if (!act.value) return ''
  const pending = occupiedSeats.value - (Number(act.value.memberCount) || 0)
  if (!act.value.canSignup) return pending > 0 && act.value.displayStatus === 1 ? `另有 ${pending} 人待审批，名额已占满` : ''
  const max = Number(act.value.maxMembers)
  return max > 0 ? `剩余 ${Math.max(0, max - occupiedSeats.value)} 个名额` : '人数不限'
})

async function load() {
  loading.value = true
  try {
    act.value = await activityDetail(id)
    if (userStore.isLoggedIn) {
      favorited.value = await favoriteStatus('activity', id)
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
    await unfavorite('activity', id)
    favorited.value = false
    ElMessage.success('已取消收藏')
  } else {
    await favorite('activity', id)
    favorited.value = true
    ElMessage.success('收藏成功')
  }
}

async function doSignup() {
  if (signing.value || checkingConflicts.value) return
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  signing.value = true
  try {
    await signupActivity(id, { remark: remark.value })
    ElMessage.success('报名已提交，等待发布者审批')
    signupVisible.value = false
    await load()
  } catch (error) {
    if (/人数已满|空位优先/.test(error.message)) {
      signupVisible.value = false
      await load()
    }
  } finally {
    signing.value = false
  }
}

async function doCancelSignup() {
  if (canceling.value) return
  try {
    await ElMessageBox.confirm('确定取消该活动的报名吗？', '取消报名', { type: 'warning' })
  } catch {
    return
  }
  canceling.value = true
  try {
    await cancelActivitySignup(id)
    ElMessage.success('已取消报名')
    load()
  } finally {
    canceling.value = false
  }
}

async function loadMembers(page = membersPage.value) {
  const res = await activityMembers(id, { pageNum: page, pageSize: membersPageSize })
  members.value = res?.list || []
  membersTotal.value = res?.total || 0
  membersPage.value = page
  membersVisible.value = true
}

function onMembersPageChange(page) {
  loadMembers(page)
}

async function approve(row, ok) {
  await handleMember(row.id, ok)
  ElMessage.success(ok ? '已通过' : '已拒绝')
  loadMembers(membersPage.value)
  load()
}
async function submitSignin() {
  if (submittingSignin.value) return
  const code = parseSigninQrContent(signinCode.value)
  if (!code || code.activityId !== id) {
    ElMessage.warning('请输入本活动的有效签到内容')
    return
  }
  submittingSignin.value = true
  try {
    await signinActivity(id, code.token)
    signinVisible.value = false
    signinCode.value = ''
    ElMessage.success('签到成功')
    await load()
  } finally {
    submittingSignin.value = false
  }
}

async function showQrcode() {
  const res = await signinQrcode(id)
  const content = normalizeSigninQrContent(res)
  if (!content) {
    ElMessage.error('签到内容生成失败，请重试')
    return
  }
  qrContent.value = content
  qrImage.value = await QRCode.toDataURL(content, {
    width: 240,
    margin: 2,
    errorCorrectionLevel: 'M'
  })
  qrVisible.value = true
}

function goUser(uid) {
  if (!uid) return
  if (Number(uid) === Number(userStore.userInfo?.id)) router.push('/profile')
  else router.push(`/user/${uid}`)
}

async function contactPublisher() {
  await startChat(router, userStore, act.value?.userId, { type: 'activity', id, title: act.value?.title })
}

async function doReport() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  await submitReport({ targetType: 'activity', targetId: id, reasonType: reportReasonType.value, reason: reportReason.value })
  ElMessage.success('举报已提交')
  reportVisible.value = false
}

onMounted(load)
</script>

<style scoped>
.event-page { min-width: 0; container-type: inline-size; }
.detail-breadcrumb {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 2px 0 16px;
  color: var(--ink-3);
  font-size: 12px;
}
.detail-breadcrumb a { color: var(--brand-strong); text-decoration: none; }
.detail-breadcrumb a:hover { text-decoration: underline; text-underline-offset: 3px; }
.detail-breadcrumb [aria-current] { color: var(--ink-2); }

.event-detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(270px, 300px);
  grid-template-areas: 'overview registration' 'description registration';
  gap: 16px 22px;
  align-items: start;
}
.event-overview, .event-description, .event-ticket {
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
}
.event-overview {
  grid-area: overview;
  display: grid;
  grid-template-columns: minmax(0, 31%) minmax(0, 1fr);
  grid-template-rows: max-content max-content 1fr;
  grid-template-areas: 'cover heading' 'cover facts' 'cover host';
  gap: 14px 22px;
  padding: 20px;
  overflow-wrap: anywhere;
}
.detail-cover { grid-area: cover; align-self: start; }
.detail-cover-img, .detail-cover :deep(.event-art) { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
.detail-cover-img { cursor: zoom-in; }
.detail-cover :deep(.event-art-copy) { display: none; }
.detail-cover :deep(.event-graphic) { inset: 50% auto auto 50%; width: 72%; height: 72%; translate: -50% -50%; }
.cover-preview-label {
  position: absolute;
  inset: auto 6px 6px auto;
  padding: 2px 7px;
  border-radius: var(--r-pill);
  background: rgb(15 23 42 / 72%);
  color: #fff;
  font-size: 10px;
  pointer-events: none;
}
.event-detail-heading { grid-area: heading; min-width: 0; }
.head-tags { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.tag { padding: 3px 9px; border-radius: var(--r-pill); font-size: 11px; font-weight: 650; line-height: 1.55; }
.tag-category { background: var(--brand-soft); color: var(--brand-strong); }
.tag-success { background: var(--success-soft); color: var(--success); }
.tag-warning { background: var(--warning-soft); color: var(--gold-strong); }
.tag-muted { background: var(--surface-3); color: var(--ink-2); }
.tag-error { background: var(--error-soft); color: var(--error); }
.event-detail-heading h1 { margin: 8px 0 0; color: var(--ink); font-size: clamp(24px, 3vw, 34px); font-weight: 700; line-height: 1.24; }
.event-detail-heading p { margin: 6px 0 0; color: var(--ink-3); font-size: 13px; }
.event-facts { grid-area: facts; display: grid; gap: 8px; align-content: center; margin: 0; }
.event-fact { display: grid; grid-template-columns: 16px 4.5em minmax(0, 1fr); align-items: baseline; gap: 7px; font-size: 13px; }
.event-fact .el-icon { color: var(--brand-strong); align-self: start; margin-top: 3px; }
.event-fact dt { color: var(--ink-3); white-space: nowrap; }
.event-fact dd { margin: 0; color: var(--ink); font-weight: 550; line-height: 1.5; }
.event-end { display: inline-block; margin-inline-start: 5px; }
.event-host { grid-area: host; align-self: end; display: flex; align-items: center; gap: 9px; padding-top: 10px; border-top: 1px solid var(--line); }
.event-host .el-avatar { flex: none; }
.host-identity { min-width: 0; }
.host-identity b, .host-identity small { display: block; }
.host-identity b { color: var(--ink); font-size: 13px; }
.host-identity small { color: var(--ink-3); font-size: 11px; }
.event-host > .text-btn { flex: none; margin-inline-start: auto; font-size: 12px; }

.event-description { grid-area: description; padding: 20px; overflow-wrap: anywhere; }
.event-description h2 { margin: 0; color: var(--ink); font-size: 19px; }
.event-description > p { margin: 10px 0 16px; max-width: 75ch; color: var(--ink-2); font-size: 14px; line-height: 1.8; white-space: pre-wrap; }
.detail-actions { display: flex; align-items: center; gap: 14px; padding-top: 12px; border-top: 1px solid var(--line); }
.btn {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 8px 18px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  color: var(--ink);
  font-size: 13px;
  font-weight: 550;
  white-space: nowrap;
  line-height: 1.6;
  cursor: pointer;
  transition: border-color .18s, background-color .18s, color .18s;
  font-family: var(--font-sans);
}
.btn:hover { border-color: var(--brand-line); background: var(--brand-soft); color: var(--brand); }
.btn.primary { background: var(--brand); border-color: var(--brand); color: #fff; }
.btn.primary:hover { background: var(--brand-strong); border-color: var(--brand-strong); }
.btn:disabled { opacity: .45; cursor: not-allowed; }
.btn.favorited { color: var(--gold-strong); border-color: var(--gold-line); background: var(--gold-soft); }
.btn svg { width: 16px; height: 16px; }
.text-btn {
  border: 0;
  background: none;
  color: var(--brand);
  font-size: 13px;
  padding: 3px 0;
  display: inline-flex;
  gap: 7px;
  align-items: center;
  cursor: pointer;
  white-space: nowrap;
}
.text-btn:hover { text-decoration: underline; text-underline-offset: 4px; }

.event-registration { grid-area: registration; align-self: stretch; min-width: 0; }
.event-ticket { position: sticky; top: 18px; padding: 20px; }
.event-ticket h2 { margin: 0 0 12px; color: var(--ink); font-size: 19px; }
.registration-count, .registration-deadline { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 4px 12px; color: var(--ink-2); font-size: 13px; }
.registration-count strong { color: var(--ink); font-size: 20px; font-variant-numeric: tabular-nums; }
.progress { height: 5px; margin-top: 6px; overflow: hidden; border-radius: var(--r-pill); background: var(--surface-2); }
.progress > i { display: block; height: 100%; background: var(--brand); }
.ticket-hint { margin: 6px 0 0; color: var(--ink-3); font-size: 12px; }
.registration-deadline { margin-top: 12px; }
.registration-deadline time { color: var(--ink); font-weight: 550; }
.stack { display: flex; flex-direction: column; gap: 8px; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--line); }
.stack .btn { width: 100%; min-height: 44px; font-size: 14px; white-space: normal; }
.ticket-tag {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  min-height: 44px;
  padding: 8px 14px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 550;
  text-align: center;
}
.ticket-tag.warning { background: var(--warning-soft); color: var(--gold-strong); }
.ticket-tag.success { background: var(--success-soft); color: var(--success); }
.ticket-tag.error { background: var(--error-soft); color: var(--error); }

.qr-code-wrap { display: flex; justify-content: center; padding: 20px 0 8px; }
.qr-code { width: 240px; height: 240px; border-radius: 8px; }
.qr-content { word-break: break-all; background: var(--surface-2); padding: 12px; border-radius: 6px; margin-top: 12px; font-family: monospace; }
.qr-tip { font-size: 12px; color: var(--ink-3); margin-top: 8px; }

@container (max-width: 850px) {
  .event-detail-layout {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas: 'overview' 'registration' 'description';
    gap: 12px;
  }
  .event-ticket { position: static; }
}
@container (max-width: 540px) {
  .event-overview {
    grid-template-columns: minmax(0, 30%) minmax(0, 1fr);
    grid-template-areas: 'heading heading' 'cover facts' 'host host';
    gap: 10px 12px;
  }
  .event-overview, .event-description, .event-ticket { padding: 13px; }
  .event-detail-heading h1 { font-size: 24px; }
  .event-fact { grid-template-columns: 14px minmax(0, 1fr); gap: 1px 5px; font-size: 12px; }
  .event-fact dd { grid-column: 2; }
  .event-end { margin-inline-start: 0; }
}
.members-pager { display: flex; justify-content: center; margin-top: 12px; }
</style>
