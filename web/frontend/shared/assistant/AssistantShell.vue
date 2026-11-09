<template>
  <div class="ai-layout" :aria-busy="busy">
    <aside class="ai-side" aria-label="助手会话">
      <button class="new-btn" type="button" :disabled="sessionsDisabled" @click="$emit('new-session')">＋ 新建会话</button>
      <div class="session-list">
        <div v-for="session in sessions" :key="session.id" class="ai-session" :class="{ active: session.id === activeId }">
          <button type="button" class="session-select" :aria-current="session.id === activeId ? 'true' : undefined"
            :disabled="sessionsDisabled" @click="$emit('select-session', session)">
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
        <span class="ai-orb" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1"/><circle cx="12" cy="12" r="3"/>
          </svg>
        </span>
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

      <div ref="messageBox" class="ai-chat" :class="{ 'ai-chat--workspace': !showComposer }">
        <div v-if="prompts.length" class="quick-prompts" aria-label="快捷问题">
          <button v-for="prompt in prompts" :key="prompt" class="quick-prompt" type="button" :disabled="busy"
            @click="$emit('prompt', prompt)">{{ prompt }}</button>
        </div>
        <slot />
      </div>

      <form v-if="showComposer" class="ai-input" @submit.prevent="$emit('send')">
        <input type="text" :value="question" :placeholder="placeholder" :aria-label="inputLabel"
          :maxlength="maxLength" :disabled="inputDisabled" @input="$emit('update:question', $event.target.value)" />
        <button class="send" type="submit" :disabled="!question.trim() || busy || inputDisabled" aria-label="发送">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M22 2 11 13M22 2l-7 20-4-9-9-4z" />
          </svg>
        </button>
      </form>
      <div v-if="footnote" class="ai-footnote">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <circle cx="12" cy="12" r="9" /><path d="M12 8h.01M11 12h1v4h1" />
        </svg>
        {{ footnote }}
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
  footnote: { type: String, default: '' },
  showComposer: { type: Boolean, default: true },
  busy: Boolean,
  inputDisabled: Boolean,
  sessionsDisabled: Boolean
})
const emit = defineEmits(['new-session', 'select-session', 'update:mode', 'update:question', 'send', 'prompt'])
const messageBox = ref(null)

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
.ai-layout { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: var(--s-5); height: calc(100vh - 220px); min-height: 480px; }
.ai-side, .ai-main { min-width: 0; min-height: 0; background: var(--surface); border: 1px solid var(--line); border-radius: var(--r-lg); }
.ai-side { display: flex; flex-direction: column; gap: var(--s-2); padding: var(--s-4); overflow: auto; }
.new-btn { flex: none; margin-bottom: var(--s-2); padding: 9px 12px; border: 1px solid var(--brand-line); border-radius: var(--r-sm); background: var(--brand-soft); color: var(--brand-strong); font: inherit; font-weight: 600; cursor: pointer; }
.session-list { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.ai-session { display: flex; align-items: center; gap: 4px; min-width: 0; padding-right: 6px; border-radius: var(--r-sm); color: var(--ink-2); }
.session-select { display: flex; align-items: center; gap: var(--s-3); min-width: 0; flex: 1; padding: var(--s-3); font: inherit; font-size: var(--fs-sm); text-align: left; color: inherit; border: 0; background: transparent; cursor: pointer; }
.ai-session__ico { width: 18px; height: 18px; flex: none; color: var(--brand); }
.ai-session:hover { background: var(--surface-2); }
.ai-session.active { background: var(--brand-soft); color: var(--brand-strong); font-weight: 600; }
.ai-session.active .ai-session__ico { color: var(--brand-strong); }
.s-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-empty { color: var(--ink-3); font-size: var(--fs-sm); }
.ai-main { display: flex; flex-direction: column; overflow: hidden; }
.ai-top { display: flex; flex-wrap: wrap; align-items: center; gap: var(--s-3); padding: var(--s-4) var(--s-5); border-bottom: 1px solid var(--line); }
.ai-orb { width: 34px; height: 34px; flex: none; display: grid; place-items: center; border-radius: var(--r-pill); color: #fff; background: linear-gradient(140deg, var(--brand), var(--accent)); box-shadow: var(--shadow-sm); }
.ai-orb svg { width: 18px; height: 18px; }
.ai-top__meta { min-width: 0; }
.ai-top__meta h1 { margin: 0; font-size: var(--fs-body); }
.ai-top__meta p { margin: 3px 0 0; font-size: var(--fs-cap); color: var(--ink-3); }
.ai-top__tools { margin-left: auto; max-width: 100%; min-width: 0; display: inline-flex; gap: 4px; overflow-x: auto; padding: 4px; background: var(--surface-2); border-radius: var(--r-pill); border: 1px solid var(--line); }
.wt-tab { flex: none; white-space: nowrap; padding: 8px 16px; border: none; background: transparent; cursor: pointer; border-radius: var(--r-pill); font: inherit; font-size: var(--fs-xs); font-weight: 600; color: var(--ink-2); }
.wt-tab[aria-selected='true'] { background: var(--surface); color: var(--brand-strong); box-shadow: var(--shadow-sm); }
.wt-tab:focus-visible, .session-select:focus-visible, .quick-prompt:focus-visible, .send:focus-visible, .new-btn:focus-visible { outline: 2px solid var(--brand); outline-offset: -2px; }
.ai-chat { flex: 1; min-height: 0; overflow-y: auto; padding: var(--s-5); display: flex; flex-direction: column; gap: var(--s-4); background: var(--surface-2); }
.ai-chat > :deep(*) { flex-shrink: 0; }
.quick-prompts { display: flex; flex-wrap: wrap; gap: var(--s-2); }
.quick-prompt { padding: 8px 14px; border-radius: var(--r-pill); background: var(--info-soft); color: var(--info-strong); border: 1px solid var(--info-line); font: inherit; font-size: var(--fs-sm); font-weight: 500; cursor: pointer; }
.quick-prompt:hover:not(:disabled) { background: var(--info); color: var(--info-ink); border-color: var(--info); }
.ai-input { display: flex; flex-shrink: 0; gap: var(--s-3); padding: var(--s-4); border-top: 1px solid var(--line); background: var(--surface); }
.ai-input input { min-width: 0; flex: 1; padding: var(--s-3) var(--s-4); border-radius: var(--r-pill); border: 1px solid var(--line); background: var(--surface-2); font: inherit; font-size: var(--fs-sm); color: var(--ink); }
.ai-input input::placeholder { color: var(--ink-3); }
.ai-input input:focus { outline: none; border-color: var(--brand); background: var(--surface); box-shadow: 0 0 0 4px var(--brand-soft); }
.send { width: 48px; height: 48px; flex: none; display: grid; place-items: center; border: 0; border-radius: var(--r-pill); background: linear-gradient(140deg, var(--brand), var(--brand-strong)); color: var(--brand-ink); cursor: pointer; }
.send:hover:not(:disabled) { box-shadow: var(--shadow-md); }
.send svg { width: 20px; height: 20px; }
button:disabled { opacity: .55; cursor: not-allowed; }
.ai-footnote { display: flex; align-items: center; flex-shrink: 0; gap: var(--s-2); font-size: var(--fs-cap); color: var(--ink-3); padding: var(--s-3) var(--s-5); border-top: 1px solid var(--line); background: var(--surface-2); }
.ai-footnote svg { width: 14px; height: 14px; color: var(--brand); flex: none; }
@media (max-width: 820px) {
  .ai-layout { grid-template-columns: minmax(0, 1fr); height: auto; min-height: 0; gap: var(--s-3); }
  .ai-side { flex-direction: row; align-items: center; overflow-x: auto; padding: var(--s-2); }
  .new-btn { margin: 0; }
  .session-list { flex-direction: row; }
  .ai-session { flex: 0 0 152px; }
  .ai-top { padding: var(--s-3); }
  .ai-top__tools { margin-left: 0; }
  .wt-tab { padding-inline: 10px; }
  .ai-chat { min-height: 230px; max-height: 56dvh; padding: var(--s-3); }
  .ai-chat--workspace { max-height: none; }
  .ai-input { padding: var(--s-3); }
}
</style>
