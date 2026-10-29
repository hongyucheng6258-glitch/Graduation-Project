let sequence = 0

export function buildWsUrl(ticket, locationLike = globalThis.location) {
  const protocol = locationLike?.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${locationLike.host}/ws/chat?ticket=${encodeURIComponent(ticket)}`
}

export function reconnectDelay(attempt, random = Math.random) {
  const base = Math.min(30000, 1000 * (2 ** Math.max(0, attempt)))
  const jitter = Math.floor(base * 0.2 * random())
  return Math.min(30000, base + jitter)
}

export function parseChatEvent(raw) {
  try {
    const value = JSON.parse(raw)
    return value && typeof value.type === 'string' ? value : null
  } catch {
    return null
  }
}

export function createRequestId(prefix = 'request') {
  sequence += 1
  return `${prefix}-${Date.now().toString(36)}-${sequence.toString(36)}`
}
