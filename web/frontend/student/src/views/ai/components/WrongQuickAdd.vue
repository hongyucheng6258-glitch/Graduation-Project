<template>
  <el-dialog
    v-model="visible"
    title="⚡ 快速收录错题"
    width="620px"
    :close-on-click-modal="false"
    append-to-body
  >
    <p class="tip">只需填写题目即可收录；也可拍照/上传图片自动识别。AI 不可用也不影响保存。</p>
    <el-form :model="form" label-width="76px">
      <el-form-item label="题目" required>
        <el-input
          v-model="form.question"
          type="textarea"
          :rows="3"
          placeholder="粘贴或输入题目内容，或拍照/上传后自动识别"
        />
        <div class="img-row">
          <input
            ref="fileInput"
            type="file"
            accept="image/*"
            capture="environment"
            hidden
            @change="onFileChange"
          />
          <el-button size="small" :loading="uploading" @click="fileInput?.click()">📷 拍照/上传题目图片</el-button>
          <el-button v-if="form.questionImage" size="small" text type="danger" @click="clearImage">移除图片</el-button>
          <span v-if="ocrState === 'running'" class="hint">正在识别题目文字（首次加载模型较慢）…</span>
          <span v-else-if="ocrState === 'failed'" class="hint warn">图片识别失败，可手动输入题目</span>
        </div>
        <img v-if="form.questionImage" :src="form.questionImage" class="q-img" alt="题目图片" />
      </el-form-item>
      <el-form-item label="学科">
        <el-select
          v-model="form.subject"
          filterable
          allow-create
          default-first-option
          clearable
          placeholder="选择或输入学科（可留空=待整理）"
          style="width: 100%"
        >
          <el-option v-for="s in subjectOptions" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>

      <el-collapse class="advanced">
        <el-collapse-item title="补充信息（可选）">
          <el-form-item label="我的答案"><el-input v-model="form.myAnswer" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="正确答案"><el-input v-model="form.correctAnswer" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="解析"><el-input v-model="form.analysis" type="textarea" :rows="3" /></el-form-item>
          <el-row :gutter="8">
            <el-col :span="8">
              <el-form-item label="题型">
                <el-select v-model="form.questionType" clearable placeholder="题型">
                  <el-option v-for="t in QUESTION_TYPES" :key="t" :label="t" :value="t" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="章节"><el-input v-model="form.chapter" placeholder="章节" /></el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="难度">
                <el-select v-model="form.difficulty" clearable placeholder="难度">
                  <el-option v-for="d in DIFFICULTIES" :key="d" :label="d" :value="d" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="错误原因">
            <el-select v-model="form.errorReason" clearable filterable placeholder="选择错误原因">
              <el-option v-for="r in ERROR_REASONS" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签"><el-input v-model="form.tag" placeholder="标签，如：多线程" /></el-form-item>
          <el-form-item label="知识点"><el-input v-model="form.knowledgePoints" placeholder="知识点，逗号分隔" /></el-form-item>
          <el-form-item label="笔记"><el-input v-model="form.note" type="textarea" :rows="2" /></el-form-item>
        </el-collapse-item>
      </el-collapse>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button :loading="saving" @click="saveAndClose">保存</el-button>
      <el-button type="primary" :loading="saving" @click="saveAndContinue">保存并继续添加</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createWrong, analyzeWrong } from '../../../api/wrong'
import { uploadImage } from '../../../api/user'
import { ocrImage } from '../../../utils/ocr'
import { ERROR_REASONS, QUESTION_TYPES, DIFFICULTIES } from '../../../utils/wrongbook'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  subjects: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const saving = ref(false)
const uploading = ref(false)
const ocrState = ref('idle') // idle / running / failed
const fileInput = ref(null)
const form = reactive({
  subject: '', question: '', tag: '', correctAnswer: '', analysis: '',
  myAnswer: '', errorReason: '', questionType: '', chapter: '',
  difficulty: '', knowledgePoints: '', note: '', questionImage: ''
})

watch(() => props.modelValue, (v) => {
  if (v) reset()
})

const subjectOptions = computed(() => {
  const list = [...new Set([...(props.subjects || []), '待整理'])]
  return list.filter((s) => s && s.trim())
})

function reset() {
  Object.assign(form, {
    subject: '', question: '', tag: '', correctAnswer: '', analysis: '',
    myAnswer: '', errorReason: '', questionType: '', chapter: '',
    difficulty: '', knowledgePoints: '', note: '', questionImage: ''
  })
  ocrState.value = 'idle'
}

function validate() {
  if (!form.question || !form.question.trim()) {
    ElMessage.warning('请填写题目内容（或先上传图片识别）')
    return false
  }
  return true
}

/** 拍照/上传 → 传 MinIO 拿 URL → OCR 识别文字填入题目 */
async function onFileChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    const res = await uploadImage(file)
    form.questionImage = res.url
  } catch (err) {
    ElMessage.error('图片上传失败')
    return
  } finally {
    uploading.value = false
  }

  // OCR 识别（失败降级为手动输入，不阻塞）
  ocrState.value = 'running'
  try {
    const text = await ocrImage(file)
    if (text) {
      if (!form.question.trim()) {
        form.question = text
      } else {
        await ElMessageBox.confirm('已识别到题目文字，是否替换当前题目内容？', '识别结果', {
          type: 'info',
          confirmButtonText: '替换',
          cancelButtonText: '保留'
        })
        form.question = text
      }
    }
    ocrState.value = 'idle'
  } catch (err) {
    ocrState.value = 'failed'
  }
}

function clearImage() {
  form.questionImage = ''
  ocrState.value = 'idle'
}

/** 创建错题；成功后后台异步触发 AI 智能整理（失败仅标记，不影响收录） */
async function doSave() {
  if (!validate()) return null
  saving.value = true
  try {
    const wq = await createWrong({
      subject: form.subject || '待整理',
      question: form.question,
      tag: form.tag || undefined,
      correctAnswer: form.correctAnswer || undefined,
      analysis: form.analysis || undefined,
      myAnswer: form.myAnswer || undefined,
      errorReason: form.errorReason || undefined,
      questionType: form.questionType || undefined,
      chapter: form.chapter || undefined,
      difficulty: form.difficulty || undefined,
      knowledgePoints: form.knowledgePoints || undefined,
      note: form.note || undefined,
      questionImage: form.questionImage || undefined
    })
    emit('saved')
    triggerAnalyze(wq.id) // fire-and-forget
    return wq
  } finally {
    saving.value = false
  }
}

async function triggerAnalyze(id) {
  try {
    await analyzeWrong(id)
  } catch (e) {
    // 整理失败：analyzeStatus=1，错题仍在，列表提供重试
  }
}

async function saveAndClose() {
  if (await doSave()) {
    ElMessage.success('已收录到错题本，正在智能整理…')
    visible.value = false
  }
}

async function saveAndContinue() {
  if (await doSave()) {
    ElMessage.success('已收录，继续添加')
    reset()
  }
}
</script>

<style scoped>
.tip {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--ink-3, #909399);
}
.img-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.hint {
  font-size: 12px;
  color: var(--ink-3, #909399);
}
.hint.warn {
  color: #e6a23c;
}
.q-img {
  max-width: 100%;
  max-height: 180px;
  border-radius: 6px;
  margin-top: 8px;
}
.advanced {
  margin-bottom: 8px;
}
</style>
