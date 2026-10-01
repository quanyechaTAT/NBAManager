<template>
  <div class="msg-item" :class="{ 'is-self': isSelf, 'is-pending': message.status === 'pending', 'is-failed': message.status === 'failed' }">
    <div class="msg-avatar">
      <img v-if="message.avatarUrl" :src="message.avatarUrl" class="avatar-img" @error="avatarFailed = true" />
      <span v-if="!message.avatarUrl || avatarFailed" class="avatar-text">{{ avatarLetter }}</span>
    </div>

    <div class="msg-content">
      <div class="msg-meta">
        <span class="msg-username">{{ message.username }}</span>
        <span v-if="message.role === 'ADMIN'" class="admin-badge">管理员</span>
        <span class="msg-time">{{ formattedTime }}</span>
      </div>
      <div class="msg-bubble">
        <!-- TEXT message -->
        <p v-if="message.type !== 'IMAGE'" class="msg-text">{{ message.content }}</p>
        <!-- IMAGE message -->
        <div v-else-if="message.imageUrl" class="msg-image-wrap">
          <img :src="message.imageUrl" class="msg-image" loading="lazy" />
        </div>
        <!-- Deleted message -->
        <p v-if="message.deleted" class="msg-deleted">此消息已被删除</p>
      </div>
      <!-- Status indicators -->
      <div v-if="message.status === 'pending'" class="msg-status">
        <span class="status-spinner"></span>
        <span class="status-text">发送中...</span>
      </div>
      <div v-if="message.status === 'failed'" class="msg-status is-failed">
        <span class="status-text">发送失败</span>
        <button class="retry-btn" @click="$emit('retry')">点击重试</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { LocalChatMessage } from '@/composables/useGameChat'

const props = defineProps<{
  message: LocalChatMessage
  isSelf: boolean
}>()

defineEmits<{ retry: [] }>()

const avatarFailed = ref(false)

const avatarLetter = computed(() => {
  const name = props.message.username || '?'
  return name.charAt(0).toUpperCase()
})

const formattedTime = computed(() => {
  if (!props.message.createdAt) return ''
  const d = new Date(props.message.createdAt)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})
</script>

<style scoped>
.msg-item {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.msg-item.is-self {
  flex-direction: row-reverse;
}

.msg-item.is-pending {
  opacity: 0.7;
}

.msg-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--cyan, #06B6D4), var(--purple, #8B5CF6));
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  font-family: var(--font-heading);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.msg-item.is-self .msg-avatar {
  background: linear-gradient(135deg, var(--purple, #8B5CF6), var(--purple-light, #A78BFA));
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.msg-content {
  min-width: 0;
  max-width: 68%;
}

.msg-item.is-self .msg-content {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.msg-item.is-self .msg-meta {
  flex-direction: row-reverse;
}

.msg-username {
  font-size: 14px;
  font-weight: 600;
  color: var(--chat-accent, var(--accent));
  font-family: var(--font-heading);
}

.msg-item.is-self .msg-username {
  color: var(--purple, #8B5CF6);
}

.admin-badge {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(var(--accent-orange-rgb, 8, 145, 178), 0.12);
  color: var(--accent, #0891B2);
  font-size: 10px;
  font-weight: 700;
}

.msg-time {
  font-size: 12px;
  color: var(--chat-dim, var(--text-muted));
  font-variant-numeric: tabular-nums;
}

.msg-bubble {
  padding: 12px 16px;
  width: fit-content;
  max-width: 100%;
  background: var(--chat-bubble-bg, var(--bg-hover));
  border: 1px solid var(--chat-border, var(--border-light));
  border-radius: 14px;
  transition: background var(--duration-fast) var(--ease-smooth);
}

.msg-bubble:hover {
  background: var(--chat-hover, var(--bg-hover));
}

.msg-item.is-self .msg-bubble {
  background: var(--chat-bubble-self, var(--accent-lighter));
  border-color: rgba(var(--accent-orange-rgb, 8, 145, 178), 0.12);
}

.msg-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--chat-text, var(--text-primary));
  word-break: break-word;
  overflow-wrap: break-word;
  white-space: pre-wrap;
}

.msg-image-wrap {
  max-width: 280px;
}

.msg-image {
  max-width: 100%;
  border-radius: 8px;
  display: block;
}

.msg-deleted {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--chat-dim, var(--text-muted));
  font-style: italic;
}

.msg-status {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--chat-dim, var(--text-muted));
}

.msg-status.is-failed {
  color: var(--danger, #ef4444);
}

.status-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid var(--border-light);
  border-top-color: var(--chat-accent, var(--accent));
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.retry-btn {
  border: none;
  background: none;
  color: var(--chat-accent, var(--accent));
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
}

.retry-btn:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .msg-content { max-width: 88%; }
  .msg-avatar { width: 36px; height: 36px; font-size: 14px; }
}

@media (min-width: 769px) and (max-width: 1100px) {
  .msg-content { max-width: 78%; }
}
</style>
