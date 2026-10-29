import test from 'node:test'
import assert from 'node:assert/strict'
import {
  mergeMessages,
  prependHistory,
  applyUnreadEvent,
  applyReadReceipt,
  optimisticMessage,
  confirmMessage,
  failMessage
} from './chatState.mjs'

const message = (id, extra = {}) => ({
  id,
  conversationId: 8,
  senderId: 1,
  receiverId: 2,
  clientMessageId: `client-${id}`,
  messageType: 'text',
  content: `消息${id}`,
  status: 1,
  createTime: `2026-08-05T10:00:0${id}`,
  ...extra
})

test('实时消息按服务端ID和clientMessageId去重并保持升序', () => {
  const current = [message(2), message(3)]
  const incoming = [message(3), message(4), message(5, { clientMessageId: 'client-4' })]
  assert.deepEqual(mergeMessages(current, incoming).map((item) => item.id), [2, 3, 4])
})

test('历史分页前插且不重复当前消息', () => {
  const current = [message(3), message(4)]
  const history = [message(1), message(2), message(3)]
  assert.deepEqual(prependHistory(current, history).map((item) => item.id), [1, 2, 3, 4])
})

test('临时发送消息始终排列在服务端历史消息末尾', () => {
  const pending = optimisticMessage({
    conversationId: 8,
    senderId: 1,
    receiverId: 2,
    messageType: 'image',
    content: 'data:image/png;base64,AA==',
    clientMessageId: 'pending-image'
  })
  const list = mergeMessages([message(8), message(9)], [pending])
  assert.deepEqual(list.map((item) => item.id ?? item.clientMessageId), [8, 9, 'pending-image'])
})

test('未读事件只更新目标会话并重算总未读', () => {
  const state = {
    conversations: [{ id: 8, unreadCount: 1 }, { id: 9, unreadCount: 2 }],
    unreadTotal: 3
  }
  const next = applyUnreadEvent(state, { conversationId: 8, unreadCount: 4 })
  assert.equal(next.conversations[0].unreadCount, 4)
  assert.equal(next.unreadTotal, 6)
})

test('较旧未读事件不能覆盖同会话较新的服务端事件', () => {
  const state = {
    conversations: [{ id: 8, unreadCount: 4, unreadVersion: 20 }],
    unreadTotal: 4
  }
  const next = applyUnreadEvent(state, { conversationId: 8, unreadCount: 1, version: 19 })
  assert.equal(next.conversations[0].unreadCount, 4)
  assert.equal(next.unreadTotal, 4)
})

test('已读回执标记不大于最后已读ID的己方消息', () => {
  const list = [message(6), message(7), message(8)]
  const next = applyReadReceipt(list, { lastReadMessageId: 7 }, 1)
  assert.equal(next[0].readTime, '已读')
  assert.equal(next[1].readTime, '已读')
  assert.equal(next[2].readTime, undefined)
})

test('乐观消息可确认或转为失败并保留重试数据', () => {
  const pending = optimisticMessage({ conversationId: 8, senderId: 1, receiverId: 2, messageType: 'text', content: '你好', clientMessageId: 'c-1' })
  assert.equal(pending.sendState, 'sending')
  assert.equal(confirmMessage(pending, message(11, { clientMessageId: 'c-1' })).sendState, 'sent')
  assert.equal(failMessage(pending, '网络异常').sendState, 'failed')
})
