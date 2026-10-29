import request from './request'

/** 公告管理 API（D4） */
export function noticeList(params) {
  return request.get('/notice/list', { params })
}

export function noticeDetail(id) {
  return request.get(`/notice/${id}`)
}

export function createNotice(data) {
  return request.post('/notice', data)
}

export function updateNotice(id, data) {
  return request.put(`/notice/${id}`, data)
}

export function publishNotice(id) {
  return request.put(`/notice/${id}/publish`)
}

export function offlineNotice(id) {
  return request.put(`/notice/${id}/offline`)
}

export function deleteNotice(id) {
  return request.delete(`/notice/${id}`)
}
