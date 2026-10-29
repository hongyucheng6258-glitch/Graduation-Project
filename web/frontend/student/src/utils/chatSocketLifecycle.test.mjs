import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

function harness() {
  const tickets = [], sockets = [], timers = new Map(), events = []
  let timerId = 0
  class WebSocket {
    static OPEN = 1
    static CONNECTING = 0
    readyState = 0
    constructor() { sockets.push(this) }
    close() { this.readyState = 3 }
  }
  const context = vm.createContext({
    WebSocket, getChatWsTicket: () => new Promise((resolve, reject) => tickets.push({ resolve, reject })),
    buildWsUrl: () => 'ws://test', reconnectDelay: () => 1000,
    setTimeout: fn => { timers.set(++timerId, fn); return timerId },
    clearTimeout: id => timers.delete(id), setInterval: () => 0, clearInterval() {},
    parseChatEvent: JSON.parse,
  })
  const source = readFileSync(new URL('./chatSocket.js', import.meta.url), 'utf8')
    .replace(/^import .*$/gm, '').replace('export class ChatSocket', 'globalThis.ChatSocket = class ChatSocket')
  vm.runInContext(source, context)
  const client = new context.ChatSocket({ onEvent: event => events.push(event) })
  return { client, tickets, sockets, timers, events }
}

for (const outcome of ['resolve', 'reject']) {
  test(`close while ticket pending ignores late ${outcome}`, async () => {
    const h = harness()
    const pending = h.client.connect()
    h.client.close()
    h.tickets[0][outcome](outcome === 'resolve' ? { ticket: 'test' } : new Error('failed'))
    await pending
    assert.equal(h.sockets.length, 0)
    assert.equal(h.timers.size, 0)
  })
}

test('duplicate connect uses one pending ticket; failure retries while active', async () => {
  const h = harness()
  const pending = h.client.connect()
  await h.client.connect()
  assert.equal(h.tickets.length, 1)
  h.tickets[0].reject(new Error('failed'))
  await pending
  assert.equal(h.timers.size, 1)
  h.client.close()
  assert.equal(h.timers.size, 0)
})

test('previous attempt and socket callbacks cannot overwrite a new connection', async () => {
  const h = harness()
  const first = h.client.connect()
  h.client.close()
  const second = h.client.connect()
  h.tickets[0].resolve({ ticket: 'old' })
  await first
  assert.equal(h.client.connecting, true)
  h.tickets[1].resolve({ ticket: 'new' })
  await second
  const oldSocket = h.sockets[0]
  h.client.close()
  const third = h.client.connect()
  h.tickets[2].resolve({ ticket: 'latest' })
  await third
  oldSocket.onclose()
  oldSocket.onmessage({ data: '{"type":"chat.message"}' })
  assert.equal(h.client.socket, h.sockets[1])
  assert.equal(h.events.length, 0)
  assert.equal(h.timers.size, 0)
  h.client.close()
})
