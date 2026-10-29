import request from './request'

/** 活动组队 API */
export function publishActivity(data) {
  return request.post('/activity', data)
}

export function updateActivity(id, data) {
  return request.put(`/activity/${id}`, data)
}

export function listActivity(params) {
  return request.get('/activity/list', { params })
}

export function activityDetail(id) {
  return request.get(`/activity/${id}`)
}

export function signupActivity(id, data) {
  return request.post(`/activity/${id}/signup`, data)
}

export function cancelActivitySignup(id) {
  return request.delete(`/activity/${id}/signup`)
}

export function activityMembers(id, params) {
  return request.get(`/activity/${id}/members`, { params })
}

export function handleMember(id, approve) {
  return request.put(`/activity/member/${id}/handle`, { approve })
}

export function signinQrcode(id) {
  return request.get(`/activity/${id}/signin-qrcode`)
}

export function myActivities(params) {
  return request.get('/activity/my', { params })
}

export function mySignups(params) {
  return request.get('/activity/my/signup', { params })
}

/** 活动 AI 智能推荐 */
export function recommendActivity() {
  return request.post('/activity/recommend', {}, { silent: true, timeout: 120000 })
}
