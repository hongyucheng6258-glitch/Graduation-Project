import { defineStore } from 'pinia'

/** 容错解析本地 JSON（admin_info 损坏时回退 null，避免登录态初始化崩溃） */
function safeParseAdminInfo() {
  try {
    return JSON.parse(localStorage.getItem('admin_info') || 'null')
  } catch (e) {
    localStorage.removeItem('admin_info')
    return null
  }
}

/**
 * 管理员登录态（Pinia）。
 */
export const useAdminStore = defineStore('admin', {
  state: () => ({
    token: localStorage.getItem('admin_token'),
    adminInfo: safeParseAdminInfo()
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isSuper: (state) => state.adminInfo?.role === 'super'
  },
  actions: {
    loginSuccess(token, adminInfo) {
      this.token = token
      this.adminInfo = adminInfo
      localStorage.setItem('admin_token', token)
      localStorage.setItem('admin_info', JSON.stringify(adminInfo))
    },
    logout() {
      this.token = null
      this.adminInfo = null
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_info')
    }
  }
})
