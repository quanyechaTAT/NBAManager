<template>
  <div class="member-panel">
    <div class="panel-header">
      <h3 class="panel-title">聊天室成员 ({{ onlineCount }})</h3>
    </div>

    <div class="member-list">
      <div
        v-for="member in displayedMembers"
        :key="member.userId"
        class="member-item"
      >
        <div class="member-avatar">
          <img v-if="member.avatarUrl" :src="member.avatarUrl" class="member-avatar-img" @error="onImgError($event)" />
          <span v-else class="member-avatar-text">{{ member.username.charAt(0).toUpperCase() }}</span>
        </div>
        <span class="member-name">{{ member.username }}</span>
        <span v-if="member.role === 'ADMIN'" class="member-admin-badge">管理员</span>
      </div>

      <div v-if="displayedMembers.length === 0" class="member-empty">
        <span>暂无在线成员</span>
      </div>
    </div>

    <button
      v-if="members.length > maxDisplay"
      class="load-more-btn"
      @click="showAll = !showAll"
    >
      <span>{{ showAll ? '收起' : '查看更多' }}</span>
      <svg
        width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
        :style="{ transform: showAll ? 'rotate(180deg)' : 'rotate(0)' }"
      >
        <polyline points="6 9 12 15 18 9" />
      </svg>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

interface Member {
  userId: number
  username: string
  avatarUrl: string | null
  role: string
}

const props = defineProps<{
  onlineCount: number
  members: Member[]
}>()

const showAll = ref(false)
const maxDisplay = 12

const displayedMembers = computed(() => {
  const list = props.members
  if (showAll.value) return list
  return list.slice(0, maxDisplay)
})

function onImgError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}
</script>

<style scoped>
.member-panel {
  background: var(--chat-panel, var(--bg-panel));
  border: 1px solid var(--chat-border, var(--border-light));
  border-radius: 16px;
  overflow: hidden;
}

.panel-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--chat-border, var(--border-light));
}

.panel-title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--chat-text, var(--text-primary));
  font-family: var(--font-heading);
}

.member-list {
  max-height: 340px;
  overflow-y: auto;
  padding: 8px 6px;
  scrollbar-width: 6px;
  scrollbar-color: var(--border-medium) transparent;
}

.member-list::-webkit-scrollbar { width: 6px; }
.member-list::-webkit-scrollbar-track { background: transparent; }
.member-list::-webkit-scrollbar-thumb { background: var(--border-medium); border-radius: 3px; }

.member-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  transition: background var(--duration-fast) var(--ease-smooth);
}

.member-item:hover {
  background: var(--chat-hover, var(--bg-hover));
}

.member-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent-light, #22D3EE), var(--accent, #0891B2));
  color: var(--accent-contrast, #04202A);
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.member-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.member-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--chat-text, var(--text-primary));
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.member-admin-badge {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(var(--accent-orange-rgb, 8, 145, 178), 0.12);
  color: var(--accent, #0891B2);
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}

.member-empty {
  text-align: center;
  padding: 24px;
  color: var(--chat-dim, var(--text-muted));
  font-size: 13px;
}

.load-more-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 44px;
  border: none;
  border-top: 1px solid var(--chat-border, var(--border-light));
  background: var(--chat-hover, var(--bg-hover));
  color: var(--chat-muted, var(--text-secondary));
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
}

.load-more-btn:hover {
  background: var(--chat-surface, var(--bg-card));
  color: var(--chat-accent, var(--accent));
}

.load-more-btn svg {
  transition: transform var(--duration-normal) var(--ease-smooth);
}
</style>
