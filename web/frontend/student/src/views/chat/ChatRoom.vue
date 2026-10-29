<template>
  <section class="room" v-loading="loading">
    <header>
      <el-button circle plain @click="$router.push('/chat')"><el-icon><ArrowLeft /></el-icon></el-button>
      <el-avatar :size="44" :src="conversation?.peerAvatar">{{ conversation?.peerNickname?.charAt(0) }}</el-avatar>
      <div><h2>{{ conversation?.peerNickname || '私信' }}</h2><span>{{ connectionText }}</span></div>
      <el-dropdown v-if="conversation" class="more" @command="command">
        <el-button circle plain><el-icon><MoreFilled /></el-icon></el-button>
        <template #dropdown><el-dropdown-menu><el-dropdown-item command="block">拉黑用户</el-dropdown-item><el-dropdown-item command="hide">隐藏会话</el-dropdown-item></el-dropdown-menu></template>
      </el-dropdown>
    </header>
    <BizContextCard :conversation="conversation" />
    <main ref="scroller">
      <el-button v-if="!chatStore.historyDone[id]" link :loading="historyLoading" @click="loadMore">加载更早消息</el-button>
      <div v-else class="history-end">没有更早的消息了</div>
      <MessageBubble v-for="message in messages" :key="message.id || message.clientMessageId" :message="message" :mine="Number(message.senderId) === Number(userStore.userInfo?.id)" :peer-name="conversation?.peerNickname" :peer-avatar="conversation?.peerAvatar" @retry="retry" />
    </main>
    <ChatComposer @send="send" />
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, MoreFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getConversation, hideConversation, blockChatUser } from '../../api/chat'
import { useChatStore } from '../../store/chat'
import { useUserStore } from '../../store/user'
import MessageBubble from '../../components/chat/MessageBubble.vue'
import ChatComposer from '../../components/chat/ChatComposer.vue'
import BizContextCard from '../../components/chat/BizContextCard.vue'

const route = useRoute(), router = useRouter(), chatStore = useChatStore(), userStore = useUserStore()
const id = Number(route.params.conversationId)
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
    await chatStore.loadHistory(id, true)
    await chatStore.markRead(id)
    scrollBottom()
  } catch (error) {
    ElMessage.error(error.message || '会话加载失败')
    router.replace('/chat')
  } finally { loading.value = false }
})
onBeforeUnmount(() => { chatStore.activeConversationId = null })
</script>

<style scoped>
.room { height:calc(100vh - 132px); min-height:620px; display:grid; grid-template-rows:auto auto 1fr auto; overflow:hidden; border:1px solid var(--line); border-radius:24px; background:var(--surface-2); box-shadow:var(--shadow-md); }
.room > header { display:flex; align-items:center; gap:12px; padding:14px 18px; border-bottom:1px solid var(--line); background:var(--surface); }
header h2 { margin:0; font-family:var(--font-display); font-size:18px; } header span { color:var(--ink-3); font-size:12px; } .more { margin-left:auto; }
main { overflow-y:auto; padding:8px 24px 24px; scroll-behavior:smooth; } main > .el-button { display:flex; margin:8px auto; }
.history-end { text-align:center; padding:10px; color:var(--ink-3); font-size:12px; }
@media(max-width:820px){ .room{height:calc(100vh - 110px);min-height:540px;border-radius:16px} main{padding:8px 12px 18px} }
</style>
