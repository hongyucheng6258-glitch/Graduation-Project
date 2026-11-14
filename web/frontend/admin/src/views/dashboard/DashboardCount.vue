<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({ value: { type: [Number, String], required: true } })
const displayed = ref(props.value)
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
let frame

function finish() {
  cancelAnimationFrame(frame)
  displayed.value = props.value
}

watch(() => props.value, target => {
  cancelAnimationFrame(frame)
  const start = displayed.value
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(target) || reducedMotion.matches || start === target) {
    displayed.value = target
    return
  }

  const startedAt = performance.now()
  function tick(now) {
    const progress = Math.min(1, Math.max(0, (now - startedAt) / 180))
    displayed.value = Math.round(start + (target - start) * (1 - (1 - progress) ** 3))
    if (progress < 1) frame = requestAnimationFrame(tick)
  }
  frame = requestAnimationFrame(tick)
})

reducedMotion.addEventListener('change', finish)
onBeforeUnmount(() => {
  reducedMotion.removeEventListener('change', finish)
  cancelAnimationFrame(frame)
})
</script>

<template>
  <b class="dashboard-count"><span aria-hidden="true">{{ displayed }}</span><span class="sr-only">{{ value }}</span></b>
</template>

<style scoped>
.dashboard-count { position: relative; font-variant-numeric: tabular-nums; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
</style>
