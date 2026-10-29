<script setup>
// 通用卡片容器；hover 提供轻微浮起，flush 用于图文贴边布局
defineProps({
  hover:  { type: Boolean, default: true },
  flush: Boolean,
  pad:    { type: String, default: '' }, // 自定义内边距，如 'var(--s-4)'
})
</script>

<template>
  <div
    class="wt-card"
    :class="{ 'wt-card--hover': hover, 'wt-card--flush': flush }"
    :style="pad ? { padding: pad } : null"
  >
    <slot />
  </div>
</template>

<style scoped>
.wt-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: var(--s-5);
  box-shadow: var(--shadow-sm);
  transition: transform .25s var(--ease-out), box-shadow .25s var(--ease-out), border-color .2s;
}
.wt-card--hover:hover { transform: translateY(-3px); box-shadow: var(--shadow-md); }
.wt-card--flush { padding: 0; overflow: hidden; }
</style>
