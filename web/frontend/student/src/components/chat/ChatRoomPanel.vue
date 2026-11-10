<template>
  <section class="room" v-loading="loading">
    <header>
      <el-button circle plain aria-label="返回会话列表" @click="$router.push('/chat')"><el-icon><ArrowLeft /></el-icon></el-button>
      <el-avatar :size="44" :src="conversation?.peerAvatar">{{ conversation?.peerNickname?.charAt(0) }}</el-avatar>
      <div><h1>{{ conversation?.peerNickname || '私信' }}</h1><span>{{ connectionText }}</span></div>
      <el-dropdown v-if="conversation" class="more" @command="command">
        <el-button circle plain aria-label="会话操作"><el-icon><MoreFilled /></el-icon></el-button>
        <template #dropdown><el-dropdown-menu><el-dropdown-item command="block">拉黑用户</el-dropdown-item><el-dropdown-item command="hide">隐藏会话</el-dropdown-item></el-dropdown-menu></template>
      </el-dropdown>
    </header>
    <BizContextCard :conversation="conversation" />
    <div ref="scroller" class="chat-flow" role="log" aria-label="私信消息">
      <el-button v-if="!chatStore.historyDone[id]" link :loading="historyLoading" @click="loadMore">加载更早消息</el-button>
      <div v-else class="history-end">没有更早的消息了</div>
      <MessageBubble v-for="message in messages" :key="message.id || message.clientMessageId" :message="message" :mine="Number(message.senderId) === Number(userStore.userInfo?.id)" :peer-name="conversation?.peerNickname" :peer-avatar="conversation?.peerAvatar" @retry="retry" />
    </div>
    <ChatComposer v-if="conversation" @send="send" />
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, MoreFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getConversation, hideConversation, blockChatUser } from '../../api/chat'
import { useChatStore } from '../../store/chat'
import { useUserStore } from '../../store/user'
import MessageBubble from './MessageBubble.vue'
import ChatComposer from './ChatComposer.vue'
import BizContextCard from './BizContextCard.vue'

const router = useRouter(), chatStore = useChatStore(), userStore = useUserStore()
const props = defineProps({ conversationId: { type: Number, required: true } })
const id = props.conversationId
let active = true
const conversation = ref(null), loading = ref(false), historyLoading = ref(false), scroller = ref(null)
const messages = computed(() => chatStore.messages(id))
const connectionText = computed(() => chatStore.connected ? '实时连接中' : '当前离线，发送将自动走 REST')
async function scrollBottom() { await nextTick(); if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight }
async function loadMore() {
  historyLoading.value = true
  try { await chatStore.loadHistory(id) } catch (error) { ElMessage.error(error.message || '历史消息加载失败') } finally { historyLoading.value = false }
}
async function send(type, content) {
  try { await chatStore.send(conversation.value, type, content); scrollBottom() } catch (error) { ElMessage.error(error.message || '发送失败') }
}
async function retry(message) {
  try { await chatStore.retry(conversation.value, message) } catch (error) { ElMessage.error(error.message || '重试失败') }
}
async function command(value) {
  if (value === 'hide') { await hideConversation(id); router.push('/chat') }
  else if (value === 'block') { await ElMessageBox.confirm('拉黑后双方将不能继续发送新消息，仍可查看历史。', '确认拉黑', { type: 'warning' }); await blockChatUser(conversation.value.peerUserId); ElMessage.success('已拉黑该用户') }
}
watch(() => messages.value.map((item) => `${item.id || item.clientMessageId}:${item.sendState || ''}`).join('|'), () => {
  scrollBottom()
})

onMounted(async () => {
  loading.value = true
  chatStore.activeConversationId = id
  try {
    conversation.value = await getConversation(id)
    if (!active) return
    await chatStore.loadHistory(id, true)
    if (!active) return
    await chatStore.markRead(id)
    scrollBottom()
  } catch (error) {
    if (!active) return
    ElMessage.error(error.message || '会话加载失败')
    router.replace('/chat')
  } finally { loading.value = false }
})
onBeforeUnmount(() => {
  active = false
  if (chatStore.activeConversationId === id) chatStore.activeConversationId = null
})
</script>

<style scoped>
.room {
  min-width: 0;
  height: max(490px, calc(100dvh - 170px));
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface-2);
}
.room > header {
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}
header h1 { margin: 0; font-size: var(--fs-body); overflow-wrap: anywhere; }
header span { color: var(--ink-3); font-size: var(--fs-xs); }
.more { margin-left: auto; }
.chat-flow { flex: 1; min-height: 0; overflow-y: auto; padding: 8px 20px 20px; }
.chat-flow > .el-button { display: flex; margin: 8px auto; }
.history-end { text-align: center; padding: 10px; color: var(--ink-3); font-size: var(--fs-xs); }
@media (max-width: 820px) {
  .room { height: max(430px, calc(100dvh - 146px)); }
  .room > header { padding: 10px 12px; gap: 8px; }
  .chat-flow { padding: 8px 12px 14px; }
}
</style>
