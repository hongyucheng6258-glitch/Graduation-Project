<script setup>
defineProps({
  image: { type: String, required: true },
  title: { type: String, required: true },
  description: { type: String, required: true },
  reverse: Boolean
})
</script>

<template>
  <section class="feature" :class="{ reverse }">
    <div class="media"><img :src="image" alt="" loading="lazy" /></div>
    <div class="copy">
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
      <div class="actions"><slot /></div>
    </div>
  </section>
</template>

<style scoped>
.feature {
  display: grid;
  grid-template-columns: minmax(0, 1.04fr) minmax(0, .96fr);
  min-height: 310px;
  margin-bottom: 24px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}
.feature.reverse { grid-template-columns: minmax(0, .96fr) minmax(0, 1.04fr); }
.reverse .media { order: 1; }
.media { position: relative; min-height: 300px; background: var(--surface-3); }
.media img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.copy { display: flex; flex-direction: column; justify-content: center; min-width: 0; padding: 38px; }
.copy h2 { font-size: 28px; line-height: 1.35; letter-spacing: -.02em; }
.copy p { margin: 14px 0 22px; color: var(--ink-3); font-size: 13px; line-height: 1.9; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; }
.actions :deep(.btn) { min-height: 42px; }
@media (max-width: 900px) {
  .feature, .feature.reverse { grid-template-columns: minmax(0, 1fr); }
  .reverse .media { order: 0; }
  .media { min-height: 280px; }
}
@media (max-width: 600px) {
  .media { min-height: 220px; }
  .copy { padding: 26px 22px; }
  .copy h2 { font-size: 24px; }
}
</style>
