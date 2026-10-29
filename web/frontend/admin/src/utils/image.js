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

/**
 * 将站内相对路径图片（如 /images/a.jpg）解析为当前应用 base 下的完整路径。
 * 管理端 base 为 /admin/，数据库返回的 /images/... 若不补前缀将 404。
 * base 为 / 时（如单测环境）恒等返回，不影响其他场景。
 */
export function resolveAssetUrl(url) {
  if (typeof url !== 'string') return url
  const value = url.trim()
  if (!value.startsWith('/') || value.startsWith('//')) return value
  let base = (import.meta.env && import.meta.env.BASE_URL) || '/'
  if (!base.endsWith('/')) base += '/'
  if (value.startsWith(base)) return value
  return base + value.replace(/^\//, '')
}

export function normalizeImages(item) {
  if (!item || typeof item !== 'object') return []
  const imageList = parseImageValue(item.imageList)
  const list = imageList.length ? imageList : parseImageValue(item.images)
  return list.map(resolveAssetUrl)
}

export function firstValidImage(item) {
  return normalizeImages(item)[0] || ''
}
