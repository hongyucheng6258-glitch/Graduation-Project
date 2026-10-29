<template>
  <!-- 多图上传组件：先传 /upload/image 拿 URL，再随表单提交（共享约定 #8） -->
  <div class="upload-img">
    <el-upload
      :show-file-list="false"
      :http-request="doUpload"
      accept="image/*"
      :disabled="uploading"
    >
      <el-button :loading="uploading" :disabled="uploading || (max > 1 && modelValue.length >= max)">
        <el-icon><Plus /></el-icon>
        {{ max === 1 && modelValue.length ? '更换头像' : '上传图片' }}（{{ modelValue.length }}/{{ max }}）
      </el-button>
    </el-upload>
    <div class="preview-list">
      <div v-for="(url, i) in modelValue" :key="url" class="preview-item">
        <el-image :src="url" fit="cover" class="preview-img" :preview-src-list="modelValue" />
        <el-icon class="remove" @click="remove(i)"><Close /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Close } from '@element-plus/icons-vue'
import { uploadImage } from '../api/user'

const props = defineProps({
  // 图片URL数组（v-model）
  modelValue: { type: Array, default: () => [] },
  max: { type: Number, default: 9 }
})
const emit = defineEmits(['update:modelValue'])
const uploading = ref(false)

async function doUpload({ file }) {
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过5MB')
    return
  }
  uploading.value = true
  try {
    const res = await uploadImage(file)
    const nextList = props.max === 1
      ? [res.url]
      : [...props.modelValue, res.url]
    emit('update:modelValue', nextList)
  } finally {
    uploading.value = false
  }
}

function remove(index) {
  const list = [...props.modelValue]
  list.splice(index, 1)
  emit('update:modelValue', list)
}
</script>

<style scoped>
.preview-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.preview-item {
  position: relative;
  width: 90px;
  height: 90px;
}
.preview-img {
  width: 90px;
  height: 90px;
  border-radius: 6px;
}
.remove {
  position: absolute;
  top: -6px;
  right: -6px;
  background: var(--error);
  color: #fff;
  border-radius: 50%;
  padding: 2px;
  cursor: pointer;
}
</style>
