<script setup>
// 品牌按钮：primary(校园蓝) / accent(杏色) / soft / ghost
const props = defineProps({
  type:   { type: String, default: 'primary' }, // primary | accent | soft | ghost
  size:   { type: String, default: 'md' },       // md | sm
  block:  Boolean,
  disabled: Boolean,
  loading: Boolean,
})
const emit = defineEmits(['click'])
</script>

<template>
  <button
    class="wt-btn"
    :class="[`wt-btn--${type}`, `wt-btn--${size}`, { 'is-block': block, 'is-disabled': disabled || loading }]"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
    @click="(e) => !disabled && !loading && emit('click', e)"
  >
    <span v-if="loading" class="wt-spin" aria-hidden="true"></span>
    <slot />
  </button>
</template>

<style scoped>
.wt-btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 8px;
  min-height: 44px; padding: 0 22px; border-radius: var(--r-pill);
  font-family: var(--font-sans); font-size: var(--fs-sm); font-weight: 600;
  cursor: pointer; border: 1px solid transparent; color: var(--ink);
  transition: transform .15s var(--ease-out), box-shadow .2s var(--ease-out),
              background .2s, color .2s, border-color .2s;
}
.wt-btn--sm { min-height: 36px; padding: 0 16px; font-size: var(--fs-xs); }

.wt-btn--primary { background: linear-gradient(140deg, var(--brand), var(--brand-strong)); color: var(--brand-ink); box-shadow: var(--shadow-sm); }
.wt-btn--primary:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }
.wt-btn--accent  { background: linear-gradient(140deg, var(--accent), var(--accent-strong)); color: var(--accent-ink); box-shadow: var(--shadow-sm); }
.wt-btn--accent:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }
.wt-btn--soft { background: var(--brand-soft); color: var(--brand-strong); }
.wt-btn--soft:hover { background: var(--brand-line); }
.wt-btn--ghost { background: var(--surface); border-color: var(--line-strong); color: var(--ink); }
.wt-btn--ghost:hover { background: var(--surface-2); border-color: var(--ink-3); }

.wt-btn.is-block { width: 100%; }
.wt-btn.is-disabled { opacity: .5; cursor: not-allowed; pointer-events: none; }
.wt-btn:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }

.wt-spin {
  width: 16px; height: 16px; border: 2px solid currentColor;
  border-right-color: transparent; border-radius: 50%;
  animation: wt-spin .7s linear infinite;
}
@keyframes wt-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) {
  .wt-btn--primary:hover, .wt-btn--accent:hover { transform: none; }
  .wt-spin { animation: none; }
}
</style>
