import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchUnreadCount } from '@/api/notification'
import { useAuthStore } from '@/stores/auth'

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  let pollTimer: ReturnType<typeof setInterval> | null = null

  async function refresh() {
    const auth = useAuthStore()
    if (!auth.token) return
    try {
      const { data } = await fetchUnreadCount()
      unreadCount.value = data
    } catch {
      // ignore
    }
  }

  function startPolling() {
    stopPolling()
    refresh()
    pollTimer = setInterval(refresh, 30000) // 每30秒轮询
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return { unreadCount, refresh, startPolling, stopPolling }
})
