import test from 'node:test'
import assert from 'node:assert/strict'
import { responseAction } from './request-policy.mjs'
test('permission denied never expires the session', () => {
  assert.equal(responseAction(403, 403), null)
  assert.equal(responseAction(undefined, 403), null)
})
test('session expiry and maintenance are recognized in HTTP and business responses', () => {
  assert.equal(responseAction(401, 200), 'login')
  assert.equal(responseAction(undefined, 401), 'login')
  assert.equal(responseAction(503, 200), 'maintenance')
  assert.equal(responseAction(undefined, 503), 'maintenance')
  assert.equal(responseAction(undefined, undefined), null)
})
