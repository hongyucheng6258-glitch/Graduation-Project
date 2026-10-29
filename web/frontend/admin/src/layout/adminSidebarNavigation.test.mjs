import test from 'node:test'
import assert from 'node:assert/strict'
import {
  findNavigationGroup,
  matchesNavigationPath,
  syncExpandedGroupForRoute,
  toggleExpandedGroup
} from './adminSidebarNavigation.mjs'

const groups = [
  { label: '概览', items: [{ to: '/dashboard' }] },
  { label: '内容审核', items: [{ to: '/audit/activity' }] },
  { label: '系统', items: [{ to: '/system/config' }, { to: '/system' }] }
]

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

test('finds the group containing the active route', () => {
  assert.equal(findNavigationGroup(groups, '/audit/activity/42'), '内容审核')
  assert.equal(findNavigationGroup(groups, '/missing'), null)
})

test('opens a selected group and collapses it when selected again', () => {
  assert.equal(toggleExpandedGroup(null, '内容审核'), '内容审核')
  assert.equal(toggleExpandedGroup('内容审核', '系统'), '系统')
  assert.equal(toggleExpandedGroup('内容审核', '内容审核'), null)
})

test('opens a new route group without reopening a manually collapsed current group', () => {
  assert.equal(syncExpandedGroupForRoute(null, '内容审核', '内容审核'), null)
  assert.equal(syncExpandedGroupForRoute(null, '内容审核', '系统'), '系统')
})
