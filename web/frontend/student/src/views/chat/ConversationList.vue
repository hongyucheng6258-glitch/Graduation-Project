<template>
  <WtPageHeader title="私信" subtitle="与同学安全沟通，消息实时同步" eyebrow="消息中心" />
  <section class="inbox" v-loading="loading">
    <header>
      <div><b>{{ chatStore.unreadTotal }}</b><span>条未读私信</span></div>
      <span class="connection" :class="chatStore.socketState">{{ stateText }}</span>
    </header>
    <button v-for="item in chatStore.conversations" :key="item.id" class="conversation" @click="$router.push(`/chat/${item.id}`)">
      <el-badge :value="item.unreadCount" :hidden="!item.unreadCount">
        <el-avatar :size="52" :src="item.peerAvatar">{{ item.peerNickname?.charAt(0) }}</el-avatar>
      </el-badge>
      <div class="body">
        <div class="top"><strong>{{ item.peerNickname || '校园同学' }}</strong><time>{{ fromNow(item.lastMessageTime) }}</time></div>
        <div class="bottom"><span>{{ item.lastMessageSummary || '开始一段新对话' }}</span><em v-if="item.contextTitle">{{ item.contextTitle }}</em></div>
      </div>
    </button>
    <EmptyBox v-if="!loading && !chatStore.conversations.length" description="暂无私信，从闲置、失物、活动或动态中联系发布者吧" />
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import EmptyBox from '../../components/EmptyBox.vue'
import { useChatStore } from '../../store/chat'
import { fromNow } from '../../utils/date'
const chatStore = useChatStore()
const loading = ref(false)
const stateText = computed(() => ({ connected: '实时在线', connecting: '连接中', disconnected: '离线补偿', error: '连接异常' }[chatStore.socketState] || '离线补偿'))
onMounted(async () => {
  loading.value = true
  try { await chatStore.loadConversations() } finally { loading.value = false }
})
</script>

<style scoped>
.inbox { overflow:hidden; border:1px solid var(--line); border-radius:22px; background:var(--surface); box-shadow:var(--shadow-sm); }
.inbox > header { display:flex; align-items:center; justify-content:space-between; padding:20px 24px; border-bottom:1px solid var(--line); background:linear-gradient(135deg,var(--brand-soft),transparent 65%); }
header div { display:flex; align-items:baseline; gap:8px; color:var(--ink-2); } header b { font-family:var(--font-display); font-size:32px; color:var(--brand-strong); }
.connection { font-size:12px; padding:6px 10px; border-radius:999px; background:var(--surface-2); color:var(--ink-3); } .connection.connected { color:var(--success); }
.conversation { width:100%; display:grid; grid-template-columns:auto 1fr; gap:16px; align-items:center; padding:18px 24px; border:0; border-bottom:1px solid var(--line); background:transparent; text-align:left; cursor:pointer; transition:.2s; }
.conversation:hover { background:var(--surface-2); transform:translateX(3px); }
.body { min-width:0; } .top,.bottom { display:flex; justify-content:space-between; gap:16px; align-items:center; } .top strong { font-size:16px; } time { flex:none; font-size:12px; color:var(--ink-3); }
.bottom { margin-top:7px; color:var(--ink-3); font-size:13px; } .bottom span { overflow:hidden; white-space:nowrap; text-overflow:ellipsis; } .bottom em { flex:none; max-width:220px; overflow:hidden; white-space:nowrap; text-overflow:ellipsis; padding:3px 8px; border-radius:999px; background:var(--brand-soft); color:var(--brand-strong); font-style:normal; font-size:11px; }
</style>
