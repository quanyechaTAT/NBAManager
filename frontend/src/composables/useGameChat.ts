import { ref, onMounted, onUnmounted, watch } from 'vue'
// @ts-ignore
import SockJS from 'sockjs-client/dist/sockjs.min.js'
import { Client, type IMessage } from '@stomp/stompjs'
import { useAuthStore } from '@/stores/auth'
import { fetchChatHistory, type ChatMessageResponse, type ChatPresenceResponse } from '@/api/chat'

export type MessageStatus = 'pending' | 'sent' | 'failed'

export interface LocalChatMessage {
  id: number | string
  clientMessageId: string
  gameId: string
  userId: number
  username: string
  avatarUrl: string | null
  role: string
  type: string
  content: string | null
  imageUrl: string | null
  createdAt: string
  deleted: boolean
  status: MessageStatus
}

export function useGameChat(gameId: () => string) {
  const auth = useAuthStore()
  const messages = ref<LocalChatMessage[]>([])
  const presence = ref<ChatPresenceResponse | null>(null)
  const connected = ref(false)
  const reconnecting = ref(false)
  const sendLoading = ref(false)
  const hasMore = ref(true)
  const loadingHistory = ref(false)

  let stompClient: Client | null = null
  let currentGameId: string | null = null
  const pendingMessages = new Map<string, LocalChatMessage>()
  const seenIds = new Set<string>()
  const sendTimeouts = new Map<string, ReturnType<typeof setTimeout>>()

  function makeClientMessageId(): string {
    return crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`
  }

  function messageKey(msg: { id?: number | string; clientMessageId?: string }): string {
    return String(msg.id ?? msg.clientMessageId ?? '')
  }

  function addMessage(msg: LocalChatMessage, skipDedupe = false) {
    const key = messageKey(msg)
    if (!skipDedupe && seenIds.has(key)) return
    seenIds.add(key)
    messages.value.push(msg)
  }

  function replacePendingWithConfirmed(response: ChatMessageResponse) {
    // Add server ID to seenIds to prevent duplicates on history reload
    const serverKey = String(response.id)
    seenIds.add(serverKey)

    const cid = response.clientMessageId
    if (cid && pendingMessages.has(cid)) {
      const idx = messages.value.findIndex(m => m.clientMessageId === cid)
      if (idx >= 0) {
        messages.value[idx] = { ...response, status: 'sent' }
      }
      pendingMessages.delete(cid)
      const timeout = sendTimeouts.get(cid)
      if (timeout) { clearTimeout(timeout); sendTimeouts.delete(cid) }
    } else {
      addMessage({ ...response, status: 'sent' })
    }
  }

  async function loadHistory(beforeId?: number) {
    const gid = gameId()
    if (!gid || loadingHistory.value) return
    loadingHistory.value = true
    try {
      const { data } = await fetchChatHistory(gid, beforeId, 50)
      // API returns DESC order; reverse to chronological
      const chronological = [...data.messages].reverse()
      const wasEmpty = messages.value.length === 0

      const newMessages: LocalChatMessage[] = []
      for (const msg of chronological) {
        const key = messageKey(msg)
        if (!seenIds.has(key)) {
          seenIds.add(key)
          newMessages.push({ ...msg, status: 'sent' })
        }
      }

      if (beforeId) {
        // Prepend older messages as a batch (maintains correct order)
        messages.value = [...newMessages, ...messages.value]
      } else {
        messages.value.push(...newMessages)
      }

      hasMore.value = data.hasMore
      return wasEmpty
    } catch (e) {
      console.error('加载聊天历史失败:', e)
    } finally {
      loadingHistory.value = false
    }
  }

  function connect() {
    const gid = gameId()
    if (!gid) return
    if (!auth.token) return
    if (stompClient?.active) {
      if (currentGameId === gid) return
      disconnect()
    }

    currentGameId = gid
    reconnecting.value = !connected.value

    const wsUrl = import.meta.env.VITE_WS_URL || window.location.origin

    stompClient = new Client({
      webSocketFactory: () => new SockJS(`${wsUrl}/ws`),
      connectHeaders: {
        Authorization: `Bearer ${auth.token}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => {
        if (import.meta.env.DEV) {
          console.debug('[STOMP]', str)
        }
      },
      onConnect: () => {
        connected.value = true
        reconnecting.value = false
        console.info('[chat] connected, subscribing gameId=', gid)

        stompClient?.subscribe(`/topic/chat/${gid}`, (msg: IMessage) => {
          try {
            const data = JSON.parse(msg.body)
            console.info('[chat] message received', { gameId: gid, messageId: data.id, type: data.type })
            // Handle MESSAGE_DELETED events
            if (data.type === 'MESSAGE_DELETED' && data.messageId) {
              const idx = messages.value.findIndex(m => m.id === data.messageId)
              if (idx >= 0) {
                messages.value[idx] = { ...messages.value[idx], deleted: true, content: null, imageUrl: null }
              }
              return
            }
            // Handle regular messages
            replacePendingWithConfirmed(data as ChatMessageResponse)
          } catch { /* ignore */ }
        })

        stompClient?.subscribe(`/topic/chat/${gid}/presence`, (msg: IMessage) => {
          try {
            presence.value = JSON.parse(msg.body)
          } catch { /* ignore */ }
        })

        stompClient?.subscribe('/user/queue/chat-errors', (msg: IMessage) => {
          try {
            const err = JSON.parse(msg.body)
            console.warn('[chat] error:', err)
            // Try to match by clientMessageId from error response
            const cid = err.clientMessageId
            if (cid && pendingMessages.has(cid)) {
              const pm = pendingMessages.get(cid)!
              pm.status = 'failed'
              pendingMessages.delete(cid)
            } else {
              // Fallback: mark first pending as failed
              for (const [key, pm] of pendingMessages) {
                if (pm.status === 'pending') {
                  pm.status = 'failed'
                  pendingMessages.delete(key)
                  break
                }
              }
            }
          } catch { /* ignore */ }
        })
      },
      onDisconnect: () => {
        console.warn('[chat] disconnected')
        connected.value = false
      },
      onStompError: (frame) => {
        console.error('[chat] STOMP error:', frame.headers['message'], frame.body)
        connected.value = false
        reconnecting.value = true
      },
      onWebSocketError: (event) => {
        console.error('[chat] WebSocket error', event)
        connected.value = false
        reconnecting.value = true
      },
      onWebSocketClose: (event) => {
        console.warn('[chat] WebSocket closed', event.code, event.reason)
        connected.value = false
      },
    })

    stompClient.activate()
  }

  function disconnect() {
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
    connected.value = false
    reconnecting.value = false
    currentGameId = null
  }

  async function sendMessage(content: string, type: 'TEXT' | 'IMAGE' = 'TEXT', imageUrl?: string): Promise<boolean> {
    const gid = gameId()
    if (!content.trim() && type === 'TEXT') return false
    if (!connected.value || !stompClient || sendLoading.value) return false
    if (!auth.token) return false

    sendLoading.value = true
    const clientMessageId = makeClientMessageId()

    const localMsg: LocalChatMessage = {
      id: clientMessageId,
      clientMessageId,
      gameId: gid,
      userId: 0,
      username: auth.username || '我',
      avatarUrl: auth.avatarUrl,
      role: auth.profile?.role || 'USER',
      type,
      content: content.trim(),
      imageUrl: imageUrl || null,
      createdAt: new Date().toISOString(),
      deleted: false,
      status: 'pending',
    }

    addMessage(localMsg, true)
    pendingMessages.set(clientMessageId, localMsg)

    try {
      const payload: Record<string, unknown> = {
        clientMessageId,
        type,
        content: content.trim(),
      }
      if (imageUrl) payload.imageUrl = imageUrl

      console.info('[chat] sending message', { gameId: gid, clientMessageId, connected: connected.value })
      stompClient.publish({
        destination: `/app/chat/${gid}`,
        body: JSON.stringify(payload),
      })

      const timeout = setTimeout(() => {
        const pm = pendingMessages.get(clientMessageId)
        if (pm && pm.status === 'pending') {
          pm.status = 'failed'
          pendingMessages.delete(clientMessageId)
        }
      }, 10000)
      sendTimeouts.set(clientMessageId, timeout)

      sendLoading.value = false
      return true
    } catch {
      localMsg.status = 'failed'
      pendingMessages.delete(clientMessageId)
      sendLoading.value = false
      return false
    }
  }

  function retryMessage(msg: LocalChatMessage) {
    if (msg.status !== 'failed') return
    msg.status = 'pending'
    pendingMessages.set(msg.clientMessageId, msg)
    const gid = gameId()
    if (!stompClient?.connected) return

    const payload: Record<string, unknown> = {
      clientMessageId: msg.clientMessageId,
      type: msg.type,
      content: msg.content,
    }
    if (msg.imageUrl) payload.imageUrl = msg.imageUrl

    stompClient.publish({
      destination: `/app/chat/${gid}`,
      body: JSON.stringify(payload),
    })

    const timeout = setTimeout(() => {
      const pm = pendingMessages.get(msg.clientMessageId)
      if (pm && pm.status === 'pending') {
        pm.status = 'failed'
        pendingMessages.delete(msg.clientMessageId)
      }
    }, 10000)
    sendTimeouts.set(msg.clientMessageId, timeout)
  }

  watch(() => gameId(), (newId, oldId) => {
    if (newId !== oldId) {
      disconnect()
      messages.value = []
      presence.value = null
      seenIds.clear()
      pendingMessages.clear()
      sendTimeouts.forEach(t => clearTimeout(t))
      sendTimeouts.clear()
      hasMore.value = true
      if (newId && auth.token) {
        loadHistory().then(() => connect())
      }
    }
  })

  onMounted(async () => {
    const gid = gameId()
    if (gid && auth.token) {
      await loadHistory()
      connect()
    }
  })

  onUnmounted(() => {
    disconnect()
    sendTimeouts.forEach(t => clearTimeout(t))
    sendTimeouts.clear()
  })

  return {
    messages,
    presence,
    connected,
    reconnecting,
    sendLoading,
    hasMore,
    loadingHistory,
    loadHistory,
    sendMessage,
    retryMessage,
    connect,
    disconnect,
  }
}
