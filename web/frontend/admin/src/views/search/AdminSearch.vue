<template>
  <div class="search-page">
    <WtPageHeader title="全局搜索" subtitle="查找学生账号、校园内容或举报工单。" />
    <form class="search-form" role="search" aria-label="搜索管理数据" @submit.prevent="submit">
      <input id="admin-search-keyword" ref="searchInput" v-model="input" type="search" maxlength="80"
        aria-label="搜索关键词" placeholder="输入学号、昵称、内容关键词或工单 ID" />
      <button class="btn btn-primary" type="submit">搜索</button>
    </form>

    <nav class="search-scopes" aria-label="搜索范围">
      <RouterLink v-for="item in scopes" :key="item.key" :to="searchRoute(keyword, item.key)"
        :aria-current="scope === item.key ? 'true' : undefined">{{ item.label }}</RouterLink>
    </nav>

    <p v-if="!keyword" class="search-state">输入关键词开始搜索。举报工单支持输入编号或 #编号。</p>
    <p v-else-if="scope === 'report' && !ticketId" class="search-state">请输入有效的举报工单编号，例如 #123。</p>
    <template v-else>
      <p class="result-status" role="status">
        {{ searching ? '正在查询…' : `“${keyword}” · 找到 ${total} 条结果${hasError ? '，部分查询失败' : ''}` }}
      </p>
      <p v-if="!searching && !total && !hasError" class="search-state">没有找到匹配的结果，试试其他关键词。</p>

      <section v-for="group in displayedGroups" :key="group.key" class="result-group" :aria-label="group.label">
        <header>
          <h2>{{ group.label }}</h2>
          <span v-if="group.total">{{ group.total }} 条</span>
        </header>
        <p v-if="group.error" class="group-state" role="alert">
          此分类查询失败 <button type="button" class="text-btn" @click="load(group, group.page)">重试</button>
        </p>
        <p v-else-if="group.loading" class="group-state">正在查询 {{ group.label }}…</p>
        <ul v-else>
          <li v-for="row in group.rows" :key="row.id">
            <RouterLink :id="resultId(group, row)" :to="resultRoute(group, row)" class="result-row">
              <div>
                <strong>{{ rowTitle(group, row) }}</strong>
                <small v-if="rowSummary(group, row)">{{ rowSummary(group, row) }}</small>
              </div>
              <span class="result-arrow" aria-hidden="true">→</span>
            </RouterLink>
          </li>
        </ul>
        <div v-if="group.total > pageSize" class="result-pager">
          <button type="button" :disabled="group.loading || group.page <= 1" @click="goPage(group, group.page - 1)">上一页</button>
          <span>第 {{ group.page }} 页</span>
          <button type="button" :disabled="group.loading || group.page * pageSize >= group.total" @click="goPage(group, group.page + 1)">下一页</button>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { listUsers } from '../../api/user'
import { auditAll } from '../../api/audit'
import { reportList } from '../../api/report'
import { searchKeyword, searchRecordId } from '../../utils/searchQuery.mjs'

const route = useRoute()
const router = useRouter()
const input = ref('')
const searchInput = ref()
const groups = ref([])
const pageSize = 5
const scopes = [
  { key: 'all', label: '全部' }, { key: 'user', label: '用户' },
  { key: 'content', label: '校园内容' }, { key: 'report', label: '举报工单' }
]
const sources = [
  { key: 'user', scope: 'user', label: '用户', route: 'UserList' },
  { key: 'activity', scope: 'content', label: '活动', route: 'ActivityAudit' },
  { key: 'idle', scope: 'content', label: '闲置', route: 'IdleAudit' },
  { key: 'lostfound', scope: 'content', label: '失物招领', route: 'LostFoundAudit' },
  { key: 'partner', scope: 'content', label: '学习搭子', route: 'PartnerAudit' },
  { key: 'post', scope: 'content', label: '校园动态', route: 'PostAudit' },
  { key: 'qa', scope: 'content', label: '互助问答', route: 'ContentManage' },
  { key: 'report', scope: 'report', label: '举报工单', route: 'ReportList' }
]

const keyword = computed(() => searchKeyword(route.query.q))
const scope = computed(() => scopes.some(item => item.key === route.query.scope) ? route.query.scope : 'all')
const ticketId = computed(() => searchRecordId(keyword.value))
const searching = computed(() => groups.value.some(group => group.loading))
const total = computed(() => groups.value.reduce((sum, group) => sum + group.total, 0))
const hasError = computed(() => groups.value.some(group => group.error))
const displayedGroups = computed(() => groups.value.filter(group => group.total || group.error))

function searchRoute(q, selectedScope) {
  return { name: 'AdminSearch', query: { ...(q && { q }), ...(selectedScope !== 'all' && { scope: selectedScope }) } }
}

function submit() {
  const q = searchKeyword(input.value)
  input.value = q
  router.push(searchRoute(q, scope.value))
}

function readPage(key) {
  const entries = typeof route.query.pages === 'string' ? route.query.pages.split(',') : []
  const value = entries.find(entry => entry.startsWith(key + ':'))?.split(':')[1]
  return /^\d{1,8}$/.test(value || '') ? Math.max(1, Number(value)) : 1
}

function goPage(target, page) {
  const pages = groups.value.map(group => [group.key, group === target ? page : group.page])
    .filter(([, current]) => current > 1).map(([key, current]) => `${key}:${current}`).join(',')
  const destination = searchRoute(keyword.value, scope.value)
  if (pages) destination.query.pages = pages
  router.replace(destination)
}

async function load(group, page) {
  const request = ++group.request
  group.loading = true
  group.error = false
  group.page = page
  const pagination = { pageNum: page, pageSize }
  try {
    const result = await (group.key === 'user' ? listUsers({ ...pagination, keyword: group.term })
      : group.key === 'report' ? reportList({ ...pagination, record: group.term })
      : auditAll({ ...pagination, type: group.key, keyword: group.term }))
    if (request !== group.request || route.name !== 'AdminSearch' || !groups.value.includes(group)) return
    if (!result.list.length && page > 1) {
      goPage(group, Math.max(1, Math.ceil(result.total / pageSize)))
    }
    group.rows = result.list
    group.total = result.total
  } catch {
    if (request === group.request) group.error = true
  } finally {
    if (request === group.request) group.loading = false
  }
}

function rowTitle(group, row) {
  if (group.key === 'user') return row.nickname || row.studentNo || `用户 #${row.id}`
  if (group.key === 'report') return `工单 #${row.id} · ${row.reasonType || '举报'}`
  return row.title || row.subject || row.content || `记录 #${row.id}`
}

function rowSummary(group, row) {
  if (group.key === 'user') return [row.studentNo, row.status === 0 ? '正常' : '已禁用'].filter(Boolean).join(' · ')
  if (group.key === 'report') return [row.status === 0 ? '待处理' : '已处理', row.reason].filter(Boolean).join(' · ')
  return row.description || row.goal || (row.title ? row.content : '')
}

function resultId(group, row) {
  return `result-${group.key}-${row.id}`
}

function resultRoute(group, row) {
  const record = String(row.id)
  const origin = { record, searchQ: keyword.value, searchScope: scope.value, searchPages: route.query.pages, searchTarget: resultId(group, row) }
  if (group.key === 'qa' || (group.scope === 'content' && group.key !== 'post' && row.auditStatus === 1)) {
    return { name: 'ContentManage', query: { ...origin, type: group.key } }
  }
  if (group.scope === 'content') return { name: group.route, query: { ...origin, view: row.auditStatus === 0 ? 'pending' : 'all' } }
  return { name: group.route, query: origin }
}

watch([keyword, scope], ([q, selectedScope]) => {
  input.value = q
  groups.value = q ? sources
    .filter(source => (selectedScope === 'all' || selectedScope === source.scope) && (source.key !== 'report' || ticketId.value))
    .map(source => ({ ...source, term: source.key === 'report' ? ticketId.value : q, rows: [], total: 0, page: 1, request: 0, loading: false, error: false })) : []
  groups.value.forEach(group => load(group, readPage(group.key)))
}, { immediate: true })

watch(() => route.query.pages, () => {
  if (route.name !== 'AdminSearch') return
  for (const group of groups.value) {
    const page = readPage(group.key)
    if (group.page !== page) load(group, page)
  }
})

watch([() => route.hash, searching], ([hash, pending]) => {
  if (pending || route.name !== 'AdminSearch' || !hash.startsWith('#result-')) return
  const target = document.getElementById(hash.slice(1))
  if (target) {
    target.focus({ preventScroll: true })
    target.scrollIntoView({ block: 'center' })
  }
}, { flush: 'post' })

onMounted(() => {
  if (!keyword.value) searchInput.value?.focus()
})
</script>

<style scoped>
.search-page { max-width: 920px; margin-inline: auto; }
.search-form { display: flex; gap: 8px; margin-bottom: 14px; }
.search-form input {
  flex: 1; min-width: 0; height: 40px; padding: 0 12px; border: 1px solid var(--line);
  border-radius: var(--r-sm); background: var(--surface); color: var(--ink); font: inherit;
}
.search-form input:focus-visible { outline: 2px solid var(--brand); outline-offset: 1px; }
.search-form button { flex: none; }
.search-scopes { display: flex; flex-wrap: wrap; gap: 6px; }
.search-scopes a {
  padding: 5px 10px; border: 1px solid var(--line); border-radius: 99px;
  background: var(--surface); color: var(--ink-2); font-size: var(--fs-sm); text-decoration: none;
}
.search-scopes a:hover, .search-scopes a[aria-current] {
  border-color: var(--brand-line); background: var(--brand-soft); color: var(--brand-strong);
}
.search-state, .result-status, .group-state { color: var(--ink-3); font-size: var(--fs-sm); overflow-wrap: anywhere; }
.search-state { padding: 22px 0; }
.result-status { margin: 15px 0 9px; }
.result-group { margin-bottom: 12px; border: 1px solid var(--line); border-radius: var(--r-md); background: var(--surface); }
.result-group header { display: flex; align-items: baseline; gap: 8px; padding: 10px 14px; }
.result-group h2 { margin: 0; font-size: 15px; }
.result-group header span, .result-pager { color: var(--ink-3); font-size: var(--fs-xs); }
.result-group ul { margin: 0; padding: 0; list-style: none; }
.result-row {
  display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 14px;
  border-top: 1px solid var(--line); color: var(--ink); text-decoration: none;
}
.result-row:hover, .result-row:focus-visible { background: var(--surface-2); }
.result-row:target { background: var(--brand-soft); outline: 2px solid var(--brand); outline-offset: -2px; }
.result-row div { min-width: 0; }
.result-row strong, .result-row small {
  display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; overflow-wrap: anywhere;
}
.result-row strong { font-size: var(--fs-sm); }
.result-row small { margin-top: 3px; color: var(--ink-3); }
.result-arrow { color: var(--brand-strong); }
.group-state, .result-pager { margin: 0; padding: 8px 14px; }
.result-pager { display: flex; align-items: center; justify-content: end; gap: 8px; }
.result-pager button, .group-state button {
  padding: 3px 6px; border: 0; background: transparent; color: var(--brand-strong); cursor: pointer;
}
.result-pager button:disabled { color: var(--ink-3); cursor: default; }
</style>
