<template>
  <div class="row" :class="{ mine }">
    <el-avatar shape="square" :size="36" :src="sender?.avatar">{{ sender?.nickname?.charAt(0) || '同' }}</el-avatar>
    <div class="wrap">
      <p v-if="group && !mine" class="sender">{{ sender?.nickname || '校园同学' }}</p>
      <div class="bubble" :class="message.messageType">
        <el-image v-if="message.messageType === 'image'" :src="message.content" fit="contain" :preview-src-list="[message.content]" preview-teleported />
        <span v-else>{{ message.content }}</span>
      </div>
      <div v-if="mine && (message.sendState === 'sending' || message.sendState === 'failed' || !group)" class="meta">
        <span v-if="message.sendState === 'sending'">发送中</span>
        <button v-else-if="message.sendState === 'failed'" :title="message.error" @click="$emit('retry', message)">发送失败，点击重试</button>
        <span v-else>{{ message.readTime ? '已读' : '已送达' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  message: { type: Object, required: true },
  mine: Boolean,
  sender: Object,
  group: Boolean
})
defineEmits(['retry'])
</script>

<style scoped>
.row { display: flex; gap: 11px; align-items: flex-start; margin: 12px 0; }
.row > .el-avatar { flex: none; background: #dce8df; color: #33483c; }
.row.mine { flex-direction: row-reverse; }
.wrap { display: flex; flex-direction: column; align-items: flex-start; max-width: min(76%, 620px); min-width: 0; }
.mine .wrap { align-items: flex-end; }
.sender { margin: 0 0 4px; color: #59635d; font-size: 12px; }
.bubble { position: relative; max-width: 100%; padding: 8px 12px; border-radius: 6px; background: #fff; color: #1f2923; line-height: 1.55; white-space: pre-wrap; overflow-wrap: anywhere; }
.mine .bubble.text { background: #a9ed87; }
.bubble.text::before { content: ''; position: absolute; top: 12px; left: -5px; width: 9px; height: 9px; background: inherit; transform: rotate(45deg); }
.mine .bubble.text::before { right: -5px; left: auto; }
.bubble.image { padding: 0; overflow: hidden; }
.bubble.image :deep(.el-image) { display: block; max-width: min(100%, 360px); border-radius: 6px; }
.bubble.image :deep(.el-image__inner) { display: block; height: auto; max-height: 360px; }
.meta { margin-top: 4px; font-size: 11px; color: #626d66; }
.meta button { border: 0; padding: 0; color: #ab322c; background: none; cursor: pointer; }
</style>
