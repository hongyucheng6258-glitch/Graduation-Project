<template>
  <ol v-if="items.length" class="idle-guide-results" aria-label="闲置查询结果">
    <li v-for="(item, index) in items" :key="item.itemId" class="idle-guide-card">
      <div class="idle-guide-heading">
        <span v-if="numbered" class="idle-guide-index">{{ index + 1 }}</span>
        <strong>{{ item.title }}</strong>
        <span class="idle-guide-status">{{ statusOf(item.status) }}</span>
      </div>
      <p v-if="item.category || item.expectItem" class="idle-guide-meta">
        <span v-if="item.category">{{ item.category }}</span>
        <span v-if="item.expectItem">想换：{{ item.expectItem }}</span>
      </p>
      <p v-if="item.description" class="idle-guide-description">{{ item.description }}</p>
      <div v-if="item.status !== null" class="idle-guide-actions">
        <RouterLink :to="{ name: 'IdleDetail', params: { id: item.itemId } }">查看详情</RouterLink>
        <button v-if="item.publisherId && !item.owned && item.status === 0" type="button"
          :disabled="contacting === item.itemId" @click="$emit('contact', item)">
          {{ contacting === item.itemId ? '正在打开…' : '联系发布者' }}
        </button>
      </div>
    </li>
  </ol>
</template>

<script setup>
defineProps({ items: { type: Array, required: true }, contacting: { type: Number, default: null }, numbered: Boolean })
defineEmits(['contact'])

function statusOf(status) {
  if (status === null) return '暂不可见'
  return ['在架', '已预约', '已完成', '已下架'][status] || '状态待确认'
}
</script>

<style scoped>
.idle-guide-results {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr));
  gap: 9px;
  list-style: none;
  padding: 0;
  margin: 0 0 14px clamp(12px, 5vw, 42px);
}
.idle-guide-card {
  min-width: 0;
  padding: 11px 13px;
  border: 1px solid var(--line);
  border-radius: var(--r-md, 10px);
  background: var(--surface, #fff);
  overflow-wrap: anywhere;
}
.idle-guide-heading { display: flex; align-items: baseline; gap: 8px; font-size: 14px; }
.idle-guide-heading strong { min-width: 0; }
.idle-guide-index { color: var(--brand-strong); font-variant-numeric: tabular-nums; }
.idle-guide-status { margin-left: auto; color: var(--ink-2); font-size: 12px; white-space: nowrap; }
.idle-guide-meta, .idle-guide-description { margin: 6px 0 0; color: var(--ink-2); font-size: 13px; }
.idle-guide-meta, .idle-guide-actions { display: flex; flex-wrap: wrap; gap: 6px 12px; }
.idle-guide-description { display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.idle-guide-actions { margin-top: 8px; align-items: center; }
.idle-guide-actions a, .idle-guide-actions button {
  padding: 4px 0; border: 0; background: transparent; color: var(--brand-strong); font: inherit;
  font-size: 13px; cursor: pointer;
}
.idle-guide-actions button:disabled { opacity: .65; cursor: wait; }
</style>
