import request from './request'

/** 举报处置 API（D3） */
export function reportList(params) {
  return request.get('/report/list', { params })
}

export function handleReport(id, data) {
  return request.put(`/report/${id}/handle`, data)
}
