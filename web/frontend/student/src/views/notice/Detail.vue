<template>
  <WtPageHeader title="公告详情" subtitle="查看公告完整内容" eyebrow="资讯" />

  <div class="notice-detail" v-loading="loading">
    <el-card v-if="notice">
      <h2 class="title">{{ notice.title }}</h2>
      <div class="time">发布时间：{{ formatTime(notice.publishTime) }}</div>
      <el-divider />
      <!-- 公告内容为 Markdown -->
      <div class="md-body" v-html="renderMarkdown(notice.content)" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
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
.title {
  text-align: center;
}
.time {
  text-align: center;
  font-size: 12px;
  color: var(--ink-3);
  margin-top: 8px;
}
.md-body {
  line-height: 1.9;
  font-size: 15px;
}
.md-body :deep(h1), .md-body :deep(h2), .md-body :deep(h3) {
  margin: 16px 0 10px;
}
.md-body :deep(img) {
  max-width: 100%;
}
</style>
