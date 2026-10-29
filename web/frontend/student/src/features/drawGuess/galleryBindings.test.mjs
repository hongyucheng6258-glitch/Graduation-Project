import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const lobbySource = readFileSync(new URL('../../views/drawGuess/Lobby.vue', import.meta.url), 'utf8')

test('gallery binds to the image URL and title fields returned by the backend', () => {
  assert.ok(lobbySource.includes(':src="record.snapshotUrl"'), 'artwork image must use snapshotUrl')
  assert.ok(lobbySource.includes("record.title || '校园画画房'"), 'caption must use the backend title')
  assert.ok(!/record\.(?:resourceUrl|roomTitle)/.test(lobbySource), 'obsolete field names must not be used')
})

test('gallery opens data-URL artwork in an image preview instead of navigating to it', () => {
  assert.ok(lobbySource.includes(':preview-src-list="[record.snapshotUrl]"'), 'artwork should use the in-page image viewer')
  assert.ok(!/:href="record\.snapshotUrl"/.test(lobbySource), 'data URLs must not be used as navigation targets')
})
