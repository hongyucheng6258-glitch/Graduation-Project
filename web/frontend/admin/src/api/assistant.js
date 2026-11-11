import request from './request'

export const assistantCapabilities = () => request.get('/assistant/capabilities')
export const assistantSessions = () => request.get('/assistant/sessions')
export const assistantHistory = (id) => request.get(`/assistant/sessions/${encodeURIComponent(id)}`)
export const askAssistant = (data) => request.post('/assistant/ask', data)
export const assistantReviews = (id) => request.get(`/assistant/sessions/${encodeURIComponent(id)}/reviews`)
export const prepareAssistantReview = (data) => request.post('/assistant/reviews/prepare', data)
export const confirmAssistantReview = (id) => request.post(`/assistant/reviews/${encodeURIComponent(id)}/confirm`)
export const assistantDiagnostics = (days) => request.get('/assistant/diagnostics', { params: { days } })
export const listNoticeDrafts = () => request.get('/assistant/notice-drafts')
export const generateNoticeDraft = (text) => request.post('/assistant/notice-drafts', { request: text })
export const getNoticeDraft = (id) => request.get(`/assistant/notice-drafts/${encodeURIComponent(id)}`)
export const saveNoticeDraft = (id, data) => request.post(`/assistant/notice-drafts/${encodeURIComponent(id)}/confirm`, data)
export const listRules = () => request.get('/assistant/rules')
export const askRules = (question) => request.post('/assistant/rules/ask', { question })
export const uploadRule = (file) => {
  const data = new FormData()
  data.append('file', file)
  return request.post('/assistant/rules', data)
}
export const withdrawRule = (id) => request.delete(`/assistant/rules/${id}`)
export const reindexRule = (id) => request.post(`/assistant/rules/${id}/reindex`)

export async function ruleFile({ documentId, version }) {
  return readAdminPdf(`/api/admin/assistant/rules/${documentId}/file?version=${encodeURIComponent(version)}`)
}

export function publicKnowledgeFile(documentId, signal) {
  return readAdminPdf(`/api/admin/ai/agent/knowledge/${encodeURIComponent(documentId)}/file`, signal)
}

async function readAdminPdf(path, signal) {
  const response = await fetch(path, {
    headers: { Authorization: `Bearer ${localStorage.getItem('admin_token') || ''}` },
    signal
  })
  if (!response.ok || !response.headers.get('content-type')?.startsWith('application/pdf')) {
    throw new Error('资料已更新、撤回或不可访问，请重新查询')
  }
  return response.blob()
}
