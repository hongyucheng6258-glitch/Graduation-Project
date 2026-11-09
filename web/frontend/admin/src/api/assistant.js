import request from './request'

export const assistantCapabilities = () => request.get('/assistant/capabilities')
export const assistantSessions = () => request.get('/assistant/sessions')
export const assistantHistory = (id) => request.get(`/assistant/sessions/${encodeURIComponent(id)}`)
export const askAssistant = (data) => request.post('/assistant/ask', data)
export const assistantReviews = (id) => request.get(`/assistant/sessions/${encodeURIComponent(id)}/reviews`)
export const prepareAssistantReview = (data) => request.post('/assistant/reviews/prepare', data)
export const confirmAssistantReview = (id) => request.post(`/assistant/reviews/${encodeURIComponent(id)}/confirm`)
