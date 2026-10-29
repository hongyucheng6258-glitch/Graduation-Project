import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('./Home.vue', import.meta.url), 'utf8')

test('V2 首页使用摄影 Hero（校园蓝视觉）', () => {
  assert.match(source, /<WtHero/)
  assert.match(source, /photo="\/images\/campus-v2\.webp"/)
  assert.match(source, /photo-note="秋日校园 · 2026"/)
  assert.match(source, /primary="\{ label:\s*'发现校园活动'/)
})

test('首页快捷服务条提供四个校园服务入口', () => {
  assert.match(source, /class="quick-strip"/)
  assert.match(source, /v-for="e in entries"/)
  // 四宫格：学习搭子 / 活动 / 闲置 / 失物
  assert.match(source, /title:\s*'你的学习搭子'/)
  assert.match(source, /title:\s*'去见见新朋友'/)
  assert.match(source, /title:\s*'给好物下一站'/)
  assert.match(source, /title:\s*'找回那份牵挂'/)
})

test('首页双栏布局：活动/闲置/动态 + 学习区/公告/日程', () => {
  assert.match(source, /class="home-columns"/)
  assert.match(source, /class="home-aside"/)
  assert.match(source, /class="study-note"/)
  assert.match(source, /class="bulletin"/)
  assert.match(source, /class="calendar-panel"/)
  assert.match(source, /class="community-banner"/)
})

test('活动区最多展示三条并使用 V2 EventArt 封面（有图时优先真实图片，缺图使用模块回退）', () => {
  assert.match(source, /slice\(0,\s*3\)/)
  assert.match(source, /<WtEventArt v-else :item="a"\s*\/>/)
  assert.match(source, /firstContentImage\(a, a\.moduleId\)/)
  assert.match(source, /class="event-cover-img"/)
})

test('登录态学习卡展示待复习错题数并跳转错题本', () => {
  assert.match(source, /wrongStats/)
  assert.match(source, /错题待复习/)
  assert.match(source, /to:\s*'\/ai\/wrong'/)
})
