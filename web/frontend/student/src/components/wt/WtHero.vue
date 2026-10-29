<script setup>
// 门户 Hero（V2）：左文案 + 右校园摄影 + 活动预告浮层。
// 对齐「前端UI-原型-HTML版」campus-hero 构图。
import { useRouter } from 'vue-router'

const props = defineProps({
  hello:      { type: String, default: '' },        // 问候行
  title:      { type: String, default: '' },        // 主标题（可用 <br>）
  description:{ type: String, default: '' },        // 副文案（可用 <br>）
  photo:      { type: String, default: '' },        // 校园摄影图
  photoNote:  { type: String, default: '秋日校园 · 2026' },
  primary:    { type: Object, default: null },      // { label, to }
  secondary:  { type: Object, default: null },      // { label, to }
  event:      { type: Object, default: null },      // { day, month, label, title, sub, to }
  avatarStack:{ type: Array, default: () => ['林', '陈', '周'] },
  personal:   { type: String, default: '从一个兴趣开始，找到你的校园同伴' },
  // 兼容旧调用：greet/title/subtitle 文本渲染（不换行）
  greet:      { type: String, default: '' },
  subtitle:   { type: String, default: '' },
})
const router = useRouter()
function go(to) {
  if (to) router.push(to)
}
</script>

<template>
  <section class="campus-hero">
    <div class="hero-copy">
      <p v-if="hello || greet" class="hello-line">
        <span class="hello-dot" aria-hidden="true"></span>{{ hello || greet }}
      </p>
      <h1 v-html="title"></h1>
      <p v-if="description" class="hero-description" v-html="description"></p>
      <p v-else-if="subtitle" class="hero-description">{{ subtitle }}</p>
      <div v-if="primary || secondary" class="hero-actions">
        <button v-if="primary" type="button" class="btn primary" @click="go(primary.to)">
          {{ primary.label }}
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
        </button>
        <button v-if="secondary" type="button" class="btn soft" @click="go(secondary.to)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/><circle cx="12" cy="12" r="3"/></svg>
          {{ secondary.label }}
        </button>
      </div>
      <div v-if="avatarStack.length" class="hero-personal">
        <span class="avatar-stack">
          <span v-for="(a, i) in avatarStack" :key="i">{{ a }}</span>
        </span>
        <span>{{ personal }}</span>
      </div>
    </div>

    <div v-if="photo" class="hero-photo">
      <img :src="photo" alt="阳光下的大学校园，图书馆前的草坪与步道" loading="eager" />
      <span v-if="photoNote" class="photo-note">{{ photoNote }}</span>
      <a v-if="event" class="hero-event" role="link" tabindex="0" @click="go(event.to)" @keydown.enter="go(event.to)">
        <span class="hero-event-date">
          <small>{{ event.month }}</small>
          <b>{{ event.day }}</b>
        </span>
        <span>
          <span class="hero-event-label">{{ event.label }}</span>
          <strong>{{ event.title }}</strong>
          <small>{{ event.sub }}</small>
        </span>
        <span class="round-arrow" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
        </span>
      </a>
    </div>
  </section>
</template>

<style scoped>
.campus-hero {
  display: grid;
  grid-template-columns: .86fr 1.14fr;
  gap: 43px;
  align-items: center;
  padding: 12px 0 36px;
}
.hero-copy { padding: 5px 0; }
.hello-line {
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-3);
  margin-bottom: 23px;
}
.hello-dot { height: 7px; width: 7px; border-radius: 50%; background: var(--accent); flex: none; }
.hero-copy h1 {
  font-size: 48px;
  line-height: 1.35;
  font-weight: 780;
  letter-spacing: -1.9px;
  color: var(--ink);
  margin: 0;
}
.hero-description {
  font-size: 14px;
  line-height: 1.95;
  margin-top: 22px;
  color: var(--ink-3);
}
.hero-actions { display: flex; gap: 12px; margin-top: 26px; }
.hero-actions .btn { min-height: 46px; border-radius: 10px; }
.hero-personal {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
  color: var(--ink-3);
  margin-top: 32px;
}
.btn {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  gap: 9px;
  min-height: 44px;
  padding: 10px 20px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  color: var(--ink);
  font-size: 14px;
  font-weight: 550;
  white-space: nowrap;
  line-height: 1.6;
  cursor: pointer;
  transition: border-color .18s, background-color .18s, color .18s;
}
.btn:hover { border-color: var(--brand-line); background: var(--brand-soft); }
.btn.primary { background: var(--brand); border-color: var(--brand); color: #fff; }
.btn.primary:hover { background: var(--brand-strong); border-color: var(--brand-strong); }
.btn.soft { background: var(--brand-soft); border-color: transparent; color: var(--brand); }
.btn.soft:hover { background: var(--brand-line); color: var(--brand-strong); }
.btn svg { width: 18px; height: 18px; }

.avatar-stack {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  padding-left: 4px;
}
.avatar-stack span {
  width: 25px;
  height: 25px;
  border: 2px solid var(--surface);
  background: var(--peach);
  color: var(--ink-2);
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 9px;
  font-weight: 600;
  margin-left: -5px;
}
.avatar-stack span:nth-child(2) { background: var(--info-soft); color: var(--info-strong); }
.avatar-stack span:nth-child(3) { background: var(--purple-soft); color: var(--purple-strong); }

.hero-photo {
  position: relative;
  height: 375px;
  border-radius: 24px;
  overflow: hidden;
  background: var(--surface-2);
}
.hero-photo > img { width: 100%; height: 100%; object-fit: cover; object-position: 60% 50%; }
.photo-note {
  position: absolute;
  top: 20px;
  right: 20px;
  border: 1px solid oklch(100% 0 0 / .5);
  background: oklch(100% 0 0 / .85);
  backdrop-filter: blur(5px);
  color: var(--ink-2);
  border-radius: 50px;
  padding: 5px 13px;
  font-size: 11px;
}
.hero-event {
  position: absolute;
  left: 22px;
  bottom: 21px;
  right: 22px;
  background: oklch(100% 0 0 / .94);
  color: var(--ink);
  border-radius: 14px;
  display: flex;
  align-items: center;
  padding: 15px 17px;
  gap: 15px;
  backdrop-filter: blur(10px);
  box-shadow: 0 5px 25px oklch(20% 0.04 265 / .07);
  cursor: pointer;
  text-decoration: none;
}
.hero-event:hover { background: #fff; }
.hero-event-date {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: var(--brand-soft);
  border-radius: 9px;
  min-width: 50px;
  padding: 5px 7px;
  color: var(--brand);
}
.hero-event-date small { font-size: 9px; }
.hero-event-date b { font-size: 27px; line-height: 1.2; font-weight: 650; }
.hero-event strong { display: block; font-size: 15px; line-height: 1.7; color: var(--ink); font-weight: 600; }
.hero-event small:not(.hero-event-date small) { display: block; font-size: 10px; color: var(--ink-3); }
.hero-event-label { color: var(--ink-3); font-size: 10px; }
.round-arrow {
  margin-left: auto;
  background: var(--brand);
  color: #fff;
  width: 35px;
  height: 35px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.round-arrow svg { width: 16px; height: 16px; }

@media (max-width: 1250px) {
  .hero-copy h1 { font-size: 42px; }
  .campus-hero { gap: 30px; grid-template-columns: .9fr 1.1fr; }
  .hero-photo { height: 354px; }
  .hero-personal { font-size: 10px; }
}
@media (max-width: 1080px) {
  .hero-copy h1 { font-size: 38px; }
  .hero-description { font-size: 13px; }
  .hello-line { font-size: 11px; }
  .hero-personal { display: none; }
  .hero-photo { height: 322px; }
  .hero-event-label { font-size: 9px; }
  .hero-event strong { font-size: 12px; }
  .hero-event .round-arrow { height: 28px; width: 28px; }
}
@media (max-width: 900px) {
  .hero-copy h1 { font-size: 34px; }
  .hero-description { font-size: 12px; }
  .hero-actions .btn { padding: 9px 12px; font-size: 12px; min-height: 41px; }
  .hero-event-date { min-width: 38px; }
  .hero-event-date b { font-size: 22px; }
  .hero-event .round-arrow { display: none; }
  .campus-hero { gap: 22px; }
}
@media (max-width: 760px) {
  .campus-hero { grid-template-columns: 1fr; padding-top: 7px; gap: 25px; padding-bottom: 26px; }
  .hero-copy { padding: 0 2px; }
  .hello-line { font-size: 12px; margin-bottom: 16px; }
  .hero-copy h1 { font-size: 39px; letter-spacing: -1px; line-height: 1.4; }
  .hero-description { font-size: 13px; margin-top: 17px; }
  .hero-actions { margin-top: 23px; gap: 12px; }
  .hero-actions .btn { min-height: 43px; padding: 10px 15px; font-size: 13px; }
  .hero-photo { height: 255px; border-radius: 19px; }
  .hero-photo > img { object-position: center 47%; }
  .hero-event { left: 14px; right: 14px; bottom: 14px; padding: 12px; border-radius: 12px; gap: 12px; }
  .hero-event-date { min-width: 43px; }
  .hero-event-date b { font-size: 24px; }
  .hero-event strong { font-size: 12px; }
  .hero-event .round-arrow { display: grid; min-width: 27px; width: 27px; }
  .hero-event small:not(.hero-event-date small) { font-size: 9px; }
  .photo-note { top: 14px; right: 14px; padding: 4px 10px; font-size: 9px; }
}
@media (max-width: 370px) {
  .hero-copy h1 { font-size: 36px; }
  .hero-event { gap: 9px; }
  .hero-event strong { font-size: 11px; }
  .hero-event .round-arrow { display: none; }
}
</style>
