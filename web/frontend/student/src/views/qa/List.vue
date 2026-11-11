<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <div class="qa-list">
    <WtPageHeader title="互助问答" subtitle="浏览同学的问题和已采纳的回答。">
      <RouterLink class="btn" to="/qa/my">我的提问</RouterLink>
      <el-button type="primary" @click="goPublish">＋ 我要提问</el-button>
    </WtPageHeader>
    <div class="toolbar">
      <el-input v-model="keyword" class="collection-search" aria-label="搜索问题" placeholder="搜索问题…" clearable @keyup.enter="search" @clear="search">
        <template #append><el-button @click="search">搜索</el-button></template>
      </el-input>
      <div class="chips">
        <button type="button" class="chip" :aria-pressed="category === ''" @click="selectCategory('')">全部</button>
        <button v-for="c in categories" :key="c" type="button" class="chip" :aria-pressed="category === c" @click="selectCategory(c)">{{ c }}</button>
      </div>
    </div>

    <div class="collection-count" v-if="!loading && !loadError">{{ total }} 个问题</div>
    <div class="q-list" v-loading="loading">
      <RouterLink v-for="q in list" :key="q.id" class="q-card" :to="`/qa/detail/${q.id}`">
        <span class="answer-count" :class="{ accepted: q.status === 1 }"><b>{{ q.answerCount ?? 0 }}</b><small>回答</small></span>
        <div class="q-card__body">
          <h2 class="title">{{ q.title }}</h2>
          <p v-if="q.content" class="content">{{ q.content }}</p>
          <div class="q-card__top">
            <span v-if="q.category" class="cat">{{ q.category }}</span>
            <span v-if="q.status === 1" class="solved">已采纳</span>
            <span v-else class="pending">{{ q.answerCount ? '待采纳' : '待回答' }}</span>
          </div>
          <div class="meta">
            <span v-if="q.publisherNickname" class="author">
              <el-avatar :size="20" :src="q.publisherAvatar">{{ q.publisherNickname.charAt(0) }}</el-avatar>
              {{ q.publisherNickname }}
            </span>
            <span v-if="q.createTime">{{ fromNow(q.createTime) }}</span>
            <span v-if="q.viewCount != null">{{ q.viewCount }} 次浏览</span>
          </div>
        </div>
      </RouterLink>
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
import { fromNow } from '../../utils/date'

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
useListState('qa', { pageNum, keyword, category })

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
.qa-list { max-width: 1020px; margin-inline: auto; }
.collection-search { width: min(100%, 290px); }
.collection-count { margin: 0 0 7px; color: var(--ink-3); font-size: 12px; }
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
  padding: 11px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
}
.chips { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.chip {
  padding: 6px 14px; border-radius: var(--r-pill); border: 1px solid var(--line);
  background: var(--surface); font-size: var(--fs-xs); font-weight: 600;
  color: var(--ink-2); cursor: pointer; transition: all .18s var(--ease-out);
}
.chip[aria-pressed='true'] { background: var(--brand-soft); color: var(--brand-strong); border-color: var(--brand-line); }
.q-list { display: flex; flex-direction: column; margin-bottom: 16px; border: 1px solid var(--line); border-radius: 12px; overflow: hidden; }
.q-card {
  display: flex; align-items: flex-start; gap: 16px; padding: 18px 20px;
  background: var(--surface); color: inherit; text-decoration: none;
  transition: border-color .18s var(--ease-out);
}
.q-card + .q-card { border-top: 1px solid var(--line); }
.q-card:hover { background: var(--surface-2); }
.q-card:focus-visible { outline: 2px solid var(--brand); outline-offset: -2px; }
.q-card__top { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.q-card__body { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.answer-count {
  display: grid; justify-items: center; gap: 2px; flex: none; min-width: 54px; padding: 8px 6px;
  border-radius: var(--r-sm); background: var(--surface-2); color: var(--ink-2);
}
.answer-count.accepted { background: var(--success-soft); color: var(--success); }
.answer-count b { font-size: var(--fs-body); }
.answer-count small { font-size: var(--fs-cap); }
.author { display: inline-flex; align-items: center; gap: 6px; }
.cat {
  padding: 2px 10px; border-radius: var(--r-pill); background: var(--brand-soft);
  color: var(--brand-strong); font-size: var(--fs-cap); font-weight: 600; flex-shrink: 0;
}
.title { margin: 0; font-size: 16px; color: var(--brand-strong); }
.solved { padding: 2px 10px; border-radius: var(--r-pill); background: var(--success-soft, #f0faf5); color: var(--success-strong, #008a5c); font-size: var(--fs-cap); font-weight: 600; flex-shrink: 0; }
.pending { padding: 2px 10px; border-radius: var(--r-pill); background: var(--warning-soft, #fff7e6); color: var(--gold-strong, #a06a00); font-size: var(--fs-cap); font-weight: 600; flex-shrink: 0; }
.content {
  margin: 6px 0 0; font-size: var(--fs-sm); color: var(--ink-2);
  display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden;
}
.meta { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 14px; margin-top: 8px; font-size: var(--fs-cap); color: var(--ink-3); }
@media (max-width: 640px) {
  .collection-search { width: 100%; }
  .q-card { gap: 12px; padding: 14px; }
  .answer-count { min-width: 44px; }
}
</style>
