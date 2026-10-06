<template>
  <div
    class="msg-item"
    :class="{
      'is-self': isSelf,
      'is-pending': message.status === 'pending',
      'is-failed': message.status === 'failed',
    }"
  >
    <div class="msg-avatar" :class="{ 'is-admin': message.role === 'ADMIN', 'is-self-avatar': isSelf }">
      <img v-if="message.avatarUrl" :src="message.avatarUrl" class="avatar-img" @error="avatarFailed = true" />
      <span v-if="!message.avatarUrl || avatarFailed" class="avatar-text">{{ avatarLetter }}</span>
    </div>

    <div class="msg-content">
      <div class="msg-meta">
        <span class="msg-username">{{ message.username }}</span>
        <!-- 身份/阵营微徽章（仅在有明确身份或阵营时展示，避免无谓微标堆叠） -->
        <span v-if="message.role === 'ADMIN'" class="role-badge admin-badge">官方解说</span>
        <span v-else-if="fanCampBadge" class="role-badge" :class="fanCampBadge.className">
          {{ fanCampBadge.text }}
        </span>
        <span class="msg-time">{{ formattedTime }}</span>
      </div>

      <div class="msg-bubble" :class="{ 'has-image': message.type === 'IMAGE' && message.imageUrl }">
        <!-- TEXT message -->
        <p v-if="message.type !== 'IMAGE'" class="msg-text">{{ message.content }}</p>

        <!-- IMAGE message -->
        <div v-else-if="message.imageUrl" class="msg-image-wrap">
          <img :src="message.imageUrl" class="msg-image" loading="lazy" alt="图片消息" />
        </div>

        <!-- Deleted message -->
        <p v-if="message.deleted" class="msg-deleted">此消息已被撤回或删除</p>
      </div>

      <!-- Status indicators -->
      <div v-if="message.status === 'pending'" class="msg-status">
        <span class="status-spinner"></span>
        <span class="status-text">发送中...</span>
      </div>
      <div v-if="message.status === 'failed'" class="msg-status is-failed">
        <span class="status-text">发送失败</span>
        <button type="button" class="retry-btn" @click="$emit('retry')">重试</button>
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
  homeTeam?: string
  awayTeam?: string
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

// 根据消息内容或用户名自动匹配阵营助威微徽章
const fanCampBadge = computed(() => {
  const c = props.message.content || ''
  if (props.awayTeam && c.includes(props.awayTeam)) {
    return { text: `${props.awayTeam} 助威军`, className: 'away-camp-badge' }
  }
  if (props.homeTeam && c.includes(props.homeTeam)) {
    return { text: `${props.homeTeam} 拥趸`, className: 'home-camp-badge' }
  }
  return null
})
</script>

<style scoped>
.msg-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 4px 0;
}

.msg-item.is-self {
  flex-direction: row-reverse;
}

.msg-item.is-pending {
  opacity: 0.7;
}

.msg-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--bg-hover, rgba(255, 255, 255, 0.08));
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  border: 1px solid var(--border-medium, rgba(255, 255, 255, 0.1));
}

.msg-avatar.is-self-avatar {
  background: var(--accent-lighter, rgba(8, 145, 178, 0.14));
  color: var(--accent, #0891B2);
  border-color: rgba(8, 145, 178, 0.3);
}

.msg-avatar.is-admin {
  background: rgba(245, 158, 11, 0.14);
  color: #F59E0B;
  border-color: rgba(245, 158, 11, 0.35);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.msg-content {
  min-width: 0;
  max-width: 72%;
}

.msg-item.is-self .msg-content {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.msg-item.is-self .msg-meta {
  flex-direction: row-reverse;
}

.msg-username {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-item.is-self .msg-username {
  color: var(--accent, #0891B2);
}

/* 身份/阵营微徽章 */
.role-badge {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}

.admin-badge {
  background: rgba(245, 158, 11, 0.12);
  color: #F59E0B;
  border: 1px solid rgba(245, 158, 11, 0.25);
}

.away-camp-badge {
  background: rgba(239, 68, 68, 0.12);
  color: #EF4444;
  border: 1px solid rgba(239, 68, 68, 0.25);
}

.home-camp-badge {
  background: rgba(8, 145, 178, 0.12);
  color: var(--accent, #0891B2);
  border: 1px solid rgba(8, 145, 178, 0.25);
}

.msg-time {
  font-size: 11px;
  color: var(--text-dim, #94a3b8);
  font-variant-numeric: tabular-nums;
}

/* 气泡本体 */
.msg-bubble {
  padding: 8px 12px;
  width: fit-content;
  max-width: 100%;
  background: var(--bg-card, #1A1D24);
  border: 1px solid var(--border-light, rgba(255, 255, 255, 0.08));
  border-radius: 10px;
}

.msg-item.is-self .msg-bubble {
  background: var(--accent-lighter, rgba(8, 145, 178, 0.1));
  border-color: rgba(8, 145, 178, 0.25);
}

.msg-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-primary);
  word-break: break-word;
  white-space: pre-wrap;
}

.msg-image-wrap {
  max-width: 240px;
}

.msg-image {
  max-width: 100%;
  border-radius: 6px;
  display: block;
}

.msg-deleted {
  margin: 0;
  font-size: 12px;
  font-style: italic;
  color: var(--text-dim);
}

/* 状态指示 */
.msg-status {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 3px;
  font-size: 11px;
  color: var(--text-dim);
}

.msg-status.is-failed {
  color: var(--danger, #EF4444);
}

.status-spinner {
  width: 10px;
  height: 10px;
  border: 2px solid var(--border-medium);
  border-top-color: var(--accent, #0891B2);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.retry-btn {
  border: none;
  background: transparent;
  color: var(--accent, #0891B2);
  font-size: 11px;
  cursor: pointer;
  padding: 0;
  text-decoration: underline;
}
</style>
