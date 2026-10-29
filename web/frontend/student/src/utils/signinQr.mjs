export function normalizeSigninQrContent(response) {
  if (typeof response === 'string') return response.trim()
  if (response && typeof response.qrContent === 'string') return response.qrContent.trim()
  return ''
}
