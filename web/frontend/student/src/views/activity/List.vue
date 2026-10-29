<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <div class="activity-page">
    <!-- V2 列表头：collection-head -->
    <section class="collection-head activity">
      <div>
        <span class="collection-label">校园活动 <b>{{ total }} 条校园活动</b></span>
        <h1>总有一场相遇，刚好是你喜欢的。</h1>
        <p>与兴趣相逢，与同伴同行。</p>
        <button type="button" class="btn primary" @click="goPublish">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
          发布活动
        </button>
      </div>
      <div class="collection-graphic" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="4" width="18" height="17" rx="2"/><path d="M3 9h18M8 2v4M16 2v4"/><path d="M8 13h.01M12 13h.01M16 13h.01M8 17h.01M12 17h.01"/></svg>
      </div>
    </section>

    <!-- 工具栏 -->
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索活动…" clearable style="width: 260px" @keyup.enter="search" @clear="search">
        <template #append><el-button @click="search">搜索</el-button></template>
      </el-input>
      <div class="chips">
        <span class="chip" :class="{ active: !category }" @click="selectCategory('')">全部</span>
        <span v-for="c in categories" :key="c" class="chip" :class="{ active: category === c }" @click="selectCategory(c)">{{ c }}</span>
      </div>
      <div class="spacer" />
      <el-button @click="$router.push('/activity/my-signup')">我的报名</el-button>
    </div>

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
        <div v-for="r in recList" :key="r.id" class="rec-card" @click="$router.push(`/activity/detail/${r.id}`)">
          <div class="rec-card__top">
            <b>{{ r.title }}</b>
            <span class="rec-cat">{{ r.category }}</span>
          </div>
          <p class="rec-reason">💡 {{ r.reason }}</p>
          <div class="rec-meta">
            <span>🕐 {{ formatTime(r.startTime) }}</span>
            <span>📍 {{ r.location || '地点待定' }}</span>
            <span>{{ r.memberCount }} 人已报名</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 活动卡片网格（V2 EventArt 封面） -->
    <div class="grid" v-loading="loading">
      <a
        v-for="a in list"
        :key="a.id"
        class="item event-card"
        @click="$router.push(`/activity/detail/${a.id}`)"
      >
        <img v-if="firstContentImage(a, 'activity')" :src="firstContentImage(a, 'activity')" :alt="a.title" class="event-cover-img" />
        <WtEventArt v-else :item="a" />
        <div class="item-body">
          <div class="event-title-row">
            <span class="event-date">
              <b>{{ dayOf(a.startTime) }}</b>
              <small>{{ monthOf(a.startTime) }}</small>
            </span>
            <div>
              <span class="card-category">{{ a.category || '校园活动' }} <i></i> {{ statusText(a) }}</span>
              <h3>{{ a.title }}</h3>
            </div>
          </div>
          <div class="item-meta">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0z"/><circle cx="12" cy="10" r="3"/></svg>
            {{ a.location || '地点待定' }}
            <span class="meta-divider"></span>
            {{ formatTime(a.startTime).slice(5) }}
          </div>
          <div class="item-bottom">
            <span>
              <span class="avatar-stack"><span>林</span><span>陈</span><span>周</span></span>
              {{ a.memberCount }}{{ a.maxMembers ? '/' + a.maxMembers : '' }} 人报名
            </span>
            <span class="card-arrow" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
            </span>
          </div>
        </div>
      </a>
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
  </div>
</template>

<script setup>
import { useListState } from '../../utils/list-state'
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import EmptyBox from '../../components/EmptyBox.vue'
import WtEventArt from '../../components/wt/WtEventArt.vue'
import { firstContentImage } from '../../utils/content-assets.mjs'
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

function formatTime(t) {
  if (!t) return ''
  return String(t).slice(0, 16)
}
function dayOf(t) {
  if (!t) return '新'
  return String(t).slice(8, 10).replace(/^0/, '') || '新'
}
function monthOf(t) {
  if (!t) return '校园'
  return `${Number(String(t).slice(5, 7))} 月`
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
  loading.value = true
  loadError.value = ''
  try {
    const res = await listActivity({ keyword: keyword.value || undefined, category: category.value || undefined, pageNum: pageNum.value, pageSize: 12 })
    list.value = res.list
    total.value = res.total
  } catch (error) {
    loadError.value = error.message || '内容加载失败，请重试'
  } finally {
    loading.value = false
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
.activity-page { display: flex; flex-direction: column; }

/* —— V2 列表头 —— */
.collection-head {
  background: var(--brand-soft);
  min-height: 250px;
  padding: 32px 36px;
  border-radius: 20px;
  margin-bottom: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  overflow: hidden;
  position: relative;
}
.collection-head > div:first-child { z-index: 2; max-width: 80%; }
.collection-label {
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 15px;
  color: var(--brand-strong);
}
.collection-label b {
  font-size: 11px;
  font-weight: 400;
  color: var(--brand-strong);
  border-left: 1px solid var(--brand-line);
  padding-left: 15px;
  opacity: .8;
}
.collection-head h1 { font-size: 31px; margin: 13px 0 9px; color: var(--ink); font-weight: 700; letter-spacing: -.6px; }
.collection-head p { font-size: 13px; color: var(--ink-3); }
.collection-head .btn { margin-top: 22px; font-size: 13px; min-height: 40px; }
.collection-graphic { margin-right: 25px; color: var(--brand-line); transform: rotate(12deg); }
.collection-graphic svg { width: 115px; height: 115px; stroke-width: 1; }

/* —— 工具栏 —— */
.toolbar { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; align-items: center; }
.spacer { flex: 1; }
.chips { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.chip {
  padding: 6px 14px;
  border-radius: var(--r-pill);
  border: 1px solid var(--line);
  background: var(--surface);
  font-size: var(--fs-xs);
  font-weight: 600;
  color: var(--ink-2);
  cursor: pointer;
  transition: all .18s var(--ease-out);
}
.chip:hover { border-color: var(--brand-line); }
.chip.active { background: var(--brand-soft); color: var(--brand-strong); border-color: var(--brand-line); }

/* —— AI 推荐（保留） —— */
.rec-block {
  margin-bottom: 24px;
  padding: 14px 16px;
  border: 1px dashed var(--brand-line);
  border-radius: var(--r-md);
  background: linear-gradient(120deg, var(--brand-soft), var(--peach));
}
.rec-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.rec-head b { font-size: var(--fs-sm); color: var(--brand-strong); display: inline-flex; align-items: center; gap: 6px; }
.rec-head b svg { width: 16px; height: 16px; }
.rec-head span { font-size: var(--fs-cap); color: var(--ink-3); }
.rec-head .el-button { margin-left: auto; }
.rec-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.rec-card {
  padding: 12px 14px; border: 1px solid var(--line); border-radius: var(--r-md);
  background: var(--surface); cursor: pointer; transition: all .18s var(--ease-out);
}
.rec-card:hover { border-color: var(--brand-line); transform: translateY(-2px); }
.rec-card__top { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.rec-card__top b { flex: 1; font-size: var(--fs-cap); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ink); }
.rec-cat { padding: 2px 8px; border-radius: var(--r-pill); background: var(--brand-soft); color: var(--brand-strong); font-size: var(--fs-cap); font-weight: 600; flex-shrink: 0; }
.rec-reason { margin: 0 0 8px; font-size: var(--fs-cap); color: var(--success); line-height: 1.6; }
.rec-meta { display: flex; flex-wrap: wrap; gap: 10px; font-size: var(--fs-cap); color: var(--ink-3); }

/* —— 活动卡片（V2 EventArt） —— */
.grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; margin-bottom: 16px; }
.item {
  display: block;
  min-width: 0;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  text-decoration: none;
  transition: border-color .2s, box-shadow .2s;
}
.item:hover { border-color: var(--brand-line); box-shadow: 0 8px 26px oklch(25% 0.04 265 / .05); }
.event-cover-img { width: 100%; aspect-ratio: 16 / 9; object-fit: cover; display: block; }
.item-body { padding: 18px; }
.item-body h3 {
  font-size: 14px;
  line-height: 1.6;
  font-weight: 650;
  color: var(--ink);
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.item-meta { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--ink-3); margin-top: 12px; flex-wrap: wrap; }
.item-meta svg { width: 14px; height: 14px; flex: none; }
.meta-divider { width: 1px; height: 10px; background: var(--line); margin: 0 4px; }
.item-bottom { display: flex; justify-content: space-between; align-items: center; gap: 9px; border-top: 1px solid var(--line); padding-top: 14px; margin-top: 17px; font-size: 11px; color: var(--ink-3); }
.item-bottom > span { display: flex; align-items: center; gap: 6px; }
.card-arrow { color: var(--brand); display: inline-flex; }
.card-arrow svg { width: 14px; height: 14px; }
.avatar-stack { display: inline-flex; align-items: center; flex-shrink: 0; padding-left: 4px; }
.avatar-stack span {
  width: 25px; height: 25px;
  border: 2px solid var(--surface);
  background: var(--peach); color: var(--ink-2);
  border-radius: 50%;
  display: grid; place-items: center;
  font-size: 9px; font-weight: 600;
  margin-left: -5px;
}
.avatar-stack span:nth-child(2) { background: var(--info-soft); color: var(--info-strong); }
.avatar-stack span:nth-child(3) { background: var(--purple-soft); color: var(--purple-strong); }
.card-category { font-size: 11px; color: var(--ink-3); margin-bottom: 4px; display: flex; gap: 5px; align-items: center; }
.card-category i { height: 3px; width: 3px; background: var(--ink-3); border-radius: 50%; display: inline-block; }
.event-title-row { display: flex; gap: 12px; align-items: flex-start; }
.event-title-row > div { min-width: 0; flex: 1; }
.event-date {
  padding-right: 12px;
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  min-width: 42px;
}
.event-date b { font-size: 25px; line-height: 1.15; letter-spacing: -1px; font-weight: 650; color: var(--ink); }
.event-date small { font-size: 10px; color: var(--ink-3); margin-top: 5px; }
.page-bar { justify-content: center; margin-top: 8px; }

@media (max-width: 1080px) {
  .collection-head h1 { font-size: 27px; }
  .rec-grid { grid-template-columns: repeat(2, 1fr); }
  .grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .collection-head { padding: 25px 23px; min-height: 240px; margin-bottom: 25px; border-radius: 16px; }
  .collection-head > div:first-child { max-width: 100%; }
  .collection-label { font-size: 12px; gap: 10px; }
  .collection-label b { font-size: 9px; padding-left: 10px; }
  .collection-head h1 { font-size: 28px; line-height: 1.55; max-width: 280px; margin: 15px 0 10px; }
  .collection-head p { font-size: 12px; max-width: 250px; }
  .collection-head .btn { min-height: 38px; font-size: 12px; margin-top: 22px; }
  .collection-graphic { right: -14px; bottom: -17px; opacity: .35; margin: 0; position: absolute; }
  .collection-graphic svg { height: 100px; width: 100px; }
  .toolbar { gap: 12px; margin-bottom: 18px; }
  .grid { grid-template-columns: 1fr; gap: 16px; }
  .event-date { min-width: 36px; padding-right: 9px; }
  .event-date b { font-size: 21px; }
  .rec-grid { grid-template-columns: 1fr; }
}
</style>
