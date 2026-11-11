<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <div class="activity-page">
    <section class="collection-head activity">
      <div>
        <h1>校园活动</h1>
        <p>查找讲座、社团活动、运动和志愿服务。</p>
      </div>
      <div class="header-actions">
        <RouterLink class="btn" to="/activity/my-signup">我的报名</RouterLink>
        <button type="button" class="btn primary" @click="goPublish">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
          发布活动
        </button>
      </div>
    </section>

    <!-- 工具栏 -->
    <div class="toolbar">
      <el-input v-model="keyword" class="activity-search" placeholder="搜索活动…" aria-label="搜索活动" clearable @keyup.enter="search" @clear="search">
        <template #append><el-button @click="search">搜索</el-button></template>
      </el-input>
      <div class="chips" aria-label="活动分类">
        <button type="button" class="chip" :class="{ active: !category }" :aria-pressed="!category" @click="selectCategory('')">全部</button>
        <button v-for="c in categories" :key="c" type="button" class="chip" :class="{ active: category === c }"
          :aria-pressed="category === c" @click="selectCategory(c)">{{ c }}</button>
      </div>
    </div>

    <div class="result-heading">
      <h2>{{ category || '活动列表' }}</h2>
      <span v-if="!loading && !loadError">{{ total }} 场活动</span>
    </div>
    <div class="activity-grid" v-loading="loading">
      <RouterLink
        v-for="a in list"
        :key="a.id"
        class="event-card"
        :to="{ name: 'ActivityDetail', params: { id: a.id } }"
      >
        <div class="item-body">
          <time v-if="a.startTime" class="event-date" :datetime="a.startTime">{{ formatEventTime(a.startTime) }}</time>
          <h3>{{ a.title }}</h3>
          <div class="item-meta">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0z"/><circle cx="12" cy="10" r="3"/></svg>
            <span>{{ a.location || '地点待定' }}</span>
          </div>
          <div class="event-tags">
            <span class="event-status" :class="{ open: statusText(a) === '报名中' }">{{ statusText(a) }}</span>
            <span class="event-category">{{ a.category || '校园活动' }}</span>
            <span>{{ a.memberCount || 0 }}{{ a.maxMembers ? ' / ' + a.maxMembers : '' }} 人报名</span>
          </div>
          <p v-if="a.signupDeadline" class="deadline">报名截止 <time :datetime="a.signupDeadline">{{ formatEventTime(a.signupDeadline, false) }}</time></p>
        </div>
        <div class="event-cover">
          <img v-if="firstContentImage(a)" :src="firstContentImage(a)" alt="" class="event-cover-img" loading="lazy"
            @error="onImageError($event, 'activity')" />
          <WtEventArt v-else :item="a" />
        </div>
      </RouterLink>
    </div>
    <EmptyBox v-if="!loadError && !loading && !list.length" description="暂无活动" />
    <el-pagination
      v-model:current-page="pageNum"
      :total="total"
      :page-size="12"
      layout="prev, pager, next"
      @current-change="load"
      class="page-bar"
    />
    <!-- AI 智能推荐（保留现有功能） -->
    <div class="rec-block">
      <div class="rec-head">
        <b>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/><circle cx="12" cy="12" r="3"/></svg>
          AI 智能推荐
        </b>
        <span>基于你的报名偏好，为你挑选最合适的活动</span>
        <el-button size="small" type="primary" plain :loading="recLoading" @click="loadRecommend">
          {{ recList.length ? '重新推荐' : '给我推荐' }}
        </el-button>
      </div>
      <div v-if="recList.length" class="rec-grid">
        <RouterLink v-for="r in recList" :key="r.id" class="rec-card" :to="`/activity/detail/${r.id}`">
          <div class="rec-card__top">
            <b>{{ r.title }}</b>
            <span class="rec-cat">{{ r.category }}</span>
          </div>
          <p class="rec-reason">{{ r.reason }}</p>
          <div class="rec-meta">
            <time v-if="r.startTime" :datetime="r.startTime">{{ formatEventTime(r.startTime) }}</time>
            <span>{{ r.location || '地点待定' }}</span>
            <span>{{ r.memberCount }} 人已报名</span>
          </div>
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useListState } from '../../utils/list-state'
import { ref, watch } from 'vue'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import EmptyBox from '../../components/EmptyBox.vue'
import WtEventArt from '../../components/wt/WtEventArt.vue'
import { firstContentImage, onImageError } from '../../utils/content-assets.mjs'
import { listActivity, recommendActivity } from '../../api/activity'
import { useUserStore } from '../../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const categories = ['学习交流', '体育运动', '文艺娱乐', '志愿服务', '竞赛组队', '其他']
const keyword = ref('')
const category = ref('')
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
let loadSequence = 0
const restoredListState = useListState('activity', { pageNum, keyword, category })
const recList = ref([])
const recLoading = ref(false)

/** AI 智能推荐 */
async function loadRecommend() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  recLoading.value = true
  try {
    const res = await recommendActivity()
    recList.value = Array.isArray(res) ? res : (res.list || [])
    if (!recList.value.length) ElMessage.info('暂时没有合适的推荐，稍后再来试试')
  } catch (e) {
    ElMessage.error(e.message || '推荐失败，请稍后重试')
  } finally {
    recLoading.value = false
  }
}

const STATUS_TEXT = ['报名中', '已满', '已结束', '已下架']
function statusText(a) {
  return a.displayStatusText || STATUS_TEXT[a.status] || '报名中'
}

function formatEventTime(value, withWeekday = true) {
  return dayjs(value).locale('zh-cn').format(withWeekday ? 'M月D日 ddd · HH:mm' : 'M月D日 HH:mm')
}

function selectCategory(c) {
  category.value = c
  search()
}

function search() {
  pageNum.value = 1
  load()
}

async function load() {
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  try {
    const res = await listActivity({ keyword: keyword.value || undefined, category: category.value || undefined, pageNum: pageNum.value, pageSize: 12 })
    if (sequence !== loadSequence) return
    list.value = res.list
    total.value = res.total
  } catch (error) {
    if (sequence === loadSequence) loadError.value = error.message || '内容加载失败，请重试'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function goPublish() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  router.push('/activity/publish')
}

let initialSearchWatch = true
watch(
  () => route.query.q,
  (q) => {
    if (!initialSearchWatch || !restoredListState) {
      keyword.value = String(q || '')
      pageNum.value = 1
    }
    initialSearchWatch = false
    load()
  },
  { immediate: true }
)
</script>

<style scoped>
.activity-page { max-width: 1152px; margin-inline: auto; }
.collection-head, .header-actions, .result-heading { display: flex; align-items: center; gap: 12px; }
.collection-head { justify-content: space-between; flex-wrap: wrap; margin-bottom: 20px; }
.collection-head h1 { margin: 0 0 4px; color: var(--ink); font-size: clamp(25px, 3vw, 33px); }
.collection-head p { margin: 0; color: var(--ink-3); font-size: var(--fs-sm); }
.header-actions { flex-wrap: wrap; gap: 8px; }
.header-actions .btn { min-height: 36px; padding: 0 12px; }
.header-actions svg { width: 15px; height: 15px; }
.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 16px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
}
.activity-search { width: min(100%, 270px); }
.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chip {
  padding: 5px 11px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
  color: var(--ink-2);
  font: inherit;
  font-size: var(--fs-xs);
  cursor: pointer;
}
.chip:hover { border-color: var(--brand-line); }
.chip.active { background: var(--brand-soft); color: var(--brand-strong); border-color: var(--brand-line); font-weight: 650; }
.result-heading { justify-content: space-between; margin: 19px 0 9px; }
.result-heading h2 { margin: 0; color: var(--ink); font-size: 18px; }
.result-heading span { color: var(--ink-3); font-size: 12px; }
.activity-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.event-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px;
  align-items: center;
  gap: 13px;
  padding: 12px;
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: 13px;
  background: var(--surface);
  color: var(--ink);
  text-decoration: none;
  transition: border-color .18s, box-shadow .18s;
}
.event-card:hover, .rec-card:hover { border-color: var(--brand-line); box-shadow: var(--shadow-sm); }
.event-card:focus-visible, .rec-card:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }
.item-body { min-width: 0; overflow-wrap: anywhere; }
.event-date { display: block; color: var(--brand-strong); font-size: 12px; font-weight: 650; }
.item-body h3 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  margin: 3px 0 5px;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.4;
}
.item-meta { display: flex; align-items: center; gap: 5px; color: var(--ink-2); font-size: 12px; }
.item-meta svg { width: 13px; height: 13px; flex: none; color: var(--ink-3); }
.item-meta span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.event-tags { display: flex; align-items: center; flex-wrap: wrap; gap: 4px 7px; margin-top: 8px; color: var(--ink-3); font-size: 11px; }
.event-status { padding: 2px 6px; border-radius: 5px; color: var(--ink-2); background: var(--surface-2); font-weight: 650; }
.event-status.open { background: var(--success-soft); color: var(--success); }
.deadline { margin: 4px 0 0; color: var(--ink-3); font-size: 11px; }
.event-cover { position: relative; width: 100%; aspect-ratio: 1; border-radius: 9px; overflow: hidden; background: var(--surface-2); }
.event-cover-img, .event-cover :deep(.event-art) { position: absolute; inset: 0; width: 100%; height: 100%; }
.event-cover-img { display: block; object-fit: contain; }
.event-cover :deep(.event-art) { aspect-ratio: auto; }
.event-cover :deep(.event-art-copy) { padding: 8px; }
.event-cover :deep(.event-art-copy strong) { font-size: 14px; }
.page-bar { justify-content: center; margin-top: 13px; }

.rec-block { margin: 22px 0; padding: 13px; border: 1px solid var(--line); border-radius: 12px; background: var(--surface); }
.rec-head { display: flex; align-items: center; gap: 8px 11px; flex-wrap: wrap; }
.rec-head b { display: inline-flex; align-items: center; gap: 5px; color: var(--brand-strong); font-size: var(--fs-sm); }
.rec-head b svg { width: 15px; height: 15px; }
.rec-head span { font-size: var(--fs-cap); color: var(--ink-3); }
.rec-head .el-button { margin-inline-start: auto; }
.rec-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-top: 10px; }
.rec-card { display: block; min-width: 0; padding: 10px; border: 1px solid var(--line); border-radius: 9px; color: var(--ink); text-decoration: none; overflow-wrap: anywhere; }
.rec-card__top, .rec-meta { display: flex; flex-wrap: wrap; gap: 5px 8px; align-items: center; }
.rec-card__top b { font-size: var(--fs-sm); }
.rec-cat, .rec-meta { color: var(--ink-3); font-size: var(--fs-cap); }
.rec-reason { margin: 5px 0; font-size: var(--fs-cap); color: var(--ink-2); }

@media (max-width: 899px) {
  .activity-grid { grid-template-columns: minmax(0, 1fr); }
  .rec-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 540px) {
  .collection-head { margin-bottom: 13px; }
  .toolbar { padding: 9px; gap: 8px; }
  .activity-search { width: 100%; }
  .chip { padding: 4px 9px; }
  .event-card { grid-template-columns: minmax(0, 1fr) clamp(84px, 25vw, 108px); gap: 8px; padding: 9px; }
  .item-body h3 { font-size: 14px; }
  .event-tags { margin-top: 5px; }
  .rec-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
