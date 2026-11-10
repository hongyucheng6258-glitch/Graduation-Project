<template>
  <WtPageHeader v-if="!embedded" title="私信" subtitle="与同学安全沟通，消息实时同步。" />
  <div class="inbox-layout" :class="{ embedded }">
    <nav class="inbox" aria-label="私信会话" v-loading="loading">
      <header>
        <div><b>{{ chatStore.unreadTotal }}</b><span>条未读私信</span></div>
        <span class="connection" :class="chatStore.socketState">{{ stateText }}</span>
      </header>
      <RouterLink v-for="item in chatStore.conversations" :key="item.id" class="conversation" :to="`/chat/${item.id}`">
        <el-badge :value="item.unreadCount" :hidden="!item.unreadCount">
          <el-avatar :size="42" :src="item.peerAvatar">{{ item.peerNickname?.charAt(0) }}</el-avatar>
        </el-badge>
        <div class="body">
          <div class="top"><strong>{{ item.peerNickname || '校园同学' }}</strong><time>{{ fromNow(item.lastMessageTime) }}</time></div>
          <div class="bottom"><span>{{ item.lastMessageSummary || '开始一段新对话' }}</span><em v-if="item.contextTitle">{{ item.contextTitle }}</em></div>
        </div>
      </RouterLink>
      <EmptyBox v-if="!loading && !chatStore.conversations.length" description="暂无私信，从闲置、失物、活动或动态中联系发布者吧" />
    </nav>
    <section v-if="!embedded" class="inbox-placeholder" aria-label="消息面板">
      <EmptyBox description="选择一条会话，查看聊天消息" />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import EmptyBox from '../../components/EmptyBox.vue'
import { useChatStore } from '../../store/chat'
import { fromNow } from '../../utils/date'
defineProps({ embedded: Boolean })

const chatStore = useChatStore()
const loading = ref(false)
const stateText = computed(() => ({ connected: '实时在线', connecting: '连接中', disconnected: '离线补偿', error: '连接异常' }[chatStore.socketState] || '离线补偿'))
onMounted(async () => {
  loading.value = true
  try { await chatStore.loadConversations() } finally { loading.value = false }
})
</script>

<style scoped>
.inbox-layout {
  min-width: 0;
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 20px;
}
.inbox-layout.embedded { grid-template-columns: minmax(0, 1fr); }
.inbox, .inbox-placeholder {
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
}
.inbox {
  overflow-y: auto;
  max-height: max(490px, calc(100dvh - 170px));
}
.inbox > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px;
  border-bottom: 1px solid var(--line);
}
header div { display: flex; align-items: baseline; gap: 5px; color: var(--ink-2); font-size: var(--fs-xs); }
header b { font-size: var(--fs-h3); color: var(--brand-strong); }
.connection { font-size: var(--fs-cap); padding: 4px 7px; border-radius: var(--r-pill); background: var(--surface-2); color: var(--ink-3); }
.connection.connected { color: var(--success); }
.conversation {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  padding: 14px;
  border-bottom: 1px solid var(--line);
  color: var(--ink);
  text-decoration: none;
}
.conversation:hover { background: var(--surface-2); }
.conversation.router-link-exact-active { background: var(--brand-soft); color: var(--brand-strong); }
.conversation:focus-visible { outline: 2px solid var(--brand); outline-offset: -2px; }
.body { min-width: 0; }
.top, .bottom { display: flex; justify-content: space-between; gap: 8px; align-items: center; }
.top strong { font-size: var(--fs-sm); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
time { flex: none; font-size: var(--fs-cap); color: var(--ink-3); }
.bottom { margin-top: 4px; color: var(--ink-3); font-size: var(--fs-xs); }
.bottom span, .bottom em { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.bottom em { max-width: 42%; color: var(--brand-strong); font-style: normal; }
.inbox-placeholder { display: grid; place-items: center; min-height: 400px; }
@media (max-width: 820px) {
  .inbox-layout { grid-template-columns: minmax(0, 1fr); }
  .inbox { max-height: none; }
  .inbox-layout.embedded, .inbox-placeholder { display: none; }
}
</style>
