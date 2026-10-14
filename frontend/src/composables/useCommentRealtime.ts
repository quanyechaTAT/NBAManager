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
    const previous = client
    client = null
    if (refreshTimer) clearTimeout(refreshTimer)
    refreshTimer = null
    if (previous) void previous.deactivate()
  }

  function connect() {
    disconnect()
    const target = destination()
    if (!target || !auth.token) return

    const wsUrl = import.meta.env.VITE_WS_URL || window.location.origin
    const connection = new Client({
      webSocketFactory: () => new SockJS(`${wsUrl}/ws`),
      connectHeaders: { Authorization: `Bearer ${auth.token}` },
      reconnectDelay: 5000,
      onConnect: () => {
        if (client !== connection) return
        connection.subscribe(target, () => {
          if (client !== connection) return
          if (refreshTimer) clearTimeout(refreshTimer)
          refreshTimer = setTimeout(() => {
            refreshTimer = null
            if (client === connection) onUpdate()
          }, 50)
        })
      },
    })
    client = connection
    client.activate()
  }

  watch([destination, () => auth.token], connect)

  onMounted(connect)
  onUnmounted(disconnect)
}
