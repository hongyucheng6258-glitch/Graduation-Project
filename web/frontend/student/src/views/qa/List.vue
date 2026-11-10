<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <WtPageHeader title="互助问答" subtitle="提出校园里的疑问，也帮同学解答。">
    <el-button type="primary" @click="goPublish">＋ 我要提问</el-button>
  </WtPageHeader>

  <div class="qa-list">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索问题…" clearable style="width: 260px" @keyup.enter="search" @clear="search">
        <template #append><el-button @click="search">搜索</el-button></template>
      </el-input>
      <div class="chips">
        <button type="button" class="chip" :aria-pressed="category === ''" @click="selectCategory('')">全部</button>
        <button v-for="c in categories" :key="c" type="button" class="chip" :aria-pressed="category === c" @click="selectCategory(c)">{{ c }}</button>
      </div>
      <div class="spacer" />
      <el-button @click="$router.push('/qa/my')">我的提问</el-button>
    </div>

    <div class="q-list" v-loading="loading">
      <RouterLink v-for="q in list" :key="q.id" class="q-card" :to="`/qa/detail/${q.id}`">
        <span class="answer-count"><b>{{ q.answerCount ?? 0 }}</b><small>回答</small></span>
        <div class="q-card__body">
          <div class="q-card__top">
            <span v-if="q.category" class="cat">{{ q.category }}</span>
            <span v-if="q.status === 1" class="solved">已采纳</span>
            <span v-else class="pending">待答</span>
          </div>
          <h2 class="title">{{ q.title }}</h2>
          <p v-if="q.content" class="content">{{ q.content }}</p>
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
.chip[aria-pressed='true'] { background: var(--brand-soft); color: var(--brand-strong); border-color: var(--brand-line); }
.q-list { display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }
.q-card {
  display: flex; align-items: flex-start; gap: 16px; padding: 18px 20px;
  border: 1px solid var(--line); border-radius: var(--r-md);
  background: var(--surface); color: inherit; text-decoration: none;
  transition: border-color .18s var(--ease-out);
}
.q-card:hover { border-color: var(--brand-line); }
.q-card:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }
.q-card__top { display: flex; flex-wrap: wrap; gap: 6px; }
.q-card__body { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.answer-count {
  display: grid; justify-items: center; gap: 2px; flex: none; min-width: 54px; padding: 8px 6px;
  border-radius: var(--r-sm); background: var(--brand-soft); color: var(--brand-strong);
}
.answer-count b { font-size: var(--fs-body); }
.answer-count small { font-size: var(--fs-cap); }
.author { display: inline-flex; align-items: center; gap: 6px; }
.cat {
  padding: 2px 10px; border-radius: var(--r-pill); background: var(--brand-soft);
  color: var(--brand-strong); font-size: var(--fs-cap); font-weight: 600; flex-shrink: 0;
}
.title { margin: 6px 0 0; font-size: var(--fs-body); }
.solved { padding: 2px 10px; border-radius: var(--r-pill); background: var(--success-soft, #f0faf5); color: var(--success-strong, #008a5c); font-size: var(--fs-cap); font-weight: 600; flex-shrink: 0; }
.pending { padding: 2px 10px; border-radius: var(--r-pill); background: var(--warning-soft, #fff7e6); color: var(--gold-strong, #a06a00); font-size: var(--fs-cap); font-weight: 600; flex-shrink: 0; }
.content {
  margin: 6px 0 0; font-size: var(--fs-sm); color: var(--ink-2);
  display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden;
}
.meta { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 14px; margin-top: 8px; font-size: var(--fs-cap); color: var(--ink-3); }
@media (max-width: 640px) {
  .q-card { gap: 12px; padding: 14px; }
  .answer-count { min-width: 44px; }
}
</style>
