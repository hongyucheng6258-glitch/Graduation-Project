export function normalizeSigninQrContent(response) {
  if (typeof response === 'string') return response.trim()
  if (response && typeof response.qrContent === 'string') return response.qrContent.trim()
  return ''
}

export function parseSigninQrContent(response) {
  const match = /^campus:\/\/signin\/([1-9]\d*)\/([A-Za-z0-9_-]+)$/.exec(normalizeSigninQrContent(response))
  if (!match || !Number.isSafeInteger(Number(match[1]))) return null
  return { activityId: Number(match[1]), token: match[2] }
}
