<template>
  <section v-if="cards.length" aria-label="审核确认与回执">
    <AssistantCard v-for="card in cards" :key="card.id" wide>
      <article>
        <header><strong>{{ card.title }}</strong><span>{{ card.decision === 'PASS' ? '审核通过' : '驳回审核' }}</span></header>
        <p v-if="showRecordLink" class="muted">
          <RouterLink :to="{ path: `/audit/${card.kind === 'activity' ? 'activity' : 'idle'}`, query: { record: card.itemId } }">
            查看{{ card.kind === 'activity' ? '活动' : '闲置' }} #{{ card.itemId }} 的审核记录
          </RouterLink>
        </p>
        <p v-if="card.excerpt">内容节选　{{ card.excerpt }}</p>
        <p v-if="card.reason">审核理由　{{ card.reason }}</p>
        <p v-if="card.state === 'APPLIED'" role="status">审核已提交，发布者已收到通知。</p>
        <p v-else-if="card.state === 'EXPIRED'" class="muted">确认卡已过期，请重新准备。</p>
        <template v-else>
          <p class="muted">{{ confirmationText(card) }}</p>
          <el-button :type="card.decision === 'PASS' ? 'primary' : 'danger'" :disabled="busy"
            @click="$emit('confirm', card)">确认执行审核</el-button>
        </template>
      </article>
    </AssistantCard>
  </section>
</template>

<script setup>
import AssistantCard from '../../../../shared/assistant/AssistantCard.vue'

defineProps({ cards: { type: Array, required: true }, busy: Boolean, showRecordLink: Boolean })
defineEmits(['confirm'])

function confirmationText(card) {
  if (card.decision === 'REJECT') return '确认后驳回理由会发给发布者。'
  return card.kind === 'activity'
    ? '确认后活动通过审核，并通知发布者。'
    : '确认后将按物品在架状态展示，并通知发布者。'
}
</script>

<style scoped>
header { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 8px; }
p { margin: 6px 0; }
.muted { color: var(--ink-2); font-size: 13px; }
</style>
