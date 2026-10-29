<script setup>
// 状态标签：brand / accent / success / warning / neutral，可选前置圆点
defineProps({
  type: { type: String, default: 'brand' }, // brand | accent | success | warning | neutral
  dot:  Boolean,
})
</script>

<template>
  <span class="wt-tag" :class="`wt-tag--${type}`">
    <span v-if="dot" class="wt-dot" aria-hidden="true"></span>
    <slot />
  </span>
</template>

<style scoped>
.wt-tag {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 3px 10px; border-radius: var(--r-pill);
  font-size: var(--fs-cap); font-weight: 600; line-height: 1.4; white-space: nowrap;
}
.wt-tag--brand   { background: var(--brand-soft);   color: var(--brand-strong); }
.wt-tag--accent  { background: var(--accent-soft);  color: var(--accent-strong); }
.wt-tag--success { background: var(--success-soft); color: var(--success); }
.wt-tag--warning { background: var(--warning-soft); color: var(--warning); }
.wt-tag--neutral { background: var(--surface-3);    color: var(--ink-2); }
.wt-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
</style>
