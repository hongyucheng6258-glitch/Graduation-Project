<template>
  <div class="chat-workspace">
    <ConversationList embedded />
    <ChatRoomPanel :key="conversationId" :conversation-id="conversationId" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ConversationList from './ConversationList.vue'
import ChatRoomPanel from '../../components/chat/ChatRoomPanel.vue'

const route = useRoute()
const conversationId = computed(() => Number(route.params.conversationId))
</script>

<style scoped>
.chat-workspace { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 20px; }
@media (max-width: 820px) {
  .chat-workspace { grid-template-columns: minmax(0, 1fr); }
}
</style>
