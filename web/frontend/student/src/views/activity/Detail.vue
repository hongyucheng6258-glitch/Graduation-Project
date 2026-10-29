<template>
  <div class="event-page" v-loading="loading">
    <template v-if="act">
      <!-- 面包屑 -->
      <div class="detail-breadcrumb">
        <a class="text-btn" @click="$router.push('/activity')">返回校园活动</a>
        <span>/</span>
        <span>活动详情</span>
      </div>

      <div class="event-detail-layout">
        <!-- 左：封面 + 介绍 -->
        <article>
          <div class="event-detail-hero">
            <el-image v-if="detailImages.length" :src="detailImages[0]" fit="cover" class="detail-cover-img" :preview-src-list="detailImages" />
            <WtEventArt v-else :item="act" large />
            <div class="event-detail-heading">
              <div class="head-tags">
                <span v-if="act.category" class="tag">{{ act.category }}</span>
                <span class="tag" :class="statusTagCls(act.displayStatus)">{{ statusText }}</span>
              </div>
              <h1>{{ act.title }}</h1>
              <p>{{ act.subtitle || '和校园里的同伴，一起做喜欢的事。' }}</p>
              <div class="event-host">
                <el-avatar :size="36" :src="act.publisherAvatar">{{ act.publisherNickname?.charAt(0) || '梧' }}</el-avatar>
                <span>
                  <b>{{ act.publisherNickname || '校园同学' }}</b>
                  <small>活动发起方</small>
                </span>
                <button type="button" class="text-btn" @click="goUser(act.userId)">查看主页</button>
              </div>
            </div>
          </div>

          <section class="event-description">
            <h2>关于这次相遇</h2>
            <p>{{ act.description }}</p>
            <h3>参加前的小提醒</h3>
            <ul>
              <li>报名后请关注审核结果，活动前再次确认地点与时间。</li>
              <li>请携带学生证，提前到达活动现场。</li>
              <li>若有疑问，可以先联系发起方。</li>
            </ul>
            <div class="detail-actions">
              <button type="button" class="btn" :class="{ favorited }" @click="toggleFavorite">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 17.3 5.8 21l1.6-6.8L2 9.2l7-.6L12 2l3 6.6 7 .6-5.4 5 1.6 6.8z"/></svg>
                {{ favorited ? '已收藏' : '收藏' }}
              </button>
              <button type="button" class="text-btn" @click="reportVisible = true">举报此内容</button>
            </div>
          </section>
        </article>

        <!-- 右：活动通行证 -->
        <aside>
          <div class="event-ticket">
            <div class="ticket-top">
              <span>我的活动通行证</span>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="4" width="18" height="17" rx="2"/><path d="M3 9h18M8 2v4M16 2v4"/></svg>
            </div>
            <div class="ticket-date">
              <strong>{{ ticketDay }}</strong>
              <span>{{ ticketMonth }} / {{ ticketYear }}<small>{{ ticketTime }} 开始</small></span>
            </div>
            <div class="ticket-perforation"></div>
            <div class="ticket-fact">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0z"/><circle cx="12" cy="10" r="3"/></svg>
              <span><small>活动地点</small><b>{{ act.location || '地点待定' }}</b></span>
            </div>
            <div class="ticket-fact">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>
              <span><small>报名名额</small><b>{{ act.memberCount || 0 }}{{ act.maxMembers ? ' / ' + act.maxMembers : '' }} 人</b></span>
            </div>
            <div class="progress"><i :style="{ width: capacityPercent + '%' }"></i></div>
            <p class="ticket-hint">{{ ticketHint }}</p>

            <!-- 报名操作区（保留全部原有逻辑） -->
            <div class="stack">
              <!-- 发布者视角：名单管理 + 签到二维码 -->
              <template v-if="act.isOwner">
                <button type="button" class="btn primary" @click="loadMembers">报名名单管理</button>
                <button type="button" class="btn" @click="showQrcode">签到二维码</button>
              </template>
              <!-- 参与者视角 -->
              <template v-else>
                <button
                  v-if="act.mySignupStatus === null || act.mySignupStatus === undefined"
                  type="button"
                  class="btn primary"
                  :disabled="!act.canSignup"
                  @click="signupVisible = true"
                >
                  {{ act.canSignup ? '立即报名' : (act.signupDisabledReason || '不可报名') }}
                </button>
                <span v-else-if="act.mySignupStatus === 0" class="ticket-tag warning">报名待审批</span>
                <span v-else-if="act.mySignupStatus === 1" class="ticket-tag success">
                  已通过报名{{ act.signedIn ? '（已签到）' : '（活动现场请扫码签到）' }}
                </span>
                <span v-else class="ticket-tag error">报名未通过</span>
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
            <small class="ticket-note">报名通过后，可在「我的报名」完成签到</small>
          </div>
        </aside>
      </div>

      <!-- ===== 保留全部弹窗 ===== -->
      <!-- 报名弹窗 -->
      <el-dialog v-model="signupVisible" title="报名活动" width="440px">
        <el-input v-model="remark" type="textarea" :rows="3"
                  placeholder="报名说明/组队信息（如：计科2201张三，求组队）" maxlength="255" />
        <template #footer>
          <el-button @click="signupVisible = false">取消</el-button>
          <el-button type="primary" :loading="signing" @click="doSignup">确认报名</el-button>
        </template>
      </el-dialog>

      <!-- 名单管理弹窗（发布者） -->
      <el-dialog v-model="membersVisible" title="报名名单" width="640px">
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
      <el-dialog v-model="qrVisible" title="活动签到" width="420px">
        <el-alert type="success" :closable="false" title="参与者打开小程序「扫一扫」扫描下方二维码即可签到" />
        <div class="qr-code-wrap">
          <img v-if="qrImage" :src="qrImage" class="qr-code" alt="活动签到二维码" />
        </div>
        <div class="qr-content">{{ qrContent }}</div>
        <p class="qr-tip">如扫码不便，可复制上方签到内容手动录入。</p>
      </el-dialog>

      <!-- 举报弹窗 -->
      <el-dialog v-model="reportVisible" title="举报该活动" width="440px">
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
import WtEventArt from '../../components/wt/WtEventArt.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { activityDetail, signupActivity, cancelActivitySignup, activityMembers, handleMember, signinQrcode } from '../../api/activity'
import { submitReport } from '../../api/report'
import { favoriteStatus, favorite, unfavorite } from '../../api/favorite'
import { formatTime } from '../../utils/date'
import { normalizeImages } from '../../utils/image'
import { firstContentImage } from '../../utils/content-assets.mjs'
import { normalizeSigninQrContent } from '../../utils/signinQr.mjs'
import { useUserStore } from '../../store/user'
import { startChat } from '../../utils/startChat'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const id = Number(route.params.id)
const act = ref(null)
const detailImages = computed(() => {
  if (!act.value) return []
  const images = normalizeImages(act.value)
  return images.length ? images : [firstContentImage(act.value, 'activity')].filter(Boolean)
})
const loading = ref(false)
const signupVisible = ref(false)
const remark = ref('')
const signing = ref(false)
const canceling = ref(false)
const membersVisible = ref(false)
const members = ref([])
const membersTotal = ref(0)
const membersPage = ref(1)
const membersPageSize = 10
const qrVisible = ref(false)
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

const ticketDay = computed(() => {
  if (!act.value?.startTime) return '新'
  return String(act.value.startTime).slice(8, 10).replace(/^0/, '') || '新'
})
const ticketMonth = computed(() => {
  if (!act.value?.startTime) return '校园'
  return `${Number(String(act.value.startTime).slice(5, 7))} 月`
})
const ticketYear = computed(() => (act.value?.startTime ? String(act.value.startTime).slice(0, 4) : '2026'))
const ticketTime = computed(() => {
  if (!act.value?.startTime) return '待定'
  return String(act.value.startTime).slice(11, 16) || '待定'
})
const capacityPercent = computed(() => {
  const max = Number(act.value?.maxMembers)
  if (!max) return 0
  return Math.min(Math.round((Number(act.value.memberCount) || 0) / max * 100), 100)
})
const ticketHint = computed(() => {
  if (!act.value) return ''
  const max = Number(act.value.maxMembers)
  if (max && Number(act.value.memberCount) >= max) return '当前活动已满员。'
  if (Number(act.value.status) === 0) return `还有 ${(max || 99) - (Number(act.value.memberCount) || 0)} 个名额，期待你加入。`
  return '活动报名通道已关闭。'
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
  if (signing.value) return
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  signing.value = true
  try {
    await signupActivity(id, { remark: remark.value })
    ElMessage.success('报名已提交，等待发布者审批')
    signupVisible.value = false
    load()
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
.event-page { display: flex; flex-direction: column; }
.detail-breadcrumb {
  display: flex;
  align-items: center;
  gap: 13px;
  color: var(--ink-3);
  font-size: 12px;
  margin: 2px 0 22px;
}
.detail-breadcrumb .text-btn { font-size: 12px; color: var(--ink-3); }
.detail-breadcrumb .text-btn:hover { color: var(--brand); }

.event-detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 36px;
  align-items: start;
}
.event-detail-hero {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 20px;
  overflow: hidden;
}
.detail-cover-img { width: 100%; aspect-ratio: 16 / 9; display: block; }
.event-detail-heading { padding: 28px 33px 24px; }
.head-tags { display: flex; gap: 8px; align-items: center; }
.tag {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.7;
  padding: 3px 9px;
  border-radius: 5px;
  background: var(--brand-soft);
  color: var(--brand);
  white-space: nowrap;
}
.tag-success { background: var(--success-soft); color: var(--success); }
.tag-warning { background: var(--warning-soft); color: var(--gold-strong); }
.tag-muted { background: var(--surface-3); color: var(--ink-2); }
.tag-error { background: var(--error-soft); color: var(--error); }
.event-detail-heading > h1 { font-size: 29px; margin: 15px 0 10px; color: var(--ink); font-weight: 700; letter-spacing: -.5px; line-height: 1.4; }
.event-detail-heading > p { font-size: 14px; color: var(--ink-3); }
.event-host {
  border-top: 1px solid var(--line);
  padding-top: 20px;
  margin-top: 23px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.event-host b { display: block; font-size: 13px; font-weight: 550; color: var(--ink); }
.event-host small { display: block; font-size: 11px; color: var(--ink-3); }
.event-host > .text-btn { margin-left: auto; font-size: 12px; }

.event-description { padding: 34px 5px 0; }
.event-description h2 { font-size: 23px; font-weight: 650; color: var(--ink); }
.event-description > p { line-height: 2; font-size: 15px; margin: 18px 0 25px; max-width: 75ch; color: var(--ink-2); white-space: pre-wrap; }
.event-description h3 { font-size: 17px; color: var(--ink); font-weight: 600; }
.event-description ul { font-size: 14px; line-height: 2.1; color: var(--ink-2); padding-left: 20px; margin: 12px 0 25px; }
.detail-actions { border-top: 1px solid var(--line); padding-top: 22px; display: flex; align-items: center; gap: 20px; }
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

/* —— 通行证 —— */
.event-ticket {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 26px;
  position: sticky;
  top: 25px;
  overflow: hidden;
  box-shadow: 0 10px 32px oklch(25% 0.04 265 / .03);
}
.ticket-top { display: flex; justify-content: space-between; align-items: center; font-size: 12px; color: var(--ink-3); }
.ticket-top > svg { width: 20px; height: 20px; color: var(--brand); }
.ticket-date { display: flex; gap: 18px; align-items: center; margin-top: 15px; color: var(--brand); }
.ticket-date > strong { font: 650 69px/1.3 var(--font-display); letter-spacing: -3px; color: var(--brand); }
.ticket-date > span { font-size: 16px; font-weight: 550; color: var(--ink); }
.ticket-date small { display: block; font-size: 12px; color: var(--ink-3); font-weight: 400; margin-top: 7px; }
.ticket-perforation {
  margin: 19px -26px 25px;
  border-top: 1px dashed var(--brand-line);
  position: relative;
}
.ticket-perforation:before, .ticket-perforation:after {
  content: "";
  height: 20px;
  width: 20px;
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: 50%;
  position: absolute;
  top: -11px;
  left: -11px;
}
.ticket-perforation:after { right: -11px; left: auto; }
.ticket-fact { display: flex; align-items: center; gap: 13px; margin-top: 20px; }
.ticket-fact > svg { color: var(--ink-3); width: 18px; height: 18px; flex: none; }
.ticket-fact small { color: var(--ink-3); display: block; font-size: 11px; }
.ticket-fact b { display: block; font-size: 14px; font-weight: 550; margin-top: 2px; color: var(--ink); }
.progress { background: var(--surface-2); height: 6px; border-radius: 6px; overflow: hidden; margin: 16px 0; }
.progress > i { height: 100%; display: block; background: var(--brand); }
.ticket-hint { font-size: 12px; margin-bottom: 22px; color: var(--ink-3); }
.stack { display: flex; flex-direction: column; gap: 10px; }
.stack .btn { width: 100%; min-height: 44px; font-size: 14px; }
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
.ticket-note { display: block; color: var(--ink-3); font-size: 10px; text-align: center; margin-top: 16px; }

.qr-code-wrap { display: flex; justify-content: center; padding: 20px 0 8px; }
.qr-code { width: 240px; height: 240px; border-radius: 8px; }
.qr-content { word-break: break-all; background: var(--surface-2); padding: 12px; border-radius: 6px; margin-top: 12px; font-family: monospace; }
.qr-tip { font-size: 12px; color: var(--ink-3); margin-top: 8px; }

@media (max-width: 1080px) {
  .event-detail-layout { grid-template-columns: minmax(0, 1fr) 290px; gap: 25px; }
  .event-ticket { padding: 21px; }
  .ticket-perforation { margin-left: -21px; margin-right: -21px; }
  .event-detail-heading { padding: 23px; }
  .event-detail-heading > h1 { font-size: 25px; }
  .ticket-date > strong { font-size: 56px; }
}
@media (max-width: 760px) {
  .event-detail-layout { grid-template-columns: 1fr; gap: 25px; }
  .event-detail-heading { padding: 22px; }
  .event-detail-heading > h1 { font-size: 25px; line-height: 1.55; }
  .event-detail-heading > p { font-size: 12px; }
  .event-description { padding-top: 25px; }
  .event-description > p { font-size: 14px; }
  .event-description ul { font-size: 13px; }
  .event-ticket { padding: 24px; position: relative; top: 0; }
  .ticket-perforation { margin-left: -24px; margin-right: -24px; }
  .ticket-hint { font-size: 13px; }
  .ticket-note { font-size: 11px; }
  .ticket-date > strong { font-size: 60px; }
  .detail-breadcrumb { font-size: 11px; margin: 2px 0 20px; }
}
</style>

<style scoped>
.members-pager { display: flex; justify-content: center; margin-top: 12px; }
</style>
