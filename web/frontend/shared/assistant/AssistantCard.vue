<template>
  <div class="ai-confirm" :class="{ 'assistant-card--wide': wide }"><slot /></div>
</template>

<script setup>
defineProps({ wide: Boolean })
</script>

<style scoped>
.ai-confirm { width: min(100% - 44px, 350px); box-sizing: border-box; margin: 0 0 12px 42px; padding: 12px 14px; border: 1px solid var(--line); border-radius: var(--r-md); background: var(--surface); overflow-wrap: anywhere; }
.assistant-card--wide { width: min(100% - 44px, 680px); }
.ai-confirm :slotted(p) { margin: 6px 0; }
@media (max-width: 820px) { .ai-confirm { margin-left: 12px; width: min(100% - 12px, 680px); } }
</style>
