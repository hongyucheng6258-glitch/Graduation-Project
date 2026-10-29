import test from 'node:test'
import assert from 'node:assert/strict'
import { formatDate, formatTime } from './date.js'

test('管理端日期工具格式化 ISO 日期和标准时间', () => {
  assert.equal(formatDate('2026-08-06T12:34:56Z'), '2026-08-06')
  assert.equal(formatTime('2026-08-06 12:34:56'), '2026-08-06 12:34:56')
})

test('管理端日期工具对空值返回空字符串', () => {
  assert.equal(formatDate(null), '')
  assert.equal(formatTime(undefined), '')
})
