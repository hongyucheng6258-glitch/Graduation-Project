import request from './request'

/** 动态广场 API */
export function publishPost(data) {
  return request.post('/post', data)
}

export function postDetail(id) {
  return request.get(`/post/${id}`)
}

export function listPost(params) {
  return request.get('/post/list', { params })
}

export function likePost(id) {
  return request.post(`/post/${id}/like`)
}

export function unlikePost(id) {
  return request.delete(`/post/${id}/like`)
}

export function commentPost(id, content) {
  return request.post(`/post/${id}/comment`, { content })
}

export function listComments(id, params) {
  return request.get(`/post/${id}/comments`, { params })
}
