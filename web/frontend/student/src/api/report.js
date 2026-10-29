import request from './request'

/** 举报 API */
export function submitReport(data) {
  return request.post('/report', data)
}

export function myReports(params = {}) {
  return request.get('/report/my', { params })
}
