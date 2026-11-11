<template>
  <div class="search-page">
    <WtPageHeader title="校园搜索" subtitle="搜索活动、闲置、失物和校园动态。" />
    <form class="search-form" role="search" aria-label="在结果页搜索" @submit.prevent="submit">
      <el-input v-model="query" aria-label="校园搜索关键词" placeholder="输入关键词，例如读书会" clearable>
        <template #append><el-button native-type="submit">搜索</el-button></template>
      </el-input>
    </form>
    <EmptyBox v-if="!keyword" description="输入关键词开始搜索" />
    <div v-else>
    <div class="chips" role="group" aria-label="结果分类">
      <button type="button" class="chip" :aria-pressed="activeType === ''" @click="activeType = ''">全部 {{ incomplete ? '' : totalCount }}</button>
      <button
        v-for="sec in sections"
        :key="sec.type"
        type="button" class="chip" :aria-pressed="activeType === sec.type"
        @click="activeType = sec.type"
      >{{ sec.label }} {{ sec.error || sec.loading ? '' : sec.total }}</button>
    </div>

    <!-- 结果列表（单列，关键词高亮） -->
    <div class="result-sections">
      <section v-for="section in visibleSections" :key="section.type" class="result-section" :aria-label="section.label">
        <header>
          <div>
            <h2>{{ section.label }}</h2>
            <span v-if="!section.loading && !section.error">显示 {{ section.items.length }} / {{ section.total }} 条</span>
          </div>
          <RouterLink v-if="!section.loading && !section.error && section.total" class="text-btn"
            :to="{ path: section.path, query: { q: keyword } }">查看全部</RouterLink>
        </header>
        <p v-if="section.error" class="section-state" role="alert">
          此分类查询失败。<button type="button" class="text-btn" @click="loadSection(section)">重试</button>
        </p>
        <p v-else-if="section.loading" class="section-state" role="status">正在查询…</p>
        <p v-else-if="!section.total" class="section-state">没有找到相关内容。</p>
        <div v-else class="result-list">
          <RouterLink v-for="item in section.items" :key="item.key" :to="item.to">
            <strong v-html="highlight(item.title)"></strong>
            <span v-html="highlight(item.meta)"></span>
          </RouterLink>
        </div>
      </section>
    </div>
    <EmptyBox v-if="!visibleSections.length" description="没有找到相关内容" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import EmptyBox from '../../components/EmptyBox.vue'
import { listActivity } from '../../api/activity'
import { listIdle } from '../../api/idle'
import { listLostFound } from '../../api/lostfound'
import { listPost } from '../../api/post'

const route = useRoute()
const router = useRouter()
const keyword = computed(() => String(route.query.q || '').trim())
const query = ref(keyword.value)
const sections = ref([])
const activeType = ref('')
const sources = [
  { type: 'activity', label: '校园活动', path: '/activity', request: listActivity,
    map: item => ({ key: item.id, title: item.title, meta: item.location || '地点待定', to: `/activity/detail/${item.id}` }) },
  { type: 'idle', label: '闲置物品', path: '/idle', request: listIdle,
    map: item => ({ key: item.id, title: item.title, meta: item.expectItem ? `期望换：${item.expectItem}` : '闲置互换', to: `/idle/detail/${item.id}` }) },
  { type: 'lost', label: '失物招领', path: '/lostfound', request: listLostFound,
    map: item => ({ key: item.id, title: item.title, meta: item.location || '地点未知', to: `/lostfound/detail/${item.id}` }) },
  { type: 'post', label: '校园动态', path: '/social', request: listPost,
    map: item => ({ key: item.id, title: item.content || '校园动态', meta: item.authorName || item.nickname || '校园同学', to: `/social?post=${item.id}` }) }
]

/** 当前展示的 section（按 chip 过滤） */
const visibleSections = computed(() =>
  sections.value.filter(section => activeType.value ? section.type === activeType.value : section.total || section.error || section.loading)
)

const totalCount = computed(() => sections.value.reduce((sum, section) => sum + section.total, 0))
const incomplete = computed(() => sections.value.some(section => section.loading || section.error))

/** 关键词高亮：先转义 HTML，再包 <mark> */
function escHtml(str) {
  return String(str ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}
function highlight(text) {
  const esc = escHtml(text)
  if (!keyword.value) return esc
  const kw = escHtml(keyword.value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return esc.replace(new RegExp(kw, 'gi'), (m) => `<mark>${m}</mark>`)
}

function rows(res) {
  return Array.isArray(res) ? res : (res?.list || [])
}

function submit() {
  const q = query.value.trim()
  router.push({ path: '/search', query: q ? { q } : {} })
}

async function loadSection(section) {
  section.loading = true
  section.error = false
  try {
    const result = await section.request({ keyword: section.keyword, pageNum: 1, pageSize: 4 })
    section.items = rows(result).map(section.map)
    section.total = Number(result?.total ?? section.items.length)
  } catch {
    section.error = true
  } finally {
    section.loading = false
  }
}

watch(keyword, value => {
  query.value = value
  activeType.value = ''
  sections.value = value ? sources.map(source => ({ ...source, keyword: value, items: [], total: 0, error: false, loading: true })) : []
  sections.value.forEach(loadSection)
}, { immediate: true })
</script>

<style scoped>
.search-page { max-width: 960px; min-width: 0; margin-inline: auto; }
.search-form { max-width: 620px; margin-bottom: 15px; }
.search-form :deep(.el-input__wrapper), .search-form :deep(.el-input-group__append) { min-height: 38px; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 15px; }
.chip { padding: 5px 10px; border: 1px solid var(--line); border-radius: 999px; background: var(--surface); color: var(--ink-2); font: inherit; font-size: 12px; cursor: pointer; }
.chip:hover, .chip[aria-pressed='true'] { border-color: var(--brand-line); }
.chip[aria-pressed='true'] { background: var(--brand-soft); color: var(--brand-strong); font-weight: 650; }
.result-sections { display: grid; gap: 12px; }
.result-section { min-width: 0; border: 1px solid var(--line); border-radius: 12px; background: var(--surface); }
.result-section header { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 6px 10px; padding: 12px 14px 8px; }
.result-section header div { display: flex; align-items: baseline; gap: 8px; }
.result-section h2 { margin: 0; color: var(--ink); font-size: 15px; }
.result-section header span { color: var(--ink-3); font-size: 12px; }
.section-state { margin: 0; padding: 0 14px 12px; color: var(--ink-2); font-size: 13px; }
.result-list > a { display: grid; gap: 3px; min-width: 0; padding: 9px 14px; border-top: 1px solid var(--line); text-decoration: none; overflow-wrap: anywhere; }
.result-list > a:hover { background: var(--surface-2); }
.result-list strong { color: var(--brand-strong); font-size: 14px; }
.result-list span { color: var(--ink-2); font-size: 12px; }
.result-list :deep(mark) { border-radius: 3px; padding-inline: 1px; color: inherit; background: var(--warning-soft); }
</style>
