import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchUnreadCount } from '@/api/notification'
import { useAuthStore } from '@/stores/auth'

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  let pollTimer: ReturnType<typeof setInterval> | null = null
  let failCount = 0

  async function refresh() {
    const auth = useAuthStore()
    if (!auth.token) return
    try {
      const { data } = await fetchUnreadCount()
      unreadCount.value = data?.count ?? 0
      failCount = 0
    } catch {
      failCount++
      if (failCount >= 3) {
        stopPolling()
      }
    }
  }

  function startPolling() {
    stopPolling()
    failCount = 0
    refresh()
    pollTimer = setInterval(refresh, 30000)
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return { unreadCount, refresh, startPolling, stopPolling }
})
