<template>
  <WtPageHeader title="他人主页" subtitle="了解 TA 的发布与评价，放心交流" eyebrow="我的" />

  <div class="user-home" v-loading="loading">
    <div v-if="profile">
      <!-- 信息卡 -->
      <div class="home-banner">
        <el-avatar :size="88" :src="profile.avatar" class="banner-avatar">{{ profile.nickname?.charAt(0) }}</el-avatar>
        <div class="banner-info">
          <h3>
            {{ profile.nickname }}
            <el-tag v-if="isSelf" size="small" type="success" effect="dark">这是我</el-tag>
          </h3>
          <p class="banner-sub">
            {{ profile.grade || '同学' }}
            <template v-if="profile.gender === 1"> · 男</template>
            <template v-else-if="profile.gender === 2"> · 女</template>
            <span v-if="profile.bio"> · 已写简介</span>
            <span v-if="profile.avgScore"> · 评分 ⭐ {{ profile.avgScore }}</span>
          </p>
          <p class="banner-bio" v-if="profile.bio">{{ profile.bio }}</p>
          <p class="banner-time">加入于 {{ fmtTime(profile.createTime) }}</p>
        </div>
        <div class="banner-ops">
          <el-button v-if="!isSelf" type="primary" plain @click="sendMsg">💬 发私信</el-button>
          <el-button v-if="!isSelf" type="danger" plain @click="reportVisible = true">举报</el-button>
          <el-button v-else @click="$router.push('/profile')">编辑我的资料 ›</el-button>
        </div>

        <!-- 举报弹窗 -->
        <el-dialog v-model="reportVisible" title="举报该用户" width="440px">
          <el-form label-width="80px">
            <el-form-item label="举报类型" required>
              <el-select v-model="reportForm.reasonType" placeholder="请选择举报类型" style="width:100%">
                <el-option label="言语辱骂/攻击" value="abuse" />
                <el-option label="发布违规内容" value="illegal" />
                <el-option label="骚扰/冒充" value="harass" />
                <el-option label="其他" value="other" />
              </el-select>
            </el-form-item>
            <el-form-item label="补充说明">
              <el-input v-model="reportForm.reason" type="textarea" :rows="3" maxlength="200" placeholder="选填，最多200字" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="reportVisible = false">取消</el-button>
            <el-button type="danger" :loading="reporting" @click="doReport">提交举报</el-button>
          </template>
        </el-dialog>
      </div>

      <!-- 统计条 -->
      <div class="stat-row">
        <div class="stat-item"><b>{{ profile.idleCount }}</b><span>发布闲置</span></div>
        <div class="stat-item"><b>{{ profile.postCount }}</b><span>动态</span></div>
        <div class="stat-item"><b>{{ profile.reviewCount }}</b><span>收到评价</span></div>
        <div class="stat-item"><b>{{ profile.avgScore ?? '—' }}</b><span>平均分 ★</span></div>
      </div>

      <!-- Tabs -->
      <el-card class="tab-card" shadow="never">
        <el-tabs v-model="tab">
          <el-tab-pane label="TA 的闲置" name="idle">
            <ContentList :items="idles" type="idle" @open="goDetail" />
          </el-tab-pane>
          <el-tab-pane label="TA 的动态" name="post">
            <ContentList :items="posts" type="post" @open="goDetail" />
          </el-tab-pane>
          <el-tab-pane label="TA 的评价" name="review">
            <div v-if="reviews.length" class="review-list">
              <div v-for="r in reviews" :key="r.id" class="review-item">
                <el-avatar :size="32" :src="r.fromAvatar" @click="goUser(r.fromUserId)" style="cursor:pointer">
                  {{ r.fromNickname?.charAt(0) }}
                </el-avatar>
                <div class="review-main">
                  <div class="review-head">
                    <span class="review-name" @click="goUser(r.fromUserId)" style="cursor:pointer">{{ r.fromNickname }}</span>
                    <span class="stars">{{ '★'.repeat(r.score) }}<i>{{ '★'.repeat(5 - r.score) }}</i></span>
                    <span class="review-item-title">于「{{ r.itemTitle || '闲置互换' }}」</span>
                  </div>
                  <div class="review-content">{{ r.content }}</div>
                  <div class="review-time">{{ fmtTime(r.createTime) }}</div>
                </div>
              </div>
            </div>
            <EmptyBox v-else description="还没有收到评价" />
          </el-tab-pane>
          <el-tab-pane label="更多发布" name="more">
            <ContentList :items="more" type="more" @open="goDetail" />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
    <EmptyBox v-else-if="!loading" description="用户不存在或已被禁用" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import EmptyBox from '../../components/EmptyBox.vue'
import { useUserStore } from '../../store/user'
import { userProfile, userIdles, userPosts, userActivities, userLostfounds, userReviews } from '../../api/user'
import { submitReport as apiSubmitReport } from '../../api/report'
import { startChat } from '../../utils/startChat'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const uid = computed(() => Number(route.params.id))
const isSelf = computed(() => Number(userStore.userInfo?.id) === uid.value)

const loading = ref(false)
const profile = ref(null)
const reportVisible = ref(false)
const reporting = ref(false)
const reportForm = reactive({ reasonType: '', reason: '' })
const tab = ref('idle')
const idles = ref([])
const posts = ref([])
const reviews = ref([])
const more = ref([])

async function loadProfile() {
  loading.value = true
  try {
    profile.value = await userProfile(uid.value)
  } catch (e) {
    profile.value = null
    if (!/不存在|禁用/.test(e.message || '')) ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadTab(name) {
  try {
    if (name === 'idle') idles.value = await userIdles(uid.value)
    else if (name === 'post') posts.value = await userPosts(uid.value)
    else if (name === 'review') reviews.value = await userReviews(uid.value)
    else if (name === 'more') {
      const [a, l] = await Promise.all([userActivities(uid.value), userLostfounds(uid.value)])
      more.value = [...a.map(x => ({ ...x })), ...l.map(x => ({ ...x }))]
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  }
}

watch(tab, (v) => loadTab(v))

onMounted(async () => {
  await loadProfile()
  if (profile.value) loadTab(tab.value)
})

watch(uid, async () => {
  tab.value = 'idle'
  await loadProfile()
  if (profile.value) loadTab('idle')
})

function goDetail(item) {
  const map = {
    idle: `/idle/detail/${item.id}`,
    post: `/social`,
    activity: `/activity/detail/${item.id}`,
    lostfound: `/lostfound/detail/${item.id}`
  }
  router.push(map[item.type] || '/')
}

function goUser(id) {
  if (Number(id) === Number(userStore.userInfo?.id)) router.push('/profile')
  else router.push(`/user/${id}`)
}

async function sendMsg() {
  try {
    await startChat(router, userStore, uid.value, { type: 'profile', id: uid.value, title: profile.value?.nickname })
  } catch (e) {
    ElMessage.error(e.message || '发起私信失败')
  }
}

async function doReport() {
  if (!reportForm.reasonType) {
    ElMessage.warning('请选择举报类型')
    return
  }
  reporting.value = true
  try {
    await apiSubmitReport({
      targetType: 'user',
      targetId: uid.value,
      reasonType: reportForm.reasonType,
      reason: reportForm.reason
    })
    ElMessage.success('举报已提交，管理员将尽快处理')
    reportVisible.value = false
    reportForm.reasonType = ''
    reportForm.reason = ''
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
  } finally {
    reporting.value = false
  }
}

function fmtTime(t) {
  return (t || '').replace('T', ' ').slice(0, 16)
}
</script>

<script>
import { h } from 'vue'

/** 统一内容列表（闲置/动态/更多） */
const ContentList = {
  name: 'ContentList',
  props: { items: { type: Array, default: () => [] }, type: { type: String, default: 'idle' } },
  emits: ['open'],
  setup(props, { emit }) {
    const open = (item) => emit('open', item)
    const extra = (item) => {
      if (props.type === 'idle') return item.extra
      if (props.type === 'more') return item.extra
      return item.extra
    }
    return () => {
      if (!props.items || !props.items.length) {
        return h('div', { style: 'padding:24px 0;text-align:center;color:#8a9b92;font-size:14px' }, '暂无内容')
      }
      return h('div', { style: 'display:flex;flex-direction:column;gap:10px' },
        props.items.map((item) => h('div', {
          class: 'uc-item',
          onClick: () => open(item),
          style: 'display:flex;align-items:center;gap:12px;background:#fafcfb;border:1px solid #e8f0ea;border-radius:10px;padding:10px 12px;cursor:pointer;'
        }, [
          item.image
            ? h('img', { src: item.image, style: 'width:56px;height:56px;border-radius:8px;object-fit:cover;flex:none;' })
            : h('div', { style: 'width:56px;height:56px;border-radius:8px;background:#eef4f0;flex:none;display:flex;align-items:center;justify-content:center;color:#9db5a9;font-size:22px;' },
              props.type === 'post' ? '📝' : props.type === 'more' ? '🗂' : '🛍'),
          h('div', { style: 'flex:1;min-width:0;' }, [
            h('div', { style: 'font-size:14px;color:#1f2d27;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;' }, item.title || '(无标题)'),
            h('div', { style: 'font-size:12px;color:#7a8b82;margin-top:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;' },
              [item.statusText ? item.statusText + ' · ' : '', item.extra || ''].join('')),
            h('div', { style: 'font-size:12px;color:#9db5a9;margin-top:2px;' }, (item.createTime || '').replace('T', ' ').slice(0, 16))
          ]),
          h('span', { style: 'color:var(--brand-strong);font-size:14px;flex:none;' }, '›')
        ]))
      )
    }
  }
}

export default {
  components: { ContentList }
}
</script>

<style scoped>
.user-home {
  max-width: 860px;
  margin: 0 auto;
}
.home-banner {
  display: flex;
  align-items: center;
  gap: 20px;
  background: linear-gradient(135deg, var(--brand-deep), var(--brand));
  border-radius: 16px;
  color: #fff;
  padding: 28px;
}
.banner-avatar {
  border: 3px solid rgba(255, 255, 255, 0.35);
  flex: none;
}
.banner-info { flex: 1; min-width: 0; }
.banner-info h3 {
  margin: 0 0 6px;
  font-size: 22px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.banner-sub { margin: 0 0 4px; font-size: 13px; opacity: 0.9; }
.banner-bio { margin: 6px 0; font-size: 14px; opacity: 0.95; }
.banner-time { margin: 0; font-size: 12px; opacity: 0.7; }
.banner-ops { display: flex; gap: 8px; flex: none; }
.banner-ops :deep(.el-button--primary) {
  --el-button-bg-color: #fff;
  --el-button-border-color: #fff;
  --el-button-text-color: var(--brand-strong);
  --el-button-hover-bg-color: #eaf5ef;
  --el-button-hover-border-color: #fff;
  --el-button-hover-text-color: var(--brand-strong);
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin: 16px 0;
}
.stat-item {
  background: #fff;
  border: 1px solid #e0ebe4;
  border-radius: 12px;
  padding: 16px 10px;
  text-align: center;
}
.stat-item b { display: block; font-size: 22px; color: var(--brand-strong); }
.stat-item span { font-size: 12px; color: #7a8b82; }
.tab-card { border-radius: 12px; }
.review-list { display: flex; flex-direction: column; gap: 12px; }
.review-item {
  display: flex;
  gap: 12px;
  background: #fafcfb;
  border: 1px solid #e8f0ea;
  border-radius: 10px;
  padding: 12px 14px;
}
.review-main { flex: 1; min-width: 0; }
.review-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.review-name { font-weight: 600; color: var(--brand-strong); font-size: 14px; }
.stars { color: #f5a623; font-size: 13px; letter-spacing: 1px; }
.stars i { color: #d8e2dc; font-style: normal; }
.review-item-title { color: #7a8b82; font-size: 12px; }
.review-content { margin-top: 6px; font-size: 14px; color: #1f2d27; }
.review-time { margin-top: 4px; font-size: 12px; color: #9db5a9; }
</style>
