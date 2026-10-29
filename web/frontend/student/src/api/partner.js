import request from './request'

/** 学习搭子 API */
export function publishPartner(data) {
  return request.post('/partner', data)
}
export function listPartner(params) {
  return request.get('/partner/list', { params })
}
export function myPartner(params) {
  return request.get('/partner/my', { params })
}
/** AI 智能匹配 */
export function partnerMatch(data) {
  return request.post('/partner/match', data, { silent: true, timeout: 120000 })
}
export function finishPartner(id) {
  return request.put(`/partner/${id}/finish`)
}
