<script setup>
// 明/暗主题切换：写 <html data-theme> 并持久化到 localStorage
import { ref, onMounted } from 'vue'
const theme = ref('light')
const emit = defineEmits(['change'])

function apply(t) {
  document.documentElement.setAttribute('data-theme', t)
  theme.value = t
  try { localStorage.setItem('wutong-theme', t) } catch (e) {}
  emit('change', t)
}
onMounted(() => {
  let t = 'light'
  try {
    t = localStorage.getItem('wutong-theme')
      || document.documentElement.getAttribute('data-theme')
      || 'light'
  } catch (e) {}
  apply(t)
})
function toggle() { apply(theme.value === 'dark' ? 'light' : 'dark') }
</script>

<template>
  <button class="wt-theme" @click="toggle" :aria-label="theme === 'dark' ? '切换为浅色' : '切换为深色'">
    <svg v-if="theme === 'dark'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
      <circle cx="12" cy="12" r="4.5" /><path d="M12 2v2M12 20v2M4 12H2M22 12h-2M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1" />
    </svg>
    <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
      <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
    </svg>
  </button>
</template>

<style scoped>
.wt-theme {
  width: 42px; height: 42px; border-radius: var(--r-pill); display: grid; place-items: center;
  color: var(--ink-2); border: 1px solid var(--line); background: var(--surface); cursor: pointer;
  transition: background .2s, color .2s, transform .15s var(--ease-out);
}
.wt-theme:hover { background: var(--surface-2); color: var(--ink); transform: translateY(-1px); }
.wt-theme svg { width: 20px; height: 20px; }
.wt-theme:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
</style>
