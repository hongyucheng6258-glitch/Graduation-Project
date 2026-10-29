<script setup>
// 头像：有 src 显示图片，否则取名字末字作首字母（中文名取末字更自然）
import { computed } from 'vue'
const props = defineProps({
  name: { type: String, default: '' },
  src:  { type: String, default: '' },
  size: { type: [String, Number], default: 'md' }, // sm | md | lg | 数字(px)
})
const map = { sm: 32, md: 40, lg: 56 }
const px = computed(() => (typeof props.size === 'number' ? props.size : (map[props.size] || 40)))
const initial = computed(() => (props.name ? props.name.slice(-1) : ''))
</script>

<template>
  <span
    class="wt-avatar"
    :style="{ width: px + 'px', height: px + 'px', fontSize: px * 0.4 + 'px' }"
    role="img"
    :aria-label="name || '头像'"
  >
    <img v-if="src" :src="src" alt="" />
    <span v-else class="wt-avatar__txt">{{ initial }}</span>
  </span>
</template>

<style scoped>
.wt-avatar {
  display: inline-grid; place-items: center; flex: none;
  border-radius: 50%; overflow: hidden;
  background: linear-gradient(140deg, var(--brand), var(--brand-strong));
  color: var(--brand-ink); font-weight: 700; line-height: 1;
}
.wt-avatar img { width: 100%; height: 100%; object-fit: cover; }
.wt-avatar__txt { user-select: none; }
</style>
