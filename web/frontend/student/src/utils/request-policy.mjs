export function responseAction(code, status) {
  if (code === 401 || status === 401) return 'login'
  if (code === 503 || status === 503) return 'maintenance'
  return null
}
