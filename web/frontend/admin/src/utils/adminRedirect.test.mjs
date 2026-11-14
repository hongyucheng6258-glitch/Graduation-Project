import test from 'node:test'
import assert from 'node:assert/strict'
import { adminRedirect } from './adminRedirect.mjs'

const router = {
  resolve(value) {
    return { name: value.startsWith('/login') ? 'Login' : 'Audit', fullPath: value, meta: {} }
  }
}

test('admin login restores an internal record link and its query', () => {
  assert.equal(adminRedirect(router, '/audit/activity?record=42&view=pending'), '/audit/activity?record=42&view=pending')
})

test('admin login falls back for external, malformed, and login destinations', () => {
  for (const value of ['https://example.test', '//example.test', '/\\example.test', '/login?redirect=/user', ['/user']]) {
    assert.deepEqual(adminRedirect(router, value), { name: 'Dashboard' })
  }
})
