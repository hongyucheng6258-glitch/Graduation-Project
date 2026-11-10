import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('./Home.vue', import.meta.url), 'utf8')
const heroSource = await readFile(new URL('../../components/wt/WtHero.vue', import.meta.url), 'utf8')

test('首页 Hero 在按钮下方预告真实活动，照片单独展示', () => {
  assert.match(source, /<WtHero/)
  assert.match(source, /photo="\/images\/home-campus\.webp"/)
  assert.match(source, /primary="\{ label:\s*'发现校园活动'/)
  assert.match(source, /if \(!a\) return null/)
  assert.match(heroSource, /class="hero-actions"[\s\S]+?class="hero-event"[\s\S]+?class="hero-photo"/)
})

test('首页快捷服务按原型顺序提供六个有效服务入口', () => {
  assert.match(source, /class="quick-strip"/)
  assert.match(source, /v-for="e in entries"/)
  const entries = source.match(/const entries = \[([\s\S]*?)\n\]/)[1]
  assert.deepEqual([...entries.matchAll(/title:\s*'([^']+)'[^\n]+?to:\s*'([^']+)'/g)].map((match) => match.slice(1)), [
    ['校园活动', '/activity'], ['闲置互换', '/idle'], ['失物招领', '/lostfound'],
    ['学习搭子', '/partner'], ['互助问答', '/qa'], ['AI 学习', '/ai/chat']
  ])
  assert.match(source, /to="\/campus-3d"/)
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
