<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <WtPageHeader title="闲置物品" subtitle="校园里的二手好物，流转给需要的人" eyebrow="校园服务" />

  <div class="idle-list">
    <!-- 搜索栏 + 分类筛选 -->
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索闲置物品…" clearable style="width: 280px" @keyup.enter="search" @clear="search">
        <template #append><el-button @click="search">搜索</el-button></template>
      </el-input>
      <div class="chips">
        <span class="chip" :class="{ active: !category }" @click="selectCategory('')">全部</span>
        <span v-for="c in categories" :key="c" class="chip" :class="{ active: category === c }" @click="selectCategory(c)">{{ c }}</span>
      </div>
      <div class="spacer" />
      <el-button @click="$router.push('/idle/appointments')">我的预约</el-button>
      <el-button type="primary" @click="goPublish">＋ 发布闲置</el-button>
    </div>

    <!-- 卡片网格 -->
    <div class="grid" v-loading="loading">
      <ItemCard
        v-for="item in list"
        :key="item.id"
        :cover="firstContentImage(item, 'idle', item.category)"
        :title="item.title"
        :desc="item.description"
        :time="item.createTime"
        module="idle"
        :category="item.category"
        @click="$router.push(`/idle/detail/${item.id}`)"
      >
        <template #badge>
          <span class="badge-tag" :class="badgeCls(item.status)">{{ badgeText(item.status) }}</span>
        </template>
        <template #footer>
          <div class="card-footer">
            <el-tag v-if="item.category" size="small">{{ item.category }}</el-tag>
            <span class="expect">期望换：{{ item.expectItem || '面议' }}</span>
          </div>
        </template>
      </ItemCard>
    </div>
    <EmptyBox v-if="!loadError && !loading && !list.length" description="暂无闲置物品" />
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
import { ref, watch } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ItemCard from '../../components/ItemCard.vue'
import EmptyBox from '../../components/EmptyBox.vue'
import { listIdle } from '../../api/idle'
import { useUserStore } from '../../store/user'
import { firstContentImage } from '../../utils/content-assets.mjs'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const categories = ['教材书籍', '数码电子', '生活用品', '运动器材', '服饰鞋包', '其他']
const keyword = ref('')
const category = ref('')
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const restoredListState = useListState('idle', { pageNum, keyword, category })

function selectCategory(c) {
  category.value = c
  search()
}

const IDLE_TAG = ['tag-brand', 'tag-warning', 'tag-neutral', 'tag-error']
const IDLE_TEXT = ['在架', '已预约', '已完成', '已下架']
function badgeCls(s) {
  return IDLE_TAG[s ?? 0] || 'tag-neutral'
}
function badgeText(s) {
  return IDLE_TEXT[s ?? 0] || '在架'
}

function search() {
  pageNum.value = 1
  load()
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listIdle({ keyword: keyword.value || undefined, category: category.value || undefined, pageNum: pageNum.value, pageSize: 12 })
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
  router.push('/idle/publish')
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
.idle-list {
  min-width: 0;
}
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
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  min-width: 0;
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
.tag-brand { background: var(--brand-soft); color: var(--brand-strong); }
.tag-warning { background: var(--warning-soft); color: var(--gold-strong); }
.tag-neutral { background: var(--surface-3); color: var(--ink-2); }
.tag-error { background: var(--error-soft); color: var(--error); }
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.expect {
  font-size: 12px;
  color: var(--warning);
}
</style>
