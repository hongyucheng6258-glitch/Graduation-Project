import { defineStore } from 'pinia'
import { unreadCount } from '../api/message'

/**
 * 消息未读数状态（顶栏铃铛角标，30s 轮询，Q7）。
 */
export const useMessageStore = defineStore('message', {
  state: () => ({
    unread: 0,
    timer: null,
    refreshing: false
  }),
  actions: {
    async refreshUnread() {
      if (this.refreshing) return
      this.refreshing = true
      try {
        const res = await unreadCount()
        this.unread = res.count
      } catch (e) {
        // 未登录或网络异常时静默
      } finally {
        this.refreshing = false
      }
    },
    /** 开启轮询（登录后调用） */
    startPolling() {
      this.refreshUnread()
      if (this.timer) clearInterval(this.timer)
      this.timer = setInterval(() => this.refreshUnread(), 30000)
    },
    stopPolling() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
      this.unread = 0
    }
  }
})
