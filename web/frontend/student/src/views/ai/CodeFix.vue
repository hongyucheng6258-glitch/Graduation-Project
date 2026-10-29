<template>
  <WtPageHeader title="AI 代码诊所" subtitle="粘贴报错，智能定位问题根因" eyebrow="AI 学习" />

  <div class="codefix">
    <div class="panel left">
      <div class="panel-head">
        <h3>💻 代码纠错</h3>
        <div class="ops">
          <el-select v-model="language" style="width: 130px">
            <el-option v-for="l in languages" :key="l" :label="l" :value="l" />
          </el-select>
          <el-button type="primary" :loading="fixing" @click="fix">开始纠错</el-button>
        </div>
      </div>
      <el-input
        v-model="extra"
        placeholder="补充说明（报错信息等，可空）"
        style="margin-bottom: 8px"
      />
      <!-- Monaco 代码编辑器 -->
      <div class="editor-box">
        <vue-monaco-editor
          v-if="editorReady"
          v-model:value="code"
          :language="monacoLang"
          theme="vs"
          :options="{ fontSize: 14, minimap: { enabled: false }, automaticLayout: true }"
        />
        <textarea v-else v-model="code" class="plain-code-editor" aria-label="代码编辑区" spellcheck="false" @focus="plainEditorInUse = true"></textarea>
      </div>
      <p v-if="!editorReady" class="editor-note">{{ editorFailed ? '已使用基础编辑器，代码输入和纠错功能可正常使用。' : '可直接输入代码，无需等待编辑器加载。' }}</p>
    </div>
    <div class="panel right">
      <div class="panel-head"><h3>📋 纠错结果</h3></div>
      <div v-if="fixing" class="loading-box" v-loading="true" element-loading-text="AI 正在分析代码…" />
      <div v-else-if="result" class="result md-body" v-html="renderMarkdown(result)" />
      <el-empty v-else description="提交含错误的代码，AI 将给出错误定位与修复建议" :image-size="100" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import WtPageHeader from '../../components/wt/WtPageHeader.vue'
import { VueMonacoEditor, loader } from '@guolao/vue-monaco-editor'
import { loadCodeEditor, cancelCodeEditorLoad } from '../../utils/code-editor'
import { codeFix } from '../../api/ai'
import { renderMarkdown } from '../../utils/markdown'

const languages = ['java', 'python', 'c', 'cpp', 'javascript', 'go', 'sql']
const language = ref('java')
const code = ref('public class Main {\n    public static void main(String[] args) {\n        // 粘贴你的代码\n    }\n}')
const extra = ref('')
const fixing = ref(false)
const result = ref('')
const editorReady = ref(false)
const editorFailed = ref(false)
const plainEditorInUse = ref(false)
let disposed = false
onMounted(() => {
  loadCodeEditor().then(monaco => {
    if (disposed) return
    loader.config({ monaco })
    if (!plainEditorInUse.value) editorReady.value = true
  }).catch((error) => {
    // 资源不可用 / 超时 / 离开页面都降级为 textarea，且不产生未处理 rejection。
    if (!disposed) {
      editorFailed.value = true
      editorReady.value = false
      if (error?.message && !/取消编辑器加载/.test(error.message)) {
        ElMessage.warning('代码增强编辑器加载失败，已切换为基础编辑器')
      }
    }
  })
})
onBeforeUnmount(() => { disposed = true; cancelCodeEditorLoad() })

// Monaco 语言 id 映射（c/cpp 均为 cpp）
const monacoLang = computed(() => {
  const map = { c: 'c', cpp: 'cpp', java: 'java', python: 'python', javascript: 'javascript', go: 'go', sql: 'sql' }
  return map[language.value] || 'plaintext'
})

async function fix() {
  if (!code.value.trim()) return
  fixing.value = true
  result.value = ''
  try {
    const res = await codeFix({ code: code.value, language: language.value, extra: extra.value })
    result.value = typeof res === 'string' ? res : (res?.answer || '')
    if (!result.value) {
      ElMessage.warning('AI未返回纠错结果，请稍后重试')
    }
  } catch (e) {
    ElMessage.error(e.message || '代码纠错失败')
  } finally {
    fixing.value = false
  }
}
</script>

<style scoped>
.codefix {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  height: calc(100vh - 140px);
}
.panel {
  background: var(--surface);
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.panel-head h3 {
  font-size: 15px;
}
.ops {
  display: flex;
  gap: 10px;
}
.editor-box {
  flex: 1;
  min-height: 300px;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  overflow: hidden;
}
.loading-box {
  flex: 1;
}
.result {
  flex: 1;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.8;
}
.result :deep(pre) {
  background: #f6f8fa;
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
}
.result :deep(h1), .result :deep(h2), .result :deep(h3) {
  margin: 12px 0 8px;
}
.plain-code-editor { width: 100%; height: 100%; min-height: 300px; display: block; resize: vertical; border: 0; padding: 16px; background: var(--surface); color: var(--ink); font: 14px/1.7 Consolas, monospace; tab-size: 2; }
.editor-note { margin-top: 8px; font-size: 12px; color: var(--ink-3); }
@media(max-width: 760px) { .codefix { grid-template-columns: 1fr; height: auto; } .panel-head { flex-wrap: wrap; gap: 10px; } .right { min-height: 250px; } }
</style>
