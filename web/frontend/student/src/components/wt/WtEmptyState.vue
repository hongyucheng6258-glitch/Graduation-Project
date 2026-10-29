<script setup>
// 空 / 加载 / 错误 三态占位（前端-dev 强制要求的状态覆盖）
// loading 显示骨架；empty/error 显示图标(slot) + 标题 + 描述 + 可选操作按钮
import WtButton from './WtButton.vue'

const props = defineProps({
  type:         { type: String, default: 'empty' }, // empty | loading | error
  title:        String,
  description:  String,
  actionLabel:  String,
})
const emit = defineEmits(['action'])
</script>

<template>
  <div class="wt-empty" :class="`is-${type}`" :aria-busy="type === 'loading' || undefined">
    <template v-if="type === 'loading'">
      <div class="wt-skel" aria-hidden="true">
        <span class="wt-skel__thumb"></span>
        <span class="wt-skel__line w1"></span>
        <span class="wt-skel__line w2"></span>
        <span class="wt-skel__line w3"></span>
      </div>
    </template>
    <template v-else>
      <div class="wt-empty__icon">
        <slot name="icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true">
            <circle cx="12" cy="12" r="9" /><path d="M8 15h8M9 9h.01M15 9h.01" />
          </svg>
        </slot>
      </div>
      <p class="wt-empty__title">{{ title || (type === 'error' ? '出错了' : '暂无内容') }}</p>
      <p v-if="description" class="wt-empty__desc">{{ description }}</p>
      <WtButton v-if="actionLabel" type="soft" size="sm" @click="emit('action')">{{ actionLabel }}</WtButton>
    </template>
  </div>
</template>

<style scoped>
.wt-empty { display: flex; flex-direction: column; align-items: center; gap: var(--s-3); padding: var(--s-7) var(--s-4); text-align: center; }
.wt-empty__icon { width: 48px; height: 48px; color: var(--ink-3); }
.wt-empty__icon :deep(svg) { width: 100%; height: 100%; }
.wt-empty__title { margin: 0; font-size: var(--fs-body); font-weight: 600; color: var(--ink); }
.wt-empty__desc { margin: 0; font-size: var(--fs-sm); color: var(--ink-3); max-width: 42ch; }

/* 骨架屏 */
.wt-skel { width: 100%; max-width: 420px; display: flex; flex-direction: column; gap: 12px; align-items: flex-start; }
.wt-skel__thumb { width: 96px; height: 96px; border-radius: var(--r-sm); }
.wt-skel__line { height: 12px; border-radius: 999px; }
.wt-skel__line.w1 { width: 80%; } .wt-skel__line.w2 { width: 60%; } .wt-skel__line.w3 { width: 40%; }
.wt-skel > * { background: linear-gradient(90deg, var(--surface-3) 25%, var(--surface-2) 37%, var(--surface-3) 63%); background-size: 400% 100%; animation: wt-shimmer 1.4s ease infinite; }
@keyframes wt-shimmer { 0% { background-position: 100% 0; } 100% { background-position: -100% 0; } }
@media (prefers-reduced-motion: reduce) { .wt-skel > * { animation: none; } }
</style>
