import test from 'node:test'
import assert from 'node:assert/strict'
import { DrawGuessSocketClient } from './drawGuessSocket.mjs'

class FakeSocket {
  static instances = []
  static OPEN = 1
  static CONNECTING = 0

  constructor(url) {
    this.url = url
    this.readyState = FakeSocket.CONNECTING
    this.handlers = {}
    this.sent = []
    FakeSocket.instances.push(this)
  }

  send(value) { this.sent.push(value) }
  close() { this.readyState = 3; this.handlers.close?.({}) }
  open() { this.readyState = FakeSocket.OPEN; this.handlers.open?.({}) }
  receive(value) { this.handlers.message?.({ data: value }) }
  disconnect() { this.readyState = 3; this.handlers.close?.({}) }
  set onopen(value) { this.handlers.open = value }
  set onmessage(value) { this.handlers.message = value }
  set onerror(value) { this.handlers.error = value }
  set onclose(value) { this.handlers.close = value }
}

function makeClient(overrides = {}) {
  const scheduled = []
  const tickets = []
  const events = []
  const states = []
  const client = new DrawGuessSocketClient({
    roomId: 42,
    getTicket: async (roomId) => { tickets.push(roomId); return `ticket-${tickets.length}` },
    onEvent: (event) => events.push(event),
    onState: (state) => states.push(state),
    WebSocketCtor: FakeSocket,
    locationLike: { protocol: 'http:', host: 'campus.test' },
    setTimeoutFn: (callback) => { scheduled.push(callback); return scheduled.length },
    clearTimeoutFn: () => {},
    setIntervalFn: () => 1,
    clearIntervalFn: () => {},
    ...overrides
  })
  return { client, events, states, tickets, scheduled }
}

test('connects to the isolated room socket with a fresh one-time ticket', async () => {
  FakeSocket.instances = []
  const { client, tickets, states } = makeClient()
  await client.connect()

  assert.deepEqual(tickets, [42])
  assert.equal(FakeSocket.instances[0].url, 'ws://campus.test/ws/draw-guess?ticket=ticket-1')
  FakeSocket.instances[0].open()
  assert.equal(states.at(-1), 'connected')
  client.close()
})

test('forwards valid events and never sends through a non-open connection', async () => {
  FakeSocket.instances = []
  const { client, events } = makeClient()
  await client.connect()
  const socket = FakeSocket.instances[0]
  assert.equal(client.send({ type: 'chat', text: 'hello' }), false)
  socket.open()
  assert.equal(client.send({ type: 'chat', text: 'hello' }), true)
  socket.receive('{"type":"room_state"}')
  socket.receive('invalid')
  assert.equal(socket.sent[0], '{"type":"chat","text":"hello"}')
  assert.deepEqual(events, [{ type: 'room_state' }])
  client.close()
})

test('reconnects with a new ticket and user close cancels further reconnects', async () => {
  FakeSocket.instances = []
  const { client, tickets, scheduled } = makeClient()
  await client.connect()
  FakeSocket.instances[0].open()
  FakeSocket.instances[0].disconnect()

  assert.equal(scheduled.length, 1)
  await scheduled[0]()
  assert.deepEqual(tickets, [42, 42])
  assert.match(FakeSocket.instances[1].url, /ticket=ticket-2$/)
  FakeSocket.instances[1].disconnect()
  client.close()
  const reconnectCount = tickets.length
  await scheduled.at(-1)()
  assert.equal(tickets.length, reconnectCount)
})

test('invokes browser timer functions with the global receiver', async () => {
  FakeSocket.instances = []
  const calls = []
  const scheduled = []
  const hostFunction = (name, result) => function (...args) {
    if (this !== globalThis) throw new TypeError('Illegal invocation')
    calls.push(name)
    return typeof result === 'function' ? result(...args) : result
  }
  const { client } = makeClient({
    setTimeoutFn: hostFunction('setTimeout', (callback) => {
      scheduled.push(callback)
      return scheduled.length
    }),
    clearTimeoutFn: hostFunction('clearTimeout'),
    setIntervalFn: hostFunction('setInterval', 1),
    clearIntervalFn: hostFunction('clearInterval')
  })

  await client.connect()
  FakeSocket.instances[0].open()
  FakeSocket.instances[0].disconnect()
  client.close()

  assert.deepEqual(calls, [
    'clearTimeout', 'setInterval', 'clearInterval', 'clearTimeout', 'setTimeout', 'clearTimeout'
  ])
})
