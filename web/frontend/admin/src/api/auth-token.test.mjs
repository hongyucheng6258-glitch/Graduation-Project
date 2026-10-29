import test from 'node:test'
import assert from 'node:assert/strict'
import { shouldAttachAdminToken } from './auth-token.js'

test('相对登录路径不附带管理员 Token', () => {
  assert.equal(shouldAttachAdminToken('/auth/login'), false)
})

test('完整管理登录路径不附带管理员 Token', () => {
  assert.equal(shouldAttachAdminToken('/api/admin/auth/login'), false)
})

test('其他管理接口附带管理员 Token', () => {
  assert.equal(shouldAttachAdminToken('/dashboard/stats'), true)
})
