<script setup>
// 快捷入口条（V2）：横向服务项 = 彩色图标块 + 标题 + 描述 + 箭头。
// 对齐「前端UI-原型-HTML版」quick-strip 快捷服务。
import { computed } from 'vue'

const props = defineProps({
  title:   String,
  desc:    String,
  variant: { type: Number, default: 1 }, // 1..6 兼容旧调用
  color:   { type: String, default: '' }, // blue | peach | sage | lavender
})
const emit = defineEmits(['click'])

const COLOR_MAP = { 1: 'blue', 2: 'peach', 3: 'sage', 4: 'lavender', 5: 'blue', 6: 'neutral' }
const colorCls = computed(() => props.color || COLOR_MAP[Math.min(Math.max(props.variant, 1), 6)] || 'blue')
</script>

<template>
  <button type="button" class="quick-service" @click="emit('click')">
    <span class="service-icon" :class="colorCls"><slot name="icon" /></span>
    <span class="quick-service__text">
      <b>{{ title }}</b>
      <small>{{ desc }}</small>
    </span>
    <svg class="quick-service__arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
  </button>
</template>

<style scoped>
.quick-service {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 19px;
  min-width: 0;
  border: 0;
  border-right: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
  font-family: var(--font-sans);
  transition: background-color .18s;
}
.quick-service:hover { background: var(--brand-soft); }
.quick-service:last-child { border-right: 0; }
.quick-service__text { min-width: 0; }
.quick-service b { font-size: 14px; display: block; font-weight: 600; color: var(--ink); }
.quick-service small { display: block; font-size: 11px; color: var(--ink-3); margin-top: 3px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.quick-service__arrow { width: 14px; height: 14px; margin-left: auto; color: var(--ink-3); flex: none; }

.service-icon {
  width: 43px;
  height: 43px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  background: var(--brand-soft);
  color: var(--brand);
}
.service-icon.blue { background: var(--info-soft); color: var(--info-strong); }
.service-icon.peach { background: var(--peach); color: var(--accent-strong); }
.service-icon.sage { background: var(--sage); color: var(--success); }
.service-icon.lavender { background: var(--purple-soft); color: var(--purple-strong); }
.service-icon.neutral { background: var(--surface-3); color: var(--ink-2); }
.service-icon :deep(svg) { width: 21px; height: 21px; }
</style>
