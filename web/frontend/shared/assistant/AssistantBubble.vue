<template>
  <div class="bubble-row" :class="{ mine: role === 'user' }">
    <span v-if="role !== 'user'" class="avatar ai-avatar" aria-hidden="true">AI</span>
    <div class="bubble" :class="role">
      <!-- AI 回复渲染 Markdown；流式中显示打字光标 -->
      <div v-if="role !== 'user' && renderMarkdown" class="md-body" v-html="html"></div>
      <span v-else class="plain">{{ content }}</span>
      <span v-if="streaming" class="cursor">▍</span>
    </div>
    <span v-if="role === 'user'" class="avatar user-avatar" aria-hidden="true">
      <img v-if="userAvatar && !avatarFailed" :src="userAvatar" alt="" @error="avatarFailed = true" />
      <template v-else>{{ nickname.charAt(0) }}</template>
    </span>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  role: { type: String, default: 'user' }, // user / assistant
  content: { type: String, default: '' },
  streaming: { type: Boolean, default: false },
  userAvatar: { type: String, default: '' },
  nickname: { type: String, default: '我' },
  renderMarkdown: { type: Function, default: null }
})

const html = computed(() => props.renderMarkdown?.(props.content) || '')
const avatarFailed = ref(false)
watch(() => props.userAvatar, () => { avatarFailed.value = false })
</script>

<style scoped>
.bubble-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 12px 0;
}
.bubble-row.mine {
  flex-direction: row-reverse;
}
.avatar { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 50%; overflow: hidden; }
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.ai-avatar {
  background: var(--brand-soft);
  color: var(--brand-strong);
  font-size: 12px;
  flex: none;
}
.user-avatar {
  background: var(--surface-2);
  color: var(--ink-2);
  font-size: 12px;
  flex: none;
}
.bubble {
  min-width: 0;
  padding: 7px 0;
  border-radius: var(--r-md);
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  overflow-wrap: anywhere;
}
.bubble.user {
  max-width: min(84%, 590px);
  padding: 9px 14px;
  background: var(--brand-soft);
  color: var(--ink);
  border-radius: 16px;
}
.bubble.assistant {
  max-width: calc(100% - 44px);
  color: var(--ink);
}
.plain {
  white-space: pre-wrap;
}
.cursor {
  animation: blink 1s infinite;
  color: var(--brand);
}
@keyframes blink {
  50% { opacity: 0; }
}
/* Markdown 内容排版 */
.md-body :deep(pre) {
  background: var(--surface-2);
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
}
.md-body :deep(code) {
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
}
.md-body :deep(p) {
  margin: 6px 0;
}
.md-body :deep(ul), .md-body :deep(ol) {
  padding-left: 20px;
}
.md-body :deep(img) { max-width: 100%; }
@media (prefers-reduced-motion: reduce) { .cursor { animation: none; } }
</style>
