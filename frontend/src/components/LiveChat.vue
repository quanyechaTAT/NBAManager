<template>
  <div class="live-chat">
    <div class="chat-header">
      <div class="chat-header-left">
        <span class="chat-title">比赛聊天室</span>
        <span class="chat-status" :class="connected ? 'chat-status--live' : 'chat-status--offline'">
          <span class="chat-status-dot"></span>
          {{ connected ? '已连接' : '未连接' }}
        </span>
      </div>
      <span class="chat-count">{{ messages.length }} 条消息</span>
    </div>

    <div class="chat-messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="chat-empty">
        <span class="chat-empty-icon">💬</span>
        <span class="chat-empty-text">暂无消息，来说点什么吧！</span>
      </div>
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="chat-message"
        :class="{ 'chat-message--self': msg.username === auth.username }"
      >
        <div class="message-avatar">
          {{ msg.username?.charAt(0)?.toUpperCase() || '?' }}
        </div>
        <div class="message-body">
          <div class="message-meta">
            <span class="message-username">{{ msg.username }}</span>
            <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
          </div>
          <div class="message-text">{{ msg.content }}</div>
        </div>
      </div>
    </div>

    <div class="chat-input-area">
      <el-input
        v-model="newMessage"
        placeholder="发送消息..."
        maxlength="500"
        show-word-limit
        :disabled="!connected"
        @keyup.enter="sendMessage"
        class="chat-input"
      >
        <template #append>
          <el-button
            type="primary"
            :disabled="!connected || !newMessage.trim()"
            @click="sendMessage"
            class="send-btn"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <line x1="22" y1="2" x2="11" y2="13"/>
              <polygon points="22 2 15 22 11 13 2 9 22 2"/>
            </svg>
          </el-button>
        </template>
      </el-input>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
// @ts-ignore
import SockJS from 'sockjs-client/dist/sockjs.min.js'
import { Client } from '@stomp/stompjs'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{
  gameId: string
}>()

const auth = useAuthStore()

interface ChatMessage {
  username: string
  content: string
  timestamp: string
  gameId: string
}

const messages = ref<ChatMessage[]>([])
const newMessage = ref('')
const connected = ref(false)
const messagesContainer = ref<HTMLElement | null>(null)

let stompClient: Client | null = null

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

function formatTime(t: string) {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function connect() {
  const wsUrl = import.meta.env.VITE_WS_URL || window.location.origin

  stompClient = new Client({
    webSocketFactory: () => new SockJS(`${wsUrl}/ws`),
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    onConnect: () => {
      connected.value = true
      // Subscribe to game chat topic
      stompClient?.subscribe(`/topic/chat/${props.gameId}`, (message) => {
        try {
          const msg: ChatMessage = JSON.parse(message.body)
          messages.value.push(msg)
          scrollToBottom()
        } catch { /* ignore malformed messages */ }
      })
    },
    onDisconnect: () => {
      connected.value = false
    },
    onStompError: () => {
      connected.value = false
    },
  })

  stompClient.activate()
}

function sendMessage() {
  if (!newMessage.value.trim() || !connected.value || !stompClient) return

  const msg: ChatMessage = {
    username: auth.username || '匿名',
    content: newMessage.value.trim(),
    timestamp: new Date().toISOString(),
    gameId: props.gameId,
  }

  stompClient.publish({
    destination: `/app/chat/${props.gameId}`,
    body: JSON.stringify(msg),
  })

  newMessage.value = ''
}

function disconnect() {
  if (stompClient) {
    stompClient.deactivate()
    stompClient = null
    connected.value = false
  }
}

watch(() => props.gameId, () => {
  disconnect()
  if (props.gameId) {
    connect()
  }
})

onMounted(() => {
  if (props.gameId) {
    connect()
  }
})

onUnmounted(() => {
  disconnect()
})
</script>

<style scoped>
.live-chat {
  display: flex;
  flex-direction: column;
  height: 500px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  overflow: hidden;
  position: relative;
}

.live-chat::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), var(--cyan), var(--purple));
  z-index: 1;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-hover);
}

.chat-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
}

.chat-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}

.chat-status--live {
  color: var(--accent);
  background: var(--accent-lighter);
}

.chat-status--offline {
  color: var(--text-dim);
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
}

.chat-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.chat-status--live .chat-status-dot {
  animation: livePulse 1.5s ease-in-out infinite;
  box-shadow: 0 0 6px var(--accent-glow);
}

@keyframes livePulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.chat-count {
  font-size: 11px;
  color: var(--text-dim);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  scrollbar-width: thin;
  scrollbar-color: var(--border-medium) transparent;
}

.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 8px;
}

.chat-empty-icon {
  font-size: 32px;
  opacity: 0.5;
}

.chat-empty-text {
  font-size: 13px;
  color: var(--text-dim);
}

.chat-message {
  display: flex;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  transition: background var(--duration-fast) var(--ease-smooth);
}

.chat-message:hover {
  background: var(--bg-hover);
}

.chat-message--self {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), var(--cyan));
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  font-family: var(--font-heading);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.chat-message--self .message-avatar {
  background: linear-gradient(135deg, var(--purple), var(--purple-light));
}

.message-body {
  min-width: 0;
  max-width: 80%;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.chat-message--self .message-meta {
  flex-direction: row-reverse;
}

.message-username {
  font-size: 12px;
  font-weight: 700;
  color: var(--accent);
  font-family: var(--font-heading);
}

.chat-message--self .message-username {
  color: var(--purple-light);
}

.message-time {
  font-size: 10px;
  color: var(--text-dim);
}

.message-text {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  word-break: break-word;
  padding: 6px 10px;
  background: var(--bg-hover);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
}

.chat-message--self .message-text {
  background: var(--accent-lighter);
  border-color: rgba(0, 255, 136, 0.15);
  color: var(--text-primary);
}

.chat-input-area {
  padding: 12px;
  border-top: 1px solid var(--border-light);
  background: var(--bg-hover);
}

:deep(.chat-input .el-input__wrapper) {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-md) !important;
}

:deep(.chat-input .el-input-group__append) {
  background: transparent !important;
  border: none !important;
  padding: 0 !important;
}

.send-btn {
  border-radius: var(--radius-md) !important;
  padding: 0 14px !important;
}
</style>
