import { defineStore } from 'pinia'
import { markRaw } from 'vue'
import * as api from '../api/chat'
import { ChatSocket } from '../utils/chatSocket'
import {
  applyReadReceipt,
  applyUnreadEvent,
  confirmMessage,
  failMessage,
  mergeMessages,
  optimisticMessage,
  prependHistory
} from './chatState.mjs'
import { shouldUseChatSocket } from './chatSendPolicy.mjs'

export const useChatStore = defineStore('chat', {
  state: () => ({
    conversations: [],
    messagesByConversation: {},
    historyDone: {},
    unreadTotal: 0,
    socketState: 'disconnected',
    socket: null,
    activeConversationId: null,
    currentUserId: null,
    pendingRequests: new Map(),
    ackTimeoutMs: 8000
  }),
  getters: {
    messages: (state) => (conversationId) => state.messagesByConversation[conversationId] || [],
    connected: (state) => state.socketState === 'connected'
  },
  actions: {
    init(userId) {
      this.currentUserId = Number(userId)
      if (!this.socket) {
        this.socket = markRaw(new ChatSocket({
          onState: (state) => {
            this.socketState = state
            if (state === 'connected') {
              this.refreshUnread()
              this.loadConversations()
            }
          },
          onEvent: (event) => this.handleEvent(event)
        }))
      }
      this.socket.connect()
    },
    destroy() {
      this.socket?.close()
      this.pendingRequests.forEach((pending) => clearTimeout(pending.timer))
      this.pendingRequests.clear()
      this.socket = null
      this.socketState = 'disconnected'
      this.conversations = []
      this.messagesByConversation = {}
      this.unreadTotal = 0
    },
    async loadConversations() {
      this.conversations = await api.listConversations()
      this.unreadTotal = this.conversations.reduce((sum, item) => sum + Number(item.unreadCount || 0), 0)
    },
    async refreshUnread() {
      const result = await api.getChatUnreadCount()
      this.unreadTotal = Number(result.count || 0)
    },
    async createConversation(payload) {
      const conversation = await api.createConversation(payload)
      await this.loadConversations()
      return conversation
    },
    async loadHistory(conversationId, reset = false) {
      const current = reset ? [] : this.messages(conversationId)
      const beforeId = reset ? undefined : current.find((item) => item.id != null)?.id
      const list = await api.listChatMessages(conversationId, { beforeId, limit: 20 })
      this.messagesByConversation[conversationId] = reset ? mergeMessages([], list) : prependHistory(current, list)
      this.historyDone[conversationId] = list.length < 20
      return list
    },
    async send(conversation, messageType, content, retryId, resourceId) {
      const imagePayload = messageType === 'image' && content && typeof content === 'object' ? content : null
      const imageUrl = imagePayload ? imagePayload.url : content
      const imageResourceId = imagePayload ? imagePayload.resourceId : resourceId
      const clientMessageId = retryId || `${this.currentUserId}-${Date.now()}-${Math.random().toString(36).slice(2)}`
      const pending = optimisticMessage({
        conversationId: conversation.id,
        senderId: this.currentUserId,
        receiverId: conversation.peerUserId,
        messageType,
        content: imageUrl,
        resourceId: imageResourceId,
        clientMessageId
      })
      this.messagesByConversation[conversation.id] = mergeMessages(this.messages(conversation.id).filter((m) => m.clientMessageId !== clientMessageId), [pending])
      const payload = { conversationId: conversation.id, clientMessageId, messageType, content: imageUrl, resourceId: imageResourceId }
      if (this.connected && shouldUseChatSocket(messageType)) {
        const result = this.socket.sendMessage(payload)
        if (result.sent) {
          const timer = setTimeout(() => this.compensateSend(result.requestId), this.ackTimeoutMs)
          this.pendingRequests.set(result.requestId, { conversationId: conversation.id, clientMessageId, messageType, content: imageUrl, resourceId: imageResourceId, timer })
          return
        }
      }
      try {
        const confirmed = await api.sendChatMessage(conversation.id, { clientMessageId, messageType, content: imageUrl, resourceId: imageResourceId })
        this.replacePending(conversation.id, clientMessageId, confirmed)
      } catch (error) {
        this.markFailed(conversation.id, clientMessageId, error.message)
      }
    },
    async compensateSend(requestId) {
      const pending = this.pendingRequests.get(requestId)
      if (!pending) return
      this.pendingRequests.delete(requestId)
      try {
        const confirmed = await api.sendChatMessage(pending.conversationId, {
          clientMessageId: pending.clientMessageId,
          messageType: pending.messageType,
          content: pending.content,
          resourceId: pending.resourceId
        })
        this.replacePending(pending.conversationId, pending.clientMessageId, confirmed)
      } catch (error) {
        this.markFailed(pending.conversationId, pending.clientMessageId, error.message)
      }
    },
    replacePending(conversationId, clientMessageId, confirmed) {
      this.messagesByConversation[conversationId] = this.messages(conversationId).map((item) => item.clientMessageId === clientMessageId
        ? confirmMessage(item, confirmed)
        : item)
    },
    markFailed(conversationId, clientMessageId, error) {
      this.messagesByConversation[conversationId] = this.messages(conversationId).map((item) => item.clientMessageId === clientMessageId
        ? failMessage(item, error)
        : item)
    },
    async retry(conversation, message) {
      await this.send(conversation, message.messageType, message.content, message.clientMessageId, message.resourceId)
    },
    async markRead(conversationId) {
      const last = [...this.messages(conversationId)].reverse().find((item) => item.id != null)
      if (this.connected) this.socket.markRead(conversationId, last?.id)
      else await api.markConversationRead(conversationId, { lastReadMessageId: last?.id })
      const next = applyUnreadEvent(this.$state, { conversationId, unreadCount: 0 })
      this.conversations = next.conversations
      this.unreadTotal = next.unreadTotal
    },
    handleEvent(event) {
      if (event.type === 'chat.message' && event.message) {
        const id = event.message.conversationId
        this.messagesByConversation[id] = mergeMessages(this.messages(id), [event.message])
        if (Number(this.activeConversationId) === Number(id)) this.markRead(id)
        this.loadConversations()
      } else if (event.type === 'chat.ack' && event.message) {
        const pending = this.pendingRequests.get(event.requestId)
        if (pending) {
          clearTimeout(pending.timer)
          this.replacePending(pending.conversationId, pending.clientMessageId, event.message)
          this.pendingRequests.delete(event.requestId)
        }
        this.loadConversations()
      } else if (event.type === 'chat.unread') {
        const next = applyUnreadEvent(this.$state, event)
        this.conversations = next.conversations
        this.unreadTotal = next.unreadTotal
      } else if (event.type === 'chat.read-receipt') {
        const id = event.conversationId
        this.messagesByConversation[id] = applyReadReceipt(this.messages(id), event, this.currentUserId)
      } else if (event.type === 'chat.error') {
        const pending = this.pendingRequests.get(event.requestId)
        if (pending) {
          clearTimeout(pending.timer)
          this.markFailed(pending.conversationId, pending.clientMessageId, event.message)
          this.pendingRequests.delete(event.requestId)
        }
      }
    }
  }
})
