import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

test('lobby skeleton animation is disabled for reduced-motion preferences', async () => {
  const lobby = await readFile(new URL('../../views/drawGuess/Lobby.vue', import.meta.url), 'utf8')

  assert.match(lobby, /@media\s*\(prefers-reduced-motion:\s*reduce\)[\s\S]*?\.room-skeleton[^{]*\{[^}]*animation:\s*none/s)
})
