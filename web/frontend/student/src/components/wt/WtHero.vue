<script setup>
defineProps({
  hello: { type: String, default: '' },
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  photo: { type: String, default: '' },
  photoNote: { type: String, default: '' },
  primary: { type: Object, default: null },
  secondary: { type: Object, default: null },
  event: { type: Object, default: null }
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
      <RouterLink v-if="event" class="hero-event" :to="event.to">
        <span class="hero-event-date">
          <b>{{ event.day }}</b>
          <small>{{ event.month }}</small>
        </span>
        <span>
          <strong>{{ event.title }}</strong>
          <small>{{ event.sub }}</small>
        </span>
      </RouterLink>
    </div>

    <div v-if="photo" class="hero-photo">
      <img :src="photo" alt="秋日校园的教学楼和林荫广场" fetchpriority="high" />
      <span v-if="photoNote" class="photo-note">{{ photoNote }}</span>
    </div>
  </section>
</template>

<style scoped>
.campus-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, .95fr);
  gap: 32px;
  align-items: center;
  padding: 44px;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: linear-gradient(135deg, var(--brand-soft), var(--paper) 60%, var(--accent-soft) 130%);
}
.hello-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  color: var(--brand-strong);
  font-size: var(--fs-sm);
  font-weight: 600;
  overflow-wrap: anywhere;
}
.hello-line svg { width: 16px; height: 16px; flex: none; }
.hero-copy h1 { margin: 0; color: var(--ink); font-size: var(--fs-display); line-height: 1.25; font-weight: 750; letter-spacing: .3px; }
.hero-description { margin: 16px 0 26px; color: var(--ink-2); font-size: 1.05rem; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 12px; }
.btn {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  min-height: 44px;
  padding: 0 22px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--ink);
  font-size: 1rem;
  font-weight: 550;
  text-decoration: none;
}
.btn:hover { border-color: var(--brand-line); background: var(--brand-soft); color: var(--brand-strong); }
.btn.primary { background: var(--brand); border-color: var(--brand); color: var(--brand-ink); }
.btn.primary:hover { background: var(--brand-strong); border-color: var(--brand-strong); }
.hero-event {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 380px;
  margin-top: 26px;
  padding: 12px 16px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--ink);
  box-shadow: var(--shadow-sm);
  text-decoration: none;
  overflow-wrap: anywhere;
}
.hero-event:hover { border-color: var(--brand-line); }
.hero-event-date {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  flex: 0 0 44px;
  min-height: 44px;
  border-radius: var(--r-sm);
  background: var(--brand-soft);
  color: var(--brand-strong);
  line-height: 1.2;
}
.hero-event-date b { font-size: 1.05rem; }
.hero-event-date small { font-size: var(--fs-cap); }
.hero-event strong { display: block; font-size: var(--fs-sm); }
.hero-event > span:last-child small { display: block; color: var(--ink-2); font-size: var(--fs-xs); }
.hero-photo { position: relative; border-radius: var(--r-md); overflow: hidden; background: var(--surface-2); box-shadow: var(--shadow-lg); }
.hero-photo > img { display: block; width: 100%; height: 320px; object-fit: cover; }
.photo-note {
  position: absolute;
  left: 14px;
  bottom: 14px;
  padding: 6px 12px;
  border-radius: var(--r-pill);
  background: rgba(27, 36, 55, .72);
  color: #fff;
  font-size: var(--fs-xs);
}
@media (max-width: 950px) {
  .campus-hero { padding: 28px; gap: 24px; }
  .hero-copy h1 { font-size: 32px; }
  .hero-description { font-size: var(--fs-body); }
  .btn { padding: 0 16px; font-size: var(--fs-body); }
  .hero-photo > img { height: 270px; }
}
@media (max-width: 700px) {
  .campus-hero { grid-template-columns: minmax(0, 1fr); padding: 20px; gap: 20px; }
  .hero-copy h1 { font-size: 30px; }
  .hero-description { margin: 12px 0 20px; }
  .hero-event { margin-top: 18px; }
  .hero-photo > img { height: clamp(170px, 49vw, 240px); }
}
</style>
