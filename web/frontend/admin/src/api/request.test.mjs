import assert from 'node:assert/strict'
import test from 'node:test'
import { shouldAttachAdminToken } from './auth-token.js'

test('管理员登录请求不携带旧管理员令牌', () => {
  assert.equal(shouldAttachAdminToken('/auth/login'), false)
  assert.equal(shouldAttachAdminToken('/auth/config'), true)
})
