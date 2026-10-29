function identity(message) {
  if (message?.id != null) return `id:${message.id}`
  return message?.clientMessageId ? `client:${message.clientMessageId}` : null
}

export function mergeMessages(current = [], incoming = []) {
  const byId = new Map()
  const clientIds = new Set()
  for (const item of [...current, ...incoming]) {
    if (!item) continue
    if (item.clientMessageId && clientIds.has(item.clientMessageId)) continue
    const key = identity(item)
    if (key && byId.has(key)) continue
    if (item.clientMessageId) clientIds.add(item.clientMessageId)
    byId.set(key || `temp:${byId.size}`, item)
  }
  return [...byId.values()].sort((a, b) => {
    if (a.id != null && b.id != null) return Number(a.id) - Number(b.id)
    if (a.id != null) return -1
    if (b.id != null) return 1
    return String(a.createTime || '').localeCompare(String(b.createTime || ''))
  })
}

export function prependHistory(current, history) {
  return mergeMessages(current, history)
}

export function applyUnreadEvent(state, event) {
  const eventVersion = Number(event.version || 0)
  const conversations = state.conversations.map((item) => {
    if (item.id !== event.conversationId) return item
    const currentVersion = Number(item.unreadVersion || 0)
    if (eventVersion && currentVersion > eventVersion) return item
    return {
      ...item,
      unreadCount: Number(event.unreadCount || 0),
      unreadVersion: Math.max(currentVersion, eventVersion)
    }
  })
  return {
    ...state,
    conversations,
    unreadTotal: conversations.reduce((sum, item) => sum + Number(item.unreadCount || 0), 0)
  }
}

export function applyReadReceipt(messages, event, currentUserId) {
  const lastReadId = Number(event.lastReadMessageId || 0)
  return messages.map((item) => item.senderId === currentUserId && item.id != null && item.id <= lastReadId
    ? { ...item, readTime: item.readTime || '已读' }
    : item)
}

export function optimisticMessage(payload) {
  return {
    ...payload,
    id: null,
    createTime: new Date().toISOString(),
    sendState: 'sending',
    status: 0
  }
}

export function confirmMessage(pending, confirmed) {
  return { ...pending, ...confirmed, sendState: 'sent', error: undefined }
}

export function failMessage(pending, error) {
  return { ...pending, sendState: 'failed', error: error || '发送失败' }
}
