import request from './request'

export const createConversation = (data) => request.post('/chat/conversations', data)
export const listConversations = () => request.get('/chat/conversations')
export const getConversation = (id) => request.get(`/chat/conversations/${id}`)
export const hideConversation = (id) => request.delete(`/chat/conversations/${id}`)
export const listChatMessages = (id, params) => request.get(`/chat/conversations/${id}/messages`, { params })
export const sendChatMessage = (id, data) => request.post(`/chat/conversations/${id}/messages`, data)
export const markConversationRead = (id, data = {}) => request.put(`/chat/conversations/${id}/read`, data)
export const getChatUnreadCount = () => request.get('/chat/unread-count')
export const getChatWsTicket = () => request.post('/chat/ws-ticket')
export const blockChatUser = (userId) => request.post(`/chat/block/${userId}`)
export const unblockChatUser = (userId) => request.delete(`/chat/block/${userId}`)
export const reportChatMessage = (id, data) => request.post(`/chat/messages/${id}/report`, data)
