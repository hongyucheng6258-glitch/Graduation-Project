<template>
  <section class="ops" :aria-busy="busy">
    <p v-if="error" role="alert" class="ops-error">{{ error }}</p>
    <template v-if="mode === 'rules'">
      <header class="ops-intro">
        <h2>内部规则查询</h2>
        <p>回答引用当前可用的内部 PDF，点击引用可核对原页。</p>
      </header>
      <form class="ops-form" @submit.prevent="run(findRules)">
        <label for="rule-question">规则问题</label>
        <el-input id="rule-question" v-model="input" maxlength="1200"
          placeholder="例如，待审核内容如何复核？" />
        <el-button type="primary" native-type="submit" :disabled="busy || !input.trim()">查找规则</el-button>
      </form>
      <article v-if="answer" class="rule-answer" aria-label="规则答复">
        <AssistantBubble role="user" :content="askedQuestion" :nickname="nickname" />
        <AssistantBubble role="assistant" :content="answer.answer" class="answer-text" />
        <AssistantCitations :sources="answer.citations" @open="openSource" />
      </article>
      <section class="ops-panel" aria-labelledby="rule-files-title">
        <header class="ops-heading">
          <h3 id="rule-files-title">内部资料</h3>
          <div class="ops-actions">
            <el-button size="small" :disabled="busy" @click="run(refresh)">刷新索引状态</el-button>
            <label v-if="canManageRules" class="el-button el-button--small upload-label" :class="{ 'is-disabled': busy }">
              上传规则 PDF <input type="file" accept="application/pdf,.pdf" :disabled="busy" @change="onUpload" />
            </label>
          </div>
        </header>
        <p v-if="!items.length && !busy" class="panel-empty">暂无内部资料。</p>
        <ul v-else-if="items.length" class="ops-list">
          <li v-for="row in items" :key="row.id" class="ops-row">
            <div class="row-info">
              <strong>{{ row.fileName }}</strong>
              <span class="state-pill" :class="{ ready: row.state === 'READY', failed: row.state === 'FAILED' }">{{ ruleStates[row.state] || row.state }}</span>
            </div>
            <div v-if="canManageRules" class="ops-actions">
              <el-button text size="small" :disabled="busy" @click="run(() => changeRule(row, reindexRule))">重建索引</el-button>
              <el-button text size="small" type="danger" :disabled="busy" @click="withdraw(row)">撤回</el-button>
            </div>
          </li>
        </ul>
      </section>
    </template>

    <template v-else-if="mode === 'notice'">
      <header class="ops-intro">
        <h2>起草校园公告</h2>
        <p>填写事项、时间和适用范围，生成草稿后可在公告编辑器核对。</p>
      </header>
      <form class="ops-form" @submit.prevent="run(writeNotice)">
        <label for="notice-request">公告要求</label>
        <el-input id="notice-request" v-model="input" type="textarea" :rows="3" maxlength="1200"
          placeholder="例如，通知同学周五图书馆闭馆，具体时段待馆方确认。" />
        <el-button type="primary" native-type="submit" :disabled="busy || !input.trim()">生成公告草稿</el-button>
      </form>
      <section class="ops-panel" aria-labelledby="notice-drafts-title">
        <header class="ops-heading"><h3 id="notice-drafts-title">我的草稿</h3></header>
        <p v-if="!items.length && !busy" class="panel-empty">暂无公告草稿。</p>
        <ul v-else-if="items.length" class="ops-list">
          <li v-for="draft in items" :key="draft.id" class="ops-row">
            <div class="row-info">
              <strong>{{ draft.title }}</strong>
              <span class="state-pill">{{ draft.state === 'DRAFT' ? '待核对' : draft.noticeId ? '已保存' : '已过期' }}</span>
            </div>
            <RouterLink v-if="draft.state === 'DRAFT'" :to="{ name: 'NoticeEdit', query: { draft: draft.id } }">核对并编辑草稿 ↗</RouterLink>
            <RouterLink v-else-if="draft.noticeId" :to="{ name: 'NoticeEdit', params: { id: draft.noticeId } }">打开已保存公告 ↗</RouterLink>
          </li>
        </ul>
      </section>
    </template>

    <template v-else>
      <header class="ops-intro diagnostic-heading">
        <div>
          <h2>学生向导运行诊断</h2>
          <p v-if="summary">{{ summary.start }} 至 {{ summary.end }}</p>
        </div>
        <el-select v-model="days" aria-label="诊断时间范围" style="width: 140px" :disabled="busy" @change="run(refresh)">
          <el-option label="今天" :value="1" /><el-option label="近7天" :value="7" /><el-option label="近30天" :value="30" />
        </el-select>
      </header>
      <section v-if="summary" aria-label="运行汇总">
        <dl class="diagnostic-metrics">
          <div><dt>总调用</dt><dd>{{ summary.runs }}</dd></div>
          <div><dt>失败</dt><dd>{{ summary.failed }}</dd></div>
          <div><dt>失败且无工具记录</dt><dd>{{ summary.untrackedFailures }}</dd></div>
        </dl>
        <section class="ops-panel" aria-labelledby="tools-title">
          <header class="ops-heading"><h3 id="tools-title">工具调用明细</h3></header>
          <p v-if="!summary.tools.length" class="panel-empty">该时段暂无工具调用记录。</p>
          <ul v-else class="ops-list">
            <li v-for="tool in summary.tools" :key="tool.name" class="ops-row">
              <strong>{{ tool.name }}</strong>
              <span class="tool-counts">涉及 {{ tool.runs }} 次 · 失败 {{ tool.failed }} 次</span>
            </li>
          </ul>
        </section>
        <p class="ops-note">同一运行内重复使用的工具只计一次。失败原因可结合日志查看。</p>
      </section>
      <RouterLink class="ops-link" :to="{ name: 'AiConfig' }">打开校园向导工作台 ↗</RouterLink>
    </template>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import AssistantBubble from '../../../../shared/assistant/AssistantBubble.vue'
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
const ruleStates = { READY: '可查询', QUEUED: '排队中', INDEXING: '索引中', FAILED: '索引失败' }

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
.ops { width: min(100%, 760px); margin: 5px auto 12px; min-width: 0; color: var(--ink); }
.ops h2, .ops h3 { margin: 0; }
.ops h2 { font-size: 20px; }
.ops h3 { font-size: 14px; }
.ops-intro { margin-bottom: 13px; }
.ops-intro p, .ops-note { margin: 4px 0 0; color: var(--ink-2); font-size: 13px; overflow-wrap: anywhere; }
.ops-form {
  display: grid;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface-2);
}
.ops-form label { font-size: 13px; font-weight: 650; }
.ops-form .el-button { justify-self: end; }
.ops-panel { margin-top: 12px; border: 1px solid var(--line); border-radius: 12px; }
.ops-heading, .ops-row, .diagnostic-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 7px 12px; }
.ops-heading { padding: 10px 12px; }
.ops-list { margin: 0; padding: 0; list-style: none; }
.ops-row { padding: 9px 12px; border-top: 1px solid var(--line); overflow-wrap: anywhere; }
.ops-row strong { font-size: 13px; }
.ops-row a, .ops-link { color: var(--brand-strong); font-size: 13px; }
.ops-actions, .row-info { display: flex; align-items: center; flex-wrap: wrap; gap: 5px 8px; min-width: 0; }
.ops-actions .el-button { margin: 0; }
.state-pill { padding: 2px 7px; border-radius: 6px; background: var(--surface-2); color: var(--ink-2); font-size: 12px; }
.state-pill.ready { background: var(--success-soft); color: var(--success); }
.state-pill.failed { background: var(--error-soft); color: var(--error); }
.panel-empty { margin: 0; padding: 9px 12px 12px; border-top: 1px solid var(--line); color: var(--ink-3); font-size: 13px; }
.rule-answer { margin: 0 0 16px; }
.diagnostic-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 0; }
.diagnostic-metrics > div { padding: 10px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface-2); }
.diagnostic-metrics dt, .tool-counts { color: var(--ink-2); font-size: 12px; }
.diagnostic-metrics dd { margin: 3px 0 0; font-size: 24px; line-height: 1.2; font-weight: 700; font-variant-numeric: tabular-nums; }
.ops-note { margin-top: 8px; }
.ops-link { display: inline-block; margin-top: 12px; }
.ops-error { color: var(--error); }
.upload-label { position: relative; margin-left: 0; }
.upload-label:focus-within { outline: 2px solid var(--brand); outline-offset: 2px; }
.upload-label input { position: absolute; inset: 0; width: 100%; opacity: 0; cursor: inherit; }
@media (max-width: 420px) {
  .ops h2 { font-size: 17px; }
  .ops-heading, .ops-row { padding-inline: 8px; }
  .diagnostic-metrics { gap: 5px; }
  .diagnostic-metrics > div { padding: 7px; }
  .diagnostic-metrics dd { font-size: 20px; }
}
</style>
