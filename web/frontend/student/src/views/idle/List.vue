<template>
  <div v-if="loadError" class="load-error" role="alert"><span>{{ loadError }}</span><button type="button" class="text-btn" @click="load">重新加载</button></div>
  <WtPageHeader title="闲置互换" subtitle="让闲置遇见新主人，也让物品继续被需要。">
    <el-button type="primary" @click="goPublish">＋ 发布闲置</el-button>
  </WtPageHeader>
  <WtFeature image="/images/generated-idle-items.webp" title="好物不闲置"
    description="支持分类搜索、收藏、预约与交接安排。双方确认后，再留下真实评价。">
    <a class="btn primary" href="#idle-list">浏览闲置物品</a>
    <RouterLink class="btn" to="/idle/appointments">我的预约</RouterLink>
  </WtFeature>

  <div id="idle-list" class="idle-list">
    <!-- 搜索栏 + 分类筛选 -->
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索闲置物品…" clearable style="width: 280px" @keyup.enter="search" @clear="search">
        <template #append><el-button @click="search">搜索</el-button></template>
      </el-input>
      <div class="chips">
        <button type="button" class="chip" :class="{ active: !category }" :aria-pressed="!category" @click="selectCategory('')">全部</button>
        <button v-for="c in categories" :key="c" type="button" class="chip" :class="{ active: category === c }" :aria-pressed="category === c" @click="selectCategory(c)">{{ c }}</button>
      </div>
    </div>

    <!-- 卡片网格 -->
    <div class="grid photo-feed" v-loading="loading">
      <RouterLink v-for="item in list" :key="item.id" class="photo-feed__link" :to="`/idle/detail/${item.id}`">
        <ItemCard feed :cover="firstContentImage(item, 'idle', item.category)" :title="item.title"
                  module="idle" :category="item.category">
          <template #badge>
            <span class="badge-tag" :class="badgeCls(item.status)">{{ badgeText(item.status) }}</span>
          </template>
          <template #footer>
            <div class="card-footer">
              <span v-if="item.category">{{ item.category }}</span>
              <span class="expect">期望换：{{ item.expectItem || '待商量' }}</span>
            </div>
          </template>
        </ItemCard>
      </RouterLink>
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
import WtFeature from '../../components/wt/WtFeature.vue'
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
#idle-list { scroll-margin-top: 84px; }
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
  flex-wrap: wrap;
  gap: 2px 8px;
  align-items: center;
  font-size: 12px;
  color: var(--ink-3);
  overflow-wrap: anywhere;
}
.expect {
  color: var(--ink-2);
}
</style>
