function isValidImage(value) {
  if (typeof value !== 'string') return false
  const url = value.trim()
  return /^(https?:\/\/|\/|data:image\/|blob:)/i.test(url)
}

function cleanImages(values) {
  return values
    .map((value) => (typeof value === 'string' ? value.trim() : ''))
    .filter(isValidImage)
}

function parseImageValue(value) {
  if (Array.isArray(value)) return cleanImages(value)
  if (typeof value !== 'string' || !value.trim()) return []

  const text = value.trim()
  if (/^data:/i.test(text)) return cleanImages([text])
  try {
    const parsed = JSON.parse(text)
    if (Array.isArray(parsed)) return cleanImages(parsed)
    if (typeof parsed === 'string') return cleanImages([parsed])
  } catch (e) {
    // 非 JSON 字符串继续按普通 URL / 逗号分隔处理
  }

  return cleanImages(text.split(','))
}

export function normalizeImages(item) {
  if (!item || typeof item !== 'object') return []
  const imageList = parseImageValue(item.imageList)
  return imageList.length ? imageList : parseImageValue(item.images)
}

export function firstValidImage(item) {
  return normalizeImages(item)[0] || ''
}
