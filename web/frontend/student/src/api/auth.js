import request from './request'

/** 认证相关 API */
export function register(data) {
  return request.post('/auth/register', data)
}

export function login(data) {
  return request.post('/auth/login', data)
}

/** 获取图形验证码（captchaId + base64 图片） */
export function getCaptcha() {
  return request.get('/auth/captcha')
}
