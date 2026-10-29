import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { requestRouter, handleAuthExpired } from './request-navigation'
import { responseAction } from './request-policy.mjs'

/**
 * axios 封装（共享约定 #1）：
 * - 请求自动携带 Authorization: Bearer <token>
 * - 响应统一解包：code!==200 弹提示；401 跳登录
 * - 传 { silent: true } 的请求不弹全局错误提示，错误对象带 code/message 供调用方自行展示
 *   （AI 场景需要区分失败原因并给出重试按钮，见 WrongQuizDrawer / WrongOutlineDialog）
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  const isAuthRequest = /^\/auth\/(login|register|captcha)$/.test(config.url || '')
  if (token && !isAuthRequest) {
    config.headers.Authorization = `Bearer ${token}`
  } else if (isAuthRequest && config.headers?.Authorization) {
    delete config.headers.Authorization
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    if (!response.config?.silent) {
      ElMessage.error(res.message || '请求失败')
    }
    handleResponseAction(res.code)
    const err = new Error(res.message || '请求失败')
    err.code = res.code
    err.message = res.message || '请求失败'
    return Promise.reject(err)
  },
  (error) => {
    const biz = error.response?.data
    const message = biz?.message || error.message || '网络异常'
    if (!error.config?.silent) {
      ElMessage.error(message)
    }
    handleResponseAction(biz?.code, error.response?.status)
    error.code = biz?.code ?? error.response?.status ?? error.code ?? -1
    error.message = message
    return Promise.reject(error)
  }
)

function handleResponseAction(code, status) {
  const action = responseAction(code, status)
  const target = requestRouter(router)
  if (action === 'login') {
    handleAuthExpired(target, target.currentRoute.value.fullPath)
  } else if (action === 'maintenance' && target.currentRoute.value.path !== '/maintenance') {
    target.replace('/maintenance')
  }
}

export default request
