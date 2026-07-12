import { onMounted, onUnmounted, watch } from 'vue'
// @ts-ignore
import SockJS from 'sockjs-client/dist/sockjs.min.js'
import { Client } from '@stomp/stompjs'
import { useAuthStore } from '@/stores/auth'

export function useCommentRealtime(destination: () => string | null, onUpdate: () => void) {
  const auth = useAuthStore()
  let client: Client | null = null
  let refreshTimer: ReturnType<typeof setTimeout> | null = null

  function disconnect() {
    if (client) {
      void client.deactivate()
      client = null
    }
  }

  function connect() {
    const target = destination()
    if (!target || !auth.token) return
    disconnect()

    const wsUrl = import.meta.env.VITE_WS_URL || window.location.origin
    client = new Client({
      webSocketFactory: () => new SockJS(`${wsUrl}/ws`),
      connectHeaders: { Authorization: `Bearer ${auth.token}` },
      reconnectDelay: 5000,
      onConnect: () => {
        client?.subscribe(target, () => {
          if (refreshTimer) clearTimeout(refreshTimer)
          refreshTimer = setTimeout(onUpdate, 50)
        })
      },
    })
    client.activate()
  }

  watch(destination, (next, previous) => {
    if (next !== previous) connect()
  })

  onMounted(connect)
  onUnmounted(() => {
    disconnect()
    if (refreshTimer) clearTimeout(refreshTimer)
  })
}
