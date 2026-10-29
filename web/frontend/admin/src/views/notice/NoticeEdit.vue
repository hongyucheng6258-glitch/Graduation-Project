<template>
  <WtPageHeader title="编辑公告" subtitle="撰写或更新一条公告" eyebrow="管理后台" />

  <el-card v-loading="loading">
    <template #header>{{ id ? '编辑公告' : '新建公告' }}（Markdown）</template>
    <el-form label-width="80px">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" maxlength="64" show-word-limit style="max-width: 560px" />
      </el-form-item>
      <el-form-item label="封面URL">
        <el-input v-model="form.cover" placeholder="可空，先经学生端/任意上传接口获取URL" style="max-width: 560px" />
      </el-form-item>
      <el-form-item label="内容" required>
        <!-- 左侧 Markdown 编辑，右侧实时预览 -->
        <div class="editor-row">
          <el-input v-model="form.content" type="textarea" :rows="18" class="md-editor"
                    placeholder="支持 Markdown 语法：# 标题、**加粗**、- 列表、```代码块```" />
          <div class="md-preview" v-html="preview" />
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save(false)">保存草稿</el-button>
        <el-button type="success" :loading="saving" @click="save(true)">保存并发布</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { createNotice, updateNotice, publishNotice, noticeDetail } from '../../api/notice'

const route = useRoute()
const router = useRouter()
const id = route.params.id ? Number(route.params.id) : null
const md = new MarkdownIt({ breaks: true })
const form = reactive({ title: '', content: '', cover: '' })
const loading = ref(false)
const saving = ref(false)

const preview = computed(() => md.render(form.content || ''))

onMounted(async () => {
  if (id) {
    loading.value = true
    try {
      const n = await noticeDetail(id)
      Object.assign(form, { title: n.title, content: n.content, cover: n.cover })
    } finally {
      loading.value = false
    }
  }
})

async function save(andPublish) {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('标题与内容必填')
    return
  }
  saving.value = true
  try {
    let noticeId = id
    if (id) {
      await updateNotice(id, form)
    } else {
      const n = await createNotice(form)
      noticeId = n.id
    }
    if (andPublish) {
      await publishNotice(noticeId)
      ElMessage.success('已发布，三端即时可见')
    } else {
      ElMessage.success('草稿已保存')
    }
    router.push('/notice')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.editor-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  width: 100%;
}
.md-preview {
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  padding: 12px;
  min-height: 380px;
  overflow-y: auto;
  line-height: 1.8;
  background: #fafbfc;
}
.md-preview :deep(h1), .md-preview :deep(h2), .md-preview :deep(h3) {
  margin: 12px 0 8px;
}
.md-preview :deep(pre) {
  background: #f6f8fa;
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
}
</style>
