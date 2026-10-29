import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeSigninQrContent } from './signinQr.mjs'

test('直接使用接口返回的签到字符串', () => {
  const content = 'campus://signin/21/a23247edc36e76a8'
  assert.equal(normalizeSigninQrContent(content), content)
})

test('兼容对象形式的 qrContent 字段', () => {
  assert.equal(
    normalizeSigninQrContent({ qrContent: 'campus://signin/21/token' }),
    'campus://signin/21/token'
  )
})

test('无有效签到内容时返回空字符串', () => {
  assert.equal(normalizeSigninQrContent(null), '')
  assert.equal(normalizeSigninQrContent({}), '')
})
