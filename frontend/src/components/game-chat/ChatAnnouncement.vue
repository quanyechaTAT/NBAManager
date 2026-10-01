<template>
  <div class="announcement">
    <div class="announcement-icon">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
    </div>
    <div class="announcement-body">
      <span class="announcement-label">系统公告</span>
      <span class="announcement-text">欢迎来到比赛聊天室！请文明发言，共同营造良好的交流环境。</span>
    </div>
    <button class="announcement-close" @click="$emit('close')" aria-label="关闭公告">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
      </svg>
    </button>
  </div>
</template>

<script setup lang="ts">
defineEmits<{ close: [] }>()
</script>

<style scoped>
.announcement {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 18px 0;
  padding: 12px 16px;
  min-height: 52px;
  background: var(--chat-hover, var(--bg-hover));
  border: 1px solid var(--chat-border, var(--border-light));
  border-radius: 12px;
  flex-shrink: 0;
}

.announcement-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(var(--accent-orange-rgb, 8, 145, 178), 0.1);
  color: var(--accent, #0891B2);
  flex-shrink: 0;
}

.announcement-body {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 1.5;
}

.announcement-label {
  font-weight: 700;
  color: var(--accent, #0891B2);
  white-space: nowrap;
  flex-shrink: 0;
}

.announcement-text {
  color: var(--chat-muted, var(--text-secondary));
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.announcement-close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  color: var(--chat-dim, var(--text-muted));
  cursor: pointer;
  flex-shrink: 0;
  transition: all var(--duration-fast) var(--ease-smooth);
}

.announcement-close:hover {
  background: var(--chat-surface, var(--bg-card));
  color: var(--chat-text, var(--text-primary));
}
</style>
