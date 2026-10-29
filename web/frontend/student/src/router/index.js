import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn } from '../utils/auth'

/**
 * 路由表 + 登录守卫（A1：未登录访问受限页面自动跳转登录页）。
 */
import { routes } from './routes'

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 登录守卫：非 public 页面必须登录
router.beforeEach((to) => {
  if (!to.meta.public && !isLoggedIn()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
