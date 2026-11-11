<template>
  <article class="record-card">
    <header>
      <div>
        <span class="record-id">记录 #{{ id }}</span>
        <h2>{{ title }}</h2>
      </div>
      <el-tag v-if="status" :type="tone" round>{{ status }}</el-tag>
    </header>
    <dl v-if="fields.length">
      <div v-for="[label, value] in fields" :key="label">
        <dt>{{ label }}</dt>
        <dd>{{ value ?? '—' }}</dd>
      </div>
    </dl>
    <p v-if="description" class="record-description">{{ description }}</p>
    <div v-if="$slots.actions" class="record-actions"><slot name="actions" /></div>
  </article>
</template>

<script setup>
defineProps({
  id: { type: [String, Number], required: true },
  title: { type: String, required: true },
  status: { type: String, default: '' },
  tone: { type: String, default: 'info' },
  fields: { type: Array, default: () => [] },
  description: { type: String, default: '' }
})
</script>

<style scoped>
.record-card {
  min-width: 0; max-width: 860px; padding: clamp(12px, 2vw, 24px);
  border: 1px solid var(--line); border-radius: var(--r-md); background: var(--surface);
  color: var(--ink); overflow-wrap: anywhere;
}
header { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: flex-start; gap: 8px; }
header > div { min-width: 0; }
h2 { margin: 2px 0 0; font-size: var(--fs-h3); }
.record-id, dt { color: var(--ink-3); font-size: var(--fs-xs); }
dl { display: flex; flex-wrap: wrap; gap: 8px 24px; margin: 12px 0 0; }
dl > div { min-width: 0; }
dd { margin: 2px 0 0; color: var(--ink-2); font-size: var(--fs-sm); }
.record-description { margin: 12px 0 0; white-space: pre-wrap; color: var(--ink-2); font-size: var(--fs-sm); }
.record-actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
.record-actions :deep(.el-button + .el-button) { margin-left: 0; }
</style>
