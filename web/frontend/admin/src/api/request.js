import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { shouldAttachAdminToken } from './auth-token'

export { shouldAttachAdminToken }

/**
 * axios 封装（管理端）：统一解包 R，401 跳登录。
 */
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

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    ElMessage.error(res.message || '请求失败')
    if (res.code === 401) {
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_info')
      router.push('/login')
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_info')
      if (router.currentRoute.value.path !== '/login') router.push('/login')
    }
    ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request
