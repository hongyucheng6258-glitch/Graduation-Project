<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <WtPageHeader title="学习搭子" subtitle="找个人一起学，更有动力" eyebrow="校园服务" />

  <div class="partner-list">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索科目/目标/介绍…" clearable style="width: 260px" @keyup.enter="search" @clear="search">
        <template #append><el-button @click="search">搜索</el-button></template>
      </el-input>
      <div class="spacer" />
      <el-button type="primary" @click="goPublish">＋ 发布搭子信息</el-button>
    </div>

    <div class="grid" v-loading="loading">
      <div v-for="p in list" :key="p.id" class="p-card" @click="goDetail(p)">
        <div class="p-card__head">
          <b>{{ p.subject }}</b>
          <span v-if="p.goal" class="goal">{{ p.goal }}</span>
        </div>
        <p v-if="p.intro" class="intro">{{ p.intro }}</p>
        <div class="meta">
          <span v-if="p.schedule">🕐 {{ p.schedule }}</span>
          <span v-if="p.contact">📞 {{ p.contact }}</span>
          <span v-if="p.publisherNickname">👤 {{ p.publisherNickname }}</span>
        </div>
      </div>
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

const router = useRouter()
const userStore = useUserStore()
const keyword = ref('')
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const restoredListState = useListState('partner', { pageNum, keyword })

function search() {
  pageNum.value = 1
  load()
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listPartner({ keyword: keyword.value || undefined, pageNum: pageNum.value, pageSize: 12 })
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

function goDetail(p) {
  if (!p.userId) return
  if (p.isOwner) {
    router.push('/profile')
    return
  }
  router.push(`/user/${p.userId}`)
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
.spacer {
  flex: 1;
}
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.p-card {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  cursor: pointer;
  transition: all .18s var(--ease-out);
}
.p-card:hover {
  border-color: var(--brand-line);
  box-shadow: 0 6px 18px rgba(16, 96, 96, 0.08);
  transform: translateY(-2px);
}
.p-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.p-card__head b {
  font-size: var(--fs-sm);
  color: var(--brand-strong);
}
.goal {
  padding: 2px 10px;
  border-radius: var(--r-pill);
  background: var(--brand-soft);
  color: var(--brand-strong);
  font-size: var(--fs-cap);
  font-weight: 600;
}
.intro {
  margin: 0 0 10px;
  font-size: var(--fs-cap);
  color: var(--ink-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: var(--fs-cap);
  color: var(--ink-3);
}
</style>
