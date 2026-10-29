export function shouldAttachAdminToken(url = '') {
  const normalized = String(url).replace(/^https?:\/\/[^/]+/, '')
  return normalized !== '/auth/login' && !normalized.startsWith('/api/admin/auth/login') && normalized !== '/auth/captcha'
}
