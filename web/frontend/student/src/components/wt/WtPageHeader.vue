<script setup>
// 统一页头：每个功能页顶部一致的标题区（eyebrow 小标签 + 标题 + 副文案 + 右侧操作区）
// 用法：<WtPageHeader title="闲置物品" subtitle="校园内的二手好物流转" eyebrow="校园服务">
//         <WtButton type="primary">发布</WtButton>
//       </WtPageHeader>
defineProps({
  title:    { type: String, required: true },
  subtitle: { type: String, default: '' },
  eyebrow:  { type: String, default: '' }, // 标题上方的小标签，如「校园服务」
})
</script>

<template>
  <header class="wt-page-header">
    <div class="wt-ph-text">
      <span v-if="eyebrow" class="wt-ph-eyebrow">{{ eyebrow }}</span>
      <h1 class="wt-ph-title">{{ title }}</h1>
      <p v-if="subtitle" class="wt-ph-sub">{{ subtitle }}</p>
    </div>
    <div class="wt-ph-actions">
      <slot />
    </div>
  </header>
</template>

<style scoped>
.wt-page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--s-4);
  flex-wrap: wrap;
  padding-bottom: var(--s-4);
  margin-bottom: var(--s-5);
  border-bottom: 1px solid var(--line);
}
.wt-ph-text { min-width: 0; }
.wt-ph-eyebrow {
  display: inline-block;
  font-size: var(--fs-cap);
  font-weight: 700;
  letter-spacing: .1em;
  text-transform: uppercase;
  color: var(--brand);
  margin-bottom: var(--s-2);
}
.wt-ph-title {
  font-family: var(--font-display);
  font-size: var(--fs-h1);
  font-weight: 600;
  color: var(--ink);
  margin: 0;
  line-height: 1.15;
}
.wt-ph-sub {
  margin: var(--s-2) 0 0;
  color: var(--ink-3);
  font-size: var(--fs-sm);
}
.wt-ph-actions {
  display: flex;
  align-items: center;
  gap: var(--s-3);
  flex-wrap: wrap;
}
@media (max-width: 640px) {
  .wt-page-header { flex-direction: column; align-items: flex-start; }
  .wt-ph-actions { width: 100%; }
}
</style>
