<template>
  <div class="chat-workspace open-room">
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
