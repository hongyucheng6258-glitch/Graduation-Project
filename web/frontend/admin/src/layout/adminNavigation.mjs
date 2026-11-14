import { searchKeyword, searchRecordId } from '../utils/searchQuery.mjs'

const groupOrder = ['概览', '用户与社区', '内容审核', '内容管理', '系统管理']

export function adminMenu(routes, isSuper, can = () => false) {
  return groupOrder.map(label => ({
    label,
    items: routes.filter(({ meta }) =>
      meta?.menu?.group === label && (!meta.superOnly || isSuper) && (!meta.permission || can(meta.permission))
    )
  })).filter(({ items }) => items.length)
}

export function listPage(value) {
  const page = typeof value === 'string' && /^[1-9]\d*$/.test(value) ? Number(value) : 1
  return Number.isSafeInteger(page) && page <= 2147483647 ? page : 1
}

export function noticeListLocation(page, status) {
  const value = listPage(page)
  const query = value > 1 ? { page: String(value) } : {}
  if (['0', '1', '2'].includes(status)) query.status = status
  return { name: 'NoticeManage', query }
}

export function adminBreadcrumbs(route, routes) {
  if (route.name === 'Dashboard') return [{ label: '梧桐校园' }, { label: route.meta.title }]
  const home = { label: '首页', to: { name: 'Dashboard' } }
  const parent = route.meta.activeMenu && routes.find(({ name }) => name === route.meta.activeMenu)
  const current = parent || route
  const group = current.meta.menu?.group
  const trail = [home]
  const record = searchRecordId(route.query.record)
  const detail = record && route.meta.detailTitle
  const keyword = searchKeyword(route.query.searchQ)

  if (detail && keyword) {
    const target = route.query.searchTarget
    trail.push({ label: '全局搜索', to: {
      name: 'AdminSearch',
      query: { q: keyword, scope: route.query.searchScope, pages: route.query.searchPages },
      hash: typeof target === 'string' && /^result-[a-z]+-[1-9]\d*$/.test(target) ? `#${target}` : ''
    } })
    return [...trail, { label: detail }]
  }

  if (group && group !== '概览' && group !== current.meta.title) trail.push({ label: group })
  if (parent && route.name === 'NoticeEdit') {
    const draft = typeof route.query.draft === 'string' && route.query.draft
    trail.push({ label: parent.meta.title, to: noticeListLocation(route.query.page, route.query.status) })
    return [...trail, { label: route.params.id || draft ? '编辑公告' : '新建公告' }]
  }
  if (detail) {
    const query = { ...route.query }
    for (const key of ['record', 'searchQ', 'searchScope', 'searchPages', 'searchTarget']) delete query[key]
    if (route.meta.detailTitle === '审核详情' && query.view !== 'all') query.view = 'pending'
    trail.push({ label: route.meta.title, to: { name: route.name, query } })
    return [...trail, { label: detail }]
  }
  return [...trail, { label: route.meta.title || '管理后台' }]
}

export function pendingQueues(routes) {
  return routes.flatMap(({ name, meta }) => {
    const key = meta?.menu?.countKey
    if (!key) return []
    return [{
      key,
      label: key === 'ai' ? 'AI 风险复核' : meta.title,
      to: key === 'ai' ? { name } : { name, query: { view: 'pending' } }
    }]
  }).sort((left, right) => Number(left.key === 'ai') - Number(right.key === 'ai'))
}

export function pendingTotal(counts) {
  const keys = ['activity', 'idle', 'lostfound', 'post', 'partner', 'report']
  return keys.reduce((total, key) => total + (Number(counts[key]) || 0), 0)
}
