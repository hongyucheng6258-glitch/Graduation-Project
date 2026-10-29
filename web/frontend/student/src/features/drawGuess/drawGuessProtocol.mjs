const MAX_STROKE_POINTS = 64

export function buildDrawGuessWsUrl(ticket, locationLike = globalThis.location) {
  const protocol = locationLike?.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${locationLike.host}/ws/draw-guess?ticket=${encodeURIComponent(ticket)}`
}

export function normalizeCanvasPoint(rect, clientX, clientY) {
  if (!rect || !Number.isFinite(rect.width) || !Number.isFinite(rect.height)
      || rect.width <= 0 || rect.height <= 0) return null
  if (!Number.isFinite(clientX) || !Number.isFinite(clientY)) return null
  return {
    x: clamp((clientX - rect.left) / rect.width),
    y: clamp((clientY - rect.top) / rect.height)
  }
}

export function limitStrokePoints(points, limit = MAX_STROKE_POINTS) {
  if (!Array.isArray(points) || !Number.isInteger(limit) || limit < 2) return []
  if (points.length <= limit) return points.slice()
  return Array.from({ length: limit }, (_, index) => {
    const sourceIndex = Math.round(index * (points.length - 1) / (limit - 1))
    return points[sourceIndex]
  })
}

export function buildDrawMessage(stroke) {
  return { type: 'draw', ...stroke }
}

export function parseDrawGuessEvent(raw) {
  try {
    const value = JSON.parse(raw)
    return value && typeof value.type === 'string' ? value : null
  } catch {
    return null
  }
}

export function reconnectDelay(attempt, random = Math.random) {
  const base = Math.min(30000, 1000 * (2 ** Math.max(0, attempt)))
  const jitter = Math.floor(base * 0.2 * random())
  return Math.min(30000, base + jitter)
}

function clamp(value) {
  return Math.min(1, Math.max(0, value))
}
