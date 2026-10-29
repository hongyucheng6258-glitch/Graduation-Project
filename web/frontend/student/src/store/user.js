import { defineStore } from 'pinia'
import { getToken, getUserInfo, setLogin, logout as clearLogin } from '../utils/auth'
import * as userApi from '../api/user'

/**
 * 用户状态（Pinia）。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    userInfo: getUserInfo()
  }),
  getters: {
    isLoggedIn: (state) => !!state.token
  },
  actions: {
    /** 登录成功后写入 */
    loginSuccess(token, userInfo) {
      this.token = token
      this.userInfo = userInfo
      setLogin(token, userInfo)
    },
    /** 刷新用户信息 */
    async refresh() {
      if (!this.token) return
      const info = await userApi.getUserInfo()
      this.userInfo = info
      setLogin(this.token, info)
    },
    logout() {
      this.token = null
      this.userInfo = null
      clearLogin()
    }
  }
})
