<template>
  <div class="notification-bell" v-if="auth.token">
    <el-popover placement="bottom-end" :width="380" trigger="click" @show="loadNotifications" :show-arrow="false" popper-class="notif-popover">
      <template #reference>
        <div class="bell-icon" @click="onBellClick">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
            <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
          </svg>
          <span v-if="pushSupported && pushPermission === 'default'" class="push-indicator" title="点击开启推送通知"></span>
          <span v-if="notificationStore.unreadCount > 0" class="bell-badge">
            <span class="badge-pulse"></span>
            {{ notificationStore.unreadCount > 99 ? '99+' : notificationStore.unreadCount }}
          </span>
        </div>
      </template>

      <div class="notif-panel">
        <div class="notif-header">
          <div class="notif-title-row">
            <span class="notif-title">通知中心</span>
            <span class="notif-count" v-if="notifications.length > 0">{{ notifications.length }}条</span>
          </div>
          <div class="notif-actions">
            <button class="notif-action-btn" @click="markAllRead" v-if="notifications.some(n => !n.isRead)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M20 6L9 17l-5-5"/></svg>
              全部已读
            </button>
            <button class="notif-action-btn notif-action-danger" @click="clearRead" v-if="notifications.some(n => n.isRead)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
              清空已读
            </button>
          </div>
        </div>
        <div class="notif-list" v-loading="loading">
          <TransitionGroup name="notif-list">
            <div v-for="n in notifications" :key="n.id" class="notif-item" :class="{ 'notif-unread': !n.isRead }" @click="readNotif(n)">
              <div class="notif-icon-wrapper" :class="`notif-icon-${n.type.toLowerCase().split('_')[0]}`">
                <span class="notif-icon">{{ typeIcon(n.type) }}</span>
              </div>
              <div class="notif-body">
                <span class="notif-text">{{ n.title }}</span>
                <span class="notif-time">{{ formatTime(n.createTime) }}</span>
              </div>
              <button class="notif-delete" @click.stop="deleteNotif(n)" title="删除通知">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M18 6L6 18M6 6l12 12"/></svg>
              </button>
              <div v-if="!n.isRead" class="notif-unread-dot"></div>
            </div>
          </TransitionGroup>
          <div v-if="!loading && notifications.length === 0" class="notif-empty">
            <span class="notif-empty-icon">🔔</span>
            <span class="notif-empty-text">暂无通知</span>
          </div>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { fetchNotifications, markAsRead, markAllAsRead, deleteNotification, clearReadNotifications } from '@/api/notification'
import { usePushNotification } from '@/composables/usePushNotification'
import type { Notification } from '@/api/notification'

const router = useRouter()
const auth = useAuthStore()
const notificationStore = useNotificationStore()

const { isSupported: pushSupported, permissionStatus: pushPermission, requestPermission } = usePushNotification()

const loading = ref(false)
const notifications = ref<Notification[]>([])

const typeIcons: Record<string, string> = {
  GAME_START: '🏀', SCORE_UPDATE: '📊', COMMENT_LIKE: '👍', POST_REPLY: '💬',
  POST_LIKE: '❤️', NEW_COMMENT: '💬', POST_DELETED: '🗑️', POST_FAVORITE: '⭐', SYSTEM: '📢',
}
function typeIcon(t: string) { return typeIcons[t] || '📢' }

function formatTime(t: string) {
  const d = new Date(t)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return d.toLocaleDateString('zh-CN')
}

async function loadNotifications() {
  loading.value = true
  try {
    const { data } = await fetchNotifications({ page: 0, size: 20 })
    notifications.value = data.content
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function readNotif(n: Notification) {
  if (!n.isRead) {
    await markAsRead(n.id)
    n.isRead = true
    notificationStore.refresh()
  }
  if (n.relatedId) {
    // 帖子相关通知跳转到帖子详情
    const postTypes = ['POST_REPLY', 'COMMENT_LIKE', 'POST_LIKE', 'NEW_COMMENT', 'POST_FAVORITE']
    if (postTypes.includes(n.type)) {
      router.push({ path: '/community/post', query: { id: String(n.relatedId) } })
    }
  }
}

async function markAllRead() {
  await markAllAsRead()
  notifications.value.forEach(n => n.isRead = true)
  notificationStore.refresh()
}

async function deleteNotif(n: Notification) {
  try {
    await deleteNotification(n.id)
    notifications.value = notifications.value.filter(item => item.id !== n.id)
    notificationStore.refresh()
  } catch { /* ignore */ }
}

async function clearRead() {
  try {
    await clearReadNotifications()
    notifications.value = notifications.value.filter(n => !n.isRead)
    notificationStore.refresh()
  } catch { /* ignore */ }
}

function onBellClick() {
  notificationStore.refresh()
  // On first click, request push notification permission if not yet granted
  if (pushSupported.value && pushPermission.value === 'default') {
    requestPermission().then((granted) => {
      if (granted) {
        ElMessage.success('已开启浏览器推送通知')
      }
    })
  }
}
</script>

<style scoped>
/* 铃铛图标 */
.notification-bell { position: relative; }
.bell-icon {
  position: relative;
  cursor: pointer;
  color: var(--text-muted);
  transition: all 0.25s var(--ease-smooth);
  display: flex;
  align-items: center;
  padding: 8px;
  border-radius: var(--radius-md);
}
.bell-icon:hover {
  color: var(--accent);
  background: var(--accent-lighter);
  transform: scale(1.05);
}

/* 推送权限指示器 */
.push-indicator {
  position: absolute;
  bottom: 4px;
  right: 4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--warning);
  border: 2px solid var(--bg-page);
  box-shadow: 0 0 6px var(--warning-glow);
  animation: pushPulse 2s ease-in-out infinite;
}
@keyframes pushPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(1.2); }
}

/* 徽章 */
.bell-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  background: var(--danger);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  font-family: var(--font-heading);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  box-shadow: 0 0 0 2px var(--bg-page);
  animation: badgePop 0.3s var(--ease-spring);
}
.badge-pulse {
  position: absolute;
  inset: -2px;
  border-radius: inherit;
  background: var(--danger);
  opacity: 0;
  animation: badgePulse 2s ease-in-out infinite;
}
@keyframes badgePop {
  0% { transform: scale(0); }
  100% { transform: scale(1); }
}
@keyframes badgePulse {
  0%, 100% { opacity: 0; transform: scale(1); }
  50% { opacity: 0.3; transform: scale(1.3); }
}

/* 通知面板 */
.notif-panel {
  max-height: 420px;
  font-family: var(--font-body);
}

/* 头部 */
.notif-header {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
  margin-bottom: 8px;
}
.notif-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.notif-title {
  font-weight: 700;
  font-size: 16px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
}
.notif-count {
  font-size: 11px;
  color: var(--text-dim);
  background: var(--bg-hover);
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 600;
}

/* 操作按钮 */
.notif-actions {
  display: flex;
  gap: 8px;
}
.notif-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s var(--ease-smooth);
}
.notif-action-btn:hover {
  background: var(--accent-lighter);
  border-color: var(--accent);
  color: var(--accent);
}
.notif-action-danger:hover {
  background: rgba(255, 71, 87, 0.1);
  border-color: var(--danger);
  color: var(--danger);
}

/* 通知列表 */
.notif-list {
  max-height: 340px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--border-medium) transparent;
}

/* 通知项 */
.notif-item {
  display: flex;
  gap: 12px;
  padding: 12px 8px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.2s var(--ease-smooth);
  position: relative;
  margin-bottom: 2px;
}
.notif-item:hover {
  background: var(--bg-hover);
  transform: translateX(2px);
}
.notif-unread {
  background: rgba(0, 255, 136, 0.04);
  border-left: 3px solid var(--accent);
}
.notif-unread:hover {
  background: rgba(0, 255, 136, 0.08);
}

/* 未读标记点 */
.notif-unread-dot {
  position: absolute;
  left: 4px;
  top: 50%;
  transform: translateY(-50%);
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 8px var(--accent-glow);
}

/* 图标容器 */
.notif-icon-wrapper {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--bg-hover);
  transition: all 0.2s var(--ease-smooth);
}
.notif-item:hover .notif-icon-wrapper {
  transform: scale(1.1);
}
.notif-icon-game { background: rgba(0, 210, 255, 0.1); }
.notif-icon-post { background: rgba(139, 92, 246, 0.1); }
.notif-icon-comment { background: rgba(0, 230, 118, 0.1); }
.notif-icon-system { background: rgba(255, 167, 38, 0.1); }
.notif-icon { font-size: 16px; }

/* 通知内容 */
.notif-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.notif-text {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.5;
  font-weight: 500;
}
.notif-time {
  font-size: 11px;
  color: var(--text-dim);
  font-weight: 500;
}

/* 删除按钮 */
.notif-delete {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--text-dim);
  cursor: pointer;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.2s var(--ease-smooth);
}
.notif-item:hover .notif-delete { opacity: 1; }
.notif-delete:hover {
  background: var(--danger);
  color: #fff;
  transform: translateY(-50%) scale(1.1);
}

/* 空状态 */
.notif-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  gap: 8px;
}
.notif-empty-icon {
  font-size: 32px;
  opacity: 0.5;
}
.notif-empty-text {
  font-size: 13px;
  color: var(--text-dim);
}

/* 列表动画 */
.notif-list-enter-active {
  transition: all 0.3s var(--ease-smooth);
}
.notif-list-leave-active {
  transition: all 0.2s var(--ease-smooth);
}
.notif-list-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}
.notif-list-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>
