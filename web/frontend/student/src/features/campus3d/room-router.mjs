import { createMemoryHistory, createRouter } from 'vue-router'

export const SERVICE_PATHS = Object.freeze({ portal: '/', activity: '/activity', idle: '/idle', partner: '/partner', lost: '/lostfound', qa: '/qa', square: '/social', drawgame: '/draw-guess', notice: '/notice', message: '/message', aichat: '/ai/chat', code: '/ai/code', wrong: '/ai/wrong' })

// Reuse the exact student page components without mounting MainLayout or changing the address bar.
export function createRoomRouter(records, serviceId, isLoggedIn) {
  const landing = SERVICE_PATHS[serviceId] || '/'
  const pages = records.find(record => record.path === '/' && record.children).children
  const history = createMemoryHistory()
  let depth = 0
  const push = history.push.bind(history)
  history.push = (...args) => { depth++; push(...args) }
  history.listen((to, from, info) => { depth = Math.max(0, depth + info.delta) })
  const router = createRouter({
    history,
    routes: [
      ...pages.map(record => ({ ...record, path: `/${record.path}` })),
      ...records.filter(record => ['/login', '/register', '/maintenance'].includes(record.path)),
      { path: '/campus-3d', redirect: landing },
      { path: '/:pathMatch(.*)*', redirect: landing }
    ]
  })
  router.beforeEach((to, from) => {
    if (to.path === '/login' && !to.query.redirect && from.path !== '/login') {
      return { path: '/login', query: { ...to.query, redirect: from.fullPath || landing } }
    }
    return !to.meta.public && !isLoggedIn() ? { path: '/login', query: { redirect: to.fullPath } } : true
  })
  const back = router.back.bind(router)
  router.back = () => depth > 0 ? back() : router.replace(landing)
  return router
}
