<template>
  <div class="composer">
    <el-upload :show-file-list="false" :http-request="upload" accept="image/*" :disabled="uploading">
      <el-button circle :loading="uploading" aria-label="发送图片"><el-icon><Picture /></el-icon></el-button>
    </el-upload>
    <el-input v-model="text" type="textarea" :autosize="{ minRows: 1, maxRows: 5 }" maxlength="2000" placeholder="输入消息…" aria-label="输入消息" title="Enter 发送，Shift+Enter 换行" @keydown.enter.exact="submitFromKeyboard" />
    <el-button type="primary" :disabled="!text.trim()" @click="submit">发送</el-button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Picture } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uploadImage } from '../../api/user'

const emit = defineEmits(['send'])
const text = ref('')
const uploading = ref(false)
function submitFromKeyboard(event) {
  if (event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  submit()
}
function submit() {
  const value = text.value.trim()
  if (!value) return
  emit('send', 'text', value)
  text.value = ''
}
async function upload({ file }) {
  if (file.size > 3 * 1024 * 1024) return ElMessage.warning('图片不能超过3MB')
  uploading.value = true
  try {
    const result = await uploadImage(file)
    emit('send', 'image', { url: result.url, resourceId: result.resourceId })
  } catch (error) {
    ElMessage.error(error.message || '图片上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.composer { display:grid; grid-template-columns:auto 1fr auto; gap:10px; align-items:end; padding:14px 16px; border-top:1px solid var(--line); background:#f9faf9; }
.composer :deep(.el-textarea__inner) { border-radius:6px; padding:11px 14px; box-shadow:none; resize:none; border:1px solid #e3e8e4; }
.composer > .el-button { background:#148343; border-color:#148343; color:white; }
.composer > .el-button.is-disabled { opacity:.48; }
</style>
