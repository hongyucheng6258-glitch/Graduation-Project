<script setup>
// 表单输入：label 在上方、错误在下方（前端-dev 表单规范）
// 支持 v-model；error 非空时显示红色描边与提示
const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  label:      String,
  placeholder: String,
  type:       { type: String, default: 'text' },
  error:      String,
  disabled:   Boolean,
  size:       { type: String, default: 'md' }, // md | sm
  id:         { type: String, default: () => 'wt-in-' + Math.random().toString(36).slice(2, 8) },
})
const emit = defineEmits(['update:modelValue'])
</script>

<template>
  <div class="wt-field" :class="`wt-field--${size}`">
    <label v-if="label" :for="id" class="wt-label">{{ label }}</label>
    <input
      :id="id"
      class="wt-input"
      :class="{ 'has-error': !!error }"
      :type="type"
      :placeholder="placeholder"
      :disabled="disabled"
      :value="modelValue"
      :aria-invalid="!!error"
      @input="emit('update:modelValue', $event.target.value)"
    />
    <p v-if="error" class="wt-err" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
.wt-field { display: flex; flex-direction: column; gap: 6px; }
.wt-label { font-size: var(--fs-sm); font-weight: 600; color: var(--ink-2); }
.wt-input {
  width: 100%; padding: var(--s-3);
  border-radius: var(--r-sm); border: 1px solid var(--line-strong);
  background: var(--surface-2); color: var(--ink);
  font-family: var(--font-sans); font-size: var(--fs-sm);
  transition: border-color .2s, box-shadow .2s, background .2s;
}
.wt-field--sm .wt-input { padding: 8px 12px; font-size: var(--fs-xs); }
.wt-input::placeholder { color: var(--ink-3); }
.wt-input:focus { outline: none; border-color: var(--brand); background: var(--surface); box-shadow: 0 0 0 4px var(--brand-soft); }
.wt-input:disabled { opacity: .6; cursor: not-allowed; }
.wt-input.has-error { border-color: var(--error); box-shadow: 0 0 0 4px var(--error-soft); }
.wt-err { margin: 0; font-size: var(--fs-xs); color: var(--error); }
</style>
