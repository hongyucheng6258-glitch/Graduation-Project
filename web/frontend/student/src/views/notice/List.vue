<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <div class="notice-list">
    <WtPageHeader title="校园公告" subtitle="查看校园通知与活动公示。" />
    <div v-loading="loading" class="list-page">
      <RouterLink
        v-for="n in list"
        :key="n.id"
        class="notice-card"
        :to="`/notice/detail/${n.id}`"
      >
        <time v-if="n.publishTime" class="n-date" :datetime="n.publishTime">
          <strong>{{ formatDate(n.publishTime).slice(8, 10) }}</strong>
          <span>{{ Number(formatDate(n.publishTime).slice(5, 7)) }} 月</span>
        </time>
        <div class="notice-main">
          <h2 class="n-title">{{ n.title }}</h2>
          <div v-if="n.publishTime" class="n-meta">发布于 {{ formatDate(n.publishTime) }}</div>
        </div>
        <span class="n-view" aria-hidden="true">→</span>
      </RouterLink>
      <EmptyBox v-if="!loadError && !loading && !list.length" description="暂无公告" />
      <el-pagination
        v-model:current-page="pageNum"
        :total="total"
        :page-size="10"
        layout="prev, pager, next"
        style="margin-top: 16px"
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { useListState } from '../../utils/list-state'
import { onMounted, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { listNotice } from '../../api/notice'
import { formatDate } from '../../utils/date'
import EmptyBox from '../../components/EmptyBox.vue'

const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
useListState('notice', { pageNum })

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listNotice({ pageNum: pageNum.value, pageSize: 10 })
    list.value = res.list
    total.value = res.total
  } catch (error) {
    loadError.value = error.message || '内容加载失败，请重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.notice-list {
  min-width: 0;
  max-width: 820px;
  margin-inline: auto;
}
.list-page {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.notice-card {
  display: flex;
  gap: 11px;
  align-items: center;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  color: inherit;
  text-decoration: none;
}
.notice-card:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }
.notice-card:hover { border-color: var(--brand-line); }
.n-date { flex: none; display: grid; place-items: center; width: 43px; min-height: 46px; padding: 3px; border-radius: 8px; color: var(--brand-strong); background: var(--brand-soft); font-size: 11px; line-height: 1.2; }
.n-date strong { font-size: 18px; font-variant-numeric: tabular-nums; }
.notice-main {
  flex: 1;
  min-width: 0;
}
.n-title {
  font-weight: 600;
  font-size: var(--fs-body);
  margin: 0 0 4px;
  overflow-wrap: anywhere;
  color: var(--ink);
}
.n-meta {
  font-size: var(--fs-cap);
  color: var(--ink-3);
}
.n-view {
  font-size: var(--fs-sm);
  color: var(--brand-strong);
  flex: none;
}
@media (max-width: 640px) {
  .notice-card { padding: 14px; gap: 10px; }
}
</style>
