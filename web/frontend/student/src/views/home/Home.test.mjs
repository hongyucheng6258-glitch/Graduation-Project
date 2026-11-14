import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('./Home.vue', import.meta.url), 'utf8')

test('首页使用原型校园照片和可访问的 3D 校园链接', () => {
  assert.match(source, /<WtHero/)
  assert.match(source, /photo="\/images\/home-campus\.webp"/)
  assert.match(source, /photo-to="\/campus-3d"/)
})

test('首页快捷服务按原型顺序提供六个有效服务入口', () => {
  const entries = source.match(/const entries = \[([\s\S]*?)\n\]/)[1]
  assert.deepEqual([...entries.matchAll(/title: '([^']+)'[^\n]+?to: '([^']+)'/g)].map(match => match.slice(1)), [
    ['校园活动', '/activity'], ['闲置互换', '/idle'], ['失物招领', '/lostfound'],
    ['学习搭子', '/partner'], ['互助问答', '/qa'], ['AI 学习', '/ai/chat']
  ])
})

test('首页纵向排列活动、动态、闲置、学习、失物和 AI', () => {
  assert.match(source, /把课余时间，留给热爱[\s\S]+有些小美好，[\s\S]+好物不闲置[\s\S]+今天的难题，[\s\S]+每一份遗失，都有回应[\s\S]+id="ai-section"/)
})

test('学习区查询真实错题统计，AI 问题进入校园向导', () => {
  assert.match(source, /await wrongStats\(\)/)
  assert.match(source, /to="\/ai\/wrong"/)
  assert.match(source, /query: \{ q: question.value, mode: 'guide' \}/)
})
