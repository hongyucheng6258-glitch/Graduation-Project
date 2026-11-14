<template>
  <div class="message-page">
    <WtPageHeader title="消息中心" subtitle="来自平台与同学的提醒" eyebrow="我的" />
    <div class="message-center">
    <!-- 左栏：消息类型列表 -->
    <nav class="msg-sidebar" aria-label="消息分类">
      <button
        v-for="t in msgTypes"
        :key="t.value"
        type="button"
        class="msg-side-item"
        :class="{ active: type === t.value }"
        :aria-pressed="type === t.value"
        @click="selectType(t.value)"
      >
        <span>{{ t.label }}</span>
        <el-badge v-if="t.badge !== undefined" :value="t.badge" :hidden="!t.badge" />
      </button>
      <RouterLink class="msg-side-item" to="/friends?tab=requests"><span>好友申请</span></RouterLink>
      <RouterLink class="msg-side-item private" to="/chat">
        <span>聊天会话</span>
        <el-badge :value="chatStore.unreadTotal" :hidden="!chatStore.unreadTotal" />
      </RouterLink>
    </nav>

    <!-- 右栏：消息详情 -->
    <div class="msg-detail">
      <div class="msg-detail-head">
        <h2>{{ currentLabel }}<small v-if="total">{{ total }} 条</small></h2>
        <el-button size="small" @click="readAll">全部已读</el-button>
      </div>
      <div v-loading="loading" class="msg-list">
        <button
          v-for="m in list"
          :key="m.id"
          type="button"
          class="msg-item"
          :class="{ unread: m.isRead === 0 }"
          @click="openMsg(m)"
        >
          <el-badge is-dot :hidden="m.isRead === 1">
            <span class="m-icon">{{ iconOf(m.type) }}</span>
          </el-badge>
          <span class="m-body">
            <strong class="m-title">{{ m.title }}</strong>
            <span class="m-content">{{ m.content }}</span>
          </span>
          <time class="m-time" :datetime="m.createTime">{{ fromNow(m.createTime) }}</time>
        </button>
        <EmptyBox v-if="!loading && !list.length" description="暂无消息" />
      </div>
      <el-pagination v-if="total > 10" v-model:current-page="pageNum" :total="total" :page-size="10"
                     layout="prev, pager, next" style="margin-top: 16px" @current-change="load" />
    </div>
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
.message-page { max-width: 1040px; margin-inline: auto; }
.message-center { display: grid; grid-template-columns: 190px minmax(0, 1fr); align-items: start; gap: 14px; }
.msg-sidebar, .msg-list { min-width: 0; border: 1px solid var(--line); border-radius: 12px; background: var(--surface); }
.msg-sidebar { position: sticky; top: 88px; display: flex; flex-direction: column; gap: 4px; padding: 8px; }
.msg-side-item { display: flex; align-items: center; justify-content: space-between; gap: 6px; padding: 7px 9px; border: 0; border-radius: 7px; background: transparent; color: var(--ink-2); font: inherit; font-size: 13px; text-align: start; text-decoration: none; cursor: pointer; }
.msg-side-item:hover { background: var(--surface-2); }
.msg-side-item.active { background: var(--brand-soft); color: var(--brand-strong); font-weight: 650; }
.msg-side-item.private { color: var(--brand-strong); }
.msg-detail { min-width: 0; }
.msg-detail-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin: 0 0 8px; }
.msg-detail-head h2 { margin: 0; font-size: 16px; }
.msg-detail-head small { margin-inline-start: 8px; color: var(--ink-3); font-size: 12px; font-weight: 500; }
.msg-item { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: start; gap: 9px; width: 100%; padding: 11px 12px; border: 0; background: transparent; color: var(--ink); text-align: start; font: inherit; cursor: pointer; }
.msg-item + .msg-item { border-top: 1px solid var(--line); }
.msg-item:first-child { border-radius: 11px 11px 0 0; }
.msg-item:last-child { border-radius: 0 0 11px 11px; }
.msg-item.unread { background: var(--brand-soft); }
.msg-item:hover { background: var(--surface-2); }
.msg-item:focus-visible, .msg-side-item:focus-visible { outline: 2px solid var(--brand); outline-offset: -2px; }
.m-icon { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 7px; background: var(--surface-2); font-size: 15px; }
.m-body { display: grid; gap: 2px; min-width: 0; overflow-wrap: anywhere; }
.m-title { font-size: 14px; }
.m-content { color: var(--ink-2); font-size: 13px; }
.m-time { color: var(--ink-3); font-size: 12px; white-space: nowrap; }
@media (max-width: 740px) {
  .message-center { grid-template-columns: minmax(0, 1fr); gap: 10px; }
  .msg-sidebar { position: static; flex-direction: row; flex-wrap: wrap; padding: 5px; }
  .msg-side-item { padding: 5px 7px; font-size: 12px; }
  .msg-item { grid-template-columns: auto minmax(0, 1fr); gap: 4px 8px; padding: 9px; }
  .m-time { grid-column: 2; }
}
</style>
