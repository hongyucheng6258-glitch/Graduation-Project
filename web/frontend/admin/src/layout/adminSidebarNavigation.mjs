export function matchesNavigationPath(to, path) {
  if (to === '/dashboard' || to === '/system') return path === to
  return path === to || path.startsWith(`${to}/`)
}
