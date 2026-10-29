<template>
  <WtPageHeader title="搜索结果" :subtitle="`关键词：${keyword || '未输入'}`" eyebrow="校园搜索" />

  <div v-if="!keyword" class="search-empty">请在顶部输入关键词开始搜索</div>
  <div v-else v-loading="loading">
    <!-- 分类计数 pill（对齐原型 filter-chip） -->
    <div class="chips">
      <span class="chip" :class="{ active: activeType === '' }" @click="activeType = ''">全部 {{ totalCount }}</span>
      <span
        v-for="sec in sections"
        :key="sec.type"
        class="chip"
        :class="{ active: activeType === sec.type }"
        @click="activeType = sec.type"
      >{{ sec.label }} {{ sec.items.length }}</span>
    </div>

    <!-- 结果列表（单列，关键词高亮） -->
    <div class="result-sections">
      <section v-for="section in visibleSections" :key="section.type" class="result-section">
        <header>
          <div>
            <h2>{{ section.label }}</h2>
            <span>{{ section.items.length ? `找到 ${section.items.length} 条相关内容` : '暂无相关内容' }}</span>
          </div>
          <button type="button" @click="openSection(section)">查看全部</button>
        </header>
        <div v-if="section.items.length" class="result-list">
          <button v-for="item in section.items" :key="item.key" type="button" @click="router.push(item.to)">
            <strong v-html="highlight(item.title)"></strong>
            <span v-html="highlight(item.meta)"></span>
          </button>
        </div>
      </section>
    </div>
    <EmptyBox v-if="!loading && !totalCount" description="没有找到相关内容" />
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
const loading = ref(false)
const sections = ref([])
const activeType = ref('')

/** 当前展示的 section（按 chip 过滤） */
const visibleSections = computed(() =>
  activeType.value ? sections.value.filter((s) => s.type === activeType.value) : sections.value
)

const totalCount = computed(() => sections.value.reduce((sum, s) => sum + s.items.length, 0))

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

function openSection(section) {
  router.push({ path: section.path, query: { q: keyword.value } })
}

async function load() {
  if (!keyword.value) {
    sections.value = []
    return
  }
  loading.value = true
  try {
    const q = keyword.value
    const [activities, idleItems, lostItems, posts] = await Promise.all([
      listActivity({ keyword: q, pageNum: 1, pageSize: 4 }),
      listIdle({ keyword: q, pageNum: 1, pageSize: 4 }),
      listLostFound({ keyword: q, pageNum: 1, pageSize: 4 }),
      listPost({ keyword: q, pageNum: 1, pageSize: 4 })
    ])
    sections.value = [
      { type: 'activity', label: '校园活动', path: '/activity', items: rows(activities).map((item) => ({ key: item.id, title: item.title, meta: item.location || '地点待定', to: `/activity/detail/${item.id}` })) },
      { type: 'idle', label: '闲置物品', path: '/idle', items: rows(idleItems).map((item) => ({ key: item.id, title: item.title, meta: item.price != null ? `¥${item.price}` : '面议', to: `/idle/detail/${item.id}` })) },
      { type: 'lost', label: '失物招领', path: '/lostfound', items: rows(lostItems).map((item) => ({ key: item.id, title: item.title, meta: item.location || '地点未知', to: `/lostfound/detail/${item.id}` })) },
      { type: 'post', label: '校园动态', path: '/social', items: rows(posts).map((item) => ({ key: item.id, title: item.content || '校园动态', meta: item.authorName || item.nickname || '校园同学', to: `/social?post=${item.id}` })) }
    ]
  } finally {
    loading.value = false
  }
}

watch(keyword, load, { immediate: true })
</script>

<style scoped>
.chips {
  display: flex;
  gap: var(--s-2);
  flex-wrap: wrap;
  margin-bottom: var(--s-5);
}
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
.chip:hover {
  border-color: var(--brand-line);
}
.chip.active {
  background: var(--brand-soft);
  color: var(--brand-strong);
  border-color: var(--brand-line);
}
.result-sections {
  display: flex;
  flex-direction: column;
  gap: var(--s-4);
}
.result-section {
  padding: var(--s-5);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: var(--surface);
}
.result-section header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s-4);
  margin-bottom: var(--s-3);
}
.result-section h2 {
  margin: 0 0 3px;
  color: var(--ink);
  font-size: var(--fs-lg);
}
.result-section header span {
  color: var(--ink-3);
  font-size: var(--fs-xs);
}
.result-section header button {
  border: none;
  background: none;
  color: var(--brand-strong);
  font-size: var(--fs-xs);
  font-weight: 600;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--r-sm);
}
.result-section header button:hover {
  background: var(--brand-soft);
}
.result-list {
  display: flex;
  flex-direction: column;
}
.result-list button {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 10px;
  border: none;
  border-bottom: 1px dashed var(--line);
  background: none;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  border-radius: var(--r-sm);
}
.result-list button:last-child {
  border-bottom: none;
}
.result-list button:hover {
  background: var(--surface-2);
}
.result-list strong {
  font-size: var(--fs-sm);
  color: var(--ink);
  font-weight: 600;
}
.result-list span {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}
.result-list mark {
  background: var(--warning-soft);
  color: var(--gold-strong);
  padding: 0 2px;
  border-radius: 3px;
}
.search-empty {
  padding: var(--s-8);
  text-align: center;
  color: var(--ink-3);
  font-size: var(--fs-sm);
}<style scoped>
.result-sections { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--s-5); }
.result-section { padding: var(--s-5); border: 1px solid var(--line); border-radius: var(--r-lg); background: var(--surface); }
.result-section header { display: flex; align-items: center; justify-content: space-between; gap: var(--s-4); margin-bottom: var(--s-4); }
.result-section h2 { margin: 0 0 3px; color: var(--ink); font-size: var(--fs-lg); }
.result-section header span { color: var(--ink-3); font-size: var(--fs-xs); }
.result-section header button { border: 0; background: transparent; color: var(--brand-strong); font-weight: 600; cursor: pointer; }
.result-list { display: grid; }
.result-list button { display: flex; align-items: center; justify-content: space-between; gap: var(--s-4); min-width: 0; padding: 12px 0; border: 0; border-bottom: 1px solid var(--line); background: transparent; text-align: left; cursor: pointer; }
.result-list button:last-child { border-bottom: 0; }
.result-list button:hover strong { color: var(--brand-strong); }
.result-list strong { overflow: hidden; color: var(--ink); font-size: var(--fs-sm); text-overflow: ellipsis; white-space: nowrap; }
.result-list span { flex: none; color: var(--ink-3); font-size: var(--fs-xs); }
.search-empty { padding: 64px; color: var(--ink-3); text-align: center; }
@media (max-width: 820px) { .result-sections { grid-template-columns: 1fr; } }
</style>
