import test from 'node:test'
import assert from 'node:assert/strict'
import { matchesNavigationPath } from './adminSidebarNavigation.mjs'

test('matches dashboard and nested navigation paths without prefix collisions', () => {
  assert.equal(matchesNavigationPath('/dashboard', '/dashboard'), true)
  assert.equal(matchesNavigationPath('/audit/activity', '/audit/activity/42'), true)
  assert.equal(matchesNavigationPath('/audit/activity', '/audit/idle'), false)
})

test('keeps the administrator account route exact while matching system config', () => {
  assert.equal(matchesNavigationPath('/system', '/system'), true)
  assert.equal(matchesNavigationPath('/system', '/system/config'), false)
  assert.equal(matchesNavigationPath('/system/config', '/system/config'), true)
})
