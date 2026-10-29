<template>
  <WtPageHeader title="我的提问" subtitle="管理你发起的互助问题" eyebrow="校园服务" />

  <div class="qa-list">
    <div class="q-list" v-loading="loading">
      <div v-for="q in list" :key="q.id" class="q-card" @click="$router.push(`/qa/detail/${q.id}`)">
        <div class="q-card__top">
          <span class="cat">{{ q.category }}</span>
          <b class="title">{{ q.title }}</b>
          <span v-if="q.status === 1" class="solved">已解决</span>
          <span v-else class="pending">待答</span>
        </div>
        <div class="meta">
          <span>💬 {{ q.answerCount }} 回答</span>
          <span>👁 {{ q.viewCount }} 浏览</span>
          <span>🕐 {{ String(q.createTime).slice(0, 16) }}</span>
        </div>
      </div>
    </div>
    <EmptyBox v-if="!loading && !list.length" description="还没有提问，去问第一个吧" />
    <el-pagination
      v-model:current-page="pageNum"
      :total="total"
      :page-size="12"
      layout="prev, pager, next"
      @current-change="load"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import EmptyBox from '../../components/EmptyBox.vue'
import { myQuestion } from '../../api/qa'

const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await myQuestion({ pageNum: pageNum.value, pageSize: 12 })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

load()
</script>

<style scoped>
.qa-list { max-width: 860px; margin: 0 auto; }
.q-list { display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }
.q-card {
  padding: 14px 16px; border: 1px solid var(--line); border-radius: var(--r-md);
  background: var(--surface); cursor: pointer; transition: all .18s var(--ease-out);
}
.q-card:hover { border-color: var(--brand-line); }
.q-card__top { display: flex; align-items: center; gap: 10px; }
.cat { padding: 2px 10px; border-radius: var(--r-pill); background: var(--brand-soft); color: var(--brand-strong); font-size: var(--fs-cap); font-weight: 600; flex-shrink: 0; }
.title { flex: 1; font-size: var(--fs-sm); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.solved { padding: 2px 10px; border-radius: var(--r-pill); background: var(--success-soft, #f0faf5); color: var(--success-strong, #008a5c); font-size: var(--fs-cap); font-weight: 600; }
.pending { padding: 2px 10px; border-radius: var(--r-pill); background: var(--warning-soft, #fff7e6); color: var(--gold-strong, #a06a00); font-size: var(--fs-cap); font-weight: 600; }
.meta { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 8px; font-size: var(--fs-cap); color: var(--ink-3); }
</style>
