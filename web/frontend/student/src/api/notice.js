import request from './request'

/** 公告与首页聚合 API */
export function listNotice(params) {
  return request.get('/notice/list', { params })
}

export function noticeDetail(id) {
  return request.get(`/notice/${id}`)
}

export function homeAggregate() {
  return request.get('/home/aggregate')
}
