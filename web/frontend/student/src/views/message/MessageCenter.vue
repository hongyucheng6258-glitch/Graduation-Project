<template>
  <WtPageHeader title="消息中心" subtitle="来自平台与同学的提醒" eyebrow="我的" />

  <div class="message-center">
    <!-- 左栏：消息类型列表 -->
    <aside class="msg-sidebar card">
      <div class="msg-sidebar-title">消息中心</div>
      <button
        v-for="t in msgTypes"
        :key="t.value"
        type="button"
        class="msg-side-item"
        :class="{ active: type === t.value }"
        @click="selectType(t.value)"
      >
        <span>{{ t.label }}</span>
        <el-badge v-if="t.badge !== undefined" :value="t.badge" :hidden="!t.badge" />
      </button>
      <button type="button" class="msg-side-item private" @click="router.push('/chat')">
        <span>私信会话</span>
        <el-badge :value="chatStore.unreadTotal" :hidden="!chatStore.unreadTotal" />
      </button>
    </aside>

    <!-- 右栏：消息详情 -->
    <div class="msg-detail">
      <div class="msg-detail-head">
        <h3>{{ currentLabel }}</h3>
        <el-button size="small" @click="readAll">全部已读</el-button>
      </div>
      <div v-loading="loading" class="msg-list">
        <div
          v-for="m in list"
          :key="m.id"
          class="msg-item"
          :class="{ unread: m.isRead === 0 }"
          @click="openMsg(m)"
        >
          <el-badge is-dot :hidden="m.isRead === 1">
            <span class="m-icon">{{ iconOf(m.type) }}</span>
          </el-badge>
          <div class="m-body">
            <div class="m-title">{{ m.title }}</div>
            <div class="m-content">{{ m.content }}</div>
          </div>
          <div class="m-time">{{ fromNow(m.createTime) }}</div>
        </div>
        <EmptyBox v-if="!loading && !list.length" description="暂无消息" />
      </div>
      <el-pagination v-model:current-page="pageNum" :total="total" :page-size="10"
                     layout="prev, pager, next" style="margin-top: 16px" @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { useRouter } from 'vue-router'
import { listMessage, markRead, markAllRead } from '../../api/message'
import { useMessageStore } from '../../store/message'
import { useChatStore } from '../../store/chat'
import { fromNow } from '../../utils/date'
import EmptyBox from '../../components/EmptyBox.vue'
import { messageTarget } from '../../utils/message-navigation.mjs'

const router = useRouter()
const messageStore = useMessageStore()
const chatStore = useChatStore()
const type = ref('')
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)

const iconOf = (t) => ({ system: '📢', interact: '💬', audit: '✅' }[t] || '📩')

const msgTypes = computed(() => [
  { label: '全部', value: '', badge: messageStore.unread },
  { label: '系统通知', value: 'system', badge: undefined },
  { label: '互动消息', value: 'interact', badge: undefined },
  { label: '审核结果', value: 'audit', badge: undefined }
])

const currentLabel = computed(() => msgTypes.value.find((t) => t.value === type.value)?.label || '全部')

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
  try {
    const res = await listMessage({ type: type.value || undefined, pageNum: pageNum.value, pageSize: 10 })
    list.value = Array.isArray(res?.list) ? res.list : []
    total.value = Number(res?.total || 0)
    messageStore.refreshUnread()
  } finally {
    loading.value = false
  }
}

/** 打开消息：标记已读并跳转关联业务页 */
async function openMsg(m) {
  if (m.isRead === 0) {
    await markRead(m.id)
    m.isRead = 1
    messageStore.refreshUnread()
  }
  const target = messageTarget(m)
  if (target) await router.push(target)
}

async function readAll() {
  await markAllRead()
  list.value.forEach((m) => (m.isRead = 1))
  messageStore.refreshUnread()
}

onMounted(load)
</script>

<style scoped>
.message-center {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: var(--s-5);
  align-items: start;
}
.msg-sidebar {
  padding: var(--s-4);
  position: sticky;
  top: var(--s-6);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.msg-sidebar-title {
  font-weight: 700;
  font-size: var(--fs-sm);
  padding: 4px 10px 10px;
  color: var(--ink);
}
.msg-side-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s-2);
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: var(--r-md);
  background: none;
  cursor: pointer;
  font-family: inherit;
  font-size: var(--fs-sm);
  color: var(--ink-2);
  text-align: left;
  transition: all .15s var(--ease-out);
}
.msg-side-item:hover {
  background: var(--surface-2);
  color: var(--ink);
}
.msg-side-item.active {
  background: var(--brand-soft);
  color: var(--brand-strong);
  font-weight: 600;
}
.msg-side-item.private {
  margin-top: var(--s-2);
  border-top: 1px solid var(--line);
  border-radius: 0 0 var(--r-md) var(--r-md);
  padding-top: var(--s-3);
}
.msg-detail {
  min-width: 0;
}
.msg-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--s-4);
}
.msg-detail-head h3 {
  font-family: var(--font-display);
  font-size: var(--fs-h2);
  font-weight: 600;
  margin: 0;
}
.msg-list {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: var(--s-3) var(--s-5);
}<style scoped>
.msg-item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px 8px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}
.msg-item.unread {
  background: #f0f6ff;
}
.m-icon {
  font-size: 22px;
}
.m-body {
  flex: 1;
}
.m-title {
  font-size: 14px;
  font-weight: 600;
}
.m-content {
  font-size: 13px;
  color: var(--ink-3);
  margin-top: 4px;
}
.m-time {
  font-size: 12px;
  color: var(--ink-3);
}
</style>
