import request from './request'

/** 校园互助问答 API */
export function publishQuestion(data) {
  return request.post('/qa', data)
}
export function listQuestion(params) {
  return request.get('/qa/list', { params })
}
export function myQuestion(params) {
  return request.get('/qa/my', { params })
}
export function questionDetail(id, params) {
  return request.get(`/qa/${id}`, { params })
}
export function answerQuestion(id, data) {
  return request.post(`/qa/${id}/answer`, data)
}
export function acceptAnswer(answerId) {
  return request.put(`/qa/answer/${answerId}/accept`)
}
/** AI 参考回答 */
export function aiAnswer(id) {
  return request.post(`/qa/${id}/ai-answer`, {}, { silent: true, timeout: 120000 })
}
