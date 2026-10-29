import request from './request'

/** 消息中心 API */
export function listMessage(params) {
  return request.get('/message/list', { params })
}

export function unreadCount() {
  return request.get('/message/unread-count')
}

export function markRead(id) {
  return request.put(`/message/${id}/read`)
}

export function markAllRead() {
  return request.put('/message/read-all')
}
