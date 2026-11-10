<template>
  <WtPageHeader title="个人中心" subtitle="管理你的资料、发布和学习记录。" />

  <div class="profile">
    <section class="profile-banner" aria-label="个人资料">
      <el-avatar :size="76" :src="user?.avatar" class="banner-avatar">{{ user?.nickname?.charAt(0) }}</el-avatar>
      <div class="banner-info">
        <h2>{{ user?.nickname || '校园同学' }}</h2>
        <p class="banner-id">学号 {{ user?.studentNo || '未绑定' }} · {{ user?.phone ? '手机 ' + user?.phone : '未绑定手机' }}</p>
        <div class="banner-tags">
          <span v-if="user?.avatar" class="banner-tag tag-brand">已设头像</span>
          <span v-if="user?.phone" class="banner-tag tag-success">手机已绑定</span>
          <span v-if="user?.bio" class="banner-tag tag-info">已写简介</span>
        </div>
      </div>
      <div class="banner-stats" aria-label="个人数据概览">
        <div><b>{{ idleTotal }}</b><small>闲置发布</small></div>
        <div><b>{{ wrongStatsData.total ?? 0 }}</b><small>累计错题</small></div>
        <div><b>{{ conversationCount }}</b><small>私信会话</small></div>
      </div>
      <div class="banner-ops">
        <el-button @click="editVisible = true">编辑资料</el-button>
        <el-button @click="pwdVisible = true">修改密码</el-button>
      </div>
    </section>

    <nav class="profile-grid" aria-label="个人中心快捷入口">
      <RouterLink v-for="entry in profileEntries" :key="entry.title" class="entry-tile" :to="entry.to" @click="scrollToManagement(entry.to.hash)">
        <span class="entry-icon"><el-icon><component :is="entry.icon" /></el-icon></span>
        <b>{{ entry.title }}</b>
        <small>{{ entry.description }}</small>
      </RouterLink>
    </nav>

    <section class="profile-recent" aria-labelledby="profile-recent-title">
      <h2 id="profile-recent-title">我的近期发布</h2>
      <p>你发布的活动、闲置与动态。</p>
      <div v-if="recent.length" class="recent-grid">
        <RouterLink v-for="item in recent" :key="`${item.type}-${item.id}`" class="recent-link" :to="contentTarget(item.type, item.id)">
          <ItemCard :cover="item.image" :module="item.type === 'post' ? '' : item.type" :category="item.category"
            :title="item.title" :time="item.createTime">
            <template #badge><el-tag size="small">{{ contentLabels[item.type] }}</el-tag></template>
          </ItemCard>
        </RouterLink>
      </div>
      <EmptyBox v-else-if="!recentLoading" description="暂无近期发布" />
    </section>

    <section class="profile-summary" aria-label="学习进度">
      <div class="summary-group">
        <h2>学习进度</h2>
        <div class="summary-items">
          <div class="summary-item"><span>待复习</span><b>{{ wrongStatsData.pending ?? 0 }}</b></div>
          <div class="summary-item"><span>已掌握</span><b>{{ wrongStatsData.mastered ?? 0 }}</b></div>
          <div class="summary-item"><span>本周复习</span><b>{{ wrongStatsData.weekReviewCount ?? 0 }}</b></div>
        </div>
      </div>
    </section>

    <el-card id="profile-management" class="profile-management">
      <el-tabs v-model="tab">
        <el-tab-pane label="我的闲置" name="idle">
          <el-table :data="myIdle" size="small">
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <router-link :to="`/idle/detail/${row.id}`">{{ row.title }}</router-link>
              </template>
            </el-table-column>
            <el-table-column label="审核" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="['warning','success','danger'][row.auditStatus]">
                  {{ ['待审核','已通过','已驳回'][row.auditStatus] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ ['在架','已预约','已完成','已下架'][row.status] }}</template>
            </el-table-column>
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <template v-if="row.status !== 3 && row.status !== 2">
                  <el-button size="small" link type="primary" @click="editIdle(row)">编辑</el-button>
                  <el-button size="small" link type="danger" @click="offlineIdle(row)">下架</el-button>
                </template>
                <el-button v-if="row.status === 3" size="small" link type="success" @click="relistIdle(row)">重新上架</el-button>
                <span v-if="row.status === 2" style="color: var(--ink-3); font-size: 12px">已完成</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="我的报名" name="signup">
          <el-button text type="primary" @click="$router.push('/activity/my-signup')">前往「我的活动」查看 ›</el-button>
        </el-tab-pane>
        <el-tab-pane label="错题本" name="wrong">
          <el-button text type="primary" @click="$router.push('/ai/wrong')">前往错题本 ›</el-button>
        </el-tab-pane>
        <el-tab-pane label="我的收藏" name="favorite">
          <div v-loading="favLoading" style="min-height: 80px">
            <el-table :data="myFavList" size="small" v-if="myFavList.length">
              <el-table-column label="类型" width="90">
                <template #default="{ row }">
                  <el-tag size="small">{{ { activity: '活动', idle: '闲置', lostfound: '失物', post: '动态' }[row.targetType] || row.targetType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">
                  <RouterLink v-if="row.title !== '内容已删除'" :to="contentTarget(row.targetType, row.targetId)">{{ row.title }}</RouterLink>
                  <span v-else style="color: var(--ink-3)">{{ row.title }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="收藏时间" width="160">
                <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').slice(0, 16) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button size="small" link type="danger" @click="removeFav(row)">取消收藏</el-button>
                </template>
              </el-table-column>
            </el-table>
            <EmptyBox v-else-if="!favLoading" description="还没有收藏内容，去逛逛吧" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="我的举报" name="report">
          <div v-loading="reportLoading" style="min-height: 80px">
            <el-table v-if="myReports.length" :data="myReports" size="small">
              <el-table-column label="对象" min-width="150">
                <template #default="{ row }">{{ reportTargetText(row) }}</template>
              </el-table-column>
              <el-table-column prop="reason" label="举报说明" min-width="180" show-overflow-tooltip />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 1 ? 'success' : 'warning'">{{ row.status === 1 ? '已处理' : '待处理' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="handleResult" label="处理结果" min-width="180" show-overflow-tooltip />
              <el-table-column prop="createTime" label="提交时间" width="160">
                <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').slice(0, 16) }}</template>
              </el-table-column>
            </el-table>
            <EmptyBox v-else-if="!reportLoading" description="还没有举报记录" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 编辑资料弹窗 -->
    <el-dialog v-model="editVisible" title="编辑资料" width="min(440px, calc(100vw - 32px))">
      <el-form :model="editForm" label-width="70px">
        <el-form-item label="昵称"><el-input v-model="editForm.nickname" maxlength="32" /></el-form-item>
        <el-form-item label="头像">
          <UploadImg v-model="avatarList" :max="1" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="editForm.gender">
            <el-radio :value="0">保密</el-radio>
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号"><el-input v-model="editForm.phone" maxlength="11" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="editForm.bio" type="textarea" :rows="2" maxlength="255" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="min(440px, calc(100vw - 32px))">
      <el-form :model="pwdForm" label-width="80px">
        <el-form-item label="原密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Calendar, Collection, Goods, Star } from '@element-plus/icons-vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { ElMessage } from 'element-plus'
import UploadImg from '../../components/UploadImg.vue'
import { useUserStore } from '../../store/user'
import * as userApi from '../../api/user'
import { myIdle as fetchMyIdle, offlineIdle as apiOfflineIdle, relistIdle as apiRelistIdle } from '../../api/idle'
import { useRouter, useRoute } from 'vue-router'
import { wrongStats } from '../../api/wrong'
import { listConversations } from '../../api/chat'
import { myFavorites, unfavorite } from '../../api/favorite'
import { myReports as fetchMyReports } from '../../api/report'
import EmptyBox from '../../components/EmptyBox.vue'
import ItemCard from '../../components/ItemCard.vue'
import { firstContentImage } from '../../utils/content-assets.mjs'
import { messageTarget } from '../../utils/message-navigation.mjs'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const user = computed(() => userStore.userInfo)
// 消息中心跳转审核结果时通过 ?tab=idle 定位到"我的闲置"标签
const profileTabs = ['idle', 'signup', 'wrong', 'favorite', 'report']
const tab = ref('idle')
const profileEntries = [
  { title: '我的闲置', description: '管理已发布的物品', to: { query: { tab: 'idle' }, hash: '#profile-management' }, icon: Goods },
  { title: '我的报名', description: '活动与日程', to: '/activity/my-signup', icon: Calendar },
  { title: '我的错题', description: '复习与薄弱点', to: '/ai/wrong', icon: Collection },
  { title: '我的收藏', description: '查看收藏内容', to: { query: { tab: 'favorite' }, hash: '#profile-management' }, icon: Star },
]
const contentLabels = { idle: '闲置', activity: '活动', post: '动态' }
const myIdle = ref([])
const idleTotal = ref(0)
const myRecentActivities = ref([])
const myRecentPosts = ref([])
const recentLoading = ref(true)
const recent = computed(() => [
  ...myIdle.value.slice(0, 3).map(item => ({
    ...item, type: 'idle', image: firstContentImage(item, 'idle', item.category),
  })),
  ...myRecentActivities.value,
  ...myRecentPosts.value,
].sort((a, b) => String(b.createTime || '').localeCompare(String(a.createTime || ''))).slice(0, 3))
const wrongStatsData = ref({ total: 0, pending: 0, mastered: 0, weekReviewCount: 0 })
const conversationCount = ref(0)
const editVisible = ref(false)
const pwdVisible = ref(false)
const saving = ref(false)
const editForm = reactive({ nickname: '', gender: 0, phone: '', bio: '' })
const avatarList = ref([])
const pwdForm = reactive({ oldPassword: '', newPassword: '' })
const myFavList = ref([])
const favLoading = ref(false)
const myReports = ref([])
const reportLoading = ref(false)

watch(tab, (v) => {
  if (v === 'favorite') loadFavorites()
  if (v === 'report') loadReports()
})
watch(() => route.query.tab, value => {
  const selected = profileTabs.includes(value) ? value : 'idle'
  if (selected !== tab.value) tab.value = selected
}, { immediate: true })

function scrollToManagement(hash) {
  if (hash) document.getElementById(hash.slice(1))?.scrollIntoView({ block: 'start' })
}

function contentTarget(type, id) {
  return messageTarget({ bizType: type, bizId: id }) || '/profile'
}

async function loadFavorites() {
  favLoading.value = true
  try {
    const res = await myFavorites({ pageNum: 1, pageSize: 50 })
    myFavList.value = res.list
  } finally {
    favLoading.value = false
  }
}

async function removeFav(row) {
  await unfavorite(row.targetType, row.targetId)
  ElMessage.success('已取消收藏')
  loadFavorites()
}

async function loadReports() {
  reportLoading.value = true
  try {
    const res = await fetchMyReports({ pageNum: 1, pageSize: 50 })
    myReports.value = res.list || []
  } finally {
    reportLoading.value = false
  }
}

function reportTargetText(row) {
  const labels = { post: '动态', idle: '闲置', activity: '活动', lostfound: '失物', user: '用户', comment: '评论' }
  return `${labels[row.targetType] || row.targetType || '内容'} #${row.targetId ?? '-'}`
}

onMounted(async () => {
  try {
    await userStore.refresh()
  } catch {
    recentLoading.value = false
    return
  }
  Object.assign(editForm, {
    nickname: user.value?.nickname,
    gender: user.value?.gender ?? 0,
    phone: user.value?.phone,
    bio: user.value?.bio
  })
  avatarList.value = user.value?.avatar ? [user.value.avatar] : []
  const [idle, activities, posts] = await Promise.allSettled([
    loadMyIdle(),
    userApi.userActivities(user.value.id),
    userApi.userPosts(user.value.id),
  ])
  if (idle.status === 'rejected') ElMessage.error(idle.reason?.message || '闲置列表加载失败')
  if (activities.status === 'fulfilled') myRecentActivities.value = activities.value.slice(0, 3)
  if (posts.status === 'fulfilled') myRecentPosts.value = posts.value.slice(0, 3)
  recentLoading.value = false
  try {
    wrongStatsData.value = await wrongStats()
  } catch { /* 未登录等场景静默 */ }
  try {
    const convs = await listConversations()
    conversationCount.value = Array.isArray(convs) ? convs.length : (convs?.list?.length || 0)
  } catch { /* 静默 */ }
})

async function loadMyIdle() {
  const res = await fetchMyIdle({ pageNum: 1, pageSize: 50 })
  myIdle.value = res.list
  idleTotal.value = res.total ?? myIdle.value.length
}

async function offlineIdle(row) {
  await apiOfflineIdle(row.id)
  ElMessage.success('已下架')
  loadMyIdle()
}

function editIdle(row) {
  router.push(`/idle/publish?id=${row.id}`)
}

async function relistIdle(row) {
  await apiRelistIdle(row.id)
  ElMessage.success('已重新上架')
  loadMyIdle()
}

async function saveProfile() {
  saving.value = true
  try {
    await userApi.updateProfile({ ...editForm, avatar: avatarList.value[0] || '' })
    await userStore.refresh()
    ElMessage.success('保存成功')
    editVisible.value = false
  } finally {
    saving.value = false
  }
}

async function savePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  saving.value = true
  try {
    await userApi.updatePassword(pwdForm)
    ElMessage.success('密码已修改')
    pwdVisible.value = false
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.profile {
  display: grid;
  gap: var(--s-4);
  min-width: 0;
}

.profile-banner {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--s-4);
  padding: 22px 28px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  color: var(--ink);
  background: var(--surface);
  overflow: hidden;
}

.banner-avatar {
  background: var(--brand-soft);
  color: var(--brand-strong);
  font-weight: 700;
  flex: none;
}

.banner-info { min-width: 0; }
.banner-info h2 { margin: 0 0 6px; font-size: var(--fs-h2); font-weight: 700; }
.banner-id { margin: 0 0 var(--s-2); color: var(--ink-3); font-size: var(--fs-sm); }
.banner-tags, .banner-ops { display: flex; align-items: center; gap: var(--s-2); flex-wrap: wrap; }
.banner-tag {
  padding: 3px 10px;
  border-radius: var(--r-pill);
  background: var(--surface-2);
  color: var(--ink-2);
  font-size: var(--fs-cap);
  font-weight: 600;
}
.banner-stats { display: flex; gap: 18px; text-align: center; }
.banner-stats b, .banner-stats small { display: block; }
.banner-stats b { font-size: var(--fs-h3); color: var(--brand-strong); }
.banner-stats small { font-size: var(--fs-cap); color: var(--ink-3); }
.banner-ops { grid-column: 2 / -1; justify-content: flex-end; }
.profile-grid, .recent-grid { display: grid; gap: 16px; }
.profile-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.recent-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.entry-tile, .recent-link { color: inherit; text-decoration: none; border-radius: var(--r-md); }
.entry-tile {
  display: flex; flex-direction: column; gap: 8px;
  padding: 22px; border: 1px solid var(--line); background: var(--surface);
}
.entry-tile:hover { border-color: var(--brand-line); }
.entry-tile:focus-visible, .recent-link:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }
.entry-tile small, .profile-recent > p { color: var(--ink-3); font-size: var(--fs-sm); }
.entry-icon {
  width: 40px; height: 40px; display: grid; place-items: center;
  color: var(--brand); background: var(--brand-soft); border-radius: var(--r-sm); font-size: 20px;
}
.profile-recent h2 { margin: 4px 0; font-size: var(--fs-h2); }
.profile-recent > p { margin: 0 0 14px; }
.recent-link { min-width: 0; }
.recent-link :deep(.cover) { height: 130px; }
.recent-link :deep(.desc:empty) { display: none; }
.recent-link :deep(.item-card) { height: 100%; border-radius: var(--r-md); border-color: var(--line); }
.banner-ops :deep(.el-button) { border-radius: var(--r-pill); }

.profile-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: var(--surface);
  overflow: hidden;
}
.summary-group { min-width: 0; padding: 16px 20px; }
.summary-group h2 { margin: 0 0 var(--s-3); color: var(--ink-2); font-size: var(--fs-xs); font-weight: 700; }
.summary-items { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); }
.summary-item {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 0 var(--s-3);
  border-left: 1px solid var(--line);
}
.summary-item:first-child { padding-left: 0; border-left: 0; }
.summary-item span { color: var(--ink-3); font-size: var(--fs-xs); }
.summary-item b { color: var(--brand-strong); font-size: var(--fs-h3); font-variant-numeric: tabular-nums; }

.profile-management { min-width: 0; border-radius: var(--r-lg); }
.profile-management :deep(.el-card__body) { min-width: 0; padding: 8px 20px 20px; }
.profile-management :deep(.el-tabs__nav-wrap) { max-width: 100%; }
.profile-management :deep(.el-tabs__content) { min-width: 0; }
.profile-management :deep(.el-table) { max-width: 100%; }
.profile-management :deep(.el-table__inner-wrapper) { min-width: 0; }

@media (max-width: 900px) {
  .profile-banner { grid-template-columns: 68px minmax(0, 1fr); padding: 18px 20px; }
  .banner-avatar { width: 68px !important; height: 68px !important; }
  .banner-stats, .banner-ops { grid-column: 1 / -1; justify-content: flex-start; }
}

@media (max-width: 640px) {
  .profile { gap: var(--s-3); }
  .profile-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .entry-tile { padding: 14px; }
  .recent-grid { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  .profile-banner { grid-template-columns: 56px minmax(0, 1fr); gap: var(--s-3); padding: 16px; }
  .banner-avatar { width: 56px !important; height: 56px !important; }
  .banner-info h2 { font-size: var(--fs-h3); }
  .banner-id { overflow-wrap: anywhere; }
  .banner-ops { grid-column: 1 / -1; }
  .profile-summary { grid-template-columns: 1fr; }
  .summary-group { padding: 14px 16px; }
  .summary-items { grid-template-columns: repeat(2, minmax(0, 1fr)); row-gap: var(--s-3); }
  .summary-item { padding: 0 8px; }
  .summary-item:nth-child(2n + 1) { padding-left: 0; border-left: 0; }
  .summary-item b { font-size: var(--fs-body); }
  .profile-management :deep(.el-card__body) { padding: 4px 12px 14px; }
  .profile-management :deep(.el-tabs__nav) { min-width: max-content; white-space: nowrap; }
  .profile-management :deep(.el-tabs__nav-scroll) { overflow-x: auto; scrollbar-width: none; }
  .profile-management :deep(.el-tabs__nav-wrap::after) { display: none; }
  .profile-management :deep(.el-table__body-wrapper) { overflow-x: auto; }
}
</style>
