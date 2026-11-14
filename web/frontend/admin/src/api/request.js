import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { useAdminStore } from '../store/admin'
import { shouldAttachAdminToken } from './auth-token'

export { shouldAttachAdminToken }

const request = axios.create({
  baseURL: '/api/admin',
  timeout: 60000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token')
  if (token && shouldAttachAdminToken(config.url)) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export function expireAdminSession(authorization, returnPath) {
  const token = localStorage.getItem('admin_token')
  if (!authorization || !token || authorization !== `Bearer ${token}`) return
  useAdminStore().logout()
  const current = router.currentRoute.value
  if (current.name !== 'Login') {
    router.replace({ name: 'Login', query: { redirect: returnPath || current.fullPath } })
  }
}

function showRequestFailure(code, message, config) {
  const authorization = config?.headers?.Authorization
  if (code === 401 && authorization) {
    // Late responses for an earlier session must not clear or interrupt the new one.
    if (authorization !== `Bearer ${localStorage.getItem('admin_token')}`) return
    expireAdminSession(authorization, config.adminReturnPath)
  }
  ElMessage.error(message)
}

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) return res.data
    const message = res.message || '请求失败'
    showRequestFailure(res.code, message, response.config)
    return Promise.reject(new Error(message))
  },
  (error) => {
    const code = error.response?.status
    const message = error.response?.data?.message || error.message || '网络异常'
    showRequestFailure(code, message, error.config)
    return Promise.reject(error)
  }
)

export default request
