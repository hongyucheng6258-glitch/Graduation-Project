<template>
  <div class="row" :class="{ mine }">
    <el-avatar v-if="!mine" :size="34" :src="peerAvatar">{{ peerName?.charAt(0) }}</el-avatar>
    <div class="wrap">
      <div class="bubble" :class="[message.messageType, { failed: message.sendState === 'failed' }]">
        <el-image v-if="message.messageType === 'image'" :src="message.content" fit="contain" :preview-src-list="[message.content]" />
        <span v-else>{{ message.content }}</span>
      </div>
      <div class="meta">
        <span>{{ formatTime(message.createTime) }}</span>
        <span v-if="mine && message.sendState === 'sending'">发送中</span>
        <button v-else-if="mine && message.sendState === 'failed'" @click="$emit('retry', message)">发送失败，点击重试</button>
        <span v-else-if="mine">{{ message.readTime ? '已读' : '已送达' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import dayjs from 'dayjs'

defineProps({
  message: { type: Object, required: true },
  mine: Boolean,
  peerName: String,
  peerAvatar: String
})
defineEmits(['retry'])
const formatTime = (value) => value ? dayjs(value).format('MM-DD HH:mm') : ''
</script>

<style scoped>
.row { display:flex; gap:10px; align-items:flex-end; margin:18px 0; }
.row.mine { justify-content:flex-end; }
.wrap { max-width:min(68%, 620px); }
.bubble { padding:11px 14px; border-radius:4px 18px 18px 18px; background:var(--surface); border:1px solid var(--line); box-shadow:var(--shadow-sm); line-height:1.65; white-space:pre-wrap; word-break:break-word; }
.mine .bubble { color:var(--brand-ink); background:linear-gradient(145deg,var(--brand),var(--brand-strong)); border:0; border-radius:18px 4px 18px 18px; }
.bubble.image { padding:5px; overflow:hidden; background:var(--surface); }
.bubble.image :deep(.el-image) { display:block; max-width:360px; max-height:420px; border-radius:13px; }
.bubble.failed { outline:2px solid color-mix(in srgb,var(--error) 45%,transparent); }
.meta { display:flex; gap:8px; justify-content:flex-start; margin-top:5px; font-size:11px; color:var(--ink-3); }
.mine .meta { justify-content:flex-end; }
.meta button { border:0; padding:0; color:var(--error); background:none; cursor:pointer; }
</style>
