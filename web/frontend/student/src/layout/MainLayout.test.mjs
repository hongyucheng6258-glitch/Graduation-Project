import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const layout = await readFile(new URL('./MainLayout.vue', import.meta.url), 'utf8')
const router = await readFile(new URL('../router/routes.js', import.meta.url), 'utf8')
const activity = await readFile(new URL('../views/activity/List.vue', import.meta.url), 'utf8')
const idle = await readFile(new URL('../views/idle/List.vue', import.meta.url), 'utf8')
const lost = await readFile(new URL('../views/lostfound/List.vue', import.meta.url), 'utf8')
const post = await readFile(new URL('../views/social/PostSquare.vue', import.meta.url), 'utf8')

test('顶部搜索提供分类选择和明确搜索按钮', () => {
  assert.match(layout, /v-model="searchType"/)
  assert.match(layout, /<option value="all">全部<\/option>/)
  assert.match(layout, /<option value="activity">活动<\/option>/)
  assert.match(layout, /class="[^"]*search-submit[^"]*"/)
  assert.doesNotMatch(layout, /搜索活动、闲置、失物、同学/)
})

test('分类搜索跳转到对应模块并携带关键词', () => {
  assert.match(layout, /all:\s*'\/search'/)
  assert.match(layout, /activity:\s*'\/activity'/)
  assert.match(layout, /idle:\s*'\/idle'/)
  assert.match(layout, /lost:\s*'\/lostfound'/)
  assert.match(layout, /post:\s*'\/social'/)
  assert.match(layout, /query:\s*\{\s*q\s*\}/)
  assert.match(router, /path:\s*'search'/)
})

test('各分类页面从查询参数初始化关键词', () => {
  for (const source of [activity, idle, lost, post]) {
    assert.match(source, /useRoute/)
    assert.match(source, /route\.query\.q/)
  }
})
