<template>
  <el-dialog v-model="open" title="发起聊天" width="min(480px, calc(100vw - 32px))" append-to-body @closed="$emit('close')">
    <el-form label-position="top" @submit.prevent="submit">
      <el-radio-group v-model="mode" aria-label="聊天类型">
        <el-radio-button value="private">私信</el-radio-button>
        <el-radio-button value="group">群聊</el-radio-button>
      </el-radio-group>
      <el-form-item v-if="isGroup" label="群聊名称">
        <el-input v-model="name" maxlength="32" placeholder="例如 周五自习小组" />
      </el-form-item>
      <el-form-item :label="isGroup ? '选择同学（至少 2 位）' : '选择同学'">
        <el-select
          ref="picker"
          :key="mode"
          v-model="selected"
          :multiple="isGroup"
          :multiple-limit="49"
          @change="picker?.blur()"
          :loading="searching"
          :no-data-text="searched ? '未找到匹配的同学' : '输入昵称搜索'"
          placeholder="选择好友或搜索昵称"
          remote
          filterable
          :remote-method="search"
        >
          <el-option v-for="user in options" :key="user.id" :value="user.id" :label="user.nickname || '校园同学'">
            <div class="option">
              <el-avatar :size="24" :src="user.avatar">{{ user.nickname?.charAt(0) }}</el-avatar>
              <span>{{ user.nickname || '校园同学' }}</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>
      <div class="actions">
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" native-type="submit" :disabled="!ready" :loading="saving">
          {{ isGroup ? '创建群聊' : '开始私信' }}
        </el-button>
      </div>
    </el-form>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createGroup, searchChatUsers } from '../../api/chat'
import { listFriends } from '../../api/friends'
import { useChatStore } from '../../store/chat'

defineEmits(['close'])
const router = useRouter()
const chatStore = useChatStore()
const open = ref(true)
const mode = ref('private')
const picker = ref(null)
const isGroup = computed(() => mode.value === 'group')
const name = ref('')
const selected = ref(null)
const options = ref([])
const friends = ref([])
const searching = ref(false)
const searched = ref(false)
const saving = ref(false)
let searchVersion = 0
const ready = computed(() => isGroup.value
  ? name.value.trim() && selected.value?.length >= 2
  : Boolean(selected.value))

watch(mode, () => {
  selected.value = isGroup.value ? [] : null
  search('')
})
onMounted(async () => {
  const version = searchVersion
  try {
    friends.value = (await listFriends()).filter((row) => row.status === 'friend').map((row) => row.user)
    if (version === searchVersion) options.value = friends.value
  } catch {
    // Nickname search remains available if the contacts list fails to load.
  }
})
onBeforeUnmount(() => searchVersion++)

async function search(keyword) {
  const version = ++searchVersion
  searching.value = false
  searched.value = Boolean(keyword.trim())
  const picked = isGroup.value ? selected.value : [selected.value]
  const retained = options.value.filter((user) => picked.includes(user.id))
  options.value = retained
  if (!searched.value) {
    options.value = [...new Map([...retained, ...friends.value].map((user) => [user.id, user])).values()]
    return
  }
  searching.value = true
  try {
    const results = await searchChatUsers(keyword.trim())
    if (version !== searchVersion) return
    options.value = [...new Map([...retained, ...results].map((user) => [user.id, user])).values()]
  } catch (error) {
    if (version === searchVersion) ElMessage.error(error.message || '搜索失败')
  } finally {
    if (version === searchVersion) searching.value = false
  }
}

async function submit() {
  if (!ready.value || saving.value) return
  saving.value = true
  try {
    const conversation = isGroup.value
      ? await createGroup({ name: name.value.trim(), memberIds: selected.value })
      : await chatStore.createConversation({ targetUserId: selected.value })
    if (isGroup.value) ElMessage.success('群聊已创建，已向所选同学发出邀请')
    open.value = false
    await router.push(`/chat/${conversation.id}`)
  } catch (error) {
    ElMessage.error(error.message || '创建会话失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.el-radio-group { margin-bottom: 18px; }
.el-select { width: 100%; }
.option { display: flex; align-items: center; gap: 8px; }
.actions { display: flex; justify-content: flex-end; padding-top: 4px; }
</style>
