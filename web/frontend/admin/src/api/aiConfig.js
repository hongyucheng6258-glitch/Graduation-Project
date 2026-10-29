import request from './request'

/** AI 配置/模板/日志 API（D5/D6） */
export function getAiConfig() {
  return request.get('/ai/config')
}

export function updateAiConfig(configs) {
  return request.put('/ai/config', { configs })
}

export function listPrompts(scene) {
  return request.get('/ai/prompt', { params: { scene } })
}

export function createPrompt(data) {
  return request.post('/ai/prompt', data)
}

export function updatePrompt(id, data) {
  return request.put(`/ai/prompt/${id}`, data)
}

export function aiLogs(params) {
  return request.get('/ai/log', { params })
}
