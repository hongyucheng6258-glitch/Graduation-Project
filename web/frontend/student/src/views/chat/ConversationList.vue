<template>
  <div class="inbox-layout" :class="{ embedded, 'chat-workspace': !embedded }">
    <nav class="inbox" aria-label="聊天会话" :aria-busy="loading">
      <header>
        <div>
          <h1>聊天 <span v-if="chatStore.unreadTotal" class="unread-total">{{ chatStore.unreadTotal }}</span></h1>
          <p>{{ connectionText }}</p>
        </div>
        <el-button circle aria-label="发起聊天" title="发起聊天" @click="dialogOpen = true">
          <el-icon><Plus /></el-icon>
        </el-button>
      </header>
      <div class="search">
        <RouterLink class="contacts-link" to="/friends">通讯录</RouterLink>
        <el-input v-model="filter" :prefix-icon="Search" placeholder="搜索会话" aria-label="搜索会话" clearable />
      </div>
      <div class="conversations" v-loading="loading">
        <RouterLink v-for="item in filtered" :key="item.id" class="conversation" :to="`/chat/${item.id}`">
          <el-badge :value="item.unreadCount" :max="99" :hidden="!item.unreadCount">
            <el-avatar shape="square" :class="{ group: item.groupName }" :size="44" :src="item.peerAvatar">
              <el-icon v-if="item.groupName"><UserFilled /></el-icon>
              <template v-else>{{ item.peerNickname?.charAt(0) || '同' }}</template>
            </el-avatar>
          </el-badge>
          <div class="body">
            <div class="top">
              <strong>{{ item.groupName || item.peerNickname || '校园同学' }}</strong>
              <time>{{ formatTime(item.lastMessageTime) }}</time>
            </div>
            <div class="bottom">{{ item.lastMessageSummary || (item.groupName ? '群聊已创建' : '开始一段新对话') }}</div>
          </div>
        </RouterLink>
        <EmptyBox v-if="!loading && !filtered.length" :description="filter ? '没有找到相关会话' : '暂无会话，点击右上角发起聊天'" />
      </div>
    </nav>
    <section v-if="!embedded" class="inbox-placeholder" aria-label="聊天面板">
      <div><el-icon><ChatDotRound /></el-icon><p>选择会话开始聊天</p></div>
    </section>
  </div>
  <StartChatDialog v-if="dialogOpen" @close="dialogOpen = false" />
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ChatDotRound, Plus, Search, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import EmptyBox from '../../components/EmptyBox.vue'
import StartChatDialog from '../../components/chat/StartChatDialog.vue'
import { useChatStore } from '../../store/chat'

defineProps({ embedded: Boolean })

const chatStore = useChatStore()
const loading = ref(false)
const dialogOpen = ref(false)
const filter = ref('')
const connectionText = computed(() => {
  if (chatStore.connected) return '实时在线'
  return chatStore.socketState === 'connecting' ? '正在连接' : '实时连接暂不可用'
})
const filtered = computed(() => {
  const term = filter.value.trim().toLocaleLowerCase()
  return chatStore.conversations.filter((item) => !term
    || [item.groupName, item.peerNickname, item.lastMessageSummary]
      .some((value) => value?.toLocaleLowerCase().includes(term)))
})

function formatTime(value) {
  if (!value) return ''
  const time = dayjs(value)
  return time.format(time.isSame(dayjs(), 'day') ? 'HH:mm' : 'MM-DD')
}

onMounted(async () => {
  loading.value = true
  try {
    await chatStore.loadConversations()
  } catch (error) {
    ElMessage.error(error.message || '会话加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<style src="./chatWorkspace.css"></style>

<style scoped>
.inbox { min-width: 0; min-height: 0; display: flex; flex-direction: column; background: #fff; }
.inbox > header { display: flex; align-items: center; justify-content: space-between; padding: 20px 18px 12px; }
h1 { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 22px; }
header p { margin: 3px 0 0; color: #5e6862; font-size: 12px; }
.unread-total { padding: 2px 7px; border-radius: 12px; background: #e4f8ec; color: #14613b; font-size: 11px; }
.search { display: flex; align-items: center; gap: 8px; padding: 0 14px 12px; }
.contacts-link { flex: none; color: #14613b; font-size: 13px; }
.search :deep(.el-input__wrapper) { background: #f2f3f2; border-radius: 6px; box-shadow: none; }
.conversations { flex: 1; min-height: 0; overflow-y: auto; border-top: 1px solid #ededed; }
.conversation { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 12px; align-items: center; padding: 13px 15px; border-bottom: 1px solid #f1f1f1; color: #202521; text-decoration: none; }
.conversation:hover { background: #f5f6f5; }
.conversation.router-link-exact-active { background: #e5f4e9; box-shadow: inset 3px 0 #198456; }
.conversation:focus-visible { outline: 2px solid #198456; outline-offset: -2px; }
.group { background: #dff2e7; color: #207650; font-size: 21px; }
.top { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.top strong, .bottom { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.top strong { font-size: 14px; }
time, .bottom { color: #606b65; font-size: 12px; }
time { flex: none; }
.bottom { margin-top: 4px; }
.inbox-placeholder { display: grid; place-items: center; background: #f4f5f4; color: #606b65; text-align: center; }
.inbox-placeholder .el-icon { font-size: 56px; color: #83a593; }
@media (max-width: 820px) {
  .inbox-placeholder { display: none; }
}
</style>
