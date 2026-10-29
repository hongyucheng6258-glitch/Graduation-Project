import { getChatWsTicket } from '../api/chat'
import { buildWsUrl, createRequestId, parseChatEvent, reconnectDelay } from './chatSocketCore.mjs'

export class ChatSocket {
  constructor({ onEvent, onState }) {
    this.onEvent = onEvent
    this.onState = onState
    this.socket = null
    this.heartbeat = null
    this.reconnectTimer = null
    this.attempt = 0
    this.closedByUser = false
    this.generation = 0
    this.connecting = false
  }

  get connected() {
    return this.socket?.readyState === WebSocket.OPEN
  }

  async connect() {
    if (this.connecting || this.connected || this.socket?.readyState === WebSocket.CONNECTING) return
    clearTimeout(this.reconnectTimer)
    this.closedByUser = false
    this.connecting = true
    const generation = ++this.generation
    const isCurrent = () => !this.closedByUser && generation === this.generation
    this.onState?.('connecting')
    try {
      const { ticket } = await getChatWsTicket()
      if (!isCurrent()) return
      const socket = new WebSocket(buildWsUrl(ticket))
      this.socket = socket
      socket.onopen = () => {
        if (!isCurrent()) return
        this.attempt = 0
        this.onState?.('connected')
        this.startHeartbeat()
      }
      socket.onmessage = ({ data }) => {
        if (!isCurrent()) return
        const event = parseChatEvent(data)
        if (event) this.onEvent?.(event)
      }
      socket.onerror = () => { if (isCurrent()) this.onState?.('error') }
      socket.onclose = () => {
        if (!isCurrent()) return
        this.stopHeartbeat()
        this.socket = null
        this.onState?.('disconnected')
        if (!this.closedByUser) this.scheduleReconnect()
      }
    } catch {
      if (!isCurrent()) return
      this.onState?.('disconnected')
      this.scheduleReconnect()
    } finally {
      if (generation === this.generation) this.connecting = false
    }
  }

  scheduleReconnect() {
    if (this.closedByUser) return
    clearTimeout(this.reconnectTimer)
    const generation = this.generation
    const delay = reconnectDelay(this.attempt++)
    this.reconnectTimer = setTimeout(() => {
      if (!this.closedByUser && generation === this.generation) this.connect()
    }, delay)
  }

  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeat = setInterval(() => this.send({ type: 'ping' }), 25000)
  }

  stopHeartbeat() {
    clearInterval(this.heartbeat)
    this.heartbeat = null
  }

  send(payload) {
    if (!this.connected) return false
    this.socket.send(JSON.stringify(payload))
    return true
  }

  sendMessage(payload) {
    const requestId = createRequestId('send')
    return { requestId, sent: this.send({ type: 'chat.send', requestId, ...payload }) }
  }

  markRead(conversationId, lastReadMessageId) {
    return this.send({ type: 'chat.read', requestId: createRequestId('read'), conversationId, lastReadMessageId })
  }

  close() {
    this.closedByUser = true
    this.generation++
    this.connecting = false
    clearTimeout(this.reconnectTimer)
    this.stopHeartbeat()
    this.socket?.close()
    this.socket = null
  }
}
