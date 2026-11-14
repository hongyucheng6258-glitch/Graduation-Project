<template>
  <div ref="portal" class="portal">
    <WtHero
      :hello="helloLine"
      title="课表之外，<br>还有整个校园。"
      description="找一场喜欢的活动，遇见同频的朋友。<br>学习和生活，在这里都有回应。"
      photo="/images/home-campus.webp"
      photo-note="探索 3D 校园"
      photo-to="/campus-3d"
      :primary="{ label: '发现校园活动', to: '/activity' }"
      :secondary="{ label: '找 AI 帮忙', to: '/ai/chat' }"
    />


    <section class="quick-section">
      <div class="quick-heading"><h2>快捷校园</h2><span>常用服务，一步抵达</span></div>
      <nav class="quick-strip" aria-label="校园服务快捷入口">
        <RouterLink v-for="entry in entries" :key="entry.to" :to="entry.to" class="quick-service">
          <span class="quick-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              aria-hidden="true" v-html="ICONS[entry.icon]" />
          </span>
          <b>{{ entry.title }}</b>
          <small>{{ entry.desc }}</small>
        </RouterLink>
      </nav>
    </section>

    <section class="home-section">
      <header class="section-title">
        <div>
          <h2>把课余时间，留给热爱</h2>
          <p>校园里的活动，总有一场适合你。</p>
          <div class="chips" aria-label="首页推荐筛选">
            <button v-for="filter in filters" :key="filter.label" class="chip" type="button"
              :aria-pressed="activeFilter.label === filter.label" @click="activeFilter = filter">{{ filter.label }}</button>
          </div>
        </div>
        <RouterLink class="text-link" to="/activity">查看全部活动 →</RouterLink>
      </header>
      <div v-if="loadingActivities" class="home-cards" aria-busy="true" aria-label="加载活动">
        <div v-for="i in 3" :key="i" class="home-card">
          <div class="cover skeleton-block"></div>
          <div class="card-body">加载中…</div>
        </div>
      </div>
      <div v-else-if="activityError" class="home-state">
        <p>活动加载失败</p><button class="btn" type="button" @click="loadActivities">重试</button>
      </div>
      <div v-else-if="activities.length" class="home-cards">
        <article v-for="item in activities" :key="item.id" class="home-card">
          <RouterLink :to="`/activity/detail/${item.id}`" class="card-link">
            <div class="cover">
              <img :src="firstContentImage(item, 'activity')" alt="" loading="lazy" @error="onImageError($event, 'activity')" />
              <span class="badge">{{ item.displayStatusText || activityStatuses[item.status] || '活动' }}</span>
            </div>
            <div class="card-body">
              <h3>{{ item.title }}</h3>
              <p>{{ shortDate(item.startTime) }} · {{ item.location || '地点待定' }}</p>
              <div class="card-meta"><span>{{ item.category || '校园活动' }}</span><span>{{ item.memberCount ?? 0 }} 人同行</span></div>
            </div>
          </RouterLink>
          <button class="save-button" type="button" :aria-label="`收藏 ${item.title}`" :aria-pressed="saved[item.id] === true"
            :disabled="saving.has(item.id)" :class="{ 'save-feedback': favoriteFeedback.has(item.id) }"
            @animationend="favoriteFeedback.delete(item.id)" @click="toggleFavorite(item)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M6 4h12v16l-6-4-6 4z" />
            </svg>
          </button>
        </article>
      </div>
      <div v-else class="home-state">该分类暂无活动</div>
    </section>

    <section class="home-section feature">
      <div class="feature-media">
        <img src="/images/generated-social-campus.webp" alt="同学们在校园中交流分享" loading="lazy" />
      </div>
      <div class="feature-copy">
        <h2>有些小美好，<br />值得一起分享。</h2>
        <p>记录校园生活，参与评论与点赞。你画我猜也在这里，用灵感连接彼此。</p>
        <div class="feature-actions">
          <RouterLink class="btn primary" to="/social">去动态广场</RouterLink>
          <RouterLink class="text-link" to="/draw-guess">你画我猜 →</RouterLink>
        </div>
      </div>
    </section>

    <section class="home-section">
      <header class="section-title">
        <div><h2>好物不闲置</h2><p>让闲置遇见新主人。</p></div>
        <RouterLink class="text-link" to="/idle">进入闲置互换 →</RouterLink>
      </header>
      <div v-if="idleItems.length" class="home-cards">
        <RouterLink v-for="item in idleItems" :key="item.id" :to="`/idle/detail/${item.id}`" class="home-card card-link">
          <div class="cover">
            <img v-if="firstContentImage(item, 'idle', item.category)" :src="firstContentImage(item, 'idle', item.category)"
              alt="" loading="lazy" @error="onImageError($event, 'idle', item.category)" />
            <span class="badge">{{ idleStatuses[item.status] || '闲置' }}</span>
          </div>
          <div class="card-body">
            <h3>{{ item.title }}</h3>
            <p>{{ item.category || '闲置' }} · {{ item.location || '地点待定' }}</p>
            <div class="card-meta">
              <span>{{ item.expectItem ? '期望换 ' + item.expectItem : '查看交换说明' }}</span>
              <span class="text-link">{{ item.status === 0 ? '预约' : '查看详情' }}</span>
            </div>
          </div>
        </RouterLink>
      </div>
      <div v-else class="home-state">{{ aggregateLoading ? '加载中…' : aggregateError ? '闲置加载失败' : '暂无闲置物品' }}</div>
    </section>

    <section class="home-section feature reverse">
      <div class="feature-copy">
        <h2>今天的难题，<br />一起解。</h2>
        <p>学习搭子、互助问答、代码纠错与错题复习，形成完整的学习闭环。</p>
        <RouterLink class="review-link" to="/ai/wrong">
          <template v-if="wrongCount != null"><b>{{ wrongCount }}</b> 道错题待复习</template>
          <template v-else>{{ userStore.isLoggedIn ? '查看待复习错题' : '登录后查看错题' }}</template>
        </RouterLink>
        <div class="feature-actions">
          <RouterLink class="btn primary" to="/partner">找到学习搭子</RouterLink>
          <RouterLink class="text-link" to="/qa">互助问答 →</RouterLink>
        </div>
      </div>
      <div class="feature-media">
        <img src="/images/generated-study-partner.webp" alt="学习伙伴共同讨论" loading="lazy" />
      </div>
    </section>

    <section class="home-section">
      <header class="section-title">
        <div><h2>每一份遗失，都有回应</h2><p>登记、匹配、核实与认领，让寻找更有方向。</p></div>
        <RouterLink class="text-link" to="/lostfound">查看全部 →</RouterLink>
      </header>
      <div v-if="lostItems.length" class="lost-list">
        <div v-for="item in lostItems" :key="item.id" class="lost-row">
          <img :src="firstContentImage(item, 'lost')" alt="" loading="lazy" @error="onImageError($event, 'lost')" />
          <div>
            <RouterLink :to="`/lostfound/detail/${item.id}`"><b>{{ item.title }}</b></RouterLink>
            <small>{{ item.type === 0 ? '寻物' : '拾到' }} · {{ shortDate(item.happenTime || item.createTime) }} · {{ item.location || '地点待定' }}</small>
          </div>
          <RouterLink class="btn" :to="`/lostfound/detail/${item.id}`">{{ item.type === 0 ? '我有线索' : '申请认领' }}</RouterLink>
        </div>
      </div>
      <div v-else class="home-state">{{ aggregateLoading ? '加载中…' : aggregateError ? '失物加载失败' : '暂无失物信息' }}</div>
      <button v-if="aggregateError" class="btn aggregate-retry" type="button" @click="loadAggregate">重新加载闲置与失物</button>
    </section>

    <section id="ai-section" class="home-section feature">
      <div class="feature-media">
        <img src="/images/generated-ai-study.webp" alt="AI 学习场景" loading="lazy" />
      </div>
      <div class="ai-panel">
        <header class="ai-title">
          <span class="ai-symbol">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" v-html="ICONS.spark" />
          </span>
          <div><h2>AI 学习</h2><small>随时获得学习回应</small></div>
          <RouterLink class="text-link" to="/ai/chat">进入助手 →</RouterLink>
        </header>
        <div class="chat-preview"><p>你想了解哪些校园活动或学习资源？</p></div>
        <div class="chips">
          <button v-for="prompt in prompts" :key="prompt" class="chip" type="button" @click="usePrompt(prompt)">{{ prompt }}</button>
        </div>
        <form class="ai-form" @submit.prevent="ask">
          <input ref="questionInput" v-model.trim="question" maxlength="2000" placeholder="输入你的问题…" aria-label="向 AI 学习助手提问" />
          <button class="btn primary" type="submit" aria-label="发送问题" :disabled="!question">发送</button>
        </form>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listActivity } from '../../api/activity'
import { favorite, favoriteStatus, unfavorite } from '../../api/favorite'
import { homeAggregate } from '../../api/notice'
import { wrongStats } from '../../api/wrong'
import WtHero from '../../components/wt/WtHero.vue'
import { useUserStore } from '../../store/user'
import { firstContentImage, onImageError } from '../../utils/content-assets.mjs'
import { formatTime } from '../../utils/date'

const ICONS = {
  spark: '<path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/><circle cx="12" cy="12" r="3"/>',
  calendar: '<rect x="3" y="4" width="18" height="17" rx="2"/><path d="M3 9h18M8 2v4M16 2v4"/>',
  bag: '<path d="M3 7h18l-2 13H5z"/><path d="M8 11v6M12 11v6M16 11v6"/>',
  lost: '<circle cx="12" cy="8" r="5"/><path d="M9 13l-1.5 8L12 18l4.5 3L15 13"/>',
  chat: '<path d="M21 11.5a8.5 8.5 0 0 1-12.5 7.5L3 21l2-5.5A8.5 8.5 0 1 1 21 11.5z"/>',
  book: '<path d="M4 19V5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2zM17 3v16"/>'
}

const entries = [
  { title: '校园活动', desc: '社团、讲座、运动', to: '/activity', icon: 'calendar' },
  { title: '闲置互换', desc: '好物转让与交换', to: '/idle', icon: 'bag' },
  { title: '失物招领', desc: '寻物、拾到登记', to: '/lostfound', icon: 'lost' },
  { title: '学习搭子', desc: '找人一起自习', to: '/partner', icon: 'spark' },
  { title: '互助问答', desc: '校园问题解答', to: '/qa', icon: 'chat' },
  { title: 'AI 学习', desc: '答疑、错题、纠错', to: '/ai/chat', icon: 'book' }
]

const router = useRouter()
const userStore = useUserStore()
const filters = [
  { label: '精选推荐', categories: [] },
  { label: '文化艺术', categories: ['文艺娱乐', '文化艺术'] },
  { label: '体育运动', categories: ['体育运动'] }
]
const activityStatuses = ['报名中', '已满', '已结束', '已下架']
const idleStatuses = ['可预约', '交接中', '已完成', '已下架']
const activeFilter = ref(filters[0])
const activities = ref([])
const idleItems = ref([])
const lostItems = ref([])
const wrongCount = ref(null)
const loadingActivities = ref(true)
const activityError = ref(false)
const aggregateLoading = ref(true)
const aggregateError = ref(false)
const saved = reactive({})
const saving = reactive(new Set())
const favoriteFeedback = reactive(new Set())
const portal = ref(null)
let revealObserver
const revealAnimations = new Set()
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')

function cancelReveals() {
  favoriteFeedback.clear()
  for (const animation of revealAnimations) animation.cancel()
  revealAnimations.clear()
}

function observeSections() {
  if (!('IntersectionObserver' in window) || !Element.prototype.animate) return
  revealObserver = new IntersectionObserver(entries => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue
      revealObserver.unobserve(entry.target)
      if (reducedMotion.matches) continue
      const cards = [...entry.target.querySelectorAll('.quick-service, .home-card')]
      const elements = cards.length && !entry.target.querySelector('[aria-busy="true"]') ? cards : [entry.target]
      elements.forEach((element, index) => {
        const animation = element.animate(
          [{ opacity: .72, translate: '0 8px' }, { opacity: 1, translate: '0 0' }],
          { duration: 180, delay: index * 50, easing: 'ease-out', fill: 'backwards' }
        )
        revealAnimations.add(animation)
        animation.onfinish = () => revealAnimations.delete(animation)
      })
    }
  }, { threshold: .08 })
  portal.value.querySelectorAll('.quick-section, .home-section').forEach(section => {
    if (section.getBoundingClientRect().top >= window.innerHeight) revealObserver.observe(section)
  })
}

onBeforeUnmount(() => {
  revealObserver?.disconnect()
  reducedMotion.removeEventListener('change', cancelReveals)
  cancelReveals()
})
const prompts = ['推荐本周活动', '查找学习搭子']
const question = ref('')
const questionInput = ref(null)
let activityRequest = 0

const helloLine = computed(() => {
  const hour = new Date().getHours()
  const greeting = hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好'
  return `${greeting}，${userStore.userInfo?.nickname || '同学'}。今天也有新发现。`
})

function shortDate(value) {
  return value ? formatTime(value).slice(5, 16) || '时间待定' : '时间待定'
}

async function loadAggregate() {
  aggregateLoading.value = true
  aggregateError.value = false
  try {
    const data = await homeAggregate()
    idleItems.value = (data.idleItems || []).slice(0, 3)
    lostItems.value = (data.lostFounds || []).slice(0, 2)
  } catch {
    aggregateError.value = true
  } finally {
    aggregateLoading.value = false
  }
}

async function loadActivities() {
  const requestId = ++activityRequest
  loadingActivities.value = true
  activityError.value = false
  try {
    const categories = activeFilter.value.categories
    const pages = await Promise.all((categories.length ? categories : [undefined])
      .map(category => listActivity({ category, pageNum: 1, pageSize: 3 })))
    if (requestId !== activityRequest) return
    activities.value = pages.flatMap(page => page.list || [])
      .sort((left, right) => Number(right.id) - Number(left.id)).slice(0, 3)
    if (userStore.isLoggedIn) {
      for (const item of activities.value) refreshFavorite(item.id)
    }
  } catch {
    if (requestId === activityRequest) activityError.value = true
  } finally {
    if (requestId === activityRequest) loadingActivities.value = false
  }
}

async function refreshFavorite(id) {
  if (saving.has(id)) return
  saving.add(id)
  try {
    saved[id] = await favoriteStatus('activity', id)
  } catch {
    delete saved[id]
  } finally {
    saving.delete(id)
  }
}

async function toggleFavorite(item) {
  if (!userStore.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: '/' } })
    return
  }
  if (saving.has(item.id)) return
  saving.add(item.id)
  favoriteFeedback.delete(item.id)
  try {
    const current = saved[item.id] ?? await favoriteStatus('activity', item.id)
    await (current ? unfavorite : favorite)('activity', item.id)
    saved[item.id] = !current
    if (!reducedMotion.matches) favoriteFeedback.add(item.id)
  } catch {
    // 请求层显示失败原因，保持原收藏状态。
  } finally {
    saving.delete(item.id)
  }
}

function usePrompt(prompt) {
  question.value = prompt
  questionInput.value?.focus()
}

function ask() {
  if (question.value) router.push({ path: '/ai/chat', query: { q: question.value, mode: 'guide' } })
}

watch(activeFilter, loadActivities, { immediate: true })
onMounted(async () => {
  observeSections()
  reducedMotion.addEventListener('change', cancelReveals)
  loadAggregate()
  if (userStore.isLoggedIn) {
    try {
      const stats = await wrongStats()
      wrongCount.value = stats.pendingReview ?? stats.reviewing ?? stats.total ?? 0
    } catch {
      // 保留错题本入口。
    }
  }
})
</script>

<style scoped>
.quick-section { margin-top: 24px; }
.quick-heading, .section-title, .card-meta, .ai-title, .lost-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.quick-heading { margin: 0 2px 12px; }
.quick-heading h2 { font-size: 14px; }
.quick-heading span, .quick-service small { color: var(--ink-3); font-size: 11px; }
.quick-strip, .home-cards { display: grid; gap: 16px; }
.quick-strip { grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; }
.home-cards { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.quick-service, .home-card, .feature, .lost-list, .home-state {
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}
.quick-service { min-height: 116px; padding: 16px; }
.quick-service b, .quick-service small { display: block; }
.quick-service b { color: var(--ink); margin-top: 12px; font-size: 13px; }
.quick-icon, .ai-symbol {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  color: var(--brand);
  background: var(--brand-soft);
}
.quick-icon svg, .ai-symbol svg { width: 20px; height: 20px; }
.home-section { margin-top: 38px; }
.section-title { align-items: flex-end; margin-bottom: 17px; }
.section-title h2 { font-size: 24px; line-height: 1.3; letter-spacing: -.015em; }
.section-title p { margin-top: 6px; color: var(--ink-3); font-size: 12px; }
.section-title .chips { margin-top: 12px; }
.text-link { color: var(--brand); font-size: 12px; }
a.text-link:hover { text-decoration: underline; text-underline-offset: 3px; }
.chips, .feature-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.chip, .badge {
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  background: var(--surface);
  color: var(--ink-3);
  font-size: 11px;
}
.chip { min-height: 32px; padding: 4px 12px; cursor: pointer; }
.chip:hover, .chip[aria-pressed="true"] { border-color: var(--brand-line); background: var(--brand-soft); color: var(--brand); }
.home-card { position: relative; overflow: hidden; }
.card-link { display: block; }
.quick-service, .home-card { transition: transform .16s var(--ease-out), border-color .16s; }
.quick-service:focus-visible, .home-card:focus-within { border-color: var(--brand-line); }
.home-card .card-link:focus-visible { outline-offset: -3px; }
@media (hover: hover) and (pointer: fine) {
  .quick-service:hover, .home-card:hover { transform: translateY(-3px); border-color: var(--brand-line); }
}
.cover { position: relative; aspect-ratio: 16 / 9; background: var(--surface-3); }
.cover img, .feature-media img { width: 100%; height: 100%; object-fit: cover; }
.badge { position: absolute; top: 10px; left: 10px; padding: 3px 8px; color: var(--brand); }
.save-button {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--ink-2);
  cursor: pointer;
}
.save-button[aria-pressed="true"] { background: var(--brand); color: var(--brand-ink); }
.save-button svg { width: 16px; height: 16px; }
.save-feedback { animation: favorite-feedback .18s var(--ease-out); }
@keyframes favorite-feedback {
  45% { transform: scale(1.14); }
}
.card-body { padding: 15px; overflow-wrap: anywhere; }
.card-body h3 { font-size: 14px; }
.card-body p, .card-meta { color: var(--ink-3); font-size: 11px; }
.card-body p { margin-top: 7px; }
.card-meta { margin-top: 12px; }
.card-meta .text-link { flex: none; }
.home-state { padding: 24px; text-align: center; color: var(--ink-3); }
.home-state .btn, .aggregate-retry { margin-top: 10px; }
.feature {
  display: grid;
  grid-template-columns: minmax(0, 1.04fr) minmax(0, .96fr);
  min-height: 310px;
  overflow: hidden;
}
.feature.reverse { grid-template-columns: minmax(0, .96fr) minmax(0, 1.04fr); }
.feature-media { position: relative; min-height: 300px; }
.feature-media img { position: absolute; inset: 0; }
.feature-copy, .ai-panel { display: flex; flex-direction: column; justify-content: center; }
.feature-copy { align-items: flex-start; padding: 38px; }
.feature-copy h2 { font-size: 28px; line-height: 1.35; letter-spacing: -.02em; }
.feature-copy p { margin: 14px 0 22px; font-size: 13px; line-height: 1.9; color: var(--ink-3); }
.feature-actions { gap: 16px; }
.review-link { margin-bottom: 16px; color: var(--ink-2); font-size: 12px; }
.review-link b { display: block; color: var(--ink); font-size: 20px; }
.lost-list { padding: 4px 20px; }
.lost-row { justify-content: flex-start; padding: 15px 0; }
.lost-row + .lost-row { border-top: 1px solid var(--line); }
.lost-row img { flex: none; width: 64px; height: 64px; object-fit: cover; border-radius: var(--r-sm); }
.lost-row div { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.lost-row small { display: block; color: var(--ink-3); }
.lost-row .btn { flex: none; min-height: 34px; font-size: 12px; padding: 0 12px; }
.ai-panel { padding: 30px; }
.ai-title { justify-content: flex-start; }
.ai-title h2 { font-size: 18px; }
.ai-title small { color: var(--ink-3); font-size: 11px; }
.ai-title .text-link { margin-left: auto; }
.ai-symbol { width: 38px; height: 38px; background: var(--brand); color: var(--brand-ink); }
.chat-preview { margin: 18px 0 12px; }
.chat-preview p {
  width: fit-content;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 4px 10px 10px 10px;
  background: var(--surface-2);
  color: var(--ink-2);
  font-size: 12px;
}
.ai-form { display: flex; gap: 8px; margin-top: 12px; }
.ai-form input {
  min-width: 0;
  flex: 1;
  height: 42px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--ink);
}
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--ink);
  cursor: pointer;
}
.btn:hover { border-color: var(--brand-line); }
.btn.primary { background: var(--brand); border-color: var(--brand); color: var(--brand-ink); }
.btn.primary:hover { background: var(--brand-strong); }
.btn:disabled { opacity: .55; cursor: not-allowed; }

@media (max-width: 1120px) {
  .quick-strip { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .feature-copy { padding: 30px; }
}
@media (max-width: 900px) {
  .home-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .feature, .feature.reverse { grid-template-columns: minmax(0, 1fr); }
  .feature.reverse .feature-media { order: -1; }
  .feature-media { min-height: 280px; }
}
@media (max-width: 600px) {
  .quick-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .quick-service { min-height: 106px; padding: 14px; }
  .home-section { margin-top: 30px; }
  .section-title { align-items: flex-start; flex-direction: column; gap: 10px; }
  .section-title h2 { font-size: 22px; }
  .home-cards { grid-template-columns: minmax(0, 1fr); }
  .feature-copy, .ai-panel { padding: 22px; }
  .feature-copy h2 { font-size: 24px; }
  .feature-media { min-height: 220px; }
  .lost-list { padding-inline: 12px; }
  .lost-row img { width: 44px; height: 44px; }
}
@media (prefers-reduced-motion: reduce) {
  .quick-service, .home-card { transition: none; }
  .save-feedback { animation: none; }
  .quick-service:hover, .home-card:hover { transform: none; }
}
</style>
