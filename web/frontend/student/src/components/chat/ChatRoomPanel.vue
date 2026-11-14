<template>
  <section class="room" v-loading="loading">
    <header>
      <el-button class="back" text aria-label="返回会话列表" @click="$router.push('/chat')"><el-icon><ArrowLeft /></el-icon></el-button>
      <div class="room-title">
        <h1>{{ conversation?.groupName || conversation?.peerNickname || '聊天' }}</h1>
        <span v-if="group && members.length">{{ members.length }} 位成员</span>
      </div>
      <el-dropdown v-if="conversation" class="more" @command="command">
        <el-button text aria-label="会话操作"><el-icon><MoreFilled /></el-icon></el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-if="group" command="members">群成员</el-dropdown-item>
            <el-dropdown-item v-else command="block">拉黑用户</el-dropdown-item>
            <el-dropdown-item command="pin">{{ conversation.pinned ? '取消置顶' : '置顶会话' }}</el-dropdown-item>
            <el-dropdown-item command="mute">{{ conversation.muted ? '关闭免打扰' : '消息免打扰' }}</el-dropdown-item>
            <el-dropdown-item v-if="group" command="leave">退出群聊</el-dropdown-item>
            <el-dropdown-item command="hide">隐藏会话</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>
    <BizContextCard :conversation="conversation" />
    <div ref="scroller" class="chat-flow" role="log" aria-label="聊天消息">
      <el-button v-if="messages.length && !chatStore.historyDone[id]" link :loading="historyLoading" @click="loadMore">加载更早消息</el-button>
      <p v-if="!loading && !messages.length" class="history-end">{{ group ? '群聊已创建，发条消息打个招呼吧' : '发条消息打个招呼吧' }}</p>
      <template v-for="(message, index) in messages" :key="message.id || message.clientMessageId">
        <div v-if="showTime(message, messages[index - 1])" class="timestamp">{{ formatTime(message.createTime) }}</div>
        <MessageBubble :message="message" :mine="isMine(message)" :sender="sender(message)" :group="group" @retry="retry" @report="report" />
      </template>
    </div>
    <ChatComposer v-if="conversation" @send="send" />
  </section>
  <el-dialog v-if="group" v-model="showMembers" title="群成员" width="min(440px, calc(100vw - 32px))" append-to-body>
    <div v-loading="membersLoading" class="members">
      <div v-for="member in members" :key="member.id" class="member">
        <el-avatar shape="square" :size="38" :src="member.avatar">{{ member.nickname?.charAt(0) }}</el-avatar>
        <RouterLink :to="`/user/${member.id}`">{{ member.nickname || '校园同学' }}</RouterLink>
        <span v-if="Number(member.id) === Number(conversation.groupOwnerId)">群主</span>
        <el-button v-else-if="ownsGroup" size="small" text type="danger" @click="removeMember(member)">移出</el-button>
      </div>
    </div>
    <div v-if="ownsGroup" class="group-management">
      <el-button size="small" @click="rename">修改群名</el-button>
      <el-button size="small" @click="inviteOpen = true">邀请好友</el-button>
    </div>
  </el-dialog>
  <el-dialog v-if="ownsGroup" v-model="inviteOpen" title="邀请好友" width="min(420px, calc(100vw - 32px))" append-to-body>
    <el-select v-model="invitee" filterable placeholder="选择好友" aria-label="选择好友" style="width: 100%">
      <el-option v-for="friend in inviteChoices" :key="friend.id" :value="friend.id" :label="friend.nickname || '校园同学'" />
    </el-select>
    <template #footer>
      <el-button type="primary" :disabled="!invitee" :loading="inviting" @click="sendInvite">发送邀请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, MoreFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { getConversation, hideConversation, blockChatUser, listGroupMembers, leaveGroup,
  removeGroupMember, inviteToGroup, renameGroup, setChatPreferences, reportChatMessage } from '../../api/chat'
import { listFriends } from '../../api/friends'
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
const messages = computed(() => {
  const joinedAfter = conversation.value?.joinedAfterMessageId
  return chatStore.messages(id).filter((message) => joinedAfter == null || message.id == null || message.id > joinedAfter)
})
const group = computed(() => Boolean(conversation.value?.groupName))
const members = ref([]), showMembers = ref(false), membersLoading = ref(false)
const ownsGroup = computed(() => group.value && Number(conversation.value?.groupOwnerId) === Number(userStore.userInfo?.id))
const inviteOpen = ref(false), invitee = ref(null), inviting = ref(false), friends = ref([])
const inviteChoices = computed(() => friends.value.filter((friend) => !members.value.some((member) => member.id === friend.id)))
const membersById = computed(() => new Map(members.value.map((member) => [Number(member.id), member])))
const isMine = (message) => Number(message.senderId) === Number(userStore.userInfo?.id)
const sender = (message) => {
  if (isMine(message)) return userStore.userInfo
  if (group.value) return membersById.value.get(Number(message.senderId))
  return { nickname: conversation.value?.peerNickname, avatar: conversation.value?.peerAvatar }
}
const showTime = (message, previous) => message.createTime
  && (!previous?.createTime || dayjs(message.createTime).diff(previous.createTime, 'minute') >= 5)
const formatTime = (value) => {
  const time = dayjs(value)
  return time.format(time.isSame(dayjs(), 'day') ? 'HH:mm' : 'MM-DD HH:mm')
}
async function scrollBottom() {
  await nextTick()
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
}
async function loadMore() {
  const element = scroller.value
  const height = element.scrollHeight
  const top = element.scrollTop
  historyLoading.value = true
  try {
    await chatStore.loadHistory(id)
    await nextTick()
    if (active) element.scrollTop = top + element.scrollHeight - height
  } catch (error) {
    ElMessage.error(error.message || '历史消息加载失败')
  } finally {
    historyLoading.value = false
  }
}
async function loadMembers() {
  if (membersLoading.value) return
  membersLoading.value = true
  try {
    members.value = await listGroupMembers(id)
  } catch (error) {
    ElMessage.error(error.message || '群成员加载失败')
  } finally {
    membersLoading.value = false
  }
}
async function send(type, content) {
  try { await chatStore.send(conversation.value, type, content); scrollBottom() } catch (error) { ElMessage.error(error.message || '发送失败') }
}
async function retry(message) {
  try { await chatStore.retry(conversation.value, message) } catch (error) { ElMessage.error(error.message || '重试失败') }
}
async function command(value) {
  try {
    if (value === 'members') {
      showMembers.value = true
      await loadMembers()
    } else if (value === 'pin' || value === 'mute') {
      const key = value === 'pin' ? 'pinned' : 'muted'
      await setChatPreferences(id, { [key]: !conversation.value[key] })
      conversation.value = await getConversation(id)
      await chatStore.loadConversations()
    } else if (value === 'leave') {
      await ElMessageBox.confirm('退出后将无法查看此群消息。群主退出后由下一位成员接任。', '退出群聊', { type: 'warning' })
      await leaveGroup(id)
      delete chatStore.messagesByConversation[id]
      await router.push('/chat')
    } else if (value === 'hide') {
      await hideConversation(id)
      await router.push('/chat')
    } else if (value === 'block') {
      await ElMessageBox.confirm('拉黑后双方将不能继续发送新私信，仍可查看历史。', '确认拉黑', { type: 'warning' })
      await blockChatUser(conversation.value.peerUserId)
      ElMessage.success('已拉黑该用户')
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || '操作失败')
  }
}
async function confirmAction(action) {
  try {
    await action()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || '操作失败')
  }
}
function rename() {
  return confirmAction(async () => {
    const { value } = await ElMessageBox.prompt('输入新的群聊名称', '修改群名', {
      inputValue: conversation.value.groupName,
      inputValidator: (text) => Boolean(text?.trim() && text.trim().length <= 32) || '请输入 1 至 32 字的群名'
    })
    await renameGroup(id, value.trim())
    conversation.value = await getConversation(id)
    await chatStore.loadConversations()
  })
}
function removeMember(member) {
  return confirmAction(async () => {
    await ElMessageBox.confirm(`将 ${member.nickname || '该成员'} 移出群聊？`, '移出成员', { type: 'warning' })
    await removeGroupMember(id, member.id)
    await loadMembers()
  })
}
watch(inviteOpen, (open) => {
  if (!open) return
  invitee.value = null
  confirmAction(async () => {
    friends.value = (await listFriends()).filter((row) => row.status === 'friend').map((row) => row.user)
  })
})
async function sendInvite() {
  if (inviting.value || !invitee.value) return
  inviting.value = true
  try {
    await confirmAction(async () => {
      await inviteToGroup(id, invitee.value)
      inviteOpen.value = false
      ElMessage.success('已发送入群邀请')
    })
  } finally {
    inviting.value = false
  }
}
function report(message) {
  return confirmAction(async () => {
    const { value } = await ElMessageBox.prompt('请说明这条消息的问题', '举报消息', {
      inputType: 'textarea',
      inputValidator: (text) => Boolean(text?.trim() && text.trim().length <= 500) || '请输入 1 至 500 字的举报原因',
      confirmButtonText: '提交举报'
    })
    await reportChatMessage(message.id, { reasonType: '聊天消息违规', reason: value.trim() })
    ElMessage.success('举报已提交，处理结果会在消息中心通知你')
  })
}
watch(() => chatStore.conversationRevision, async () => {
  try {
    conversation.value = await getConversation(id)
    if (active && group.value) await loadMembers()
  } catch (error) {
    if (!active) return
    if (error.code === 403 || error.code === 404) {
      delete chatStore.messagesByConversation[id]
      await router.replace('/chat')
    } else ElMessage.error(error.message || '会话状态刷新失败')
  }
})
watch(() => messages.value.at(-1), (last) => {
  const element = scroller.value
  if (!historyLoading.value && last && element
      && (isMine(last) || element.scrollHeight - element.scrollTop - element.clientHeight < 120)) {
    scrollBottom()
  }
})

onMounted(async () => {
  loading.value = true
  chatStore.activeConversationId = id
  try {
    conversation.value = await getConversation(id)
    if (!active) return
    if (group.value) loadMembers()
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
.room { min-width: 0; min-height: 0; display: flex; flex-direction: column; overflow: hidden; background: #f1f2f1; }
.room > header { flex: none; display: flex; align-items: center; gap: 10px; min-height: 62px; padding: 9px 18px; border-bottom: 1px solid #e2e5e2; background: #f9faf9; }
.room-title { min-width: 0; }
header h1 { margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 16px; }
header span { color: #69736d; font-size: 12px; }
.back { display: none; }
.more { margin-left: auto; }
.chat-flow { flex: 1; min-height: 0; overflow-y: auto; overflow-anchor: none; padding: 10px 22px 20px; }
.chat-flow > .el-button { display: flex; margin: 6px auto; }
.history-end, .timestamp { text-align: center; color: #606b65; font-size: 12px; }
.timestamp { margin: 19px 0 13px; }
.members { display: grid; gap: 12px; max-height: 55vh; overflow: auto; }
.member, .group-management { display: flex; align-items: center; gap: 10px; }
.group-management { margin-top: 16px; }
@media (max-width: 820px) {
  .room > header { padding: 8px 10px; }
  .back { display: inline-flex; }
  .chat-flow { padding: 8px 12px 14px; }
}
</style>
