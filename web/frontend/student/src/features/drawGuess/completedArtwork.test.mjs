import test from 'node:test'
import assert from 'node:assert/strict'
import * as completedArtwork from './completedArtwork.mjs'
import { captureCompletedArtwork, dataUrlToBlob } from './completedArtwork.mjs'

test('captures an ended canvas only for its drawer and keeps the source image', () => {
  const imageDataUrl = 'data:image/png;base64,AQID'
  const canvas = { toDataURL: (type) => type === 'image/png' ? imageDataUrl : null }

  assert.deepEqual(captureCompletedArtwork({ roundId: 19, drawerUserId: 7 }, 7, canvas), {
    roundId: 19,
    imageDataUrl
  })
  assert.equal(captureCompletedArtwork({ roundId: 19, drawerUserId: 7 }, 8, canvas), null)
  assert.equal(captureCompletedArtwork({ roundId: null, drawerUserId: 7 }, 7, canvas), null)
})

test('converts a preserved data URL into an uploadable PNG blob', async () => {
  const blob = dataUrlToBlob('data:image/png;base64,AQID')

  assert.equal(blob.type, 'image/png')
  assert.deepEqual([...new Uint8Array(await blob.arrayBuffer())], [1, 2, 3])
})

test('rebuilds persisted pen and eraser strokes on a detached canvas', () => {
  const calls = []
  const context = new Proxy({}, {
    set(target, property, value) {
      calls.push([property, value])
      target[property] = value
      return true
    },
    get(target, property) {
      if (property in target) return target[property]
      return (...args) => calls.push([property, ...args])
    }
  })
  let canvas
  const canvasFactory = () => {
    canvas = {
      width: 0,
      height: 0,
      getContext: () => context,
      toDataURL: type => type === 'image/png' ? 'data:image/png;base64,AQID' : null
    }
    return canvas
  }
  const strokes = [
    { userId: 7, stroke: { points: [{ x: 0.25, y: 0.5 }, { x: 0.75, y: 0.25 }], color: '#304d99', width: 4, tool: 'pen' } },
    { userId: 7, stroke: { points: [{ x: 0.5, y: 0.5 }, { x: 0.6, y: 0.6 }], color: '#304d99', width: 12, tool: 'eraser' } }
  ]

  assert.equal(typeof completedArtwork.renderStoredArtwork, 'function')
  assert.equal(completedArtwork.renderStoredArtwork(strokes, 200, 100, 2, canvasFactory),
    'data:image/png;base64,AQID')
  assert.equal(canvas.width, 400)
  assert.equal(canvas.height, 200)
  assert.ok(calls.some(call => call[0] === 'setTransform' && call[1] === 2))
  assert.ok(calls.some(call => call[0] === 'moveTo' && call[1] === 50 && call[2] === 50))
  assert.ok(calls.some(call => call[0] === 'lineTo' && call[1] === 150 && call[2] === 25))
  assert.ok(calls.some(call => call[0] === 'strokeStyle' && call[1] === '#304d99'))
  assert.ok(calls.some(call => call[0] === 'lineWidth' && call[1] === 4))
  assert.ok(calls.some(call => call[0] === 'globalCompositeOperation' && call[1] === 'destination-out'))
  assert.ok(calls.some(call => call[0] === 'lineCap' && call[1] === 'round'))
  assert.ok(calls.some(call => call[0] === 'lineJoin' && call[1] === 'round'))
})

test('rejects malformed, empty, and unrenderable stored strokes', () => {
  assert.equal(typeof completedArtwork.renderStoredArtwork, 'function')
  assert.equal(completedArtwork.renderStoredArtwork([], 200, 100, 1, () => ({})), null)
  assert.equal(completedArtwork.renderStoredArtwork([{ stroke: { points: [] } }], 200, 100, 1, () => ({})), null)
  assert.equal(completedArtwork.renderStoredArtwork([{ stroke: { points: [{ x: NaN, y: 0.5 }] } }], 200, 100, 1, () => ({})), null)
  assert.equal(completedArtwork.renderStoredArtwork([{ stroke: { points: [{ x: 0.5, y: 0.5 }] } }], 200, 100, 1,
    () => ({ getContext: () => null })), null)
})

test('maps only unique pending artworks that are not already saved', () => {
  const entries = [
    { roundId: 11, turnNumber: 1, strokes: [{ stroke: { points: [{ x: 0.2, y: 0.3 }] } }] },
    { roundId: '12', turnNumber: 2, strokes: [{ stroke: { points: [{ x: 0.4, y: 0.6 }] } }] },
    { roundId: 13, turnNumber: 3, strokes: [{ stroke: { points: [{ x: 0.5, y: 0.5 }] } }] },
    { roundId: 14, turnNumber: 4, strokes: [] },
    { roundId: 11, turnNumber: 1, strokes: [{ stroke: { points: [{ x: 0.9, y: 0.9 }] } }] },
    { roundId: 15, turnNumber: 5, strokes: [{ stroke: { points: [{ x: 0.1, y: 0.1 }] } }] }
  ]
  const calls = []
  const renderArtwork = (...args) => {
    calls.push(args)
    return args[0] === entries[0].strokes ? 'data:image/png;base64,AQID'
      : args[0] === entries[1].strokes ? 'data:image/png;base64,BAUG'
        : null
  }

  assert.equal(typeof completedArtwork.restorePendingArtworks, 'function')
  assert.deepEqual(completedArtwork.restorePendingArtworks(entries, renderArtwork, new Set(['13'])), [
    { roundId: 11, turnNumber: 1, imageDataUrl: 'data:image/png;base64,AQID' },
    { roundId: '12', turnNumber: 2, imageDataUrl: 'data:image/png;base64,BAUG' }
  ])
  assert.deepEqual(calls.map(call => call[0]), [entries[0].strokes, entries[1].strokes, entries[5].strokes])
  assert.equal(calls.every(call => call.length === 1), true)
})
