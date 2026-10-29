<template>
  <!-- 聊天气泡：用户右侧蓝色，AI 左侧白色 + Markdown 渲染 -->
  <div class="bubble-row" :class="{ mine: role === 'user' }">
    <el-avatar v-if="role !== 'user'" :size="34" class="avatar ai-avatar">AI</el-avatar>
    <div class="bubble" :class="role">
      <!-- AI 回复渲染 Markdown；流式中显示打字光标 -->
      <div v-if="role !== 'user'" class="md-body" v-html="html"></div>
      <span v-else class="plain">{{ content }}</span>
      <span v-if="streaming" class="cursor">▍</span>
    </div>
    <el-avatar v-if="role === 'user'" :size="34" class="avatar user-avatar" :src="userAvatar">
      {{ nickname.charAt(0) }}
    </el-avatar>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { renderMarkdown } from '../utils/markdown'

const props = defineProps({
  role: { type: String, default: 'user' }, // user / assistant
  content: { type: String, default: '' },
  streaming: { type: Boolean, default: false },
  userAvatar: { type: String, default: '' },
  nickname: { type: String, default: '我' }
})

const html = computed(() => renderMarkdown(props.content))
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
.ai-avatar {
  background: linear-gradient(140deg, var(--brand), var(--accent));
  color: #fff;
  font-size: 12px;
  flex: none;
}
.user-avatar {
  background: linear-gradient(140deg, var(--brand), var(--brand-strong));
  color: var(--brand-ink);
  font-size: 12px;
  flex: none;
}
.bubble {
  max-width: 78%;
  padding: 12px 16px;
  border-radius: var(--r-md);
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}
.bubble.user {
  background: linear-gradient(140deg, var(--brand), var(--brand-strong));
  color: var(--brand-ink);
  border-top-right-radius: 4px;
}
.bubble.assistant {
  background: var(--surface);
  border: 1px solid var(--line);
  border-top-left-radius: 4px;
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
</style>
