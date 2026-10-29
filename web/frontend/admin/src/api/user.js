import request from './request'

/** 用户管理 API（D1） */
export function listUsers(params) {
  return request.get('/user/list', { params })
}

export function updateUserStatus(id, status) {
  return request.put(`/user/${id}/status`, { status })
}

export function resetPassword(id) {
  return request.put(`/user/${id}/reset-password`)
}

/** 子管理员管理 API（D8） */
export function listAdmins(params) {
  return request.get('/system/admin', { params })
}

export function createAdmin(data) {
  return request.post('/system/admin', data)
}

export function updateAdmin(id, data) {
  return request.put(`/system/admin/${id}`, data)
}
