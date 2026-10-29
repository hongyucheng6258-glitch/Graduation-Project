import request from './request'

/** 管理员登录 API */
export function adminLogin(data) {
  return request.post('/auth/login', data)
}

/** 获取图形验证码（公开接口 /api/auth/captcha，绕开 /api/admin baseURL） */
export async function getCaptcha() {
  const axios = (await import('axios')).default
  const res = await axios.get('/api/auth/captcha')
  return res.data.data
}
