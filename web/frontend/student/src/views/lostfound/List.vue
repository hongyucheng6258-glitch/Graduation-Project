<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <WtPageHeader title="失物招领" subtitle="遗失物品别着急，先在这里找一找。">
    <el-button type="primary" @click="goPublish">＋ 登记信息</el-button>
  </WtPageHeader>

  <div class="lf-list">
    <div class="toolbar">
      <div class="chips">
        <button type="button" class="chip" :aria-pressed="type === undefined" @click="selectType(undefined)">全部</button>
        <button type="button" class="chip" :aria-pressed="type === 0" @click="selectType(0)">我要寻物</button>
        <button type="button" class="chip" :aria-pressed="type === 1" @click="selectType(1)">我拾到物品</button>
      </div>
      <el-input v-model="keyword" placeholder="搜索…" clearable style="width: 240px" @keyup.enter="search" @clear="search">
        <template #append><el-button @click="search">搜索</el-button></template>
      </el-input>
    </div>

    <div class="grid photo-feed" v-loading="loading">
      <RouterLink v-for="lf in list" :key="lf.id" class="lost-card photo-feed__link" :to="`/lostfound/detail/${lf.id}`">
        <ItemCard feed :cover="firstContentImage(lf, 'lost')" :title="lf.title" module="lost">
          <template #badge>
            <span class="badge-tag" :class="lf.type === 0 ? 'tag-seeking' : 'tag-found'">{{ lf.type === 0 ? '寻物中' : '待认领' }}</span>
          </template>
          <template #footer>
            <div class="card-footer">
              <span>📍 {{ lf.location || '未知地点' }}</span>
              <span v-if="lf.happenTime || lf.createTime">{{ fromNow(lf.happenTime || lf.createTime) }}</span>
            </div>
          </template>
        </ItemCard>
      </RouterLink>
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
import { fromNow } from '../../utils/date'
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
.chip[aria-pressed='true'] {
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
.tag-seeking { background: var(--brand-soft); color: var(--brand-strong); }
.tag-found { background: var(--success-soft); color: var(--success); }
.card-footer {
  display: flex;
  gap: 4px 10px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 12px;
  color: var(--ink-3);
}
</style>
