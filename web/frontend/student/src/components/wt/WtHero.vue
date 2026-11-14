<script setup>
import { RouterLink } from 'vue-router'

defineProps({
  hello: { type: String, default: '' },
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  photo: { type: String, default: '' },
  photoTo: { type: String, default: '' },
  photoNote: { type: String, default: '' },
  primary: { type: Object, default: null },
  secondary: { type: Object, default: null }
})
</script>

<template>
  <section class="campus-hero">
    <div class="hero-copy">
      <p v-if="hello" class="hello-line">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 2v5m0 10v5M2 12h5m10 0h5m-3-7-3 3m-8 8-3 3m14 0-3-3M8 8 5 5"/><circle cx="12" cy="12" r="3"/></svg>
        {{ hello }}
      </p>
      <h1 v-html="title"></h1>
      <p v-if="description" class="hero-description" v-html="description"></p>
      <div v-if="primary || secondary" class="hero-actions">
        <RouterLink v-if="primary" class="btn primary" :to="primary.to">{{ primary.label }}</RouterLink>
        <RouterLink v-if="secondary" class="btn" :to="secondary.to">{{ secondary.label }}</RouterLink>
      </div>
    </div>

    <component :is="photoTo ? RouterLink : 'div'" v-if="photo" class="hero-photo" :to="photoTo || undefined">
      <img :src="photo" :alt="photoTo && photoNote ? '' : '秋日校园的教学楼和林荫广场'" fetchpriority="high" />
      <span v-if="photoNote" class="photo-note">
        {{ photoNote }} <span v-if="photoTo" aria-hidden="true">→</span>
      </span>
    </component>
  </section>
</template>

<style scoped>
.campus-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(0, .98fr);
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}
.hero-copy { display: flex; flex-direction: column; justify-content: center; align-items: flex-start; padding: 42px; }
.hello-line { display: flex; align-items: center; gap: 7px; margin-bottom: 17px; color: var(--brand); font-size: 12px; overflow-wrap: anywhere; }
.hello-line svg { width: 16px; height: 16px; flex: none; }
.hero-copy h1 { margin: 0; font-size: clamp(34px, 3.3vw, 48px); line-height: 1.18; letter-spacing: -.025em; }
.hero-description { margin: 16px 0 24px; color: var(--ink-3); font-size: 14px; line-height: 1.85; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.btn {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--ink);
  font-size: 14px;
  font-weight: 550;
}
.btn:hover { border-color: var(--brand-line); }
.btn.primary { background: var(--brand); border-color: var(--brand); color: var(--brand-ink); }
.btn.primary:hover { background: var(--brand-strong); }
.hero-photo {
  position: relative;
  min-height: 360px;
  margin: 12px 12px 12px 0;
  border-radius: var(--r-md);
  overflow: hidden;
  background: var(--surface-3);
}
.hero-photo > img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.hero-photo:focus-visible { outline: 2px solid var(--brand); outline-offset: -3px; }
.photo-note {
  position: absolute;
  inset: auto 16px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid rgba(255, 255, 255, .32);
  border-radius: var(--r-sm);
  background: rgba(20, 36, 48, .48);
  backdrop-filter: blur(12px);
  color: #fff;
  font-size: 12px;
}
.hero-photo:hover .photo-note { background: rgba(20, 36, 48, .64); }
@media (min-width: 1600px) { .hero-photo { min-height: 390px; } }
@media (max-width: 1120px) { .hero-copy { padding: 32px; } }
@media (max-width: 900px) {
  .hero-copy { padding: 28px; }
  .hero-copy h1 { font-size: 36px; }
}
@media (max-width: 600px) {
  .campus-hero { grid-template-columns: minmax(0, 1fr); }
  .hero-copy { padding: 28px 24px; }
  .hero-copy h1 { font-size: 34px; }
  .hero-description { margin: 14px 0 20px; }
  .hero-photo { min-height: 240px; margin: 0 12px 12px; }
}
</style>
