<template>
  <div v-if="hasNewMessages && !isAtBottom" class="divider-wrap">
    <button class="new-messages-btn" @click="$emit('scroll-to-bottom')">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="6 9 12 15 18 9" />
      </svg>
      <span>有新消息</span>
    </button>
  </div>
  <div v-else-if="messagesLength > 0" class="divider-line">
    <span class="divider-text">以下是最新消息</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  isAtBottom: boolean
  hasNewMessages: boolean
  messagesLength?: number
}>()

defineEmits<{ 'scroll-to-bottom': [] }>()
</script>

<style scoped>
.divider-line {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 22px;
  flex-shrink: 0;
}

.divider-line::before,
.divider-line::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--chat-border, var(--border-light));
}

.divider-text {
  font-size: 11px;
  color: var(--chat-dim, var(--text-muted));
  white-space: nowrap;
}

.divider-wrap {
  display: flex;
  justify-content: center;
  padding: 6px 0;
  flex-shrink: 0;
}

.new-messages-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border: 1px solid var(--chat-border, var(--border-light));
  border-radius: var(--radius-full);
  background: var(--chat-surface, var(--bg-card));
  color: var(--chat-accent, var(--accent));
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.new-messages-btn:hover {
  background: var(--chat-hover, var(--bg-hover));
  border-color: var(--chat-accent, var(--accent));
}
</style>
