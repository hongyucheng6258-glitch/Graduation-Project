import request from './request'

/** 统一收藏 API（活动/闲置/失物/动态） */
export function favorite(type, id) {
  return request.post(`/favorite/${type}/${id}`)
}

export function unfavorite(type, id) {
  return request.delete(`/favorite/${type}/${id}`)
}

export function favoriteStatus(type, id) {
  return request.get(`/favorite/${type}/${id}/status`)
}

export function myFavorites(params) {
  return request.get('/favorite/my', { params })
}
