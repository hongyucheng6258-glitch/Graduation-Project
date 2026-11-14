<template>
  <div>
    <WtPageHeader title="通讯录" subtitle="找同学、处理好友申请，从好友列表继续聊天。" />
    <section class="directory" v-loading="loading">
      <el-tabs v-model="tab">
        <el-tab-pane :label="`好友 (${friends.length})`" name="contacts" />
        <el-tab-pane :label="`好友申请 (${incoming.length})`" name="requests" />
        <el-tab-pane :label="`群聊邀请 (${groupInvites.length})`" name="groups" />
        <el-tab-pane label="找同学" name="search" />
      </el-tabs>
      <form v-if="tab === 'search'" class="search-form" @submit.prevent="search">
        <el-input v-model="keyword" maxlength="32" placeholder="输入同学昵称" aria-label="同学昵称" clearable />
        <el-button native-type="submit" type="primary" :disabled="!keyword.trim()" :loading="searching">搜索</el-button>
      </form>
      <template v-if="tab === 'groups'">
        <div v-for="invite in groupInvites" :key="invite.conversationId" class="contact">
          <strong>{{ invite.groupName }}</strong>
          <div class="actions">
            <el-button size="small" type="primary" :loading="busy.has(invite.conversationId)" @click="decide(invite, true)">加入群聊</el-button>
            <el-button size="small" text :disabled="busy.has(invite.conversationId)" @click="decide(invite, false)">拒绝</el-button>
          </div>
        </div>
      </template>
      <div v-for="row in visible" :key="row.user.id" class="contact">
        <RouterLink class="person" :to="`/user/${row.user.id}`">
          <el-avatar shape="square" :size="42" :src="row.user.avatar">{{ row.user.nickname?.[0] || '同' }}</el-avatar>
          <div>
            <strong>{{ row.user.nickname || '校园同学' }}</strong>
            <small v-if="tab === 'requests'">{{ row.greeting || (row.status === 'incoming' ? '申请添加你为好友' : '等待对方处理') }}</small>
          </div>
        </RouterLink>
        <div class="actions">
          <template v-if="row.status === 'friend'">
            <el-button size="small" type="primary" plain @click="act(row, () => startChat(router, userStore, row.user.id), false)">私信</el-button>
            <el-button v-if="tab === 'contacts'" size="small" text @click="remove(row)">移除</el-button>
          </template>
          <template v-else-if="row.status === 'incoming'">
            <el-button size="small" type="primary" :loading="busy.has(row.user.id)" @click="act(row, () => respondToFriend(row.user.id, true))">同意</el-button>
            <el-button size="small" text :disabled="busy.has(row.user.id)" @click="act(row, () => respondToFriend(row.user.id, false))">拒绝</el-button>
          </template>
          <el-button v-else-if="row.status === 'outgoing'" size="small" text :disabled="busy.has(row.user.id)" @click="act(row, () => removeFriend(row.user.id))">撤回申请</el-button>
          <el-button v-else size="small" type="primary" :loading="busy.has(row.user.id)" @click="apply(row)">加好友</el-button>
        </div>
      </div>
      <EmptyBox v-if="!loading && !(tab === 'groups' ? groupInvites.length : visible.length)" :description="emptyText" />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listFriends, removeFriend, requestFriend, respondToFriend } from '../../api/friends'
import { searchChatUsers, listGroupInvitations, respondToGroupInvite } from '../../api/chat'
import { useUserStore } from '../../store/user'
import { startChat } from '../../utils/startChat'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import EmptyBox from '../../components/EmptyBox.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const requestedTab = (value) => ['requests', 'groups'].includes(value) ? value : 'contacts'
const tab = ref(requestedTab(route.query.tab))
const groupInvites = ref([])
const loading = ref(false)
const searching = ref(false)
const searched = ref(false)
const keyword = ref('')
const people = ref([])
const contacts = ref([])
const busy = ref(new Set())
let loadVersion = 0

const friends = computed(() => contacts.value.filter((row) => row.status === 'friend'))
const incoming = computed(() => contacts.value.filter((row) => row.status === 'incoming'))
const visible = computed(() => {
  if (tab.value === 'groups') return []
  if (tab.value === 'contacts') return friends.value
  if (tab.value === 'requests') return [...incoming.value, ...contacts.value.filter((row) => row.status === 'outgoing')]
  const byId = new Map(contacts.value.map((row) => [row.user.id, row.status]))
  return people.value.map((user) => ({ user, status: byId.get(user.id) || 'none' }))
})
const emptyText = computed(() => {
  if (tab.value === 'contacts') return '还没有好友，可以去「找同学」发送申请'
  if (tab.value === 'requests') return '暂无待处理的好友申请'
  if (tab.value === 'groups') return '暂无待确认的群聊邀请'
  return searched.value ? '没有找到匹配的同学' : '输入昵称查找同学'
})

watch(() => route.query.tab, (value) => {
  tab.value = requestedTab(value)
  load()
})
onMounted(load)

async function load() {
  const version = ++loadVersion
  loading.value = true
  try {
    const [result, invites] = await Promise.all([listFriends(), listGroupInvitations()])
    if (version === loadVersion) {
      contacts.value = result
      groupInvites.value = invites
    }
  } catch (error) {
    if (version === loadVersion) ElMessage.error(error.message || '通讯录加载失败')
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

async function decide(invite, accept) {
  const id = invite.conversationId
  if (busy.value.has(id)) return
  busy.value.add(id)
  try {
    await respondToGroupInvite(id, accept)
    if (accept) await router.push(`/chat/${id}`)
    else await load()
  } catch (error) {
    ElMessage.error(error.message || '无法处理邀请')
    await load()
  } finally {
    busy.value.delete(id)
  }
}

async function search() {
  if (searching.value || !keyword.value.trim()) return
  searching.value = true
  try {
    people.value = await searchChatUsers(keyword.value.trim())
    searched.value = true
    await load()
  } catch (error) {
    ElMessage.error(error.message || '搜索失败')
  } finally {
    searching.value = false
  }
}

async function act(row, operation, refresh = true) {
  const id = row.user.id
  if (busy.value.has(id)) return
  busy.value.add(id)
  try {
    await operation()
    if (refresh) await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || '操作失败')
  } finally {
    busy.value.delete(id)
  }
}

function apply(row) {
  return act(row, async () => {
    const { value } = await ElMessageBox.prompt('可以写一句话介绍自己', '添加好友', {
      inputPlaceholder: '验证消息（选填）',
      inputValidator: (value) => (value?.length || 0) <= 80 || '最多输入 80 个字',
      confirmButtonText: '发送申请',
      cancelButtonText: '取消'
    })
    const result = await requestFriend(row.user.id, value || '')
    if (result.status === 'outgoing') ElMessage.success('好友申请已发送')
  })
}

function remove(row) {
  return act(row, async () => {
    await ElMessageBox.confirm(`将 ${row.user.nickname || '这位同学'} 从通讯录移除？`, '移除好友', { type: 'warning' })
    await removeFriend(row.user.id)
  })
}
</script>

<style scoped>
.directory { max-width: 850px; padding: 12px 22px 22px; border: 1px solid var(--line); border-radius: var(--r-md); background: var(--surface); }
.search-form, .contact, .person, .actions { display: flex; align-items: center; gap: 10px; }
.search-form { margin-bottom: 8px; max-width: 420px; }
.contact { justify-content: space-between; padding: 14px 0; border-bottom: 1px solid var(--line); }
.person { min-width: 0; color: var(--ink); text-decoration: none; overflow-wrap: anywhere; }
.person:hover strong { text-decoration: underline; }
.person :deep(.el-avatar) { flex: none; }
.contact small { display: block; margin-top: 4px; color: var(--ink-2); }
.actions { flex: none; gap: 2px; }
.actions :deep(.el-button + .el-button) { margin-left: 0; }
@media (max-width: 600px) {
  .directory { padding: 8px 12px 14px; }
}
</style>
