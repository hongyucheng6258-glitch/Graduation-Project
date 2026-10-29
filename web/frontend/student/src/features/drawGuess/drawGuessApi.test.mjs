import test from 'node:test'
import assert from 'node:assert/strict'
import { createDrawGuessApi } from './drawGuessApi.mjs'

test('draw guess API keeps REST requests under the module prefix', async () => {
  const calls = []
  const request = Object.fromEntries(['get', 'post'].map(method => [method, async (...args) => {
    calls.push([method, ...args])
    return args
  }]))
  const api = createDrawGuessApi(request)

  await api.listRooms({ page: 1 })
  await api.getRoom(12)
  await api.createRoom({ title: '课间画画' })
  await api.joinRoom('ABCD23', { password: 'secret' })
  await api.createWsTicket(12)
  await api.startRoom(12)
  await api.leaveRoom(12)
  await api.recentRooms()
  await api.listRecords()

  assert.deepEqual(calls.map(([method, url]) => [method, url]), [
    ['get', '/draw-guess/rooms'],
    ['get', '/draw-guess/rooms/12'],
    ['post', '/draw-guess/rooms'],
    ['post', '/draw-guess/rooms/ABCD23/join'],
    ['post', '/draw-guess/ws-ticket'],
    ['post', '/draw-guess/rooms/12/start'],
    ['post', '/draw-guess/rooms/12/leave'],
    ['get', '/draw-guess/rooms/recent'],
    ['get', '/draw-guess/records']
  ])
  assert.deepEqual(calls[4][2], { roomId: 12 })
})

test('snapshot upload uses the current room and round with multipart data', async () => {
  const calls = []
  const api = createDrawGuessApi({
    get() {},
    post: async (...args) => { calls.push(args); return args }
  }, () => ({ entries: [], append(key, value) { this.entries.push([key, value]) } }))
  const image = { name: 'drawing.png' }

  await api.uploadSnapshot(9, 33, image)

  assert.equal(calls[0][0], '/draw-guess/rooms/9/rounds/33/snapshot')
  assert.deepEqual(calls[0][1].entries, [['file', image]])
  assert.equal(calls[0][2].headers['Content-Type'], 'multipart/form-data')
})
