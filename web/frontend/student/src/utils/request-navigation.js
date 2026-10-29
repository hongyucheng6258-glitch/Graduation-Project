import { logout } from './auth'

let activeRouter
export function useRequestRouter(router) {
  activeRouter = router
  return () => { if (activeRouter === router) activeRouter = undefined }
}
export function requestRouter(fallback) { return activeRouter || fallback }

/** 清理失效登录态并把当前（可能是房内 memory router）路由到登录页。 */
export function handleAuthExpired(router, currentPath) {
  logout()
  window.dispatchEvent(new Event('auth-expired'))
  const target = requestRouter(router)
  const redirect = currentPath || target.currentRoute?.value?.fullPath || '/'
  if (target.currentRoute?.value?.path !== '/login') {
    return target.replace({ path: '/login', query: redirect && redirect !== '/' ? { redirect } : {} })
  }
  return Promise.resolve()
}
