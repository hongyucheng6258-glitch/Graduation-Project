<template>
  <div class="portal">
    <WtHero
      :hello="helloLine"
      title="课表之外，<br>还有整个校园。"
      description="找一场喜欢的活动，遇见同频的朋友。<br>学习和生活，在这里都有回应。"
      photo="/images/home-campus.webp"
      photo-note="秋日校园"
      :primary="{ label: '发现校园活动', to: '/activity' }"
      :secondary="{ label: '找 AI 帮忙', to: '/ai/chat' }"
      :event="heroEvent"
    />

    <nav class="quick-strip" aria-label="校园服务快捷入口">
      <RouterLink v-for="e in entries" :key="e.to" :to="e.to" class="quick-service">
        <span class="quick-icon" :class="e.color">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" v-html="ICONS[e.icon]"></svg>
        </span>
        <b>{{ e.title }}</b>
        <small>{{ e.desc }}</small>
      </RouterLink>
    </nav>

    <RouterLink class="campus3d-entry" to="/campus-3d">
      <span class="campus3d-entry-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m12 3 9 5v8l-9 5-9-5V8l9-5Zm0 9 9-4m-9 4L3 8m9 4v9"/></svg></span>
      <span class="campus3d-entry-copy">
        <b>进入3D校园</b>
        <small>走进一扇门，探索对应的校园服务空间</small>
      </span>
      <span class="campus3d-entry-arrow" aria-hidden="true">→</span>
    </RouterLink>

    <!-- ===== 双栏 ===== -->
    <div class="home-columns">
      <section>
        <!-- 活动区 -->
        <div class="section-title">
          <div>
            <h2>把课余时间，留给热爱</h2>
            <p>校园里的相遇，总有一场适合你。</p>
          </div>
          <RouterLink class="text-btn" to="/activity">查看全部 <span aria-hidden="true">→</span></RouterLink>
        </div>
        <div class="tabs home-tabs">
          <button
            v-for="t in tabs"
            :key="t.value"
            type="button"
            class="tab"
            :class="{ active: tab === t.value }"
            :aria-pressed="tab === t.value"
            @click="tab = t.value"
          >{{ t.label }}</button>
        </div>
        <div v-if="loading" class="cards home-cards">
          <div v-for="i in 3" :key="i" class="item event-card skeleton-card">
            <div class="skeleton-block skeleton-cover"></div>
            <div class="item-body"><div class="skeleton-block" style="height:14px;width:70%"></div></div>
          </div>
        </div>
        <div v-else-if="feedList.length" class="cards home-cards">
          <RouterLink
            v-for="a in feedList"
            :key="a.key"
            class="item event-card"
            :to="a.to"
          >
            <img v-if="firstContentImage(a, a.moduleId)" :src="firstContentImage(a, a.moduleId)" :alt="a.title" class="event-cover-img" />
            <WtEventArt v-else :item="a" />
            <div class="item-body">
              <div class="card-meta-row">
                <span class="card-category">{{ a.category }}</span>
                <span class="card-status" :class="{ open: a.statusText === '报名中' }">{{ a.statusText }}</span>
              </div>
              <h3>{{ a.title }}</h3>
              <div class="item-meta">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0z"/><circle cx="12" cy="10" r="3"/></svg>
                {{ a.location || '地点待定' }}
                <span class="meta-divider"></span>
                {{ a.timeText }}
              </div>
              <div class="item-bottom">
                <span v-if="a.memberCount != null">
                  {{ a.memberCount }}{{ a.moduleId === 'activity' ? ' 人同行' : ' 次浏览' }}
                </span>
                <span class="card-arrow" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
                </span>
              </div>
            </div>
          </RouterLink>
        </div>
        <div v-else class="empty">该分类下还没有内容，换个分类看看～</div>

        <!-- 好物不闲置 -->
        <div class="section-title spaced section-gap">
          <div>
            <h2>好物不闲置</h2>
            <p>换一件物品，也分享一段校园记忆。</p>
          </div>
          <RouterLink class="text-btn" to="/idle">逛逛互换区 <span aria-hidden="true">→</span></RouterLink>
        </div>
        <div v-if="data.idleItems.length" class="cards home-cards">
          <RouterLink
            v-for="i in data.idleItems.slice(0, 3)"
            :key="'i' + i.id"
            class="item object-card"
            :to="`/idle/detail/${i.id}`"
          >
            <div class="cover">
              <img v-if="firstContentImage(i, 'idle', i.category)" :src="firstContentImage(i, 'idle', i.category)" :alt="i.title" />
              <div v-else class="cover-fallback">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7h18l-2 13H5z"/><path d="M8 11v6M12 11v6M16 11v6"/></svg>
              </div>
            </div>
            <div class="item-body">
              <div class="card-meta-row">
                <span class="card-category">{{ i.category || '二手' }}</span>
                <span v-if="i.location" class="card-location">{{ i.location }}</span>
              </div>
              <h3>{{ i.title }}</h3>
              <p class="exchange-line">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 17l4-4M4 7v10M20 7l-4 4M20 17V7"/></svg>
                {{ i.expectItem ? '期望换：' + i.expectItem : '面议' }}
              </p>
              <div class="item-bottom">
                <span v-if="i.nickname">
                  <span class="mini-avatar">{{ i.nickname.charAt(0) }}</span>
                  {{ i.nickname }}
                </span>
                <span>看看怎么换 <span aria-hidden="true">→</span></span>
              </div>
            </div>
          </RouterLink>
        </div>
        <div v-else-if="!loading" class="empty">暂无闲置物品</div>

        <!-- 校园动态横幅 -->
        <RouterLink class="community-banner" to="/social">
          <div>
            <span>校园动态</span>
            <h3>有些小美好，<br>值得一起看见。</h3>
            <p>记录今天，也认识身边有趣的人。</p>
          </div>
          <img src="/images/05-校园晚霞.png" alt="校园晚霞" loading="lazy" />
          <span class="round-arrow" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
          </span>
        </RouterLink>
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
            <span v-if="wrongCount != null">你有 <b>{{ wrongCount }}</b> 道错题待复习</span>
            <span v-else>{{ userStore.isLoggedIn ? '查看待复习错题' : '登录后查看错题' }}</span>
            <button type="button" class="text-btn" @click="go({ to: '/ai/wrong', needLogin: true })">去复习</button>
          </div>
        </section>

        <!-- 校园公告 -->
        <section class="bulletin">
          <div class="section-title">
            <h3>校园公告</h3>
            <RouterLink class="text-btn" to="/notice">全部</RouterLink>
          </div>
          <RouterLink v-for="n in notices" :key="n.id" class="bulletin-item" :to="`/notice/detail/${n.id}`">
            <span><span>{{ n.type || '公告' }}</span><time>{{ formatNoticeDate(n.createTime || n.publishTime) }}</time></span>
            <h4>{{ n.title }}</h4>
          </RouterLink>
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
            <button type="button" class="text-btn" @click="go({ to: '/activity/my-signup', needLogin: true })">查看我的报名 <span aria-hidden="true">→</span></button>
          </div>
        </section>

        <!-- 消息入口 -->
        <RouterLink class="message-entry" to="/chat">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M21 11.5a8.5 8.5 0 0 1-12.5 7.5L3 21l2-5.5A8.5 8.5 0 1 1 21 11.5z"/></svg>
          <span><b>聊聊共同的兴趣</b><small>看看同学给你的留言</small></span>
          <svg class="msg-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
        </RouterLink>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { homeAggregate, listNotice } from '../../api/notice'
import { wrongStats } from '../../api/wrong'
import { formatTime } from '../../utils/date'
import { firstContentImage } from '../../utils/content-assets.mjs'
import { useUserStore } from '../../store/user'
import WtHero from '../../components/wt/WtHero.vue'
import WtEventArt from '../../components/wt/WtEventArt.vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(true)
const tab = ref('activity')

const ICONS = {
  spark: '<path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/><circle cx="12" cy="12" r="3"/>',
  calendar: '<rect x="3" y="4" width="18" height="17" rx="2"/><path d="M3 9h18M8 2v4M16 2v4"/>',
  bag: '<path d="M3 7h18l-2 13H5z"/><path d="M8 11v6M12 11v6M16 11v6"/>',
  lost: '<circle cx="12" cy="8" r="5"/><path d="M9 13l-1.5 8L12 18l4.5 3L15 13"/>',
  chat: '<path d="M21 11.5a8.5 8.5 0 0 1-12.5 7.5L3 21l2-5.5A8.5 8.5 0 1 1 21 11.5z"/>',
  book: '<path d="M4 19V5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2zM17 3v16"/>'
}

const entries = [
  { title: '校园活动', desc: '社团、讲座、运动', to: '/activity', color: 'blue', icon: 'calendar' },
  { title: '闲置互换', desc: '好物转让与交换', to: '/idle', color: 'peach', icon: 'bag' },
  { title: '失物招领', desc: '寻物、拾到登记', to: '/lostfound', color: 'sage', icon: 'lost' },
  { title: '学习搭子', desc: '找人一起自习', to: '/partner', color: 'lavender', icon: 'spark' },
  { title: '互助问答', desc: '校园问题解答', to: '/qa', color: 'blue', icon: 'chat' },
  { title: 'AI 学习', desc: '答疑、错题、纠错', to: '/ai/chat', color: 'peach', icon: 'book' }
]

const tabs = [
  { value: 'activity', label: '校园活动' },
  { value: 'idle', label: '闲置互换' },
  { value: 'lost', label: '失物招领' }
]

const data = ref({ activities: [], idleItems: [], lostFounds: [] })
const notices = ref([])
const wrongCount = ref(null)

const nickname = computed(() => userStore.userInfo?.nickname || '同学')
const helloLine = computed(() => {
  const h = new Date().getHours()
  const word = h < 11 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
  return `${word}，${nickname.value}。今天也有新发现。`
})

const heroEvent = computed(() => {
  const a = data.value.activities?.[0]
  if (!a) return null
  const date = formatTime(a.startTime)
  const hasDate = /^\d{4}-\d{2}-\d{2}/.test(date)
  const count = a.memberCount != null ? ` · ${a.memberCount} 人同行` : ''
  return {
    day: hasDate ? date.slice(8, 10) : '待定',
    month: hasDate ? `${Number(date.slice(5, 7))} 月` : '',
    title: a.title,
    sub: `${a.location || '地点待定'} · ${hasDate ? date.slice(11, 16) + ' 开始' : '时间待定'}${count}`,
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
      memberCount: i.viewCount,
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
      memberCount: l.viewCount,
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
    memberCount: a.memberCount,
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
  }
  loading.value = false
})
</script>

<style scoped>
.portal { padding-bottom: var(--s-5); }

.quick-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
  margin: 24px 0 14px;
}
.quick-service {
  display: block;
  min-width: 0;
  padding: 18px 16px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--ink);
  text-decoration: none;
  overflow-wrap: anywhere;
  transition: border-color .18s, box-shadow .18s;
}
.quick-service:hover { border-color: var(--brand-line); box-shadow: var(--shadow-md); }
.quick-service b { display: block; font-size: var(--fs-body); font-weight: 650; }
.quick-service small { display: block; margin-top: 3px; color: var(--ink-3); font-size: var(--fs-xs); }
.quick-icon { display: grid; place-items: center; width: 38px; height: 38px; margin-bottom: 12px; border-radius: var(--r-sm); }
.quick-icon svg { width: 20px; height: 20px; }
.quick-icon.blue { background: var(--brand-soft); color: var(--brand); }
.quick-icon.peach { background: var(--accent-soft); color: var(--accent-ink); }
.quick-icon.sage { background: var(--sage); color: var(--success); }
.quick-icon.lavender { background: var(--purple-soft); color: var(--purple); }

.campus3d-entry {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-bottom: 24px;
  padding: 10px 16px;
  border: 1px solid var(--brand-line);
  border-radius: var(--r-md);
  background: linear-gradient(100deg, var(--brand-soft), var(--surface));
  color: var(--ink);
  text-decoration: none;
  transition: border-color .18s, box-shadow .18s;
}
.campus3d-entry:hover { border-color: var(--brand); box-shadow: var(--shadow-sm); }
.campus3d-entry-icon { width: 36px; height: 36px; display: grid; place-items: center; flex: none; border-radius: 10px; background: var(--brand); color: var(--brand-ink); }
.campus3d-entry-icon svg { width: 20px; height: 20px; }
.campus3d-entry-copy b, .campus3d-entry-copy small { display: block; }
.campus3d-entry-copy b { color: var(--brand-strong); font-size: var(--fs-body); }
.campus3d-entry-copy small { color: var(--ink-2); font-size: var(--fs-xs); }
.campus3d-entry-arrow { margin-left: auto; color: var(--brand); font-size: 20px; }

.home-columns { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 28px; align-items: start; }
.home-aside { display: flex; flex-direction: column; gap: 18px; }
.section-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.section-title h2 { color: var(--ink); font-size: var(--fs-h2); font-weight: 700; letter-spacing: .2px; }
.section-title h3 { color: var(--ink); font-size: var(--fs-body); font-weight: 650; }
.section-title p { margin-top: 2px; color: var(--ink-3); font-size: var(--fs-sm); }
.section-title .muted { font-size: var(--fs-sm); }
.section-gap { margin-top: 32px; }
.text-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  padding: 3px 0;
  background: none;
  color: var(--brand);
  font-size: var(--fs-sm);
  white-space: nowrap;
  cursor: pointer;
  text-decoration: none;
}
.text-btn:hover { text-decoration: underline; text-underline-offset: 4px; }
.home-tabs { display: flex; flex-wrap: wrap; gap: 6px; margin: -4px 0 12px; }
.tab {
  min-height: 32px;
  padding: 4px 11px;
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  background: var(--surface);
  color: var(--ink-2);
  font-size: var(--fs-xs);
  cursor: pointer;
}
.tab.active, .tab:hover { border-color: var(--brand-line); background: var(--brand-soft); color: var(--brand-strong); }
.tab.active { font-weight: 650; }

.cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; }
.item {
  display: block;
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  overflow: hidden;
  background: var(--surface);
  text-decoration: none;
  transition: border-color .18s, box-shadow .18s;
}
.item:hover { border-color: var(--brand-line); box-shadow: var(--shadow-md); }
.item-body { padding: 14px 16px; overflow-wrap: anywhere; }
.item-body h3 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: var(--ink);
  font-size: var(--fs-body);
  font-weight: 650;
  line-height: 1.45;
}
.card-meta-row { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-bottom: 8px; }
.card-category, .card-status { padding: 2px 8px; border-radius: var(--r-pill); font-size: var(--fs-xs); font-weight: 600; }
.card-category { background: var(--brand-soft); color: var(--brand-strong); }
.card-status { background: var(--surface-2); color: var(--ink-2); }
.card-status.open { background: var(--success-soft); color: var(--success); }
.object-card .card-category { background: var(--peach); color: var(--accent-ink); }
.card-location { color: var(--ink-3); font-size: var(--fs-xs); }
.item-meta, .exchange-line { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin-top: 10px; color: var(--ink-3); font-size: var(--fs-xs); }
.item-meta svg, .exchange-line svg { width: 14px; height: 14px; flex: none; }
.meta-divider { width: 1px; height: 10px; background: var(--line-strong); margin: 0 2px; }
.item-bottom { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-top: 12px; color: var(--ink-3); font-size: var(--fs-xs); }
.item-bottom > span { display: inline-flex; align-items: center; gap: 6px; }
.card-arrow { margin-left: auto; color: var(--brand); }
.card-arrow svg { width: 14px; height: 14px; }
.event-cover-img, .event-card :deep(.event-art) { display: block; width: 100%; height: 150px; aspect-ratio: auto; object-fit: cover; }
.cover { height: 130px; overflow: hidden; background: var(--surface-2); }
.cover img { display: block; width: 100%; height: 100%; object-fit: cover; }
.cover-fallback { height: 100%; display: grid; place-items: center; color: var(--ink-3); }
.cover-fallback svg { width: 40px; height: 40px; }
.mini-avatar { width: 24px; height: 24px; display: inline-grid; place-items: center; border-radius: 50%; background: var(--peach); color: var(--accent-ink); font-size: var(--fs-cap); font-weight: 600; flex: none; }
.empty { padding: 32px 16px; border: 1px dashed var(--line-strong); border-radius: var(--r-md); color: var(--ink-3); text-align: center; font-size: var(--fs-sm); }
.skeleton-block { border-radius: 6px; background: linear-gradient(90deg, var(--surface-2), var(--surface-3), var(--surface-2)); background-size: 200% 100%; animation: shimmer 1.4s infinite; }
.skeleton-cover { min-height: 150px; }
@keyframes shimmer { from { background-position: 200% 0; } to { background-position: -200% 0; } }

.community-banner {
  display: flex;
  align-items: center;
  gap: 22px;
  margin-top: 32px;
  padding: 22px 24px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  text-decoration: none;
}
.community-banner:hover { border-color: var(--brand-line); }
.community-banner > div > span { color: var(--accent-ink); font-size: var(--fs-xs); font-weight: 600; }
.community-banner h3 { margin: 6px 0; color: var(--ink); font-size: var(--fs-h3); }
.community-banner p { color: var(--ink-3); font-size: var(--fs-sm); }
.community-banner > img { display: block; width: 180px; max-width: 37%; height: 96px; border-radius: var(--r-sm); object-fit: cover; }
.round-arrow { display: grid; place-items: center; width: 38px; height: 38px; margin-left: auto; border-radius: 50%; background: var(--brand-soft); color: var(--brand); flex: none; }
.round-arrow svg { width: 16px; height: 16px; }

.study-note { padding: 22px; border-radius: var(--r-md); background: linear-gradient(150deg, #1e3a8a, #2e5bff); color: #fff; }
.study-note-top { display: flex; align-items: center; gap: 8px; color: #edf1ff; font-size: var(--fs-sm); }
.study-note-top .service-icon { display: flex; }
.study-note-top svg { width: 16px; height: 16px; }
.study-note h3 { margin: 12px 0 6px; color: inherit; font-size: var(--fs-h3); font-weight: 700; }
.study-note p { color: #edf1ff; font-size: var(--fs-sm); }
.study-note > .btn { display: inline-flex; align-items: center; gap: 8px; min-height: 38px; margin-top: 16px; padding: 0 16px; border: 1px solid #fff; border-radius: var(--r-sm); background: #fff; color: #1e47d6; font-size: var(--fs-body); font-weight: 600; cursor: pointer; }
.study-note > .btn:hover { background: #edf1ff; }
.study-todo { display: flex; align-items: center; gap: 8px; margin-top: 16px; padding: 8px 12px; border-radius: var(--r-sm); background: rgba(255, 255, 255, .14); font-size: var(--fs-xs); }
.study-todo > svg { width: 15px; height: 15px; flex: none; }
.study-todo b, .study-todo .text-btn { color: #ffe3b3; }
.study-todo .text-btn { margin-left: auto; font-size: var(--fs-xs); }

.bulletin, .calendar-panel, .message-entry { padding: 18px; border: 1px solid var(--line); border-radius: var(--r-md); background: var(--surface); }
.bulletin .section-title, .calendar-panel .section-title { margin-bottom: 12px; }
.bulletin-item { display: block; padding: 10px 0; border-bottom: 1px solid var(--line); text-decoration: none; }
.bulletin-item:last-child { padding-bottom: 0; border-bottom: 0; }
.bulletin-item > span { display: flex; align-items: center; justify-content: space-between; color: var(--ink-3); font-size: var(--fs-cap); }
.bulletin-item > span > span { padding: 2px 8px; border-radius: var(--r-pill); background: var(--surface-2); color: var(--ink-2); }
.bulletin-item h4 { margin-top: 4px; color: var(--ink); font-size: var(--fs-sm); font-weight: 500; }
.bulletin-item:hover h4 { color: var(--brand); }
.muted { color: var(--ink-3); font-size: var(--fs-xs); }
.weekly { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 4px; text-align: center; }
.weekly > span { display: grid; justify-items: center; gap: 4px; color: var(--ink-3); font-size: var(--fs-cap); }
.weekly b { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 50%; color: var(--ink-2); font-size: var(--fs-xs); }
.weekly .today { background: var(--brand); color: var(--brand-ink); }
.calendar-empty { margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--line); color: var(--ink-3); text-align: center; }
.calendar-empty > svg { width: 20px; height: 20px; margin: 0 auto; color: var(--brand); }
.calendar-empty p { margin: 5px 0 2px; font-size: var(--fs-xs); }
.calendar-empty .text-btn { font-size: var(--fs-xs); }
.message-entry { display: flex; align-items: center; gap: 12px; padding: 16px 18px; text-decoration: none; }
.message-entry:hover { border-color: var(--brand-line); }
.message-entry > svg:first-child { width: 38px; height: 38px; padding: 9px; border-radius: var(--r-sm); background: var(--brand-soft); color: var(--brand); flex: none; }
.message-entry > span { flex: 1; }
.message-entry b { display: block; color: var(--ink); font-size: var(--fs-sm); }
.message-entry small { display: block; margin-top: 2px; color: var(--ink-3); font-size: var(--fs-xs); }
.msg-arrow { width: 16px; height: 16px; color: var(--ink-3); flex: none; }

@media (max-width: 1120px) {
  .home-columns { grid-template-columns: minmax(0, 1fr) 270px; gap: 20px; }
  .cards { gap: 12px; }
  .item-body { padding: 12px; }
}
@media (max-width: 950px) {
  .quick-strip { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .home-columns { grid-template-columns: minmax(0, 1fr); }
  .home-aside { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: start; }
}
@media (max-width: 700px) {
  .quick-strip { gap: 8px; margin-top: 16px; }
  .quick-service { padding: 11px 8px; }
  .quick-service b { font-size: var(--fs-sm); }
  .quick-service small { font-size: var(--fs-cap); }
  .quick-icon { width: 32px; height: 32px; margin-bottom: 7px; }
  .quick-icon svg { width: 18px; height: 18px; }
  .section-title h2 { font-size: var(--fs-h3); }
  .section-title p { font-size: var(--fs-xs); }
  .cards, .home-aside { grid-template-columns: minmax(0, 1fr); }
  .event-card, .object-card { display: grid; grid-template-columns: minmax(0, 34%) minmax(0, 1fr); }
  .event-cover-img, .event-card :deep(.event-art), .object-card .cover { height: 100%; min-height: 132px; }
  .skeleton-cover { min-height: 132px; }
  .community-banner { gap: 12px; padding: 16px; }
  .community-banner > img { max-width: 32%; }
  .round-arrow { width: 27px; height: 27px; }
}
@media (prefers-reduced-motion: reduce) {
  .skeleton-block { animation: none; }
}
</style>
