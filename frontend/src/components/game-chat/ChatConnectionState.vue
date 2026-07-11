<template>
  <div v-if="state === 'reconnecting'" class="conn-state reconnecting">
    <span class="conn-spinner"></span>
    <span>正在重连...</span>
  </div>
  <div v-else-if="state === 'unauthenticated'" class="conn-state unauthenticated">
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
    <span>登录后查看聊天内容</span>
    <button class="conn-login-btn" @click="$emit('login')">去登录</button>
  </div>
</template>

<script setup lang="ts">
defineProps<{ state: 'reconnecting' | 'unauthenticated' }>()
defineEmits<{ login: [] }>()
</script>

<style scoped>
.conn-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 500;
  flex-shrink: 0;
}

.conn-state.reconnecting {
  background: rgba(234, 179, 8, 0.08);
  color: #ca8a04;
  border-bottom: 1px solid rgba(234, 179, 8, 0.15);
}

.conn-state.unauthenticated {
  background: var(--chat-hover, var(--bg-hover));
  color: var(--chat-muted, var(--text-secondary));
  border-bottom: 1px solid var(--chat-border, var(--border-light));
}

.conn-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(202, 138, 4, 0.3);
  border-top-color: #ca8a04;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.conn-login-btn {
  padding: 4px 12px;
  border: 1px solid var(--chat-accent, var(--accent));
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--chat-accent, var(--accent));
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
}

.conn-login-btn:hover {
  background: var(--chat-accent, var(--accent));
  color: #fff;
}
</style>
