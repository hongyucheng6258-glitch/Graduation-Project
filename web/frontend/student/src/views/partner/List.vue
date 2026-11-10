<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <WtPageHeader title="学习搭子" subtitle="按学科与目标找到一起自习的人。">
    <el-button type="primary" @click="goPublish">＋ 发布需求</el-button>
  </WtPageHeader>

  <div class="partner-list">
    <div class="toolbar">
      <div class="subject-filters" aria-label="学科快捷搜索">
        <button v-for="subject in subjects" :key="subject" type="button" class="subject-filter"
          :aria-pressed="activeKeyword === subject" @click="selectSubject(subject)">{{ subject || '全部' }}</button>
      </div>
      <el-input v-model="keyword" placeholder="搜索科目/目标/介绍…" clearable style="width: 260px" @keyup.enter="search" @clear="search">
        <template #append><el-button @click="search">搜索</el-button></template>
      </el-input>
    </div>

    <div class="grid" v-loading="loading">
      <article v-for="p in list" :key="p.id" class="p-card">
        <div class="p-card__head">
          <el-avatar :size="44" :src="p.publisherAvatar">{{ p.publisherNickname?.charAt(0) || '同' }}</el-avatar>
          <b>{{ p.publisherNickname || '校园同学' }}</b>
          <span class="subject">{{ p.subject }}</span>
        </div>
        <h2 v-if="p.goal" class="goal">{{ p.goal }}</h2>
        <p v-if="p.intro" class="intro">{{ p.intro }}</p>
        <div v-if="p.schedule || p.contact" class="meta">
          <span v-if="p.schedule">🕐 {{ p.schedule }}</span>
          <span v-if="p.contact">📞 {{ p.contact }}</span>
        </div>
        <div v-if="p.userId" class="card-actions">
          <el-button v-if="!isOwn(p)" plain type="primary" size="small" @click="invite(p)">私信邀约</el-button>
          <RouterLink class="text-btn" :to="isOwn(p) ? '/profile' : `/user/${p.userId}`">查看主页</RouterLink>
        </div>
      </article>
    </div>
    <EmptyBox v-if="!loadError && !loading && !list.length" description="暂无搭子信息，来发布第一条吧" />
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
import { listPartner } from '../../api/partner'
import { useUserStore } from '../../store/user'
import { startChat } from '../../utils/startChat'

const router = useRouter()
const userStore = useUserStore()
const subjects = ['', '计算机', '英语', '数学', '经管', '文科']
const keyword = ref('')
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
useListState('partner', { pageNum, keyword })
const activeKeyword = ref(keyword.value.trim())

function selectSubject(subject) {
  keyword.value = subject
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
    activeKeyword.value = keyword.value.trim()
    const res = await listPartner({ keyword: activeKeyword.value || undefined, pageNum: pageNum.value, pageSize: 12 })
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
  router.push('/partner/publish')
}

function isOwn(partner) {
  return Number(partner.userId) === Number(userStore.userInfo?.id)
}

async function invite(partner) {
  try {
    await startChat(router, userStore, partner.userId, { type: 'profile', id: partner.userId })
  } catch (error) {
    ElMessage.error(error.message || '暂时无法发起私信，请重试')
  }
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
.subject-filters { display: flex; flex-wrap: wrap; gap: 8px; }
.subject-filter {
  padding: 6px 14px;
  border-radius: var(--r-pill);
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink-2);
  font: inherit;
  font-size: var(--fs-xs);
  font-weight: 600;
  cursor: pointer;
}
.subject-filter:hover, .subject-filter[aria-pressed='true'] { border-color: var(--brand-line); }
.subject-filter[aria-pressed='true'] { background: var(--brand-soft); color: var(--brand-strong); }
.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; margin-bottom: 16px; }
.p-card {
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  overflow-wrap: anywhere;
}
.p-card__head { display: flex; align-items: center; gap: 12px; }
.p-card__head :deep(.el-avatar) { flex: none; background: var(--brand-soft); color: var(--brand-strong); }
.p-card__head b { min-width: 0; font-size: var(--fs-body); }
.subject {
  margin-left: auto;
  padding: 4px 10px;
  border-radius: var(--r-pill);
  background: var(--purple-soft);
  color: var(--purple-strong);
  font-size: var(--fs-xs);
  font-weight: 600;
}
.goal { margin: 14px 0 6px; color: var(--ink); font-size: var(--fs-body); }
.intro {
  margin: 8px 0 0;
  font-size: var(--fs-sm);
  color: var(--ink-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 8px; font-size: var(--fs-xs); color: var(--ink-3); }
.card-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-top: 16px; }
@media (max-width: 640px) {
  .grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
