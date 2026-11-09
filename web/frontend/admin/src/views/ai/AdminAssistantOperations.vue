<template>
  <section class="ops" :aria-busy="busy">
    <p v-if="error" role="alert" class="ops-error">{{ error }}</p>
    <template v-if="mode === 'rules'">
      <h2>内部规则查询</h2>
      <p class="muted">回答引用当前可用的内部 PDF，点击引用可核对原页。</p>
      <form class="ops-form" @submit.prevent="run(findRules)">
        <label for="rule-question">规则问题</label>
        <el-input id="rule-question" v-model="input" maxlength="1200" placeholder="例如，待审核内容如何复核？" />
        <el-button type="primary" native-type="submit" :disabled="busy || !input.trim()">查找规则</el-button>
      </form>
      <article v-if="answer" class="rule-answer" aria-label="规则答复">
        <AssistantBubble role="user" :content="askedQuestion" :nickname="nickname" />
        <AssistantBubble role="assistant" :content="answer.answer" class="answer-text" />
        <AssistantCitations :sources="answer.citations" @open="openSource" />
      </article>
      <header class="ops-heading">
        <h3>内部资料</h3>
        <div class="ops-actions">
          <el-button :disabled="busy" @click="run(refresh)">刷新索引状态</el-button>
          <label v-if="canManageRules" class="el-button upload-label" :class="{ 'is-disabled': busy }">
            上传规则 PDF <input type="file" accept="application/pdf,.pdf" :disabled="busy" @change="onUpload" />
          </label>
        </div>
      </header>
      <el-table :data="items" empty-text="暂无内部资料">
        <el-table-column prop="fileName" label="文件" min-width="180" />
        <el-table-column label="索引" width="100">
          <template #default="{ row }">{{ ({ READY: '可查询', QUEUED: '排队中', INDEXING: '索引中', FAILED: '索引失败' })[row.state] || row.state }}</template>
        </el-table-column>
        <el-table-column v-if="canManageRules" label="操作" width="172">
          <template #default="{ row }">
            <el-button text :disabled="busy" @click="run(() => changeRule(row, reindexRule))">重建索引</el-button>
            <el-button text type="danger" :disabled="busy" @click="withdraw(row)">撤回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <template v-else-if="mode === 'notice'">
      <h2>起草校园公告</h2>
      <p class="muted">提供已确定的事项、时间和适用范围。生成后可在原公告编辑器核对。</p>
      <form class="ops-form" @submit.prevent="run(writeNotice)">
        <label for="notice-request">公告要求</label>
        <el-input id="notice-request" v-model="input" type="textarea" :rows="3" maxlength="1200"
          placeholder="例如，通知同学周五图书馆闭馆，具体时段待馆方确认。" />
        <el-button type="primary" native-type="submit" :disabled="busy || !input.trim()">生成公告草稿</el-button>
      </form>
      <section class="draft-list" aria-label="我的公告草稿">
        <h3>我的草稿</h3>
        <p v-if="!items.length && !busy" class="muted">暂无公告草稿。</p>
        <AssistantCard v-for="draft in items" :key="draft.id" class="draft-row" wide>
          <strong>{{ draft.title }}</strong>
          <RouterLink v-if="draft.state === 'DRAFT'" :to="{ name: 'NoticeEdit', query: { draft: draft.id } }">核对并编辑草稿</RouterLink>
          <RouterLink v-else-if="draft.noticeId" :to="{ name: 'NoticeEdit', params: { id: draft.noticeId } }">打开已保存公告</RouterLink>
          <span v-else class="muted">草稿已过期</span>
        </AssistantCard>
      </section>
    </template>

    <template v-else>
      <header class="ops-heading">
        <h2>学生向导运行诊断</h2>
        <el-select v-model="days" aria-label="诊断时间范围" style="width: 140px" :disabled="busy" @change="run(refresh)">
          <el-option label="今天" :value="1" /><el-option label="近7天" :value="7" /><el-option label="近30天" :value="30" />
        </el-select>
      </header>
      <div v-if="summary" aria-label="运行汇总">
        <p class="muted">{{ summary.start }} 至 {{ summary.end }}</p>
        <p>调用 {{ summary.runs }} 次，失败 {{ summary.failed }} 次，其中 {{ summary.untrackedFailures }} 次未记录工具。</p>
        <p class="muted">同一运行内重复出现的工具只计一次。工具出现在失败记录中时，仍需结合日志排查原因。</p>
        <el-table :data="summary.tools" empty-text="该时段暂无工具调用记录">
          <el-table-column prop="name" label="工具" min-width="220" />
          <el-table-column prop="runs" label="涉及运行" width="120" />
          <el-table-column prop="failed" label="其中失败" width="120" />
        </el-table>
      </div>
      <RouterLink :to="{ name: 'AiConfig' }">打开校园向导工作台</RouterLink>
    </template>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import AssistantBubble from '../../../../shared/assistant/AssistantBubble.vue'
import AssistantCard from '../../../../shared/assistant/AssistantCard.vue'
import AssistantCitations from '../../../../shared/assistant/AssistantCitations.vue'
import { askRules, assistantDiagnostics, generateNoticeDraft, listNoticeDrafts, listRules, reindexRule, ruleFile,
  uploadRule, withdrawRule } from '../../api/assistant'

const props = defineProps({ mode: { type: String, required: true }, canManageRules: Boolean, nickname: String })
const input = ref('')
const askedQuestion = ref('')
const items = ref([])
const answer = ref(null)
const summary = ref(null)
const days = ref(1)
const busy = ref(false)
const error = ref('')

async function run(work) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { await work() } catch (e) { error.value = e.message }
  finally { busy.value = false }
}

async function refresh() {
  if (props.mode === 'rules') items.value = await listRules()
  else if (props.mode === 'notice') items.value = await listNoticeDrafts()
  else summary.value = await assistantDiagnostics(days.value)
}

async function findRules() {
  answer.value = null
  const question = input.value.trim()
  answer.value = await askRules(question)
  askedQuestion.value = question
}

async function writeNotice() {
  const draft = await generateNoticeDraft(input.value.trim())
  items.value.unshift(draft)
  input.value = ''
}

async function changeRule(row, action) {
  await action(row.id)
  answer.value = null
  await refresh()
}

async function withdraw(row) {
  try { await ElMessageBox.confirm('撤回后将停止引用这份资料。', '撤回内部资料', { type: 'warning' }) }
  catch { return }
  await run(() => changeRule(row, withdrawRule))
}

function onUpload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) run(async () => { await uploadRule(file); await refresh() })
}

async function openSource(source) {
  const tab = window.open('', '_blank')
  if (!tab) { error.value = '请允许打开引用窗口'; return }
  tab.opener = null
  try {
    const url = URL.createObjectURL(await ruleFile(source))
    tab.location.replace(`${url}#page=${source.page}`)
    setTimeout(() => URL.revokeObjectURL(url), 120000)
  } catch (e) { tab.close(); error.value = e.message }
}

onMounted(() => run(refresh))
</script>

<style scoped>
.ops { max-width: 900px; min-width: 0; }
.ops h2 { margin: 0; font-size: 19px; }
.ops h3 { margin: 0; font-size: 15px; }
.ops-form { display: grid; gap: 8px; margin: 16px 0; }
.ops-form .el-button { justify-self: start; }
.ops-heading, .draft-row { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 10px; }
.ops-heading { margin-block: 12px; padding: 8px 12px; background: var(--surface-2); border-radius: var(--r-sm); }
.ops-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.muted { color: var(--ink-2); font-size: 13px; }
.draft-list h3 { margin-bottom: 8px; }
.rule-answer { margin-top: var(--s-4); }
.ops-error { color: var(--danger, #a52a2a); }
.upload-label { position: relative; margin-left: 0; }
.upload-label:focus-within { outline: 2px solid var(--brand); outline-offset: 2px; }
.upload-label input { position: absolute; inset: 0; width: 100%; opacity: 0; cursor: inherit; }
</style>
