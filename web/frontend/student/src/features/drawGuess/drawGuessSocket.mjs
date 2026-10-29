import { buildDrawGuessWsUrl, parseDrawGuessEvent, reconnectDelay } from './drawGuessProtocol.mjs'

export class DrawGuessSocketClient {
  constructor({ roomId, getTicket, onEvent, onState, WebSocketCtor = globalThis.WebSocket,
    locationLike = globalThis.location, setTimeoutFn = globalThis.setTimeout,
    clearTimeoutFn = globalThis.clearTimeout, setIntervalFn = globalThis.setInterval,
    clearIntervalFn = globalThis.clearInterval, random = Math.random }) {
    this.roomId = roomId
    this.getTicket = getTicket
    this.onEvent = onEvent
    this.onState = onState
    this.WebSocketCtor = WebSocketCtor
    this.locationLike = locationLike
    this.setTimeoutFn = setTimeoutFn.bind(globalThis)
    this.clearTimeoutFn = clearTimeoutFn.bind(globalThis)
    this.setIntervalFn = setIntervalFn.bind(globalThis)
    this.clearIntervalFn = clearIntervalFn.bind(globalThis)
    this.random = random
    this.socket = null
    this.heartbeat = null
    this.reconnectTimer = null
    this.attempt = 0
    this.closedByUser = false
    this.generation = 0
    this.connecting = false
  }

  get connected() {
    return this.socket?.readyState === (this.WebSocketCtor?.OPEN ?? 1)
  }

  async connect() {
    if (this.closedByUser || this.connecting || this.connected
        || this.socket?.readyState === (this.WebSocketCtor?.CONNECTING ?? 0)) return
    this.clearTimeoutFn(this.reconnectTimer)
    this.reconnectTimer = null
    this.connecting = true
    const generation = ++this.generation
    const isCurrent = () => !this.closedByUser && generation === this.generation
    this.onState?.('connecting')
    try {
      const ticket = await this.getTicket(this.roomId)
      if (!isCurrent()) return
      const socket = new this.WebSocketCtor(buildDrawGuessWsUrl(ticket, this.locationLike))
      this.socket = socket
      socket.onopen = () => {
        if (!isCurrent()) return
        this.attempt = 0
        this.onState?.('connected')
        this.startHeartbeat()
      }
      socket.onmessage = ({ data }) => {
        if (!isCurrent()) return
        const event = parseDrawGuessEvent(data)
        if (event) this.onEvent?.(event)
      }
      socket.onerror = () => { if (isCurrent()) this.onState?.('error') }
      socket.onclose = () => {
        if (!isCurrent()) return
        this.stopHeartbeat()
        this.socket = null
        this.onState?.('disconnected')
        this.scheduleReconnect()
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
    this.clearTimeoutFn(this.reconnectTimer)
    const generation = this.generation
    this.reconnectTimer = this.setTimeoutFn(() => {
      if (!this.closedByUser && generation === this.generation) return this.connect()
      return undefined
    }, reconnectDelay(this.attempt++, this.random))
  }

  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeat = this.setIntervalFn(() => this.send({ type: 'ping' }), 25000)
  }

  stopHeartbeat() {
    if (this.heartbeat != null) this.clearIntervalFn(this.heartbeat)
    this.heartbeat = null
  }

  send(payload) {
    if (!this.connected) return false
    this.socket.send(JSON.stringify(payload))
    return true
  }

  close() {
    this.closedByUser = true
    this.generation++
    this.connecting = false
    this.clearTimeoutFn(this.reconnectTimer)
    this.reconnectTimer = null
    this.stopHeartbeat()
    this.socket?.close()
    this.socket = null
  }
}
