import test from 'node:test'
import assert from 'node:assert/strict'
import { buildWsUrl, reconnectDelay, parseChatEvent, createRequestId } from './chatSocketCore.mjs'

test('短票据连接地址使用当前协议与主机', () => {
  assert.equal(buildWsUrl('abc 123', { protocol: 'https:', host: 'campus.test' }), 'wss://campus.test/ws/chat?ticket=abc%20123')
  assert.equal(buildWsUrl('t', { protocol: 'http:', host: 'localhost:5173' }), 'ws://localhost:5173/ws/chat?ticket=t')
})

test('指数退避有上限并可注入抖动', () => {
  assert.equal(reconnectDelay(0, () => 0), 1000)
  assert.equal(reconnectDelay(3, () => 0), 8000)
  assert.equal(reconnectDelay(20, () => 0), 30000)
})

test('仅接受合法聊天事件JSON', () => {
  assert.deepEqual(parseChatEvent('{"type":"chat.message","message":{"id":1}}'), { type: 'chat.message', message: { id: 1 } })
  assert.equal(parseChatEvent('not json'), null)
  assert.equal(parseChatEvent('{"message":{}}'), null)
})

test('请求ID连续生成且不重复', () => {
  const first = createRequestId('chat')
  const second = createRequestId('chat')
  assert.notEqual(first, second)
  assert.match(first, /^chat-/)
})
