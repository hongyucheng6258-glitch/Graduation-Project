export function captureCompletedArtwork(event, viewerUserId, canvas) {
  const roundId = event?.roundId
  const drawerUserId = Number(event?.drawerUserId)
  if (roundId == null || viewerUserId == null || drawerUserId !== Number(viewerUserId)
      || typeof canvas?.toDataURL !== 'function') return null

  try {
    const imageDataUrl = canvas.toDataURL('image/png')
    return typeof imageDataUrl === 'string' && imageDataUrl.startsWith('data:image/png;base64,')
      ? { roundId, imageDataUrl }
      : null
  } catch {
    return null
  }
}

export function dataUrlToBlob(imageDataUrl) {
  const match = /^data:(image\/[\w.+-]+);base64,([\w+/=\s]+)$/.exec(imageDataUrl || '')
  if (!match) throw new Error('画作数据格式无效。')

  const binary = atob(match[2].replace(/\s/g, ''))
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index)
  return new Blob([bytes], { type: match[1] })
}

export function renderStoredArtwork(strokes, width, height, pixelRatio, canvasFactory) {
  const ratio = Number(pixelRatio)
  if (!Array.isArray(strokes) || strokes.length === 0
      || !Number.isFinite(width) || width <= 0 || !Number.isFinite(height) || height <= 0
      || !Number.isFinite(ratio) || ratio <= 0 || typeof canvasFactory !== 'function') return null

  const paths = []
  for (const item of strokes) {
    const stroke = item?.stroke
    const points = stroke?.points
    const lineWidth = Number(stroke?.width) || 4
    if (!Array.isArray(points) || points.length === 0 || !Number.isFinite(lineWidth) || lineWidth <= 0
        || !points.every(point => Number.isFinite(point?.x) && Number.isFinite(point?.y)
          && point.x >= 0 && point.x <= 1 && point.y >= 0 && point.y <= 1)) return null
    paths.push({
      points,
      color: typeof stroke.color === 'string' && stroke.color ? stroke.color : '#304d99',
      width: lineWidth,
      tool: stroke.tool === 'eraser' ? 'eraser' : 'pen'
    })
  }

  try {
    const canvas = canvasFactory()
    if (!canvas) return null
    canvas.width = Math.round(width * ratio)
    canvas.height = Math.round(height * ratio)
    if (canvas.width <= 0 || canvas.height <= 0) return null
    const context = canvas.getContext?.('2d')
    if (!context) return null
    context.setTransform(ratio, 0, 0, ratio, 0, 0)

    for (const path of paths) {
      context.save()
      context.globalCompositeOperation = path.tool === 'eraser' ? 'destination-out' : 'source-over'
      context.strokeStyle = path.color
      context.lineWidth = path.width
      context.lineCap = 'round'
      context.lineJoin = 'round'
      context.beginPath()
      context.moveTo(path.points[0].x * width, path.points[0].y * height)
      if (path.points.length === 1) {
        context.lineTo(path.points[0].x * width + 0.1, path.points[0].y * height + 0.1)
      } else {
        for (let index = 1; index < path.points.length; index++) {
          context.lineTo(path.points[index].x * width, path.points[index].y * height)
        }
      }
      context.stroke()
      context.restore()
    }

    const imageDataUrl = canvas.toDataURL('image/png')
    return typeof imageDataUrl === 'string' && imageDataUrl.startsWith('data:image/png;base64,')
      ? imageDataUrl
      : null
  } catch {
    return null
  }
}

export function restorePendingArtworks(entries, renderArtwork, excludedRoundIds = []) {
  if (!Array.isArray(entries) || typeof renderArtwork !== 'function') return []
  const excluded = new Set([...excludedRoundIds].map(roundId => String(roundId)))
  const seen = new Set()
  const restored = []

  for (const entry of entries) {
    const roundId = entry?.roundId
    const key = roundId == null ? '' : String(roundId)
    if (!key || excluded.has(key) || seen.has(key) || !Array.isArray(entry?.strokes) || !entry.strokes.length) continue
    seen.add(key)
    try {
      const imageDataUrl = renderArtwork(entry.strokes)
      if (typeof imageDataUrl !== 'string' || !imageDataUrl.startsWith('data:image/png;base64,')) continue
      restored.push({ roundId, turnNumber: entry.turnNumber, imageDataUrl })
    } catch {
      // Skip a single bad artwork and keep the rest of the room usable.
    }
  }

  return restored
}
