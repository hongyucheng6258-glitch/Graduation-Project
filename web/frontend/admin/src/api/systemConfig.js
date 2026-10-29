import request from './request'

/** 通用系统配置 API（管理端在线修改，即时生效） */

/** 获取全部系统配置（可按 category 筛选） */
export function getSystemConfig(category) {
  return request.get('/system/config', { params: { category } })
}

/** 批量更新配置值 → 即时生效 */
export function updateSystemConfig(configs) {
  return request.put('/system/config', configs)
}

/** 新增配置项 */
export function createSystemConfig(data) {
  return request.post('/system/config', data)
}

/** 更新配置项（含元信息） */
export function updateSystemConfigItem(id, data) {
  return request.put(`/system/config/${id}`, data)
}

/** 删除配置项 */
export function deleteSystemConfig(id) {
  return request.delete(`/system/config/${id}`)
}

/** 手动刷新配置缓存 */
export function refreshSystemConfig() {
  return request.post('/system/config/refresh')
}
