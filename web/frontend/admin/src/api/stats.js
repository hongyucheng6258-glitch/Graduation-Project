import request from './request'

/** 数据大屏 API（D7） */
export function statsOverview() {
  return request.get('/stats/overview')
}

export function statsTrend() {
  return request.get('/stats/trend')
}

export function statsModule() {
  return request.get('/stats/module')
}

export function statsPie() {
  return request.get('/stats/pie')
}

/** 各类型待办数（后端准确 COUNT；ai 为待审子集，不参与角标合计） */
export function statsPendingCounts() {
  return request.get('/stats/pending-counts')
}
