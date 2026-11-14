import request from './request'

export const phoneOptions = () => request.get('/auth/phone/options', { silent: true })
export const phoneBinding = () => request.get('/user/verified-phone')
export async function sendPhoneCode(phone, purpose) {
  const path = purpose === 'bind' ? '/user/verified-phone/send' : '/auth/phone/send'
  const result = await request.post(path, { phone, purpose })
  if (result.consoleCode) console.info('[梧桐校园短信测试] 验证码：', result.consoleCode)
}
export const bindPhone = data => request.put('/user/verified-phone', data)
export const loginByPhone = data => request.post('/auth/phone/login', data)
export const resetByPhone = data => request.post('/auth/phone/reset', data)
export const applyRecovery = data => request.post('/auth/recovery/apply', data)
export const recoveryStatus = receipt => request.post('/auth/recovery/status', { receipt })
export const claimPassword = receipt => request.post('/auth/recovery/claim', { receipt })
