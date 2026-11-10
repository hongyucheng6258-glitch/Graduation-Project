<template>
  <div class="notice-detail" v-loading="loading">
    <RouterLink to="/notice" class="text-btn detail-back">返回校园公告</RouterLink>
    <el-card v-if="notice">
      <h1 class="title">{{ notice.title }}</h1>
      <div class="time">发布时间：{{ formatTime(notice.publishTime) }}</div>
      <el-divider />
      <!-- 公告内容为 Markdown -->
      <div class="md-body" v-html="renderMarkdown(notice.content)" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { noticeDetail } from '../../api/notice'
import { formatTime } from '../../utils/date'
import { renderMarkdown } from '../../utils/markdown'

const route = useRoute()
const notice = ref(null)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    notice.value = await noticeDetail(Number(route.params.id))
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.notice-detail { max-width: 820px; }
.detail-back { display: inline-block; margin-bottom: 16px; }
.notice-detail :deep(.el-card) { border-color: var(--line); border-radius: var(--r-md); }
.title { margin: 0; font-size: var(--fs-h1); overflow-wrap: anywhere; }
.time {
  font-size: 12px;
  color: var(--ink-3);
  margin-top: 8px;
}
.md-body {
  line-height: 1.9;
  font-size: 15px;
  overflow-wrap: anywhere;
}
.md-body :deep(h1), .md-body :deep(h2), .md-body :deep(h3) {
  margin: 16px 0 10px;
}
.md-body :deep(img) {
  max-width: 100%;
}
</style>
