<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <WtPageHeader title="校园互助问答" subtitle="有问题，问同学" eyebrow="校园服务" />

  <div class="qa-list">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索问题…" clearable style="width: 260px" @keyup.enter="search" @clear="search">
        <template #append><el-button @click="search">搜索</el-button></template>
      </el-input>
      <div class="chips">
        <span class="chip" :class="{ active: category === '' }" @click="selectCategory('')">全部</span>
        <span v-for="c in categories" :key="c" class="chip" :class="{ active: category === c }" @click="selectCategory(c)">{{ c }}</span>
      </div>
      <div class="spacer" />
      <el-button @click="$router.push('/qa/my')">我的提问</el-button>
      <el-button type="primary" @click="goPublish">＋ 我要提问</el-button>
    </div>

    <div class="q-list" v-loading="loading">
      <div v-for="q in list" :key="q.id" class="q-card" @click="$router.push(`/qa/detail/${q.id}`)">
        <div class="q-card__top">
          <span class="cat">{{ q.category }}</span>
          <b class="title">{{ q.title }}</b>
          <span v-if="q.status === 1" class="solved">已解决</span>
          <span v-else class="pending">待答</span>
        </div>
        <p v-if="q.content" class="content">{{ q.content }}</p>
        <div class="meta">
          <span>👤 {{ q.publisherNickname }}</span>
          <span>💬 {{ q.answerCount }} 回答</span>
          <span>👁 {{ q.viewCount }} 浏览</span>
          <span>🕐 {{ String(q.createTime).slice(0, 16) }}</span>
        </div>
      </div>
    </div>
    <EmptyBox v-if="!loadError && !loading && !list.length" description="还没有问题，来问第一个吧" />
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
import { useListState } from '../../utils/list-state'
import { ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import EmptyBox from '../../components/EmptyBox.vue'
import { listQuestion } from '../../api/qa'
import { useUserStore } from '../../store/user'

const router = useRouter()
const userStore = useUserStore()
const categories = ['课程', '考试', '技术', '生活', '其他']
const keyword = ref('')
const category = ref('')
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const restoredListState = useListState('qa', { pageNum, keyword, category })

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
    const res = await listQuestion({ keyword: keyword.value || undefined, category: category.value || undefined, pageNum: pageNum.value, pageSize: 12 })
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
  router.push('/qa/publish')
}

load()
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
}
.spacer { flex: 1; }
.chips { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.chip {
  padding: 6px 14px; border-radius: var(--r-pill); border: 1px solid var(--line);
  background: var(--surface); font-size: var(--fs-xs); font-weight: 600;
  color: var(--ink-2); cursor: pointer; transition: all .18s var(--ease-out);
}
.chip.active { background: var(--brand-soft); color: var(--brand-strong); border-color: var(--brand-line); }
.q-list { display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }
.q-card {
  padding: 14px 16px; border: 1px solid var(--line); border-radius: var(--r-md);
  background: var(--surface); cursor: pointer; transition: all .18s var(--ease-out);
}
.q-card:hover { border-color: var(--brand-line); box-shadow: 0 6px 18px rgba(16, 96, 96, 0.08); }
.q-card__top { display: flex; align-items: center; gap: 10px; }
.cat {
  padding: 2px 10px; border-radius: var(--r-pill); background: var(--brand-soft);
  color: var(--brand-strong); font-size: var(--fs-cap); font-weight: 600; flex-shrink: 0;
}
.title { flex: 1; font-size: var(--fs-sm); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.solved { padding: 2px 10px; border-radius: var(--r-pill); background: var(--success-soft, #f0faf5); color: var(--success-strong, #008a5c); font-size: var(--fs-cap); font-weight: 600; flex-shrink: 0; }
.pending { padding: 2px 10px; border-radius: var(--r-pill); background: var(--warning-soft, #fff7e6); color: var(--gold-strong, #a06a00); font-size: var(--fs-cap); font-weight: 600; flex-shrink: 0; }
.content {
  margin: 8px 0 0; font-size: var(--fs-cap); color: var(--ink-2);
  display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden;
}
.meta { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 8px; font-size: var(--fs-cap); color: var(--ink-3); }
</style>
