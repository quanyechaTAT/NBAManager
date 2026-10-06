<template>
  <div class="app-layout" :class="{ 'app-layout--copilot': isCopilotRoute }">
    <!-- 顶部导航栏 -->
    <header class="top-nav">
      <div class="top-nav-left">
        <router-link to="/dashboard" class="brand-link">
          <img src="/images/nba-logo.png" alt="NBA" class="brand-logo" />
          <span class="brand-text">Courtside</span>
          <span class="brand-badge">{{ currentSeason }}</span>
          <span v-if="hasLiveGame" class="brand-live-tag" title="当前有比赛正在进行">
            <span class="live-indicator-dot"></span>
            <span>LIVE</span>
          </span>
        </router-link>
        <nav class="nav-links">
          <router-link v-for="item in visibleNavItems" :key="item.path" :to="item.path" class="nav-link" :class="{ active: isActive(item.path) }">
            <span class="nav-icon" v-html="item.icon"></span>
            <span class="nav-label">{{ item.label }}</span>
          </router-link>
          <!-- 中等宽度：次要入口收进"更多" -->
          <el-dropdown v-if="isCompactNav" trigger="click" popper-class="nav-more-popper">
            <button type="button" class="nav-link nav-more" :class="{ active: isMoreActive }">
              <span class="nav-icon" v-html="moreIcon"></span>
              <span class="nav-label">更多</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="item in secondaryNavItems"
                  :key="item.path"
                  :class="{ 'is-current': isActive(item.path) }"
                  @click="router.push(item.path)"
                >
                  <span class="more-item-icon" v-html="item.icon"></span>{{ item.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </nav>
      </div>
      <div class="top-nav-right">
        <!-- 移动端：AI 助手常驻顶栏入口（底栏让位给赛程/数据） -->
        <router-link to="/smart-search" class="mobile-ai-link" aria-label="AI 助手">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5 4 4 0 0 1-5-5"/><path d="M12 12v.01"/><path d="M7 14v.01"/><path d="M16 15.5v.01"/></svg>
        </router-link>
        <GlobalSearch class="nav-search" />
        <ThemeToggle />
        <NotificationBell />
        <el-dropdown trigger="click" popper-class="user-dropdown-popper">
          <div class="user-menu" role="button" tabindex="0" :aria-label="`账户菜单：${auth.username}`">
            <div class="user-avatar">
              <img v-if="userAvatar" :src="userAvatar" />
              <span v-else>{{ auth.username?.charAt(0)?.toUpperCase() }}</span>
            </div>
            <span class="user-name">{{ auth.username }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/profile')">个人中心</el-dropdown-item>
              <el-dropdown-item @click="showUsernameDialog = true">修改用户名</el-dropdown-item>
              <el-dropdown-item @click="showPwdDialog = true">修改密码</el-dropdown-item>
              <el-dropdown-item v-if="auth.isAdmin" @click="router.push('/admin/sync')">数据调度中心</el-dropdown-item>
              <el-dropdown-item divided @click="onLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 全站实时赛况条 -->
    <LiveScoreTicker :matches="tickerMatches" />

    <!-- 主内容：布局保持挂载，仅页面组件做 out-in 过渡 -->
    <main class="main-content" ref="mainRef">
      <router-view v-slot="{ Component }">
        <transition name="page-fade-slide" mode="out-in">
          <div :key="route.fullPath" class="route-shell">
            <component :is="Component" />
          </div>
        </transition>
      </router-view>
    </main>

    <!-- 移动端底部导航 -->
    <nav class="mobile-nav">
      <router-link v-for="item in mobileNavItems" :key="item.path" :to="item.path" class="mobile-nav-item" :class="{ active: isActive(item.path) }">
        <span class="mobile-nav-icon" v-html="item.icon"></span>
        <span class="mobile-nav-label">{{ item.label }}</span>
      </router-link>
    </nav>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="showPwdDialog" title="修改密码" width="420px" :close-on-click-modal="false" @closed="resetPwdForm" destroy-on-close :append-to-body="true" :center="true" class="dialog-light">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="80px" label-position="right">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="请输入新密码（至少 6 位）" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPwdDialog = false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="submitPwd">确认</el-button>
      </template>
    </el-dialog>

    <!-- 修改用户名弹窗 -->
    <el-dialog v-model="showUsernameDialog" title="修改用户名" width="420px" :close-on-click-modal="false" @closed="resetUsernameForm" destroy-on-close :append-to-body="true" :center="true" class="dialog-light">
      <el-form ref="usernameFormRef" :model="usernameForm" :rules="usernameRules" label-width="80px" label-position="right">
        <el-form-item label="当前用户">
          <el-input :model-value="auth.username" disabled />
        </el-form-item>
        <el-form-item label="新用户名" prop="newUsername">
          <el-input v-model="usernameForm.newUsername" placeholder="请输入新用户名（2-30个字符）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUsernameDialog = false">取消</el-button>
        <el-button type="primary" :loading="usernameLoading" @click="submitUsername">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
// @ts-ignore
import SockJS from 'sockjs-client/dist/sockjs.min.js'
import { Client } from '@stomp/stompjs'
import { useAuthStore } from '@/stores/auth'
import { useIdentityStore } from '@/stores/identity'
import { useNotificationStore } from '@/stores/notification'
import { changePasswordApi, changeUsernameApi } from '@/api/auth'
import NotificationBell from '@/components/NotificationBell.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import GlobalSearch from '@/components/GlobalSearch.vue'
import LiveScoreTicker from '@/components/domain/LiveScoreTicker.vue'
import { useMatchFeed } from '@/composables/useMatchFeed'
import { useCurrentSeason } from '@/composables/useCurrentSeason'

const route = useRoute()
const router = useRouter()
const isCopilotRoute = computed(() => route.path === '/smart-search')
const auth = useAuthStore()
const identityStore = useIdentityStore()
const notificationStore = useNotificationStore()
const mainRef = ref<HTMLElement | null>(null)

const { currentSeason, loadSeason } = useCurrentSeason()

let identityClient: Client | null = null

/* -------- 全站赛况条 -------- */
const { tickerMatches, live, refresh: refreshMatchFeed } = useMatchFeed()
let tickerTimer: number | undefined
const hasLiveGame = computed(() => live.value.length > 0)


watch(() => route.path, () => {
  if (mainRef.value) mainRef.value.scrollTop = 0
})

function connectIdentity() {
  if (!auth.token || identityClient?.active) return
  const wsUrl = import.meta.env.VITE_WS_URL || window.location.origin
  identityClient = new Client({
    webSocketFactory: () => new SockJS(`${wsUrl}/ws`),
    connectHeaders: { Authorization: `Bearer ${auth.token}` },
    reconnectDelay: 10000,
    heartbeatIncoming: 0,
    heartbeatOutgoing: 0,
    onConnect: () => {
      identityClient?.subscribe('/topic/user-identity', (msg) => {
        try {
          const data = JSON.parse(msg.body)
          if (data.type === 'USER_IDENTITY_UPDATED' && data.userId) {
            identityStore.updateIdentity({
              userId: data.userId,
              username: data.username,
              avatarUrl: data.avatarUrl || null,
              updatedAt: data.updatedAt,
            })
          }
        } catch { /* ignore */ }
      })
    },
  })
  identityClient.activate()
}

function disconnectIdentity() {
  identityClient?.deactivate()
  identityClient = null
}

watch(() => auth.token, (newToken, oldToken) => {
  if (newToken && newToken !== oldToken) {
    disconnectIdentity()
    connectIdentity()
  } else if (!newToken) {
    disconnectIdentity()
  }
})

onMounted(() => {
  void loadSeason()
  compactNavQuery?.addEventListener('change', onCompactNavChange)
  if (auth.token) {
    notificationStore.startPolling()
    void auth.syncLegacyAvatar()
    connectIdentity()
    void refreshMatchFeed()
    tickerTimer = window.setInterval(() => { void refreshMatchFeed() }, 120000)
  }
})
onUnmounted(() => {
  compactNavQuery?.removeEventListener('change', onCompactNavChange)
  notificationStore.stopPolling()
  disconnectIdentity()
  if (tickerTimer) window.clearInterval(tickerTimer)
})

// 导航项
const navItems = [
  { path: '/dashboard', label: '总览', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>' },
  { path: '/news', label: '赛事中心', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-2 2Zm0 0a2 2 0 0 1-2-2v-9c0-1.1.9-2 2-2h2"/></svg>' },
  { path: '/teams', label: '球队榜', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>' },
  { path: '/players', label: '球员', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>' },
  { path: '/community', label: '球迷社区', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>' },
  { path: '/playoff', label: '季后赛', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22"/><path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22"/><path d="M18 2H6v7a6 6 0 0 0 12 0V2Z"/></svg>' },
  { path: '/drafts', label: '选秀', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M12 20V10"/><path d="M18 20V4"/><path d="M6 20v-4"/></svg>' },
  { path: '/smart-search', label: 'AI 助手', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5 4 4 0 0 1-5-5"/><path d="M8.5 8.5v.01"/><path d="M16 15.5v.01"/><path d="M12 12v.01"/><path d="M11 17v.01"/><path d="M7 14v.01"/></svg>' },
]

/* 中等宽度（769–1199px）：栏内只留高频入口，其余收进"更多" */
const COMPACT_NAV_QUERY = '(min-width: 769px) and (max-width: 1199px)'
const compactNavQuery = typeof window !== 'undefined' && 'matchMedia' in window
  ? window.matchMedia(COMPACT_NAV_QUERY)
  : null
const isCompactNav = ref(compactNavQuery?.matches ?? false)

function onCompactNavChange(e: MediaQueryListEvent) {
  isCompactNav.value = e.matches
}

const COMPACT_PRIMARY_PATHS = ['/dashboard', '/teams', '/players', '/community']
const visibleNavItems = computed(() =>
  isCompactNav.value
    ? navItems.filter((item) => COMPACT_PRIMARY_PATHS.includes(item.path))
    : navItems
)
const secondaryNavItems = computed(() =>
  navItems.filter((item) => !COMPACT_PRIMARY_PATHS.includes(item.path))
)
const isMoreActive = computed(() => secondaryNavItems.value.some((item) => isActive(item.path)))

const moreIcon = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/></svg>'

const mobileNavItems = [
  { path: '/dashboard', label: '总览', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>' },
  { path: '/news', label: '赛事', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>' },
  { path: '/players', label: '球员', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M12 20V10"/><path d="M18 20V4"/><path d="M6 20v-4"/></svg>' },
  { path: '/community', label: '社区', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>' },
  { path: '/profile', label: '我的', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>' },
]

function isActive(path: string) {
  if (path === '/dashboard') return route.path === '/dashboard' || route.path === '/'
  return route.path.startsWith(path)
}

const userAvatar = computed(() => auth.avatarUrl)

function onLogout() {
  auth.logout()
  router.replace('/login')
}

/* -------- 修改密码 -------- */
const showPwdDialog = ref(false)
const pwdLoading = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const validateConfirm = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value !== pwdForm.newPassword) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 128, message: '密码长度 6-128 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

function resetPwdForm() {
  pwdForm.oldPassword = ''; pwdForm.newPassword = ''; pwdForm.confirmPassword = ''
  pwdFormRef.value?.resetFields()
}

async function submitPwd() {
  await pwdFormRef.value?.validate()
  pwdLoading.value = true
  try {
    await changePasswordApi({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    showPwdDialog.value = false
    auth.logout(); router.replace('/login')
  } catch (e: unknown) {
    const msg = typeof e === 'object' && e !== null && 'response' in e
      ? (e as { response?: { data?: { message?: string } } }).response?.data?.message : undefined
    ElMessage.error(msg || '密码修改失败')
  } finally { pwdLoading.value = false }
}

/* -------- 修改用户名 -------- */
const showUsernameDialog = ref(false)
const usernameLoading = ref(false)
const usernameFormRef = ref<FormInstance>()
const usernameForm = reactive({ newUsername: '' })
const usernameRules: FormRules = {
  newUsername: [{ required: true, message: '请输入新用户名', trigger: 'blur' }, { min: 2, max: 30, message: '2-30个字符', trigger: 'blur' }],
}

function resetUsernameForm() { usernameForm.newUsername = ''; usernameFormRef.value?.resetFields() }

async function submitUsername() {
  await usernameFormRef.value?.validate()
  usernameLoading.value = true
  try {
    const { data } = await changeUsernameApi({ newUsername: usernameForm.newUsername })
    auth.updateToken(data.token); auth.setUsername(data.username)
    ElMessage.success('用户名修改成功'); showUsernameDialog.value = false
  } catch (e: unknown) {
    const msg = typeof e === 'object' && e !== null && 'response' in e
      ? (e as { response?: { data?: { message?: string } } }).response?.data?.message : undefined
    ElMessage.error(msg || '用户名修改失败')
  } finally { usernameLoading.value = false }
}
</script>

<style scoped>
/* 整体布局 */
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-page);
}

/* 顶部导航：毛玻璃 + 细橙光边 */
.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--nav-height, 60px);
  padding: 0 22px;
  background: rgba(11, 15, 23, 0.78);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid var(--border-subtle);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
  position: sticky;
  top: 0;
  z-index: 100;
  flex-shrink: 0;
}

/* 导航底部渐变发丝线：中央一抹橙光 */
.top-nav::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 1px;
  background: linear-gradient(90deg, transparent 4%, rgba(34, 211, 238, 0.35) 30%, rgba(34, 211, 238, 0.55) 50%, rgba(34, 211, 238, 0.35) 70%, transparent 96%);
  opacity: 0.6;
  pointer-events: none;
}

[data-theme="light"] .top-nav {
  background: rgba(255, 255, 255, 0.75);
}

.top-nav-left {
  display: flex;
  align-items: center;
  gap: 28px;
  min-width: 0;
}

.brand-link {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--text-primary);
  font-weight: 800;
  font-size: 18px;
  letter-spacing: -0.4px;
  transition: transform var(--duration-nav, 0.18s) var(--ease-out-quart, ease);
}

.brand-link:hover {
  transform: translateY(-1px);
}

.brand-logo {
  width: 32px;
  height: 32px;
  object-fit: contain;
  filter: drop-shadow(0 2px 8px rgba(34, 211, 238, 0.25));
}

.brand-text {
  font-family: var(--font-heading);
  background: linear-gradient(120deg, var(--text-primary) 0%, var(--text-primary) 55%, var(--accent) 140%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.brand-badge {
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.14em;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--accent-soft);
  color: var(--accent);
  border: 1px solid rgba(var(--accent-rgb), 0.32);
  box-shadow: 0 0 10px rgba(var(--accent-rgb), 0.18);
  line-height: 1;
}

.brand-live-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(249, 115, 22, 0.14);
  border: 1px solid rgba(249, 115, 22, 0.4);
  color: #FB923C;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  margin-left: 2px;
  animation: fadeIn 0.3s ease;
}

.nav-links {
  display: flex;
  gap: 2px;
  padding: 3px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.03);
}

[data-theme="light"] .nav-links {
  background: rgba(15, 23, 42, 0.03);
  border-color: rgba(15, 23, 42, 0.04);
}

.nav-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 13px;
  border-radius: 8px;
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 13.5px;
  font-weight: 500;
  transition:
    color var(--duration-nav, 0.18s) var(--ease-smooth),
    background var(--duration-nav, 0.18s) var(--ease-smooth),
    transform var(--duration-nav, 0.18s) var(--ease-smooth);
  white-space: nowrap;
}

.nav-link:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
  transform: translateY(-1px);
}

.nav-link.active {
  color: var(--text-primary);
  font-weight: 600;
  background: linear-gradient(135deg, rgba(34, 211, 238, 0.18), rgba(34, 211, 238, 0.07));
  box-shadow:
    inset 0 0 0 1px rgba(34, 211, 238, 0.28),
    inset 0 1px 0 rgba(255, 255, 255, 0.06),
    0 4px 14px -4px rgba(34, 211, 238, 0.35);
}

.nav-link.active .nav-icon {
  color: var(--accent);
  opacity: 1;
  filter: drop-shadow(0 0 6px rgba(34, 211, 238, 0.5));
}

.nav-link.active::after {
  content: '';
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 3px;
  height: 2px;
  border-radius: 2px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
}

[data-theme="light"] .nav-link.active {
  background: linear-gradient(135deg, rgba(34, 211, 238, 0.12), rgba(34, 211, 238, 0.05));
  box-shadow:
    inset 0 0 0 1px rgba(34, 211, 238, 0.22),
    0 4px 12px -6px rgba(34, 211, 238, 0.3);
}

.nav-icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  opacity: 0.9;
}

/* "更多"下拉触发按钮：与 nav-link 同款外观 */
.nav-more {
  border: none;
  background: transparent;
  font-family: inherit;
  cursor: pointer;
  outline-offset: 2px;
}

.mobile-ai-link {
  display: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: var(--accent);
  background: var(--accent-soft);
  transition: background var(--duration-nav, 0.18s) var(--ease-smooth), transform var(--duration-nav, 0.18s) var(--ease-smooth);
}

.mobile-ai-link:hover {
  background: var(--accent-glow-strong);
  transform: translateY(-1px);
}

.top-nav-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  border: 1px solid var(--border-light);
  background: var(--bg-card);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
  transition:
    background var(--duration-nav, 0.18s) var(--ease-smooth),
    border-color var(--duration-nav, 0.18s) var(--ease-smooth),
    box-shadow var(--duration-nav, 0.18s) var(--ease-smooth),
    transform var(--duration-nav, 0.18s) var(--ease-smooth);
}

.user-menu:hover {
  background: var(--bg-hover);
  border-color: rgba(34, 211, 238, 0.4);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12), 0 0 12px rgba(34, 211, 238, 0.18);
  transform: translateY(-1px);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(145deg, var(--accent-light), var(--accent));
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-contrast, #04202A);
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
  overflow: hidden;
  box-shadow: 0 0 0 2px var(--accent-soft);
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  padding-right: 2px;
}

/* 主内容（relative 供页面过渡的离场绝对定位参照） */
.main-content {
  position: relative;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  scroll-behavior: smooth;
}

.app-layout--copilot {
  height: 100dvh;
  background: var(--bg-page);
}

.app-layout--copilot .main-content {
  min-height: 0;
  overflow: hidden;
  scroll-behavior: auto;
}

.app-layout--copilot .route-shell {
  height: 100%;
}

/* 移动端底部导航 */
.mobile-nav {
  display: none;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: var(--mobile-nav-height, 64px);
  background: var(--surface-glass, var(--bg-card));
  backdrop-filter: blur(18px) saturate(160%);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
  border-top: 1px solid var(--surface-glass-border, var(--border-light));
  z-index: 100;
  padding: 0 8px;
  padding-bottom: env(safe-area-inset-bottom, 0);
  box-shadow: 0 -8px 28px rgba(0, 0, 0, 0.14);
}

.mobile-nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--text-muted);
  text-decoration: none;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.2px;
  padding: 8px 0;
  border-radius: 12px;
  transition:
    color var(--duration-nav, 0.18s) var(--ease-smooth),
    background var(--duration-nav, 0.18s) var(--ease-smooth),
    transform var(--duration-nav, 0.18s) var(--ease-out-quart, ease);
}

.mobile-nav-item:active {
  transform: scale(0.96);
}

.mobile-nav-item.active {
  color: var(--accent);
  background: var(--accent-lighter);
}

.mobile-nav-icon {
  margin-bottom: 3px;
  display: flex;
}

/* 移动端响应式 */
@media (max-width: 1200px) {
  .nav-search {
    display: none;
  }
}

@media (max-width: 768px) {
  .nav-links {
    display: none;
  }

  .user-name {
    display: none;
  }

  .mobile-nav {
    display: flex;
  }

  .mobile-ai-link {
    display: flex;
  }

  .main-content {
    padding-bottom: calc(var(--mobile-nav-height, 64px) + env(safe-area-inset-bottom, 0px));
  }

  .app-layout--copilot .main-content {
    padding-bottom: calc(var(--mobile-nav-height, 64px) + env(safe-area-inset-bottom, 0px));
  }

  .brand-text {
    display: none;
  }

  .top-nav {
    padding: 0 12px;
    height: 56px;
  }
}
</style>
