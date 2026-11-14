import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getAdminSession } from '../api/auth'

function readAdminInfo() {
  try {
    return JSON.parse(localStorage.getItem('admin_info') || 'null')
  } catch {
    localStorage.removeItem('admin_info')
    return null
  }
}

export const useAdminStore = defineStore('admin', () => {
  const token = ref(localStorage.getItem('admin_token'))
  const adminInfo = ref(readAdminInfo())
  const permissions = ref(null)
  const isLoggedIn = computed(() => !!token.value)
  const isSuper = computed(() => adminInfo.value?.role === 'super')
  let sessionRequest

  function can(permission) {
    return permissions.value?.includes(permission) === true
  }

  function loginSuccess(value, info) {
    token.value = value
    adminInfo.value = info
    permissions.value = null
    localStorage.setItem('admin_token', value)
    localStorage.setItem('admin_info', JSON.stringify(info))
  }

  function logout() {
    token.value = null
    adminInfo.value = null
    permissions.value = null
    sessionRequest = undefined
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_info')
  }

  async function loadSession(returnPath) {
    const currentToken = token.value
    if (!currentToken || permissions.value) return
    if (sessionRequest?.token === currentToken) return sessionRequest.promise

    const request = { token: currentToken }
    request.promise = getAdminSession(returnPath).then(({ permissions: grants, ...profile }) => {
      if (sessionRequest !== request || token.value !== currentToken) return
      adminInfo.value = profile
      permissions.value = Array.isArray(grants) ? grants : []
      localStorage.setItem('admin_info', JSON.stringify(profile))
    }).finally(() => {
      if (sessionRequest === request) sessionRequest = undefined
    })
    sessionRequest = request
    return request.promise
  }

  return { token, adminInfo, permissions, isLoggedIn, isSuper, can, loginSuccess, logout, loadSession }
})
