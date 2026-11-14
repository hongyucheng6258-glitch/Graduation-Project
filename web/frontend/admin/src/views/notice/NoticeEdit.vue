<template>
  <WtPageHeader :title="id || draftId ? '编辑公告' : '新建公告'" subtitle="撰写内容、预览排版并发布到学生端。" />

  <el-card v-loading="loading" class="notice-editor">
    <template #header>{{ id ? '编辑公告' : '新建公告' }}（Markdown）</template>
    <el-alert v-if="draftId" :title="draftMessage" :closable="false" type="info" show-icon />
    <a v-if="savedNoticeId" :href="router.resolve({ name: 'NoticeEdit', params: { id: savedNoticeId } }).href">打开已保存公告</a>
    <el-form v-if="!draftId || draftReady" label-position="top">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" maxlength="64" show-word-limit style="max-width: 560px" />
      </el-form-item>
      <el-form-item v-if="!draftId" label="封面URL">
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
      <el-form-item class="editor-actions">
        <el-button type="primary" :loading="saving" @click="save(false)">保存草稿</el-button>
        <el-button v-if="!draftId" type="success" :loading="saving" @click="save(true)">保存并发布</el-button>
        <el-button @click="router.push(listLocation)">返回</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { useRoute, useRouter } from 'vue-router'
import { noticeListLocation } from '../../layout/adminNavigation.mjs'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { createNotice, updateNotice, publishNotice, noticeDetail } from '../../api/notice'
import { getNoticeDraft, saveNoticeDraft } from '../../api/assistant'

const route = useRoute()
const router = useRouter()
const listLocation = computed(() => noticeListLocation(route.query.page, route.query.status))
const id = route.params.id || null
const draftId = !id && typeof route.query.draft === 'string' ? route.query.draft : ''
const md = new MarkdownIt({ breaks: true })
const form = reactive({ title: '', content: '', cover: '' })
const loading = ref(false)
const saving = ref(false)
const draftReady = ref(false)
const savedNoticeId = ref(null)
const draftMessage = ref('正在加载公告草稿…')

const preview = computed(() => md.render(form.content || ''))

onMounted(async () => {
  if (draftId) {
    loading.value = true
    try {
      const draft = await getNoticeDraft(draftId)
      if (draft.noticeId) {
        savedNoticeId.value = draft.noticeId
        draftMessage.value = '草稿已保存，可打开公告继续编辑。'
      } else if (draft.state === 'EXPIRED') {
        draftMessage.value = '草稿已过期，请回到管理助手重新生成。'
      } else {
        Object.assign(form, { title: draft.title, content: draft.content })
        draftReady.value = true
        draftMessage.value = '请核对文案中的待核对内容。保存后可在公告管理预览并发布。'
      }
    } catch (e) {
      draftMessage.value = e.message || '无法读取公告草稿'
    } finally {
      loading.value = false
    }
    return
  }
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
  if (saving.value || (draftId && !draftReady.value)) return
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('标题与内容必填')
    return
  }
  saving.value = true
  try {
    if (draftId) {
      await saveNoticeDraft(draftId, { title: form.title, content: form.content })
      ElMessage.success('草稿已保存')
      await router.push(listLocation.value)
      return
    }
    let noticeId = id || savedNoticeId.value
    if (noticeId) {
      await updateNotice(noticeId, form)
    } else {
      const n = await createNotice(form)
      noticeId = n.id
      savedNoticeId.value = noticeId
    }
    if (andPublish) {
      await publishNotice(noticeId)
      ElMessage.success('已发布，三端即时可见')
    } else {
      ElMessage.success('草稿已保存')
    }
    router.push(listLocation.value)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.notice-editor { border-color: var(--line); border-radius: var(--r-md); }
.editor-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  width: 100%;
}
.md-preview {
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  padding: 12px;
  min-height: 380px;
  overflow: auto;
  min-width: 0;
  overflow-wrap: anywhere;
  line-height: 1.8;
  background: var(--surface-2);
}
.md-preview :deep(h1), .md-preview :deep(h2), .md-preview :deep(h3) {
  margin: 12px 0 8px;
}
.md-preview :deep(pre) {
  background: var(--surface);
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
}
.editor-actions :deep(.el-form-item__content) { gap: 8px; }
.editor-actions :deep(.el-button + .el-button) { margin-left: 0; }
@media (max-width: 820px) {
  .editor-row { grid-template-columns: minmax(0, 1fr); }
  .md-preview { min-height: 160px; }
}
</style>
