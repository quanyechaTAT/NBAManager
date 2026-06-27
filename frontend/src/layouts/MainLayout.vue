<template>
  <div class="app-layout">
    <!-- 顶部导航栏 -->
    <header class="top-nav">
      <div class="top-nav-left">
        <router-link to="/dashboard" class="brand-link">
          <img src="/images/nba-logo.png" alt="NBA" class="brand-logo" />
          <span class="brand-text">Courtside</span>
        </router-link>
        <nav class="nav-links">
          <router-link v-for="item in navItems" :key="item.path" :to="item.path" class="nav-link" :class="{ active: isActive(item.path) }">
            <span class="nav-icon" v-html="item.icon"></span>
            <span class="nav-label">{{ item.label }}</span>
          </router-link>
        </nav>
      </div>
      <div class="top-nav-right">
        <GlobalSearch />
        <ThemeToggle />
        <NotificationBell />
        <el-dropdown trigger="click">
          <div class="user-menu">
            <div class="user-avatar">
              <img v-if="userAvatar" :src="userAvatar" />
              <span v-else>{{ auth.username?.charAt(0)?.toUpperCase() }}</span>
            </div>
            <span class="user-name">{{ auth.username }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/profile')">个人主页</el-dropdown-item>
              <el-dropdown-item @click="showUsernameDialog = true">修改用户名</el-dropdown-item>
              <el-dropdown-item @click="showPwdDialog = true">修改密码</el-dropdown-item>
              <el-dropdown-item v-if="auth.isAdmin" @click="router.push('/admin/sync')">数据管理</el-dropdown-item>
              <el-dropdown-item divided @click="onLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 主内容 -->
    <main class="main-content" ref="mainRef">
      <router-view :key="route.fullPath" />
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
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { changePasswordApi, changeUsernameApi } from '@/api/auth'
import NotificationBell from '@/components/NotificationBell.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import GlobalSearch from '@/components/GlobalSearch.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const notificationStore = useNotificationStore()
const mainRef = ref<HTMLElement | null>(null)

watch(() => route.path, () => {
  if (mainRef.value) mainRef.value.scrollTop = 0
})

onMounted(() => { if (auth.token) notificationStore.startPolling() })
onUnmounted(() => { notificationStore.stopPolling() })

// 导航项
const navItems = [
  { path: '/dashboard', label: '首页', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>' },
  { path: '/news', label: '资讯', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-2 2Zm0 0a2 2 0 0 1-2-2v-9c0-1.1.9-2 2-2h2"/></svg>' },
  { path: '/teams', label: '球队', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>' },
  { path: '/players', label: '球员', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>' },
  { path: '/community', label: '社区', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>' },
  { path: '/playoff', label: '季后赛', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22"/><path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22"/><path d="M18 2H6v7a6 6 0 0 0 12 0V2Z"/></svg>' },
  { path: '/drafts', label: '选秀', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M12 20V10"/><path d="M18 20V4"/><path d="M6 20v-4"/></svg>' },
  { path: '/smart-search', label: 'AI 助手', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5 4 4 0 0 1-5-5"/><path d="M8.5 8.5v.01"/><path d="M16 15.5v.01"/><path d="M12 12v.01"/><path d="M11 17v.01"/><path d="M7 14v.01"/></svg>' },
]

const mobileNavItems = [
  { path: '/dashboard', label: '首页', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>' },
  { path: '/news', label: '资讯', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-2 2Zm0 0a2 2 0 0 1-2-2v-9c0-1.1.9-2 2-2h2"/></svg>' },
  { path: '/community', label: '社区', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>' },
  { path: '/smart-search', label: 'AI', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5"/></svg>' },
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

/* 顶部导航 */
.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-light);
  position: sticky;
  top: 0;
  z-index: 100;
  flex-shrink: 0;
}

.top-nav-left {
  display: flex;
  align-items: center;
  gap: 32px;
}

.brand-link {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--text-primary);
  font-weight: 700;
  font-size: 18px;
  letter-spacing: -0.5px;
}

.brand-logo {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.brand-text {
  font-family: var(--font-heading);
}

.nav-links {
  display: flex;
  gap: 4px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 6px;
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.nav-link:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.nav-link.active {
  background: var(--accent-lighter);
  color: var(--accent);
  font-weight: 600;
}

.nav-icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.top-nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.15s;
}

.user-menu:hover {
  background: var(--bg-hover);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
  overflow: hidden;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

/* 主内容 */
.main-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

/* 移动端底部导航 */
.mobile-nav {
  display: none;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 56px;
  background: var(--bg-card);
  border-top: 1px solid var(--border-light);
  z-index: 100;
  padding: 0 8px;
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
  padding: 6px 0;
  transition: color 0.15s;
}

.mobile-nav-item.active {
  color: var(--accent);
}

.mobile-nav-icon {
  margin-bottom: 2px;
}

/* 移动端响应式 */
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

  .main-content {
    padding-bottom: 56px;
  }

  .brand-text {
    display: none;
  }

  .top-nav {
    padding: 0 12px;
  }
}
</style>
