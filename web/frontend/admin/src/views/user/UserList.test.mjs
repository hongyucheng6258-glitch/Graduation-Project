import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'

const source = await fs.readFile(new URL('./UserList.vue', import.meta.url), 'utf8')

test('用户列表使用统一日期工具展示最近登录时间', () => {
  assert.match(source, /utils\/date/)
  assert.match(source, /formatTime\(row\.lastLoginTime\)/)
})
