export function adminRedirect(router, value) {
  if (typeof value === 'string' && /^\/(?!\/)/.test(value) && !/[\\\u0000-\u001f]/.test(value)) {
    const target = router.resolve(value)
    if (target.name && target.name !== 'Login' && !target.meta.public) return target.fullPath
  }
  return { name: 'Dashboard' }
}
