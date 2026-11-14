<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <div class="activity-page">
    <section class="collection-head">
      <div>
        <h1>校园活动</h1>
        <p>发现热爱，也遇见一起参与的人。</p>
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
      <div class="chips" aria-label="活动分类">
        <button type="button" class="chip" :class="{ active: !category }" :aria-pressed="!category" @click="selectCategory('')">全部</button>
        <button v-for="c in categories" :key="c" type="button" class="chip" :class="{ active: category === c }"
          :aria-pressed="category === c" @click="selectCategory(c)">{{ c }}</button>
      </div>
      <el-input v-model="keyword" class="activity-search" placeholder="搜索活动…" aria-label="搜索活动" clearable @keyup.enter="search" @clear="search">
        <template #append><el-button @click="search">搜索</el-button></template>
      </el-input>
    </div>

    <div class="result-heading">
      <h2>{{ category || '活动列表' }}</h2>
      <span v-if="!loading && !loadError">{{ total }} 场活动</span>
    </div>
    <div class="grid photo-feed activity-grid" v-loading="loading">
      <RouterLink
        v-for="a in list"
        :key="a.id"
        class="event-card photo-feed__link"
        :to="{ name: 'ActivityDetail', params: { id: a.id } }"
      >
        <div class="content-photo event-cover">
          <img v-if="firstContentImage(a)" :src="firstContentImage(a)" alt="" class="content-photo__image" loading="lazy"
            @error="onImageError($event, 'activity')" />
          <WtEventArt v-else :item="a" aria-hidden="true" />
          <span class="event-status content-photo__badge" :class="{ open: statusText(a) === '报名中' }">
            {{ statusText(a) }}
          </span>
        </div>
        <div class="item-body">
          <h3 :title="a.title">{{ a.title }}</h3>
          <div class="event-details">
            <div class="item-meta event-date">
              <el-icon aria-hidden="true"><Calendar /></el-icon>
              <time v-if="a.startTime" :datetime="a.startTime">{{ formatEventTime(a.startTime) }}</time>
              <span v-else>时间待定</span>
            </div>
            <div class="item-meta">
              <el-icon aria-hidden="true"><Location /></el-icon>
              <span :title="a.location">{{ a.location || '地点待定' }}</span>
            </div>
          </div>
          <footer class="event-footer">
            <div class="event-tags">
              <span class="event-category" :title="a.category">{{ a.category || '校园活动' }}</span>
              <span class="event-count"><strong>{{ a.memberCount || 0 }}{{ a.maxMembers ? ' / ' + a.maxMembers : '' }}</strong> 人报名</span>
            </div>
            <p v-if="a.signupDeadline" class="deadline">
              <span>截止</span>
              <time :datetime="a.signupDeadline">{{ formatEventTime(a.signupDeadline, false) }}</time>
            </p>
          </footer>
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
import { Calendar, Location } from '@element-plus/icons-vue'
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
.activity-page { min-width: 0; container-type: inline-size; }
.collection-head, .header-actions, .result-heading { display: flex; align-items: center; gap: 12px; }
.collection-head {
  justify-content: space-between;
  flex-wrap: wrap;
  padding-bottom: 20px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--line);
}
.collection-head h1 { margin: 0 0 6px; color: var(--ink); font-size: clamp(26px, 3vw, 36px); line-height: 1.18; }
.collection-head p { margin: 0; color: var(--ink-3); font-size: var(--fs-sm); }
.header-actions { flex-wrap: wrap; gap: 8px; }
.header-actions .btn { min-height: 36px; padding: 0 12px; }
.header-actions svg { width: 15px; height: 15px; }
.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 10px 16px;
}
.activity-search { width: min(100%, 270px); }
.chips { display: flex; flex-wrap: wrap; gap: 8px; }
.chip {
  padding: 6px 13px;
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
.result-heading { justify-content: space-between; align-items: baseline; margin: 22px 0 12px; }
.result-heading h2 { margin: 0; color: var(--ink); font-size: 18px; }
.result-heading span { color: var(--ink-3); font-size: 12px; }
.rec-card:hover { border-color: var(--brand-line); box-shadow: var(--shadow-sm); }
.rec-card:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }
.activity-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.activity-grid .event-cover { aspect-ratio: 16 / 9; }
@media (max-width: 900px) { .activity-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) { .activity-grid { grid-template-columns: minmax(0, 1fr); } }
.event-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  transition: border-color .18s, box-shadow .18s;
}
.event-card:hover { border-color: var(--brand-line); box-shadow: var(--shadow-sm); }
.item-body { display: flex; flex: 1; flex-direction: column; gap: 10px; min-width: 0; padding: 12px 14px; }
.item-body h3 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  min-block-size: 2.8em;
  overflow: hidden;
  overflow-wrap: anywhere;
  margin: 0;
  color: var(--ink);
  font-size: 17px;
  font-weight: 700;
  line-height: 1.4;
}
.event-card:hover h3 { color: var(--brand-strong); }
.event-details { display: grid; gap: 4px; }
.item-meta { display: flex; align-items: center; gap: 6px; min-width: 0; color: var(--ink-2); font-size: 12px; line-height: 1.45; }
.item-meta .el-icon { flex: none; color: var(--ink-3); font-size: 14px; }
.item-meta :is(span, time) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.event-date { color: var(--brand-strong); font-weight: 600; }
.event-date .el-icon { color: inherit; }
.event-status { padding: 3px 10px; border-radius: var(--r-pill); color: var(--ink-2); background: var(--surface); font-size: var(--fs-cap); font-weight: 650; }
.event-status.open { background: var(--success-soft); color: var(--success); }
.event-footer { padding-top: 9px; border-top: 1px solid var(--line); }
.event-tags { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.event-category {
  min-width: 0;
  padding: 2px 7px;
  overflow: hidden;
  border-radius: var(--r-pill);
  background: var(--surface-2);
  color: var(--ink-2);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.event-count, .deadline { color: var(--ink-3); font-size: 11px; font-variant-numeric: tabular-nums; }
.event-count { white-space: nowrap; }
.event-count strong { color: var(--ink-2); }
.deadline { display: flex; flex-wrap: wrap; gap: 2px 5px; margin: 6px 0 0; line-height: 1.4; }
.event-cover :deep(.event-art) { position: absolute; inset: 0; width: 100%; height: 100%; aspect-ratio: auto; }
.event-cover :deep(.event-art-copy) { display: none; }
.event-cover :deep(.event-graphic) {
  inset: 50% auto auto 50%;
  width: 72%;
  height: 72%;
  translate: -50% -50%;
}
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

@container (max-width: 1020px) {
  .activity-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .rec-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@container (max-width: 640px) {
  .activity-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
  .collection-head { padding-bottom: 13px; margin-bottom: 13px; }
  .activity-search { width: 100%; }
  .chip { padding: 4px 10px; }
  .result-heading { margin-top: 16px; }
  .item-body { gap: 8px; padding: 9px; }
  .item-body h3 { font-size: 15px; }
  .item-meta { gap: 4px; font-size: 11px; }
  .item-meta .el-icon { font-size: 12px; }
  .event-tags { gap: 4px; }
  .event-category { padding-inline: 5px; }
  .rec-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
