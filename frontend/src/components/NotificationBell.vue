<template>
  <div class="notification-bell" v-if="auth.token">
    <el-popover
      ref="popoverRef"
      placement="bottom-end"
      :width="popoverWidth"
      trigger="click"
      @show="loadNotifications"
      @hide="onClose"
      :show-arrow="false"
      popper-class="notif-popover"
      :popper-options="{ modifiers: [{ name: 'offset', options: { offset: [0, 10] } }] }"
    >
      <template #reference>
        <button class="bell-icon" @click="onBellClick" aria-label="通知中心" :title="`通知中心${notificationStore.unreadCount > 0 ? `，${notificationStore.unreadCount} 条未读` : ''}`">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
            <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
          </svg>
          <span v-if="pushSupported && pushPermission === 'default'" class="push-dot" title="点击开启推送通知"></span>
          <span v-if="notificationStore.unreadCount > 0" class="bell-badge" :key="notificationStore.unreadCount">
            {{ notificationStore.unreadCount > 99 ? '99+' : notificationStore.unreadCount }}
          </span>
        </button>
      </template>

      <div class="notif-panel" @keydown.escape="closePopover">
        <!-- 固定头部 -->
        <div class="notif-header">
          <div class="notif-header-left">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18" class="notif-header-icon">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
            </svg>
            <span class="notif-header-title">通知中心</span>
            <span v-if="unreadCount > 0" class="notif-unread-badge">{{ unreadCount }}</span>
          </div>
          <button
            class="notif-mark-all-btn"
            @click="markAllRead"
            :disabled="!hasUnread || markAllLoading"
            :aria-label="'全部标记为已读'"
            :title="hasUnread ? '全部已读' : '没有未读通知'"
          >
            <svg v-if="!markAllLoading" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M20 6L9 17l-5-5"/></svg>
            <svg v-else class="spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M12 2v4m0 12v4M4.93 4.93l2.83 2.83m8.48 8.48l2.83 2.83M2 12h4m12 0h4M4.93 19.07l2.83-2.83m8.48-8.48l2.83-2.83"/></svg>
          </button>
        </div>

        <!-- 骨架屏加载 -->
        <div v-if="loading" class="notif-list">
          <div v-for="i in 3" :key="`skeleton-${i}`" class="notif-item skeleton-item">
            <div class="skeleton-icon"></div>
            <div class="skeleton-body">
              <div class="skeleton-title"></div>
              <div class="skeleton-text"></div>
              <div class="skeleton-time"></div>
            </div>
          </div>
        </div>

        <!-- 通知列表 -->
        <div v-else-if="notifications.length > 0" class="notif-list">
          <TransitionGroup name="notif-item" tag="div">
            <div
              v-for="n in notifications"
              :key="n.id"
              class="notif-item"
              :class="{ 'notif-unread': !n.isRead }"
              @click="readNotif(n)"
              role="button"
              :aria-label="`${n.title}，${n.isRead ? '已读' : '未读'}`"
              tabindex="0"
              @keydown.enter="readNotif(n)"
            >
              <div class="notif-icon-box" :class="`notif-icon-${getTypeMeta(n.type).category}`">
                <component :is="getTypeIcon(n.type)" />
              </div>
              <div class="notif-content">
                <div class="notif-title-row">
                  <span class="notif-title">{{ n.title }}</span>
                  <span v-if="!n.isRead" class="notif-unread-dot"></span>
                </div>
                <p v-if="n.content" class="notif-desc">{{ n.content }}</p>
                <span class="notif-time">{{ formatTime(n.createTime) }}</span>
              </div>
              <button
                class="notif-delete-btn"
                @click.stop="deleteNotif(n)"
                aria-label="删除通知"
                title="删除通知"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M18 6L6 18M6 6l12 12"/></svg>
              </button>
            </div>
          </TransitionGroup>
        </div>

        <!-- 空状态 -->
        <div v-else class="notif-empty">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="48" height="48" class="notif-empty-icon">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
            <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
          </svg>
          <span class="notif-empty-text">暂无通知</span>
        </div>

        <!-- 固定底部 -->
        <div v-if="notifications.length > 0" class="notif-footer">
          <button
            class="notif-footer-btn"
            @click="clearRead"
            :disabled="!hasRead || clearLoading"
            :aria-label="'清空已读通知'"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
            {{ clearLoading ? '清空中...' : '清空已读' }}
          </button>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, h, defineComponent, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { fetchNotifications, markAsRead, markAllAsRead, deleteNotification, clearReadNotifications, fetchPostIdByCommentId } from '@/api/notification'
import { usePushNotification } from '@/composables/usePushNotification'
import type { Notification } from '@/api/notification'

const router = useRouter()
const auth = useAuthStore()
const notificationStore = useNotificationStore()

const { isSupported: pushSupported, permissionStatus: pushPermission, requestPermission } = usePushNotification()

const loading = ref(false)
const markAllLoading = ref(false)
const clearLoading = ref(false)
const notifications = ref<Notification[]>([])
const popoverRef = ref<{ hide?: () => void } | null>(null)

const unreadCount = computed(() => notifications.value.filter(n => !n.isRead).length)
const hasUnread = computed(() => unreadCount.value > 0)
const hasRead = computed(() => notifications.value.some(n => n.isRead))

const isMobile = ref(false)
const popoverWidth = computed(() => isMobile.value ? Math.min(390, window.innerWidth - 24) : 440)

function checkMobile() {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

// --- 通知类型配置 ---
const notificationTypeMeta: Record<string, { category: string; label: string }> = {
  POST_REPLY: { category: 'post', label: '帖子回复' },
  NEW_COMMENT: { category: 'comment', label: '新评论' },
  COMMENT_LIKE: { category: 'like', label: '评论获赞' },
  POST_LIKE: { category: 'like', label: '帖子获赞' },
  POST_FAVORITE: { category: 'favorite', label: '帖子收藏' },
  GAME_START: { category: 'game', label: '比赛开始' },
  SCORE_UPDATE: { category: 'game', label: '比赛更新' },
  POST_DELETED: { category: 'system', label: '内容处理' },
  SYSTEM: { category: 'system', label: '系统通知' },
}

function getTypeMeta(type: string) {
  return notificationTypeMeta[type] || { category: 'system', label: '通知' }
}

// --- SVG 图标组件 ---
function closePopover() {
  popoverRef.value?.hide?.()
}

function createSvgIcon(pathD: string): Component {
  return defineComponent({
    render() {
      return h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '18', height: '18' }, [
        h('path', { d: pathD })
      ])
    }
  })
}

const iconPost = createSvgIcon('M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z')
const iconComment = createSvgIcon('M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z')
const iconLike = createSvgIcon('M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z')
const iconFavorite = createSvgIcon('M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.09-1.03L12 2z')
const iconGame = createSvgIcon('M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z M12 6v6l4 2')
const iconSystem = createSvgIcon('M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9 M13.73 21a2 2 0 0 1-3.46 0')

const typeIconMap: Record<string, Component> = {
  post: iconPost,
  comment: iconComment,
  like: iconLike,
  favorite: iconFavorite,
  game: iconGame,
  system: iconSystem,
}

function getTypeIcon(type: string) {
  const meta = getTypeMeta(type)
  return typeIconMap[meta.category] || iconSystem
}

// --- 时间格式化 ---
function formatTime(t: string) {
  const d = new Date(t)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return d.toLocaleDateString('zh-CN')
}

// --- 数据加载 ---
async function loadNotifications() {
  loading.value = true
  try {
    const { data } = await fetchNotifications({ page: 0, size: 20 })
    notifications.value = data.content
  } catch { /* ignore */ }
  finally { loading.value = false }
}

// --- 通知操作 ---
async function readNotif(n: Notification) {
  if (!n.isRead) {
    try {
      await markAsRead(n.id)
      n.isRead = true
      notificationStore.refresh()
    } catch { /* ignore */ }
  }
  if (!n.relatedId) return
  // COMMENT_LIKE 的 relatedId 是 commentId（安全报告 §十二 V4 的去重键），需先反查 postId
  if (n.type === 'COMMENT_LIKE') {
    try {
      const { data } = await fetchPostIdByCommentId(n.relatedId)
      router.push({ path: '/community/post', query: { id: String(data.postId) } })
    } catch {
      // 评论已被删除：降级到社区列表，避免落进死链
      router.push('/community')
    }
    return
  }
  const postTypes = ['POST_REPLY', 'POST_LIKE', 'NEW_COMMENT', 'POST_FAVORITE']
  if (postTypes.includes(n.type)) {
    router.push({ path: '/community/post', query: { id: String(n.relatedId) } })
  } else if (n.type === 'GAME_START' || n.type === 'SCORE_UPDATE') {
    router.push({ path: '/match-detail', query: { gameId: String(n.relatedId) } })
  }
}

async function markAllRead() {
  if (!hasUnread.value || markAllLoading.value) return
  markAllLoading.value = true
  try {
    await markAllAsRead()
    notifications.value.forEach(n => n.isRead = true)
    notificationStore.refresh()
  } catch { /* ignore */ }
  finally { markAllLoading.value = false }
}

async function deleteNotif(n: Notification) {
  try {
    await deleteNotification(n.id)
    notifications.value = notifications.value.filter(item => item.id !== n.id)
    notificationStore.refresh()
  } catch { /* ignore */ }
}

async function clearRead() {
  if (!hasRead.value || clearLoading.value) return
  clearLoading.value = true
  try {
    await clearReadNotifications()
    notifications.value = notifications.value.filter(n => !n.isRead)
    notificationStore.refresh()
  } catch { /* ignore */ }
  finally { clearLoading.value = false }
}

function onClose() {
  // popover 关闭时的清理
}

function onBellClick() {
  notificationStore.refresh()
  if (pushSupported.value && pushPermission.value === 'default') {
    requestPermission().then((granted) => {
      if (granted) ElMessage.success('已开启浏览器推送通知')
    })
  }
}
</script>

<style scoped>
/* === 铃铛 === */
.notification-bell { position: relative; }

.bell-icon {
  position: relative;
  cursor: pointer;
  color: var(--text-muted);
  transition:
    color var(--duration-nav, 0.18s) var(--ease-smooth),
    background var(--duration-nav, 0.18s) var(--ease-smooth),
    border-color var(--duration-nav, 0.18s) var(--ease-smooth),
    box-shadow var(--duration-nav, 0.18s) var(--ease-smooth),
    transform var(--duration-nav, 0.18s) var(--ease-out-quart, ease);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  padding: 0;
}
.bell-icon:hover {
  color: var(--accent);
  background: var(--accent-lighter);
  border-color: rgba(34, 211, 238, 0.22);
  box-shadow: 0 6px 18px rgba(34, 211, 238, 0.1);
  transform: translateY(-1px);
}
.bell-icon:active {
  transform: scale(0.96);
}
.bell-icon:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

/* 推送权限小橙点 */
.push-dot {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--warning);
  border: 1.5px solid var(--bg-page);
}

/* 未读徽章 */
.bell-badge {
  position: absolute;
  top: 0;
  right: -2px;
  min-width: 16px;
  height: 16px;
  border-radius: 999px;
  background: var(--accent);
  color: #04202A;
  font-size: 10px;
  font-weight: 800;
  font-family: var(--font-heading);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
  box-shadow: 0 0 0 2px var(--bg-page);
  animation: badgePop 0.3s var(--ease-spring);
}
@keyframes badgePop {
  0% { transform: scale(0); }
  100% { transform: scale(1); }
}

/* === 弹窗面板 === */
.notif-panel {
  display: flex;
  flex-direction: column;
  max-height: min(680px, calc(100vh - 96px));
  width: 100%;
  font-family: var(--font-body);
}

/* === 头部 === */
.notif-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 12px;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}
.notif-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.notif-header-icon {
  color: var(--text-muted);
  flex-shrink: 0;
}
.notif-header-title {
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
  font-family: var(--font-heading);
}
.notif-unread-badge {
  font-size: 11px;
  font-weight: 700;
  color: var(--accent-contrast, #04202A);
  background: var(--accent);
  padding: 1px 6px;
  border-radius: 10px;
  line-height: 1.4;
}
.notif-mark-all-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.2s var(--ease-smooth);
}
.notif-mark-all-btn:hover:not(:disabled) {
  background: var(--bg-hover);
  color: var(--accent);
}
.notif-mark-all-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.notif-mark-all-btn:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* === 通知列表 === */
.notif-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 8px;
  scrollbar-width: thin;
  scrollbar-color: var(--border-medium) transparent;
}

/* === 通知项 === */
.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 8px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background 0.15s var(--ease-smooth);
  position: relative;
  min-height: 72px;
}
.notif-item:hover {
  background: var(--bg-hover);
}
.notif-item:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
  border-radius: var(--radius-md);
}
.notif-unread {
  background: rgba(34, 211, 238, 0.04);
}
.notif-unread:hover {
  background: rgba(34, 211, 238, 0.08);
}

/* 未读圆点 */
.notif-unread-dot {
  position: absolute;
  top: 12px;
  right: 8px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent);
  flex-shrink: 0;
}

/* 图标盒子 */
.notif-icon-box {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: transform 0.15s var(--ease-smooth);
}
.notif-item:hover .notif-icon-box {
  transform: scale(1.05);
}
.notif-icon-box :deep(svg) {
  width: 18px;
  height: 18px;
}
.notif-icon-post { background: rgba(139, 92, 246, 0.1); color: #8B5CF6; }
.notif-icon-comment { background: rgba(34, 197, 94, 0.1); color: #22C55E; }
.notif-icon-like { background: rgba(34, 211, 238, 0.1); color: #22D3EE; }
.notif-icon-favorite { background: rgba(34, 211, 238, 0.1); color: #22D3EE; }
.notif-icon-game { background: rgba(59, 130, 246, 0.1); color: #3B82F6; }
.notif-icon-system { background: var(--bg-hover); color: var(--text-muted); }

/* 已读降低饱和度 */
.notif-item:not(.notif-unread) .notif-icon-box {
  opacity: 0.55;
}

/* 通知内容 */
.notif-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.notif-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.notif-title {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.4;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.notif-unread .notif-title {
  font-weight: 700;
}
.notif-desc {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.notif-time {
  font-size: 11px;
  color: var(--text-dim);
  margin-top: 2px;
}

/* 删除按钮 */
.notif-delete-btn {
  position: absolute;
  top: 10px;
  right: 8px;
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
  transition: all 0.15s var(--ease-smooth);
  flex-shrink: 0;
}
.notif-item:hover .notif-delete-btn,
.notif-item:focus-within .notif-delete-btn {
  opacity: 1;
}
.notif-delete-btn:hover {
  background: var(--danger);
  color: #fff;
}
.notif-delete-btn:focus-visible {
  outline: 2px solid var(--danger);
  outline-offset: 2px;
  opacity: 1;
}

/* === 空状态 === */
.notif-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  gap: 12px;
}
.notif-empty-icon {
  color: var(--text-dim);
  opacity: 0.4;
}
.notif-empty-text {
  font-size: 13px;
  color: var(--text-dim);
  font-weight: 500;
}

/* === 底部 === */
.notif-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 16px;
  border-top: 1px solid var(--border-light);
  flex-shrink: 0;
}
.notif-footer-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s var(--ease-smooth);
}
.notif-footer-btn:hover:not(:disabled) {
  background: var(--danger-glow);
  border-color: var(--danger);
  color: var(--danger);
}
.notif-footer-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.notif-footer-btn:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

/* === 骨架屏 === */
.skeleton-item {
  pointer-events: none;
}
.skeleton-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: var(--bg-hover);
  flex-shrink: 0;
  animation: shimmer 1.5s ease-in-out infinite;
}
.skeleton-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.skeleton-title {
  width: 60%;
  height: 13px;
  border-radius: 4px;
  background: var(--bg-hover);
  animation: shimmer 1.5s ease-in-out infinite;
}
.skeleton-text {
  width: 85%;
  height: 11px;
  border-radius: 4px;
  background: var(--bg-hover);
  animation: shimmer 1.5s ease-in-out infinite 0.1s;
}
.skeleton-time {
  width: 40%;
  height: 10px;
  border-radius: 4px;
  background: var(--bg-hover);
  animation: shimmer 1.5s ease-in-out infinite 0.2s;
}
@keyframes shimmer {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

/* === 列表过渡动画 === */
.notif-item-enter-active {
  transition: all 0.3s var(--ease-smooth);
}
.notif-item-leave-active {
  transition: all 0.25s var(--ease-smooth);
}
.notif-item-enter-from {
  opacity: 0;
  transform: translateX(-12px);
}
.notif-item-leave-to {
  opacity: 0;
  transform: translateX(12px) scale(0.96);
}
.notif-item-move {
  transition: transform 0.3s var(--ease-smooth);
}

/* === 移动端适配 === */
@media (max-width: 768px) {
  .notif-panel {
    max-height: min(680px, calc(100vh - 80px));
  }
}

/* === 浅色主题覆盖 === */
[data-theme="light"] .notif-unread {
  background: rgba(34, 211, 238, 0.04);
}
[data-theme="light"] .notif-unread:hover {
  background: rgba(34, 211, 238, 0.08);
}
[data-theme="light"] .notif-icon-box {
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
[data-theme="light"] .skeleton-icon,
[data-theme="light"] .skeleton-title,
[data-theme="light"] .skeleton-text,
[data-theme="light"] .skeleton-time {
  background: #E5E7EB;
}
</style>
