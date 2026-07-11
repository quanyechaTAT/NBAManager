<template>
  <header class="chat-header">
    <div class="header-left">
      <div class="header-icon">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>
      </div>
      <h2 class="header-title">比赛聊天室</h2>
      <span class="connection-badge" :class="{ 'is-connected': connected, 'is-reconnecting': reconnecting }">
        <span class="connection-dot"></span>
        {{ reconnecting ? '重连中' : connected ? '已连接' : '未连接' }}
      </span>
    </div>

    <div class="header-right">
      <div class="online-info">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </svg>
        <span class="online-count">{{ onlineCount }} 人在线</span>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  connected: boolean
  reconnecting: boolean
  onlineCount: number
}>()
</script>

<style scoped>
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 22px;
  border-bottom: 1px solid var(--chat-border, var(--border-light));
  background: var(--chat-hover, var(--bg-hover));
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent, #E85D26), var(--purple, #8B5CF6));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 12px rgba(var(--accent-orange-rgb, 232, 93, 38), 0.25);
  flex-shrink: 0;
}

.header-title {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--chat-text, var(--text-primary));
  font-family: var(--font-heading);
  letter-spacing: -0.01em;
}

.connection-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  transition: all var(--duration-normal) var(--ease-smooth);
}

.connection-badge.is-connected {
  color: var(--success);
  background: rgba(34, 197, 94, 0.08);
  border-color: rgba(34, 197, 94, 0.2);
}

.connection-badge.is-reconnecting {
  color: #ca8a04;
  background: rgba(234, 179, 8, 0.08);
  border-color: rgba(234, 179, 8, 0.2);
}

.connection-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.connection-badge.is-connected .connection-dot {
  animation: pulse-dot 1.5s ease-in-out infinite;
  box-shadow: 0 0 6px rgba(34, 197, 94, 0.4);
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.online-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--chat-muted, var(--text-secondary));
  font-size: 14px;
  font-weight: 500;
}

.online-count {
  font-variant-numeric: tabular-nums;
}

@media (max-width: 768px) {
  .chat-header {
    padding: 12px 16px;
    flex-wrap: wrap;
    gap: 10px;
  }

  .header-title {
    font-size: 17px;
  }

  .header-icon {
    width: 36px;
    height: 36px;
  }

  .header-icon svg {
    width: 18px;
    height: 18px;
  }
}
</style>
