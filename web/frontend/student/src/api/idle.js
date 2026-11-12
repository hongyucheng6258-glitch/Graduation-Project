import request from './request'

/** 闲置互换 API */
export function publishIdle(data) {
  return request.post('/idle', data)
}

export function listIdle(params) {
  return request.get('/idle/list', { params })
}

export function idleDetail(id) {
  return request.get(`/idle/${id}`)
}

export function updateIdle(id, data) {
  return request.put(`/idle/${id}`, data)
}

export function offlineIdle(id) {
  return request.delete(`/idle/${id}`)
}

export function relistIdle(id) {
  return request.put(`/idle/${id}/relist`)
}

export function myIdle(params) {
  return request.get('/idle/my', { params })
}

export function appoint(id, data) {
  return request.post(`/idle/${id}/appoint`, data)
}

export function handleAppoint(id, accept) {
  return request.put(`/idle/appoint/${id}/handle`, { accept })
}

export function finishAppoint(id) {
  return request.put(`/idle/appoint/${id}/finish`)
}

export function reviewAppoint(id, data) {
  return request.post(`/idle/appoint/${id}/review`, data)
}

export function myAppointments(params) {
  return request.get('/idle/appoint/my', { params })
}

/** 闲置 AI 智能估价 */
export function idleEstimate(data) {
  return request.post('/idle/estimate', data, { silent: true, timeout: 120000 })
}

export function getIdleHandoff(id) {
  return request.get(`/idle/appoint/${id}/handoff`, { silent: true, skipMaintenanceRedirect: true })
}

export function proposeIdleHandoff(id, plan) {
  return request.put(`/idle/appoint/${id}/handoff`, plan)
}

export function confirmIdleHandoff(id, version) {
  return request.put(`/idle/appoint/${id}/handoff/confirm`, { version })
}
