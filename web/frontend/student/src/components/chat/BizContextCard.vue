<template>
  <button v-if="conversation?.contextType" class="context" @click="openContext">
    <span class="kind">{{ labels[conversation.contextType] || '校园内容' }}</span>
    <strong>{{ conversation.contextTitle || '查看关联内容' }}</strong>
    <span>打开详情 →</span>
  </button>
</template>

<script setup>
import { useRouter } from 'vue-router'
const props = defineProps({ conversation: Object })
const router = useRouter()
const labels = { idle: '闲置物品', lostfound: '失物招领', activity: '校园活动', post: '校园动态', profile: '用户主页' }
const paths = { idle: '/idle/detail/', lostfound: '/lostfound/detail/', activity: '/activity/detail/', profile: '/user/' }
function openContext() {
  const prefix = paths[props.conversation.contextType]
  if (prefix && props.conversation.contextId) router.push(prefix + props.conversation.contextId)
  else if (props.conversation.contextType === 'post') router.push('/social')
}
</script>

<style scoped>
.context { width:100%; display:grid; grid-template-columns:auto 1fr auto; gap:12px; align-items:center; padding:12px 16px; border:0; border-bottom:1px solid var(--line); background:linear-gradient(90deg,var(--brand-soft),transparent); color:var(--ink-2); text-align:left; cursor:pointer; }
.context strong { color:var(--ink); overflow:hidden; white-space:nowrap; text-overflow:ellipsis; }
.kind { padding:4px 8px; border-radius:999px; background:var(--surface); color:var(--brand-strong); font-size:12px; }
</style>
