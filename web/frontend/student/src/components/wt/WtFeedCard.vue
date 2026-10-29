<script setup>
import WtTag from './WtTag.vue'

defineProps({
  compact: Boolean,
  title: { type: String, default: '' },
  meta: { type: Array, default: () => [] },
  tag: { type: Object, default: null },
  price: { type: String, default: '' },
  image: { type: String, default: '' },
  actionLabel: { type: String, default: '' },
})

defineEmits(['action'])
</script>

<template>
  <div class="wt-feed-card" :class="{ 'wt-feed-card--compact': compact }">
    <div class="wt-feed-card__thumb">
      <img v-if="image" :src="image" alt="" />
      <slot v-else name="thumb" />
    </div>
    <div class="wt-feed-card__body">
      <div class="wt-feed-card__head">
        <WtTag v-if="tag" :type="tag.type">{{ tag.label }}</WtTag>
        <span v-if="price" class="wt-feed-card__price">{{ price }}</span>
      </div>
      <div class="wt-feed-card__title">{{ title }}</div>
      <div v-if="meta.length" class="wt-feed-card__meta">
        <span v-for="(m, i) in meta" :key="i">{{ m }}</span>
      </div>
    </div>
    <button v-if="actionLabel" class="wt-feed-card__action" type="button" @click="$emit('action')">
      {{ actionLabel }}
    </button>
  </div>
</template>

<style scoped>
.wt-feed-card {
  display: flex;
  gap: var(--s-4);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: var(--s-4);
  box-shadow: var(--shadow-sm);
  transition: transform .25s var(--ease-out), box-shadow .25s var(--ease-out), border-color .2s;
}
.wt-feed-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }

.wt-feed-card--compact { padding: var(--s-3) var(--s-4); }

.wt-feed-card__thumb {
  width: 56px; height: 56px; flex: none;
  border-radius: var(--r-md);
  background: var(--surface-2);
  display: grid; place-items: center;
  overflow: hidden;
  color: var(--ink-3);
}
.wt-feed-card__thumb img { width: 100%; height: 100%; object-fit: cover; }
.wt-feed-card__thumb svg { width: 24px; height: 24px; }

.wt-feed-card__body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.wt-feed-card__head { display: flex; align-items: center; gap: var(--s-2); }
.wt-feed-card__price { font-weight: 700; font-size: var(--fs-sm); color: var(--brand-strong); }
.wt-feed-card__title {
  font-weight: 600; font-size: var(--fs-sm); line-height: 1.4;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.wt-feed-card__meta {
  display: flex; flex-wrap: wrap; gap: 6px;
  font-size: var(--fs-cap); color: var(--ink-3);
}
.wt-feed-card__meta span:not(:last-child)::after { content: ' ·'; }

.wt-feed-card__action {
  flex: none; align-self: center;
  padding: 6px 14px; border: 1px solid var(--line-strong); border-radius: var(--r-pill);
  background: var(--surface); color: var(--brand-strong);
  font: 600 var(--fs-xs)/1 var(--font-sans); cursor: pointer;
  transition: border-color .18s ease, background-color .18s ease;
}
.wt-feed-card__action:hover { border-color: var(--brand-line); background: var(--brand-soft); }
</style>
