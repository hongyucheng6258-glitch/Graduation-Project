<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <WtPageHeader title="校园公告" subtitle="学校与平台的重要通知" eyebrow="资讯" />

  <div class="notice-list">
    <div v-loading="loading" class="list-page">
      <div
        v-for="n in list"
        :key="n.id"
        class="notice-card card card-hover"
        @click="$router.push(`/notice/detail/${n.id}`)"
      >
        <span class="tag tag-brand">公告</span>
        <div class="notice-main">
          <div class="n-title">{{ n.title }}</div>
          <div class="n-meta">校园平台 · {{ formatTime(n.publishTime) }}</div>
        </div>
        <span class="n-arrow">→</span>
      </div>
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
import { formatTime } from '../../utils/date'
import EmptyBox from '../../components/EmptyBox.vue'

const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const restoredListState = useListState('notice', { pageNum })

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
}
.list-page {
  display: flex;
  flex-direction: column;
  gap: var(--s-3);
}
.notice-card {
  display: flex;
  gap: var(--s-4);
  align-items: center;
  cursor: pointer;
}
.notice-card .tag {
  flex: none;
}
.notice-main {
  flex: 1;
  min-width: 0;
}
.n-title {
  font-weight: 600;
  font-size: var(--fs-sm);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ink);
}
.n-meta {
  font-size: var(--fs-cap);
  color: var(--ink-3);
}
.n-arrow {
  font-size: var(--fs-cap);
  color: var(--ink-3);
  flex: none;
}
.tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: var(--r-pill);
  font-size: var(--fs-cap);
  font-weight: 600;
  letter-spacing: 0.02em;
}
.tag-brand { background: var(--brand-soft); color: var(--brand-strong); }
</style>
