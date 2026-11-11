<template>
  <div class="ai-layout" :aria-busy="busy" @keydown.esc="closeHistory">
    <button v-if="historyOpen" type="button" class="history-scrim" aria-label="关闭会话历史" @click="closeHistory" />
    <aside id="assistant-history" ref="historyPanel" class="ai-side" :class="{ 'history-open': historyOpen }" aria-label="助手会话">
      <button class="new-btn" type="button" :disabled="sessionsDisabled" @click="chooseSession()">＋ 新建会话</button>
      <p class="history-label">会话历史</p>
      <div class="session-list">
        <div v-for="session in sessions" :key="session.id" class="ai-session" :class="{ active: session.id === activeId }">
          <button type="button" class="session-select" :aria-current="session.id === activeId ? 'true' : undefined"
            :disabled="sessionsDisabled" @click="chooseSession(session)">
            <svg class="ai-session__ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M21 11.5a8.5 8.5 0 0 1-12.5 7.5L3 21l2-5.5A8.5 8.5 0 1 1 21 11.5z" />
            </svg>
            <span class="s-title">{{ session.title }}</span>
          </button>
          <slot name="session-actions" :session="session" />
        </div>
        <slot v-if="!sessions.length" name="empty-sessions">
          <p class="session-empty">暂无会话</p>
        </slot>
      </div>
    </aside>

    <section class="ai-main" :aria-label="title">
      <header class="ai-top">
        <button ref="historyButton" class="history-toggle" type="button" aria-controls="assistant-history"
          :aria-expanded="historyOpen" aria-label="会话历史" @click="openHistory">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true">
            <rect x="3" y="5" width="18" height="14" rx="2" /><path d="M9 5v14" />
          </svg>
        </button>
        <div class="ai-top__meta">
          <h1>{{ title }}</h1>
          <p>{{ subtitle }}</p>
        </div>
        <div v-if="tabs.length" class="ai-top__tools wt-tabs" role="tablist" :aria-label="tabsLabel" @keydown="moveTab">
          <button v-for="item in tabs" :key="item.value" type="button" role="tab" class="wt-tab"
            :aria-selected="mode === item.value" :tabindex="mode === item.value ? 0 : -1"
            @click="$emit('update:mode', item.value)">{{ item.label }}</button>
        </div>
      </header>
      <slot name="toolbar" />

      <div class="ai-workspace" :class="{ 'ai-workspace--welcome': emptyTitle }">
        <div ref="messageBox" class="ai-chat" :class="{ 'ai-chat--workspace': !showComposer }">
          <div class="ai-chat__inner" :class="{ 'ai-chat__inner--welcome': emptyTitle }">
            <div v-if="emptyTitle" class="ai-welcome">
              <span class="ai-orb" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                  <path d="M12 3c0 6-3 9-9 9 6 0 9 3 9 9 0-6 3-9 9-9-6 0-9-3-9-9Z" />
                </svg>
              </span>
              <h2>{{ emptyTitle }}</h2>
              <p v-if="emptyDescription">{{ emptyDescription }}</p>
            </div>
            <div v-if="prompts.length" class="quick-prompts" aria-label="快捷问题">
              <button v-for="prompt in prompts" :key="prompt" class="quick-prompt" type="button" :disabled="busy"
                @click="$emit('prompt', prompt)">{{ prompt }}</button>
            </div>
            <slot />
          </div>
        </div>

        <div v-if="showComposer || footnote" class="ai-composer">
          <form v-if="showComposer" class="ai-input" @submit.prevent="submitQuestion">
            <textarea rows="2" :value="question" :placeholder="placeholder" :aria-label="inputLabel"
              :maxlength="maxLength" :disabled="inputDisabled" @input="$emit('update:question', $event.target.value)"
              @keydown.enter="onEnter" />
            <button class="send" type="submit" :disabled="!question.trim() || busy || inputDisabled" aria-label="发送">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M12 19V5m-6 6 6-6 6 6" />
              </svg>
            </button>
          </form>
          <p v-if="footnote" class="ai-footnote">{{ footnote }}</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { nextTick, ref } from 'vue'

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  tabs: { type: Array, default: () => [] },
  tabsLabel: { type: String, default: '助手功能' },
  mode: { type: String, default: '' },
  sessions: { type: Array, default: () => [] },
  activeId: { type: [String, Number], default: null },
  question: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  inputLabel: { type: String, default: '输入问题' },
  maxLength: { type: Number, default: undefined },
  prompts: { type: Array, default: () => [] },
  emptyTitle: { type: String, default: '' },
  emptyDescription: { type: String, default: '' },
  footnote: { type: String, default: '' },
  showComposer: { type: Boolean, default: true },
  busy: Boolean,
  inputDisabled: Boolean,
  sessionsDisabled: Boolean
})
const emit = defineEmits(['new-session', 'select-session', 'update:mode', 'update:question', 'send', 'prompt'])
const messageBox = ref(null)
const historyOpen = ref(false)
const historyButton = ref(null)
const historyPanel = ref(null)

function openHistory() {
  historyOpen.value = true
  nextTick(() => historyPanel.value?.querySelector('button:not(:disabled)')?.focus())
}

function closeHistory() {
  if (!historyOpen.value) return
  historyOpen.value = false
  nextTick(() => historyButton.value?.focus())
}

function chooseSession(session) {
  closeHistory()
  if (session) emit('select-session', session)
  else emit('new-session')
}

function submitQuestion() {
  if (props.question.trim() && !props.busy && !props.inputDisabled) emit('send')
}

function onEnter(event) {
  if (event.shiftKey || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  submitQuestion()
}

function scrollToBottom() {
  nextTick(() => {
    if (messageBox.value) messageBox.value.scrollTop = messageBox.value.scrollHeight
  })
}

function moveTab(event) {
  const buttons = [...event.currentTarget.querySelectorAll('[role="tab"]')]
  const current = buttons.indexOf(event.target)
  if (current < 0) return
  let index
  switch (event.key) {
    case 'ArrowLeft': index = (current - 1 + buttons.length) % buttons.length; break
    case 'ArrowRight': index = (current + 1) % buttons.length; break
    case 'Home': index = 0; break
    case 'End': index = buttons.length - 1; break
    default: return
  }
  event.preventDefault()
  emit('update:mode', props.tabs[index].value)
  buttons[index].focus()
}

defineExpose({ scrollToBottom })
</script>

<style scoped>
.ai-layout {
  position: relative;
  display: grid;
  grid-template-columns: 212px minmax(0, 1fr);
  height: calc(100dvh - 132px);
  min-height: 470px;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
}
.ai-side, .ai-main { min-width: 0; min-height: 0; }
.ai-side {
  display: flex;
  flex-direction: column;
  padding: 14px 11px 10px;
  background: var(--surface-2);
  border-inline-end: 1px solid var(--line);
}
.new-btn {
  flex: none;
  padding: 9px 11px;
  border: 1px solid var(--brand-line);
  border-radius: 8px;
  background: var(--brand-soft);
  color: var(--brand-strong);
  text-align: start;
  font: inherit;
  font-size: var(--fs-sm);
  font-weight: 650;
  cursor: pointer;
}
.history-label { margin: 18px 7px 5px; color: var(--ink-3); font-size: var(--fs-cap); }
.session-list { min-width: 0; min-height: 0; overflow: auto; }
.ai-session { display: flex; align-items: center; gap: 4px; min-width: 0; padding-right: 6px; border-radius: 7px; color: var(--ink-2); }
.session-select { display: flex; align-items: center; gap: 8px; min-width: 0; flex: 1; padding: 9px 7px; font: inherit; font-size: var(--fs-sm); text-align: start; color: inherit; border: 0; background: transparent; cursor: pointer; }
.ai-session__ico { width: 15px; height: 15px; flex: none; }
.ai-session:hover { background: var(--surface); }
.ai-session.active { background: var(--brand-soft); color: var(--brand-strong); font-weight: 650; }
.s-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-empty { margin: 8px 7px; color: var(--ink-3); font-size: var(--fs-sm); }
.history-toggle, .history-scrim { display: none; }

.ai-main { display: flex; flex-direction: column; overflow: hidden; }
.ai-top { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; padding: 10px 16px; border-bottom: 1px solid var(--line); }
.ai-top__meta { min-width: 0; }
.ai-top__meta h1 { margin: 0; color: var(--ink); font-size: var(--fs-body); }
.ai-top__meta p { margin: 2px 0 0; font-size: var(--fs-cap); color: var(--ink-3); }
.ai-top__tools { margin-inline-start: auto; max-width: 100%; min-width: 0; display: flex; gap: 4px; overflow-x: auto; }
.wt-tab { flex: none; white-space: nowrap; padding: 6px 10px; border: 0; background: transparent; cursor: pointer; border-radius: 8px; font: inherit; font-size: var(--fs-xs); color: var(--ink-2); }
.wt-tab:hover { background: var(--surface-2); }
.wt-tab[aria-selected='true'] { background: var(--brand-soft); color: var(--brand-strong); font-weight: 650; }
.ai-layout button:focus-visible { outline: 2px solid var(--brand); outline-offset: -2px; }
.ai-workspace { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.ai-workspace--welcome { justify-content: center; }
.ai-workspace--welcome .ai-chat { flex: 0 1 auto; }
.ai-chat { flex: 1; min-height: 0; overflow: auto; padding: 14px 20px; }
.ai-chat__inner { width: min(100%, 760px); margin-inline: auto; min-width: 0; }
.ai-chat--workspace .ai-chat__inner { width: min(100%, 1000px); }
.ai-chat__inner--welcome { display: grid; justify-items: center; gap: 15px; }
.ai-welcome { max-width: 520px; padding: 4px 0; color: var(--ink); text-align: center; }
.ai-orb { display: grid; place-items: center; width: 42px; height: 42px; margin: 0 auto 12px; border-radius: 14px; color: var(--brand-strong); background: var(--brand-soft); }
.ai-orb svg { width: 24px; height: 24px; }
.ai-welcome h2 { margin: 0; font-size: clamp(19px, 2.4vw, 27px); line-height: 1.3; }
.ai-welcome p { margin: 6px 0 0; color: var(--ink-2); font-size: var(--fs-sm); }
.quick-prompts { display: flex; justify-content: center; flex-wrap: wrap; gap: 7px; }
.quick-prompt { padding: 6px 11px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); color: var(--ink-2); font: inherit; font-size: var(--fs-xs); cursor: pointer; }
.quick-prompt:hover:not(:disabled) { background: var(--brand-soft); color: var(--brand-strong); border-color: var(--brand-line); }
.ai-composer { flex: none; width: min(100%, 800px); margin-inline: auto; padding: 8px 20px 11px; }
.ai-input { display: flex; align-items: flex-end; gap: 8px; padding: 8px; border: 1px solid var(--line-strong); border-radius: 16px; background: var(--surface); box-shadow: var(--shadow-sm); }
.ai-input:focus-within { border-color: var(--brand); }
.ai-input textarea {
  field-sizing: content;
  flex: 1;
  min-width: 0;
  min-height: 40px;
  max-height: 132px;
  resize: none;
  border: 0;
  outline: none;
  padding: 7px 5px;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: var(--fs-sm);
  line-height: 1.5;
}
.ai-input textarea::placeholder { color: var(--ink-3); }
.send { width: 36px; height: 36px; flex: none; display: grid; place-items: center; border: 0; border-radius: 50%; background: var(--brand); color: var(--brand-ink); cursor: pointer; }
.send:hover:not(:disabled) { background: var(--brand-strong); }
.send svg { width: 18px; height: 18px; }
button:disabled { opacity: .55; cursor: not-allowed; }
.ai-footnote { margin: 7px 0 0; color: var(--ink-3); text-align: center; font-size: var(--fs-cap); }

@media (max-width: 900px) {
  .ai-layout { grid-template-columns: minmax(0, 1fr); height: calc(100dvh - 112px); }
  .ai-side { display: none; }
  .ai-side.history-open { display: flex; position: absolute; inset: 0 auto 0 0; z-index: 3; width: min(82%, 246px); }
  .history-scrim { display: block; position: absolute; inset: 0; z-index: 2; border: 0; background: rgba(15, 23, 42, .26); cursor: pointer; }
  .history-toggle { display: grid; place-items: center; flex: none; width: 34px; height: 34px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); color: var(--ink-2); cursor: pointer; }
  .history-toggle svg { width: 18px; height: 18px; }
  .ai-top { padding: 8px 10px; }
  .ai-chat { padding: 9px 12px; }
  .ai-composer { padding-inline: 10px; }
}
@media (max-width: 540px) {
  .ai-top__tools { margin-inline-start: 0; width: 100%; }
  .wt-tab { padding-inline: 8px; }
}
</style>
