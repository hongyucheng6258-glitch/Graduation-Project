import request from './request'

/** 举报处置 API（D3） */
export function reportList(params) {
  return request.get('/report/list', { params })
}

export function reportDetail(id) {
  return request.get(`/report/${encodeURIComponent(id)}`)
}

export function handleReport(id, data) {
  return request.put(`/report/${id}/handle`, data)
}

export function reportChatEvidence(id) {
  return request.get(`/report/${id}/chat-evidence`)
}
