<script setup>
// 分段式标签栏（胶囊）；v-model 为当前选中 value
// options: [{ value, label }]
defineProps({
  modelValue: { type: [String, Number], default: '' },
  options:    { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue', 'change'])
function pick(o) {
  emit('update:modelValue', o.value)
  emit('change', o.value)
}
</script>

<template>
  <div class="wt-tabs" role="tablist">
    <button
      v-for="o in options"
      :key="o.value"
      role="tab"
      :aria-selected="modelValue === o.value"
      class="wt-tab"
      :class="{ active: modelValue === o.value }"
      @click="pick(o)"
    >{{ o.label }}</button>
  </div>
</template>

<style scoped>
.wt-tabs {
  display: inline-flex; gap: 4px;
  background: var(--surface-2); padding: 4px;
  border-radius: var(--r-pill); border: 1px solid var(--line);
}
.wt-tab {
  padding: 8px 16px; border: none; background: transparent; cursor: pointer;
  border-radius: var(--r-pill); font-size: var(--fs-xs); font-weight: 600;
  color: var(--ink-2); font-family: var(--font-sans);
  transition: background .2s var(--ease-out), color .2s var(--ease-out);
}
.wt-tab.active { background: var(--surface); color: var(--brand-strong); box-shadow: var(--shadow-sm); }
.wt-tab:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
</style>
