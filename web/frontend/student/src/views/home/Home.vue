<template>
  <div class="portal">
    <!-- ===== V2 Hero：左文案 + 右校园摄影 + 活动预告 ===== -->
    <WtHero
      :hello="helloLine"
      title="课表之外，<br>还有整个校园。"
      description="找一场喜欢的活动，遇见同频的朋友。<br>学习和生活，在这里都有回应。"
      photo="/images/campus-v2.webp"
      photo-note="秋日校园 · 2026"
      :primary="{ label: '发现校园活动', to: '/activity' }"
      :secondary="{ label: '找 AI 帮忙', to: '/ai/chat' }"
      :event="heroEvent"
    />

    <button class="campus3d-entry" type="button" @click="go({ to: '/campus-3d' })">
      <span class="campus3d-entry-icon" aria-hidden="true">◆</span>
      <span class="campus3d-entry-copy">
        <b>进入3D校园</b>
        <small>走进一扇门，探索对应的校园服务空间</small>
      </span>
      <span class="campus3d-entry-arrow" aria-hidden="true">→</span>
    </button>

    <!-- ===== 快捷服务条 ===== -->
    <div class="quick-strip" aria-label="校园服务快捷入口">
      <WtQuickEntry
        v-for="e in entries"
        :key="e.to"
        :title="e.title"
        :desc="e.desc"
        :color="e.color"
        @click="go(e)"
      >
        <template #icon>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" v-html="ICONS[e.icon]"></svg>
        </template>
      </WtQuickEntry>
    </div>

    <!-- ===== 双栏 ===== -->
    <div class="home-columns">
      <section>
        <!-- 活动区 -->
        <div class="section-title">
          <div>
            <h2>把课余时间，留给热爱</h2>
            <p>校园里的相遇，总有一场适合你。</p>
          </div>
          <a class="text-btn" @click="go({ to: '/activity' })">查看全部 <span aria-hidden="true">→</span></a>
        </div>
        <div class="tabs home-tabs">
          <button
            v-for="t in tabs"
            :key="t.value"
            type="button"
            class="tab"
            :class="{ active: tab === t.value }"
            @click="tab = t.value"
          >{{ t.label }}</button>
        </div>
        <div v-if="loading" class="cards home-cards">
          <div v-for="i in 3" :key="i" class="item event-card skeleton-card">
            <div class="skeleton-block" style="height:178px"></div>
            <div class="item-body"><div class="skeleton-block" style="height:14px;width:70%"></div></div>
          </div>
        </div>
        <div v-else-if="feedList.length" class="cards home-cards">
          <a
            v-for="a in feedList"
            :key="a.key"
            class="item event-card"
            @click="go({ to: a.to, needLogin: a.needLogin })"
          >
            <img v-if="firstContentImage(a, a.moduleId)" :src="firstContentImage(a, a.moduleId)" :alt="a.title" class="event-cover-img" />
            <WtEventArt v-else :item="a" />
            <div class="item-body">
              <div class="event-title-row">
                <span class="event-date">
                  <b>{{ a.day }}</b>
                  <small>{{ a.month }}</small>
                </span>
                <div>
                  <span class="card-category">{{ a.category }} <i></i> {{ a.statusText }}</span>
                  <h3>{{ a.title }}</h3>
                </div>
              </div>
              <div class="item-meta">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0z"/><circle cx="12" cy="10" r="3"/></svg>
                {{ a.location || '地点待定' }}
                <span class="meta-divider"></span>
                {{ a.timeText }}
              </div>
              <div class="item-bottom">
                <span>
                  <span class="avatar-stack"><span>林</span><span>陈</span><span>周</span></span>
                  {{ a.memberCount }} 人同行
                </span>
                <span class="card-arrow" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
                </span>
              </div>
            </div>
          </a>
        </div>
        <div v-else class="empty">该分类下还没有内容，换个分类看看～</div>

        <!-- 好物不闲置 -->
        <div class="section-title spaced section-gap">
          <div>
            <h2>好物不闲置</h2>
            <p>换一件物品，也分享一段校园记忆。</p>
          </div>
          <a class="text-btn" @click="go({ to: '/idle' })">逛逛互换区 <span aria-hidden="true">→</span></a>
        </div>
        <div class="cards home-cards">
          <a
            v-for="i in data.idleItems.slice(0, 3)"
            :key="'i' + i.id"
            class="item object-card"
            @click="go({ to: `/idle/detail/${i.id}` })"
          >
            <div class="cover">
              <img v-if="firstContentImage(i, 'idle', i.category)" :src="firstContentImage(i, 'idle', i.category)" :alt="i.title" />
              <div v-else class="cover-fallback">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7h18l-2 13H5z"/><path d="M8 11v6M12 11v6M16 11v6"/></svg>
              </div>
              <span class="tag">{{ i.category || '二手' }}</span>
            </div>
            <div class="item-body">
              <div class="card-category">{{ i.location || '校内面交' }}</div>
              <h3>{{ i.title }}</h3>
              <p class="exchange-line">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 17l4-4M4 7v10M20 7l-4 4M20 17V7"/></svg>
                {{ i.expectItem ? '期望换：' + i.expectItem : '面议' }}
              </p>
              <div class="item-bottom">
                <span>
                  <span class="mini-avatar">{{ (i.nickname || '同').charAt(0) }}</span>
                  {{ i.nickname || '同学' }}
                </span>
                <span>看看怎么换 <span aria-hidden="true">→</span></span>
              </div>
            </div>
          </a>
        </div>

        <!-- 校园动态横幅 -->
        <a class="community-banner" @click="go({ to: '/social' })">
          <div>
            <span>校园动态</span>
            <h3>有些小美好，<br>值得一起看见。</h3>
            <p>记录今天，也认识身边有趣的人。</p>
          </div>
          <img src="/images/05-校园晚霞.png" alt="同学分享的校园晚霞" loading="lazy" />
          <span class="round-arrow" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
          </span>
        </a>
      </section>

      <!-- 右栏 -->
      <aside class="home-aside">
        <!-- 学习卡 -->
        <section class="study-note">
          <div class="study-note-top">
            <span class="service-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/><circle cx="12" cy="12" r="3"/></svg>
            </span>
            <span>一点点进步，也值得</span>
          </div>
          <h3>今天的难题，<br>我们一起解。</h3>
          <p>从一个问题开始，让思路慢慢清晰。</p>
          <button type="button" class="btn primary" @click="go({ to: '/ai/chat', needLogin: true })">
            开始学习 <span aria-hidden="true">→</span>
          </button>
          <div class="study-todo">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 19V5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2zM17 3v16"/></svg>
            <span>你有 <b>{{ wrongCount }}</b> 道错题待复习</span>
            <a class="text-btn" @click="go({ to: '/ai/wrong', needLogin: true })">去复习</a>
          </div>
        </section>

        <!-- 校园公告 -->
        <section class="bulletin">
          <div class="section-title">
            <h3>校园公告</h3>
            <a class="text-btn" @click="go({ to: '/notice' })">全部</a>
          </div>
          <a v-for="n in notices" :key="n.id" class="bulletin-item" @click="go({ to: `/notice/detail/${n.id}` })">
            <span><span>{{ n.type || '公告' }}</span><time>{{ formatNoticeDate(n.createTime || n.publishTime) }}</time></span>
            <h4>{{ n.title }}</h4>
          </a>
          <div v-if="!notices.length" class="muted">暂无公告</div>
        </section>

        <!-- 校园日程 -->
        <section class="calendar-panel">
          <div class="section-title">
            <h3>我的校园日程</h3>
            <span class="muted">{{ monthLabel }}</span>
          </div>
          <div class="weekly">
            <span v-for="(d, i) in weekDays" :key="i">
              <span>{{ d.week }}</span>
              <b :class="{ today: i === todayIdx }">{{ d.day }}</b>
            </span>
          </div>
          <div class="calendar-empty">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="4" width="18" height="17" rx="2"/><path d="M3 9h18M8 2v4M16 2v4"/></svg>
            <p>把下一次期待，安排进日程</p>
            <a class="text-btn" @click="go({ to: '/activity/my-signup', needLogin: true })">查看我的报名 <span aria-hidden="true">→</span></a>
          </div>
        </section>

        <!-- 消息入口 -->
        <a class="message-entry" @click="go({ to: '/chat', needLogin: true })">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M21 11.5a8.5 8.5 0 0 1-12.5 7.5L3 21l2-5.5A8.5 8.5 0 1 1 21 11.5z"/></svg>
          <span><b>聊聊共同的兴趣</b><small>看看同学给你的留言</small></span>
          <svg class="msg-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
        </a>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { homeAggregate, listNotice } from '../../api/notice'
import { listPost } from '../../api/post'
import { listMessage } from '../../api/message'
import { wrongStats } from '../../api/wrong'
import { formatTime } from '../../utils/date'
import { firstContentImage } from '../../utils/content-assets.mjs'
import { useUserStore } from '../../store/user'
import { useMessageStore } from '../../store/message'
import WtHero from '../../components/wt/WtHero.vue'
import WtQuickEntry from '../../components/wt/WtQuickEntry.vue'
import WtEventArt from '../../components/wt/WtEventArt.vue'

const router = useRouter()
const userStore = useUserStore()
const messageStore = useMessageStore()
const loading = ref(true)
const tab = ref('activity')

const ICONS = {
  spark: '<path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/><circle cx="12" cy="12" r="3"/>',
  calendar: '<rect x="3" y="4" width="18" height="17" rx="2"/><path d="M3 9h18M8 2v4M16 2v4"/>',
  bag: '<path d="M3 7h18l-2 13H5z"/><path d="M8 11v6M12 11v6M16 11v6"/>',
  lost: '<circle cx="12" cy="8" r="5"/><path d="M9 13l-1.5 8L12 18l4.5 3L15 13"/>'
}

const entries = [
  { title: '你的学习搭子', desc: '答疑、读 PDF、整理错题', to: '/ai/chat', color: 'blue', icon: 'spark', needLogin: true },
  { title: '去见见新朋友', desc: '活动报名，一起出发', to: '/activity', color: 'peach', icon: 'calendar' },
  { title: '给好物下一站', desc: '校园互换，循环有趣', to: '/idle', color: 'sage', icon: 'bag' },
  { title: '找回那份牵挂', desc: '寻物、认领、分享线索', to: '/lostfound', color: 'lavender', icon: 'lost' }
]

const tabs = [
  { value: 'activity', label: '校园活动' },
  { value: 'idle', label: '闲置互换' },
  { value: 'lost', label: '失物招领' }
]

const data = ref({ activities: [], idleItems: [], lostFounds: [] })
const notices = ref([])
const wrongCount = ref(0)

const nickname = computed(() => userStore.userInfo?.nickname || '同学')
const helloLine = computed(() => {
  const h = new Date().getHours()
  const word = h < 11 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
  return `${word}，${nickname.value}。今天也有新发现。`
})

const heroEvent = computed(() => {
  const a = data.value.activities?.[0]
  if (!a) return { day: '25', month: '9 月', label: '本周值得期待', title: '落日之后，一起听见青春', sub: '南区大草坪 · 18:30 开始', to: '/activity' }
  const d = new Date(a.startTime)
  return {
    day: String(d.getDate()).padStart(2, '0'),
    month: `${d.getMonth() + 1} 月`,
    label: '本周值得期待',
    title: a.title,
    sub: `${a.location || '地点待定'} · ${formatTime(a.startTime).slice(5)} 开始`,
    to: `/activity/detail/${a.id}`
  }
})

const STATUS_TEXT = ['报名中', '已满', '已结束', '已下架']
function statusText(a) {
  return a.displayStatusText || STATUS_TEXT[a.status] || '报名中'
}
function timeText(t) {
  return formatTime(t).slice(5) || '待定'
}
function dayOf(t) {
  if (!t) return '新'
  return String(t).slice(8, 10).replace(/^0/, '') || '新'
}
function monthOf(t) {
  if (!t) return '校园'
  return `${Number(String(t).slice(5, 7))} 月`
}

const feedList = computed(() => {
  const d = data.value
  if (tab.value === 'idle') {
    return (d.idleItems || []).slice(0, 3).map((i) => ({
      key: 'i' + i.id,
      id: i.id,
      category: i.category || '闲置',
      statusText: '二手',
      title: i.title,
      location: i.location,
      timeText: i.price ? '¥' + i.price : '面议',
      day: '换',
      month: '好物',
      memberCount: i.viewCount ?? 0,
      images: i.images,
      imageList: i.imageList,
      moduleId: 'idle',
      to: `/idle/detail/${i.id}`
    }))
  }
  if (tab.value === 'lost') {
    return (d.lostFounds || []).slice(0, 3).map((l) => ({
      key: 'l' + l.id,
      id: l.id,
      category: l.type === 0 ? '寻物启事' : '失物招领',
      statusText: l.type === 0 ? '寻物' : '待认领',
      title: l.title,
      location: l.location,
      timeText: l.type === 0 ? '发布寻物' : '等待认领',
      day: '寻',
      month: '牵挂',
      memberCount: l.viewCount ?? 0,
      images: l.images,
      imageList: l.imageList,
      moduleId: 'lost',
      to: `/lostfound/detail/${l.id}`
    }))
  }
  return (d.activities || []).slice(0, 3).map((a) => ({
    key: 'a' + a.id,
    id: a.id,
    category: a.category || '校园活动',
    statusText: statusText(a),
    title: a.title,
    location: a.location,
    timeText: timeText(a.startTime),
    day: dayOf(a.startTime),
    month: monthOf(a.startTime),
    memberCount: a.memberCount || 0,
    images: a.images,
    imageList: a.imageList,
    moduleId: 'activity',
    to: `/activity/detail/${a.id}`
  }))
})

function go(entry) {
  if (entry.needLogin && !userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  router.push(entry.to)
}

// 本周日历
const now = new Date()
const todayDow = now.getDay() // 0 周日
const mondayOffset = todayDow === 0 ? -6 : 1 - todayDow
const monday = new Date(now)
monday.setDate(now.getDate() + mondayOffset)
const weekDays = [0, 1, 2, 3, 4, 5, 6].map((i) => {
  const d = new Date(monday)
  d.setDate(monday.getDate() + i)
  return { week: '一二三四五六日'[i], day: d.getDate() }
})
const todayIdx = todayDow === 0 ? 6 : todayDow - 1
const monthLabel = computed(() => `${now.getMonth() + 1} 月`)

function formatNoticeDate(t) {
  if (!t) return ''
  return formatTime(t).slice(5, 10).replace('-', '/')
}

onMounted(async () => {
  try {
    const agg = await homeAggregate()
    data.value.activities = agg.activities || []
    data.value.idleItems = agg.idleItems || []
    data.value.lostFounds = agg.lostFounds || []
  } catch (e) {
    // 聚合接口异常不影响页面骨架
  }
  try {
    const nr = await listNotice({ pageNum: 1, pageSize: 3 })
    const arr = Array.isArray(nr) ? nr : (nr.list || [])
    notices.value = arr.slice(0, 3)
  } catch (e) {}
  if (userStore.isLoggedIn) {
    try {
      const st = await wrongStats()
      wrongCount.value = (st && (st.pendingReview ?? st.reviewing ?? st.total ?? 0)) || 0
    } catch (e) {}
  } else {
    wrongCount.value = 0
  }
  loading.value = false
})
</script>

<style scoped>
.portal { display: flex; flex-direction: column; }

.campus3d-entry {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 13px;
  margin: -12px 0 32px;
  padding: 15px 18px;
  border: 1px solid var(--brand-line);
  border-radius: 14px;
  background: linear-gradient(100deg, var(--brand-soft), var(--surface));
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  transition: border-color .18s, box-shadow .18s, transform .18s;
}
.campus3d-entry:hover { border-color: var(--brand); box-shadow: var(--shadow-md); transform: translateY(-1px); }
.campus3d-entry-icon { width: 38px; height: 38px; display: grid; place-items: center; flex: none; border-radius: 11px; background: var(--brand); color: #fff; font-size: 14px; box-shadow: 0 6px 15px oklch(56% .19 265 / .18); }
.campus3d-entry-copy { min-width: 0; }
.campus3d-entry-copy b, .campus3d-entry-copy small { display: block; }
.campus3d-entry-copy b { color: var(--brand-strong); font-size: 14px; }
.campus3d-entry-copy small { margin-top: 3px; color: var(--ink-3); font-size: 11px; }
.campus3d-entry-arrow { margin-left: auto; color: var(--brand); font-size: 20px; }

/* —— 快捷服务条 —— */
.quick-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border: 1px solid var(--line);
  background: var(--surface);
  border-radius: 14px;
  margin-bottom: 43px;
  padding: 21px 9px;
}

/* —— 区块标题 —— */
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 21px;
}
.section-title h2 { font-size: 23px; font-weight: 650; color: var(--ink); letter-spacing: -.4px; margin: 0; }
.section-title h3 { font-size: 18px; font-weight: 650; color: var(--ink); margin: 0; }
.section-title p { font-size: 13px; margin-top: 5px; color: var(--ink-3); }
.section-title .muted { font-size: 11px; color: var(--ink-3); }
.section-gap { margin-top: 42px; }
.spaced { margin-top: 28px; }
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
  text-decoration: none;
}
.text-btn:hover { text-decoration: underline; text-underline-offset: 4px; }

/* —— Tab —— */
.tabs { display: flex; gap: 8px; flex-wrap: wrap; margin: 0 0 24px; align-items: center; }
.tab {
  border: 1px solid transparent;
  background: none;
  border-radius: 20px;
  padding: 6px 15px;
  font-size: 12px;
  min-height: 33px;
  color: var(--ink-3);
  cursor: pointer;
  transition: background .18s, color .18s;
  font-family: var(--font-sans);
}
.tab.active { background: var(--ink); color: var(--surface); font-weight: 600; }
.tab:hover { color: var(--brand); }
.home-tabs { margin-top: -6px; margin-bottom: 20px; }

/* —— 卡片 —— */
.cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
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
.item-body { padding: 15px; }
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
.item-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ink-3);
  margin-top: 12px;
  flex-wrap: wrap;
}
.item-meta svg { width: 14px; height: 14px; flex: none; }
.meta-divider { width: 1px; height: 10px; background: var(--line); margin: 0 4px; }
.item-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 9px;
  border-top: 1px solid var(--line);
  padding-top: 14px;
  margin-top: 17px;
  font-size: 11px;
  color: var(--ink-3);
}
.item-bottom > span { display: flex; align-items: center; gap: 6px; }
.item-bottom svg { width: 14px; height: 14px; }
.avatar-stack {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  padding-left: 4px;
}
.avatar-stack span {
  width: 25px;
  height: 25px;
  border: 2px solid var(--surface);
  background: var(--peach);
  color: var(--ink-2);
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 9px;
  font-weight: 600;
  margin-left: -5px;
}
.avatar-stack span:nth-child(2) { background: var(--info-soft); color: var(--info-strong); }
.avatar-stack span:nth-child(3) { background: var(--purple-soft); color: var(--purple-strong); }
.card-arrow { color: var(--brand); display: inline-flex; }
.card-arrow svg { width: 14px; height: 14px; }
.card-category {
  font-size: 11px;
  color: var(--ink-3);
  margin-bottom: 4px;
  display: flex;
  gap: 5px;
  align-items: center;
}
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
.event-date b { font-size: 22px; line-height: 1.15; letter-spacing: -1px; font-weight: 650; color: var(--ink); }
.event-date small { font-size: 10px; color: var(--ink-3); margin-top: 5px; }

/* 闲置卡 */
.cover { height: 175px; position: relative; overflow: hidden; background: var(--surface-2); }
.cover img { width: 100%; height: 100%; object-fit: cover; }
.event-cover-img { width: 100%; aspect-ratio: 16 / 9; object-fit: cover; display: block; }
.cover-fallback { width: 100%; height: 100%; display: grid; place-items: center; color: var(--ink-3); }
.cover-fallback svg { width: 40px; height: 40px; }
.cover .tag {
  position: absolute;
  left: 12px;
  top: 12px;
  background: oklch(100% 0 0 / .94);
  color: var(--ink-2);
  backdrop-filter: blur(5px);
  border-radius: 5px;
  padding: 3px 9px;
  font-size: 11px;
  font-weight: 500;
}
.exchange-line {
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--success);
  margin-top: 10px;
  min-height: 22px;
}
.exchange-line svg { width: 15px; height: 15px; flex: none; }
.mini-avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-grid;
  place-items: center;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 9px;
  font-weight: 600;
}
.empty {
  padding: 50px 25px;
  text-align: center;
  color: var(--ink-3);
  border: 1px dashed var(--line);
  border-radius: 14px;
  font-size: 13px;
}
.skeleton-card { cursor: default; }
.skeleton-block { background: linear-gradient(90deg, var(--surface-2), var(--surface-3), var(--surface-2)); background-size: 200% 100%; animation: shimmer 1.4s infinite; border-radius: 6px; margin-bottom: 8px; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

/* —— 双栏 —— */
.home-columns { display: grid; grid-template-columns: minmax(0, 1fr) 294px; gap: 30px; align-items: start; }
.home-aside { padding-top: 2px; display: flex; flex-direction: column; gap: 29px; }

/* 学习卡 */
.study-note {
  background: var(--brand-soft);
  border: 1px solid var(--brand-line);
  border-radius: 18px;
  padding: 23px;
}
.study-note-top { display: flex; gap: 10px; align-items: center; color: var(--brand-strong); font-size: 11px; }
.study-note-top .service-icon {
  background: #fff;
  width: 33px;
  height: 33px;
  border-radius: 10px;
  display: grid;
  place-items: center;
}
.study-note-top .service-icon svg { width: 17px; height: 17px; }
.study-note h3 { font-size: 24px; line-height: 1.5; margin: 18px 0 10px; color: var(--ink); letter-spacing: -.5px; font-weight: 650; }
.study-note > p { font-size: 12px; line-height: 1.9; color: var(--ink-3); }
.study-note > .btn { margin-top: 19px; width: 100%; font-size: 13px; justify-content: space-between; }
.btn {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  gap: 9px;
  min-height: 44px;
  padding: 10px 20px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  color: var(--ink);
  font-size: 14px;
  font-weight: 550;
  white-space: nowrap;
  line-height: 1.6;
  cursor: pointer;
  transition: border-color .18s, background-color .18s, color .18s;
}
.btn:hover { border-color: var(--brand-line); background: #fff; }
.btn.primary { background: var(--brand); border-color: var(--brand); color: #fff; }
.btn.primary:hover { background: var(--brand-strong); border-color: var(--brand-strong); }
.study-todo {
  border-top: 1px solid var(--brand-line);
  margin-top: 19px;
  padding-top: 17px;
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 10px;
  color: var(--ink-3);
}
.study-todo > svg { width: 14px; height: 14px; flex: none; color: var(--brand); }
.study-todo b { color: var(--brand); }
.study-todo .text-btn { margin-left: auto; font-size: 10px; }

/* 公告 */
.bulletin { padding: 0 4px; }
.bulletin .section-title { margin-bottom: 2px; }
.bulletin-item { display: block; padding: 16px 0; border-bottom: 1px solid var(--line); text-decoration: none; }
.bulletin-item:last-child { border-bottom: none; }
.bulletin-item > span { font-size: 10px; color: var(--ink-3); display: flex; justify-content: space-between; }
.bulletin-item h4 { font-size: 13px; line-height: 1.8; font-weight: 500; margin-top: 8px; color: var(--ink); }
.bulletin-item:hover h4 { color: var(--brand); }
.bulletin .muted { font-size: 12px; color: var(--ink-3); padding: 14px 0; }

/* 日历 */
.calendar-panel { border: 1px solid var(--line); border-radius: 16px; padding: 21px; background: var(--surface); }
.calendar-panel .section-title { margin-bottom: 16px; }
.weekly { display: flex; justify-content: space-between; gap: 7px; }
.weekly > span { display: flex; flex-direction: column; align-items: center; gap: 9px; font-size: 10px; color: var(--ink-3); }
.weekly b { width: 28px; height: 32px; display: grid; place-items: center; font-size: 12px; font-weight: 500; border-radius: 8px; color: var(--ink); }
.weekly .today { background: var(--brand); color: #fff; }
.calendar-empty { text-align: center; border-top: 1px solid var(--line); padding-top: 19px; margin-top: 18px; color: var(--ink-3); }
.calendar-empty > svg { width: 25px; height: 25px; color: var(--brand); margin: 0 auto; }
.calendar-empty p { font-size: 11px; margin: 9px 0 4px; color: var(--ink-3); }
.calendar-empty .text-btn { font-size: 11px; }

/* 消息入口 */
.message-entry { display: flex; align-items: center; gap: 11px; padding: 0 4px; text-decoration: none; cursor: pointer; }
.message-entry > svg:first-child { color: var(--brand); width: 20px; height: 20px; flex: none; }
.message-entry > span { flex: 1; min-width: 0; }
.message-entry b { display: block; font-size: 12px; font-weight: 500; color: var(--ink); }
.message-entry small { display: block; font-size: 10px; color: var(--ink-3); margin-top: 3px; }
.msg-arrow { width: 16px; height: 16px; color: var(--ink-3); flex: none; }

/* 动态横幅 */
.community-banner {
  position: relative;
  display: flex;
  background: var(--sage);
  color: var(--ink-2);
  border-radius: 17px;
  overflow: hidden;
  min-height: 207px;
  align-items: center;
  margin-top: 32px;
  text-decoration: none;
  cursor: pointer;
}
.community-banner > div { padding: 24px; z-index: 2; flex: 1; }
.community-banner > div > span { font-size: 11px; color: var(--ink-3); }
.community-banner h3 { font-size: 24px; line-height: 1.45; margin-top: 8px; color: var(--ink-2); font-weight: 650; }
.community-banner p { font-size: 12px; color: var(--ink-3); margin-top: 12px; }
.community-banner > img { width: 47%; height: 207px; object-fit: cover; clip-path: polygon(10% 0, 100% 0, 100% 100%, 0 100%); }
.community-banner .round-arrow {
  position: absolute;
  right: 18px;
  bottom: 18px;
  background: #fff;
  color: var(--ink-2);
  width: 35px;
  height: 35px;
  border-radius: 50%;
  display: grid;
  place-items: center;
}
.community-banner .round-arrow svg { width: 16px; height: 16px; }

/* —— 响应式 —— */
@media (max-width: 1250px) {
  .home-columns { grid-template-columns: minmax(0, 1fr) 265px; gap: 24px; }
  .cards { gap: 12px; }
  .item-body { padding: 13px; }
  .event-date { padding-right: 9px; min-width: 35px; }
  .quick-service small { font-size: 10px; }
}
@media (max-width: 1080px) {
  .home-columns { grid-template-columns: 1fr; }
  .home-aside { display: grid; grid-template-columns: 1fr 1fr; gap: 25px; margin-top: 10px; }
  .home-aside .study-note { margin: 0; }
  .home-aside .bulletin { margin: 0; }
  .home-aside .calendar-panel, .home-aside .message-entry { display: none; }
  .event-date { min-width: 42px; padding-right: 12px; }
  .cover { height: 195px; }
}
@media (max-width: 760px) {
  .quick-strip { grid-template-columns: 1fr 1fr; padding: 0; border-radius: 13px; margin-bottom: 32px; overflow: hidden; }
  .quick-service { padding: 17px 12px; gap: 9px; border: 0; }
  .quick-service:nth-child(odd) { border-right: 1px solid var(--line); }
  .quick-service:nth-child(-n+2) { border-bottom: 1px solid var(--line); }
  .quick-service .service-icon { height: 33px; width: 33px; border-radius: 10px; }
  .quick-service b { font-size: 12px; }
  .quick-service small { font-size: 9px; margin-top: 4px; }
  .section-title { gap: 10px; align-items: center; margin-bottom: 17px; }
  .section-title h2 { font-size: 20px; letter-spacing: -.5px; }
  .section-title p { font-size: 12px; line-height: 1.8; }
  .home-tabs { gap: 6px; margin-top: 0; margin-bottom: 17px; }
  .home-tabs .tab { padding: 6px 11px; font-size: 11px; }
  .cards { grid-template-columns: 1fr; gap: 16px; }
  .event-card, .object-card { display: grid; grid-template-columns: 120px minmax(0, 1fr); }
  .event-card :deep(.event-art), .object-card .cover { height: 100%; min-height: 175px; }
  .item-body { padding: 15px; }
  .event-date { min-width: 30px; padding-right: 9px; }
  .event-date b { font-size: 21px; }
  .event-date small { font-size: 9px; }
  .item-body h3 { font-size: 13px; line-height: 1.6; }
  .card-category { font-size: 9px; }
  .item-meta { font-size: 10px; margin-top: 10px; gap: 4px; }
  .item-meta .meta-divider, .item-meta .meta-divider + span { display: none; }
  .item-bottom { font-size: 9px; padding-top: 11px; margin-top: 11px; }
  .avatar-stack span { height: 19px; width: 19px; font-size: 7px; }
  .item-bottom .card-arrow { display: none; }
  .exchange-line { font-size: 10px; margin-top: 10px; }
  .item-bottom > span:last-child { display: none; }
  .home-aside { grid-template-columns: 1fr; gap: 28px; margin-top: 0; }
  .study-note { padding: 25px; }
  .study-note h3 { font-size: 27px; }
  .study-note > .btn { width: auto; min-width: 160px; gap: 28px; }
  .study-note > p { font-size: 13px; }
  .study-todo { font-size: 12px; margin-top: 22px; }
  .study-todo .text-btn { font-size: 12px; }
  .study-note-top { font-size: 12px; }
  .bulletin-item h4 { font-size: 14px; }
  .community-banner { min-height: 195px; margin-top: 28px; }
  .community-banner > div { padding: 21px; }
  .community-banner h3 { font-size: 22px; }
  .community-banner p { font-size: 11px; max-width: 150px; }
  .community-banner > img { width: 40%; height: 218px; }
  .community-banner .round-arrow { height: 29px; width: 29px; right: 13px; bottom: 13px; }
}
@media (max-width: 370px) {
  .event-card, .object-card { grid-template-columns: 110px minmax(0, 1fr); }
  .section-title h2 { font-size: 18px; }
}
</style>
