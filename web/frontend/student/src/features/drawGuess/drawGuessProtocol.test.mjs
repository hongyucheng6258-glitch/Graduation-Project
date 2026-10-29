import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildDrawGuessWsUrl,
  buildDrawMessage,
  limitStrokePoints,
  normalizeCanvasPoint,
  parseDrawGuessEvent,
  reconnectDelay
} from './drawGuessProtocol.mjs'

test('draw game socket uses its own endpoint and sends the one-time ticket only in the query', () => {
  assert.equal(buildDrawGuessWsUrl('one time', { protocol: 'https:', host: 'campus.test' }),
    'wss://campus.test/ws/draw-guess?ticket=one%20time')
  assert.equal(buildDrawGuessWsUrl('abc', { protocol: 'http:', host: 'localhost:8080' }),
    'ws://localhost:8080/ws/draw-guess?ticket=abc')
})

test('normalizes pointer coordinates and ignores zero-sized canvases', () => {
  assert.deepEqual(normalizeCanvasPoint({ left: 10, top: 20, width: 200, height: 100 }, 110, 70), { x: 0.5, y: 0.5 })
  assert.deepEqual(normalizeCanvasPoint({ left: 10, top: 20, width: 200, height: 100 }, 999, -1), { x: 1, y: 0 })
  assert.equal(normalizeCanvasPoint({ left: 0, top: 0, width: 0, height: 0 }, 1, 1), null)
})

test('limits strokes to the server cap while preserving the first and last points', () => {
  const input = Array.from({ length: 100 }, (_, index) => ({ x: index / 100, y: 0.5 }))
  const limited = limitStrokePoints(input)
  assert.equal(limited.length, 64)
  assert.deepEqual(limited[0], input[0])
  assert.deepEqual(limited.at(-1), input.at(-1))
  assert.deepEqual(limitStrokePoints([]), [])
})

test('sends stroke fields in the flat shape accepted by the server', () => {
  const message = buildDrawMessage({ points: [{ x: 0.1, y: 0.2 }], color: '#304d99', width: 4, tool: 'pen' })
  assert.equal(message.type, 'draw')
  assert.deepEqual(message.points, [{ x: 0.1, y: 0.2 }])
  assert.equal('stroke' in message, false)
})

test('parses only typed events and keeps reconnect delay bounded', () => {
  assert.deepEqual(parseDrawGuessEvent('{"type":"timer","remainingSeconds":12}'), { type: 'timer', remainingSeconds: 12 })
  assert.equal(parseDrawGuessEvent('nope'), null)
  assert.equal(parseDrawGuessEvent('{"remainingSeconds":12}'), null)
  assert.equal(reconnectDelay(4, () => 0), 16000)
  assert.equal(reconnectDelay(99, () => 1), 30000)
})
