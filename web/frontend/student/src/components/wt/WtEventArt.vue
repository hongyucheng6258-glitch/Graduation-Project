<script setup>
// 活动封面（V2）：HTML/SVG 生成式活动海报封面。
// 对齐「前端UI-原型-HTML版」eventArt：6 套视觉（音乐/运动/志愿/阅读/夜跑/灵感），
// 深色模式保留印刷色，图形不依赖主题变量。
import { computed } from 'vue'

const props = defineProps({
  item:  { type: Object, required: true }, // 活动对象：{ id, category, title, ... }
  large: { type: Boolean, default: false },
})

// 分类关键词 → 视觉类型（保留原型 6 套视觉）
function clsByCategory(cat = '') {
  const c = String(cat)
  if (/音乐|文艺|娱乐|歌|晚会的?/.test(c)) return 'music'
  if (/运动|体育|篮球|足球|球|竞赛|比赛|夜跑|跑步|马拉松/.test(c)) return c.includes('跑') ? 'running' : 'sport'
  if (/志愿|公益|敬老|服务|募捐/.test(c)) return 'volunteer'
  if (/学习|读书|阅读|讲座|学业|考研|英语/.test(c)) return 'reading'
  if (/创意|灵感|工作坊|设计|竞赛组队|组队/.test(c)) return 'ideas'
  return 'ideas'
}

// 各视觉类型的兜底文案（仅在无真实标题数据时使用）
const PRESETS = {
  music: { word: '把夜晚\n交给音乐', note: '校园音乐活动', day: '25', month: '9 月', cls: 'music' },
  sport: { word: '热爱上场\n就现在', note: '校园体育赛事', day: '08', month: '10 月', cls: 'sport' },
  volunteer: { word: '小小善意\n大大温暖', note: '校园志愿服务', day: '27', month: '9 月', cls: 'volunteer' },
  reading: { word: '在书页里\n遇见彼此', note: '校园共读计划', day: '28', month: '9 月', cls: 'reading' },
  running: { word: '迎着晚风\n跑一小段', note: '晚风夜跑计划', day: '29', month: '9 月', cls: 'running' },
  ideas: { word: '让好点子\n发生', note: '校园灵感工作坊', day: '30', month: '9 月', cls: 'ideas' },
}
const FALLBACK = { word: '一起出发\n遇见同好', note: '校园活动', day: '新', month: '校园', cls: 'reading' }

// 真实数据驱动：视觉按分类映射，文案/日期取活动的真实字段
const v = computed(() => {
  const item = props.item || {}
  const cls = clsByCategory(item.category)
  const preset = PRESETS[cls] || FALLBACK
  const d = item.startTime ? new Date(String(item.startTime).replace(/-/g, '/')) : null
  const title = item.title || item.note || preset.note
  const cat = item.category || '校园活动'
  return {
    cls,
    word: title || preset.word,
    note: item.category ? cat : preset.note,
    day: d ? String(d.getDate()).padStart(2, '0') : (item.day || preset.day),
    month: d ? `${d.getMonth() + 1} 月` : (item.month || preset.month),
    small: item.title ? '梧桐校园' : `梧桐校园 · ${cat}`,
  }
})

const wordHtml = computed(() => v.value.word.replace('\n', '<br>'))

const GRAPHICS = {
  music: '<circle cx="128" cy="104" r="84" fill="currentColor"/><g fill="none" stroke="#ffdcc2" opacity=".4"><circle cx="128" cy="104" r="65"/><circle cx="128" cy="104" r="49"/><circle cx="128" cy="104" r="31"/></g><circle cx="128" cy="104" r="18" fill="#ffdcc2"/><circle cx="128" cy="104" r="5" fill="currentColor"/><path d="M186 18h18v96l-42 20" stroke="#19243d" stroke-width="8" fill="none"/>',
  sport: '<circle cx="128" cy="103" r="79" fill="currentColor"/><g stroke="#ffd69c" stroke-width="3" fill="none"><circle cx="128" cy="103" r="69"/><path d="M49 103h158M128 24v158M69 48c91 12 91 98 0 110M187 48c-91 12-91 98 0 110"/></g>',
  volunteer: '<path d="M126 172 59 108C-10 40 91 0 126 60c35-60 136-20 67 48Z" fill="currentColor"/><path d="M68 181c50-27 87-27 129 0" stroke="#345d50" stroke-width="7" fill="none"/>',
  reading: '<path d="m49 41 78 19 78-19v125l-78 19-78-19Z" fill="currentColor"/><path d="M127 60v125M65 67l46 11M65 89l46 11M65 111l46 11M143 78l46-11M143 100l46-11" stroke="#e5e5ff" stroke-width="4" fill="none"/>',
  running: '<path d="M24 153c31-96 133-116 173-34s-86 100-92 28 65-83 83-26" fill="none" stroke="currentColor" stroke-width="24"/><path d="M32 153c24-90 120-103 153-37" fill="none" stroke="#e8f3c6" stroke-width="3"/>',
  ideas: '<path d="m126 13 23 57 59-20-28 56 49 38-62 6-3 63-38-49-44 44 5-63-61-13 55-32-22-59 58 27Z" fill="currentColor"/>',
}
</script>

<template>
  <div class="event-art" :class="[v.cls, { large }]">
    <div class="event-art-copy">
      <span>{{ v.note }}</span>
      <strong v-html="wordHtml"></strong>
      <small>{{ v.small }}</small>
    </div>
    <svg class="event-graphic" viewBox="0 0 250 220" aria-hidden="true" v-html="GRAPHICS[v.cls]"></svg>
  </div>
</template>

<style scoped>
/* —— 封面印刷色（深色主题下保持实物感，不随主题变量） —— */
.event-art {
  height: 178px;
  position: relative;
  overflow: hidden;
  background: #ffe2cc;
  color: #b34b32;
  isolation: isolate;
}
.event-art-copy {
  position: relative;
  z-index: 2;
  padding: 18px 19px;
  width: 75%;
}
.event-art-copy > span {
  display: block;
  font-size: 10px;
  font-weight: 550;
  letter-spacing: .3px;
}
.event-art-copy strong {
  display: block;
  font-size: 25px;
  font-weight: 800;
  letter-spacing: -.5px;
  line-height: 1.32;
  margin: 11px 0;
}
.event-art-copy small { font-size: 9px; display: block; }
.event-graphic {
  position: absolute;
  width: 170px;
  height: 170px;
  right: -41px;
  bottom: -22px;
  color: #4260da;
  transform: rotate(-12deg);
  z-index: 1;
}
.event-art.sport { background: #dce5ff; color: #2c4596; }
.event-art.sport .event-graphic { color: #e4974e; right: -39px; bottom: -23px; transform: rotate(-25deg); }
.event-art.volunteer { background: #ddece7; color: #376657; }
.event-art.volunteer .event-graphic { color: #7db298; right: -30px; transform: rotate(-12deg); }
.event-art.reading { background: #e5e5f9; color: #6666a3; }
.event-art.reading .event-graphic { color: #8282b4; }
.event-art.running { background: #e8efdb; color: #536c39; }
.event-art.running .event-graphic { color: #7d9e62; }
.event-art.ideas { background: #f6e4d5; color: #a4724e; }
.event-art.ideas .event-graphic { color: #d5a878; }

/* 大尺寸（详情页） */
.event-art.large { height: 290px; }
.event-art.large .event-art-copy { padding: 30px 39px; }
.event-art.large .event-art-copy > span { font-size: 13px; }
.event-art.large .event-art-copy strong { font-size: 45px; line-height: 1.3; margin: 18px 0; }
.event-art.large .event-art-copy small { font-size: 12px; }
.event-art.large .event-graphic { width: 360px; height: 315px; right: -12px; bottom: -55px; transform: rotate(-15deg); }

@media (max-width: 1080px) {
  .event-art.large .event-art-copy { padding: 27px; }
  .event-art.large .event-art-copy strong { font-size: 36px; }
  .event-art.large .event-graphic { width: 270px; right: -50px; }
}
@media (max-width: 760px) {
  .event-art { height: 147px; }
  .event-art-copy { padding: 16px 13px; width: 100%; }
  .event-art-copy > span { font-size: 9px; }
  .event-art-copy strong { font-size: 21px; }
  .event-art-copy small { font-size: 8px; }
  .event-graphic { width: 140px; height: 140px; right: -52px; bottom: -36px; }
  .event-art.large { height: 265px; }
  .event-art.large .event-art-copy { padding: 26px; }
  .event-art.large .event-art-copy strong { font-size: 39px; }
  .event-art.large .event-art-copy > span { font-size: 11px; }
  .event-art.large .event-art-copy small { font-size: 10px; }
  .event-art.large .event-graphic { width: 238px; height: 255px; right: -66px; bottom: -55px; opacity: .8; }
}
</style>
