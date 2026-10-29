<template>
  <WtPageHeader title="我要提问" subtitle="描述清楚，更容易得到帮助" eyebrow="校园服务" />

  <div class="publish">
    <el-card>
      <template #header><h3>发布问题</h3></template>
      <el-form :model="form" label-width="90px" style="max-width: 640px">
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="选择分类" style="width: 200px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="64" show-word-limit placeholder="一句话说清问题，如：高数不定积分有哪些常用技巧？" />
        </el-form-item>
        <el-form-item label="详细描述">
          <el-input v-model="form.content" type="textarea" :rows="5" placeholder="补充背景、尝试过的方法、具体困惑…" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">发布问题</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { publishQuestion } from '../../api/qa'

const router = useRouter()
const categories = ['课程', '考试', '技术', '生活', '其他']
const form = reactive({ category: '课程', title: '', content: '' })
const submitting = ref(false)

async function submit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写问题标题')
    return
  }
  submitting.value = true
  try {
    await publishQuestion({ title: form.title.trim(), content: form.content.trim(), category: form.category })
    ElMessage.success('发布成功，等待同学回答')
    router.push('/qa')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.publish {
  max-width: 760px;
  margin: 0 auto;
}
</style>
