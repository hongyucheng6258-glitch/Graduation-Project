<template>
  <div v-if="sources.length" class="ai-sources" aria-label="检索到的资料">
    <span>资料来源</span>
    <button v-for="source in sources" :key="`${source.documentId}-${source.page}`" type="button"
      @click="$emit('open', source)">{{ source.fileName }} · 第 {{ source.page }} 页</button>
  </div>
</template>

<script setup>
defineProps({ sources: { type: Array, default: () => [] } })
defineEmits(['open'])
</script>

<style scoped>
.ai-sources { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 9px; margin: 0 0 12px 42px; font-size: 12px; }
.ai-sources button { min-width: 0; max-width: 100%; border: 1px solid var(--line); border-radius: 6px; padding: 4px 8px; background: var(--surface); color: var(--brand-strong); cursor: pointer; overflow-wrap: anywhere; }
.ai-sources button:focus-visible { outline: 2px solid var(--brand); }
@media (max-width: 820px) { .ai-sources { margin-left: 12px; } }
</style>
