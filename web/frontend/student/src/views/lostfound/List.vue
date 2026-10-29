<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <WtPageHeader title="失物招领" subtitle="遗失与拾获，都在这里相遇" eyebrow="校园服务" />

  <div class="lf-list">
    <div class="toolbar">
      <div class="chips">
        <span class="chip" :class="{ active: type === undefined }" @click="selectType(undefined)">全部</span>
        <span class="chip" :class="{ active: type === 0 }" @click="selectType(0)">寻物</span>
        <span class="chip" :class="{ active: type === 1 }" @click="selectType(1)">招领</span>
      </div>
      <el-input v-model="keyword" placeholder="搜索…" clearable style="width: 240px" @keyup.enter="search" @clear="search">
        <template #append><el-button @click="search">搜索</el-button></template>
      </el-input>
      <div class="spacer" />
      <el-button type="primary" @click="goPublish">＋ 发布信息</el-button>
    </div>

    <div class="grid" v-loading="loading">
      <ItemCard
        v-for="lf in list"
        :key="lf.id"
        :cover="firstContentImage(lf, 'lost')"
        :title="lf.title"
        :desc="lf.description"
        :time="lf.createTime"
        @click="$router.push(`/lostfound/detail/${lf.id}`)"
      >
        <template #badge>
          <span class="badge-tag" :class="lf.type === 0 ? 'tag-error' : 'tag-success'">{{ lf.type === 0 ? '寻物' : '招领' }}</span>
        </template>
        <template #footer>
          <div class="card-footer">
            <span>📍 {{ lf.location || '未知地点' }}</span>
          </div>
        </template>
      </ItemCard>
    </div>
    <EmptyBox v-if="!loadError && !loading && !list.length" description="暂无信息" />
    <el-pagination v-model:current-page="pageNum" :total="total" :page-size="12"
                   layout="prev, pager, next" @current-change="load" />
  </div>
</template>

<script setup>
import { useListState } from '../../utils/list-state'
import { ref, watch } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ItemCard from '../../components/ItemCard.vue'
import EmptyBox from '../../components/EmptyBox.vue'
import { listLostFound } from '../../api/lostfound'
import { useUserStore } from '../../store/user'
import { firstContentImage } from '../../utils/content-assets.mjs'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const type = ref(undefined)
const keyword = ref('')
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const restoredListState = useListState('lostfound', { pageNum, keyword, type })

function selectType(t) {
  type.value = t
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
    const res = await listLostFound({ type: type.value, keyword: keyword.value || undefined, pageNum: pageNum.value, pageSize: 12 })
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
  router.push('/lostfound/publish')
}

let initialSearchWatch = true
watch(
  () => route.query.q,
  (q) => {
    if (!initialSearchWatch || !restoredListState) {
      keyword.value = String(q || '')
      pageNum.value = 1
    }
    initialSearchWatch = false
    load()
  },
  { immediate: true }
)
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
.chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
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
.badge-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: var(--r-pill);
  font-size: var(--fs-cap);
  font-weight: 600;
  letter-spacing: 0.02em;
}
.tag-error { background: var(--error-soft); color: var(--error); }
.tag-success { background: var(--success-soft); color: var(--success); }
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--ink-3);
}
</style>
