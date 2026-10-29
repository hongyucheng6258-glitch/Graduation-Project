import test from 'node:test'
import assert from 'node:assert/strict'
import { shouldUseChatSocket } from './chatSendPolicy.mjs'

test('文本消息优先通过 WebSocket 发送', () => {
  assert.equal(shouldUseChatSocket('text'), true)
})

test('Base64 图片消息通过 HTTP 发送以避免 WebSocket 大帧限制', () => {
  assert.equal(shouldUseChatSocket('image'), false)
})
