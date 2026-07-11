<template>
  <div class="game-chat">
    <GameChatHeader
      :connected="connected"
      :reconnecting="reconnecting"
      :online-count="onlineCount"
    />

    <div class="chat-layout">
      <div class="chat-panel">
        <ChatAnnouncement v-if="showAnnouncement" @close="showAnnouncement = false" />

        <div class="message-area">
          <!-- Connection state -->
          <ChatConnectionState
            v-if="!auth.isAuthenticated"
            state="unauthenticated"
            @login="goToLogin"
          />
          <ChatConnectionState
            v-else-if="reconnecting"
            state="reconnecting"
          />

          <div ref="scrollContainer" class="message-scroll" @scroll="onScroll">
            <!-- Load more -->
            <div v-if="hasMore && !loadingHistory" class="load-more-wrap">
              <button class="load-more-btn" @click="handleLoadMore">加载更早消息</button>
            </div>
            <div v-if="loadingHistory" class="load-more-wrap">
              <span class="loading-text">加载中...</span>
            </div>

            <!-- Empty state -->
            <div v-if="messages.length === 0 && !loadingHistory" class="empty-state">
              <div class="empty-icon">
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
              </div>
              <p class="empty-text">暂无消息，来说点什么吧！</p>
            </div>

            <ChatMessageItem
              v-for="msg in messages"
              :key="msg.clientMessageId || msg.id"
              :message="msg"
              :is-self="msg.username === auth.username"
              @retry="retryMessage(msg)"
            />
          </div>

          <ChatLatestDivider
            v-if="messages.length > 0"
            :is-at-bottom="isAtBottom"
            :has-new-messages="hasNewMessages"
            :messages-length="messages.length"
            @scroll-to-bottom="scrollToBottom(true)"
          />
        </div>

        <ChatComposer
          v-if="auth.isAuthenticated"
          :connected="connected"
          :send-loading="sendLoading"
          @send="handleSend"
        />
        <div v-else class="login-prompt">
          <button class="login-btn" @click="goToLogin">登录后参与聊天</button>
        </div>
      </div>

      <aside class="chat-sidebar">
        <ChatMemberPanel
          :online-count="onlineCount"
          :members="members"
        />
        <ChatRulesCard />
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useGameChat, type LocalChatMessage } from '@/composables/useGameChat'
import GameChatHeader from './GameChatHeader.vue'
import ChatAnnouncement from './ChatAnnouncement.vue'
import ChatMessageItem from './ChatMessageItem.vue'
import ChatLatestDivider from './ChatLatestDivider.vue'
import ChatComposer from './ChatComposer.vue'
import ChatMemberPanel from './ChatMemberPanel.vue'
import ChatRulesCard from './ChatRulesCard.vue'
import ChatConnectionState from './ChatConnectionState.vue'

const props = defineProps<{
  gameId: string
  homeTeam?: string
  awayTeam?: string
}>()

const auth = useAuthStore()
const router = useRouter()

const gameIdFn = () => props.gameId
const {
  messages, presence, connected, reconnecting, sendLoading, hasMore,
  loadingHistory, loadHistory, sendMessage, retryMessage,
} = useGameChat(gameIdFn)

// Scroll management
const scrollContainer = ref<HTMLElement | null>(null)
const isAtBottom = ref(true)
const hasNewMessages = ref(false)

function checkIfAtBottom() {
  const el = scrollContainer.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 60
}

function scrollToBottom(smooth = false) {
  const el = scrollContainer.value
  if (!el) return
  if (smooth) {
    el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
  } else {
    el.scrollTop = el.scrollHeight
  }
  hasNewMessages.value = false
}

const isLoadingMore = ref(false)

function onScroll() {
  isAtBottom.value = checkIfAtBottom()
  if (isAtBottom.value) hasNewMessages.value = false
}

watch(() => messages.value.length, () => {
  // Don't auto-scroll when prepending older messages
  if (isLoadingMore.value) return
  nextTick(() => {
    if (isAtBottom.value) scrollToBottom()
    else hasNewMessages.value = true
  })
})

async function handleLoadMore() {
  if (!messages.value.length || !hasMore.value || loadingHistory.value) return
  const el = scrollContainer.value
  const prevHeight = el?.scrollHeight ?? 0

  // Find first message with a numeric server ID
  const firstServerMsg = messages.value.find(m => typeof m.id === 'number')
  if (!firstServerMsg || typeof firstServerMsg.id !== 'number') return

  isLoadingMore.value = true
  try {
    await loadHistory(firstServerMsg.id)
  } finally {
    isLoadingMore.value = false
  }
  nextTick(() => {
    if (el) el.scrollTop = el.scrollHeight - prevHeight
  })
}

const showAnnouncement = ref(true)

const onlineCount = computed(() => presence.value?.onlineCount ?? 0)

const members = computed(() => {
  if (presence.value?.members) {
    return presence.value.members.map(m => ({
      userId: m.userId,
      username: m.username,
      avatarUrl: m.avatarUrl,
      role: m.role,
    }))
  }
  return []
})

async function handleSend(content: string) {
  await sendMessage(content)
}

function goToLogin() {
  router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
}
</script>

<style scoped>
.game-chat {
  --chat-surface: var(--bg-card);
  --chat-panel: var(--bg-panel);
  --chat-border: var(--border-light);
  --chat-text: var(--text-primary);
  --chat-muted: var(--text-secondary);
  --chat-dim: var(--text-muted);
  --chat-accent: var(--accent);
  --chat-accent-soft: var(--accent-lighter);
  --chat-input-bg: var(--bg-input);
  --chat-hover: var(--bg-hover);
  --chat-bubble-bg: var(--bg-hover);
  --chat-bubble-self: var(--accent-lighter);

  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: 760px;
  max-height: calc(100vh - 150px);
  background:
    radial-gradient(ellipse 60% 40% at 10% 0%, rgba(var(--accent-orange-rgb, 232, 93, 38), 0.04), transparent),
    var(--chat-surface);
  border: 1px solid var(--chat-border);
  border-radius: 18px;
  overflow: hidden;
}

.chat-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 0;
  flex: 1;
  min-height: 0;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid var(--chat-border);
}

.message-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  position: relative;
}

.message-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 18px 22px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  scrollbar-width: thin;
  scrollbar-color: var(--border-medium) transparent;
}

.message-scroll::-webkit-scrollbar { width: 6px; }
.message-scroll::-webkit-scrollbar-track { background: transparent; }
.message-scroll::-webkit-scrollbar-thumb { background: var(--border-medium); border-radius: 3px; }
.message-scroll::-webkit-scrollbar-thumb:hover { background: var(--text-muted); }

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 200px;
  gap: 12px;
}

.empty-icon { color: var(--chat-dim, var(--text-muted)); opacity: 0.4; }
.empty-text { font-size: 14px; color: var(--chat-dim, var(--text-muted)); margin: 0; }

.load-more-wrap {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}

.load-more-btn {
  padding: 6px 16px;
  border: 1px solid var(--chat-border, var(--border-light));
  border-radius: var(--radius-full);
  background: var(--chat-surface, var(--bg-card));
  color: var(--chat-accent, var(--accent));
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
}

.load-more-btn:hover {
  background: var(--chat-hover, var(--bg-hover));
  border-color: var(--chat-accent, var(--accent));
}

.loading-text {
  font-size: 12px;
  color: var(--chat-dim, var(--text-muted));
}

.login-prompt {
  padding: 18px;
  display: flex;
  justify-content: center;
}

.login-btn {
  width: 100%;
  height: 44px;
  border: 1px solid var(--chat-accent, var(--accent));
  border-radius: 14px;
  background: transparent;
  color: var(--chat-accent, var(--accent));
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
}

.login-btn:hover {
  background: var(--chat-accent, var(--accent));
  color: #fff;
}

.chat-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px;
  overflow-y: auto;
  min-height: 0;
}

@media (max-width: 1100px) {
  .chat-layout { grid-template-columns: minmax(0, 1fr) 280px; }
}

@media (max-width: 768px) {
  .game-chat { min-height: auto; max-height: none; }
  .chat-layout { grid-template-columns: 1fr; }
  .chat-panel { border-right: none; }
  .chat-sidebar { border-top: 1px solid var(--chat-border); padding: 14px; }
  .message-scroll { padding: 14px 16px; }
}
</style>
