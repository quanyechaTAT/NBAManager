<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <div class="logo" aria-hidden="true">
          <img src="/images/nba-logo.png" alt="NBA Logo" />
        </div>
        <div>
          <strong>数据分析系统</strong>
          <span>Manager Console</span>
        </div>
      </div>
      <el-menu :default-active="active" router class="side-menu">
        <el-menu-item index="/dashboard">
          <el-icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg></el-icon>
          <span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/teams">
          <el-icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg></el-icon>
          <span>球队战绩</span>
        </el-menu-item>
        <el-menu-item index="/players">
          <el-icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg></el-icon>
          <span>球员数据</span>
        </el-menu-item>
        <el-menu-item index="/news">
          <el-icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-2 2Zm0 0a2 2 0 0 1-2-2v-9c0-1.1.9-2 2-2h2"/><path d="M18 14h-8"/><path d="M15 18h-5"/><path d="M10 6h8v4h-8V6Z"/></svg></el-icon>
          <span>赛事资讯</span>
        </el-menu-item>
        <el-menu-item index="/community">
          <el-icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg></el-icon>
          <span>社区</span>
        </el-menu-item>
        <el-menu-item index="/drafts">
          <el-icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg></el-icon>
          <span>选秀数据库</span>
        </el-menu-item>
        <el-menu-item index="/playoff">
          <el-icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22"/><path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22"/><path d="M18 2H6v7a6 6 0 0 0 12 0V2Z"/></svg></el-icon>
          <span>季后赛</span>
        </el-menu-item>
        <el-menu-item index="/history">
          <el-icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></el-icon>
          <span>历史数据</span>
        </el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/admin/sync">
          <el-icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 0 1-9 9m9-9a9 9 0 0 0-9-9m9 9H3m9 9a9 9 0 0 1-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9"/></svg></el-icon>
          <span>数据管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="title">{{ detailTeamName || pageTitle }}</span>
        </div>
        <GlobalSearch />
        <div class="right">
          <ThemeToggle />
          <NotificationBell />
          <el-tag :type="auth.isAdmin ? 'warning' : 'info'" effect="light" class="role-tag">{{ roleLabel }}</el-tag>
          <div class="user-avatar" @click="router.push('/profile')" style="cursor:pointer">
            <img v-if="userAvatar" :src="userAvatar" class="user-avatar-img" />
            <span v-else>{{ auth.username?.charAt(0)?.toUpperCase() }}</span>
          </div>
          <span class="user" @click="router.push('/profile')" style="cursor:pointer">{{ auth.username }}</span>
          <el-dropdown trigger="click">
            <el-button class="more-btn" link>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/></svg>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="showUsernameDialog = true">修改用户名</el-dropdown-item>
                <el-dropdown-item @click="showPwdDialog = true">修改密码</el-dropdown-item>
                <el-dropdown-item divided @click="onLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main" ref="mainRef">
        <router-view :key="route.fullPath" />
      </el-main>
    </el-container>
  </el-container>

  <!-- 修改密码弹窗 -->
  <el-dialog v-model="showPwdDialog" title="修改密码" width="420px" :close-on-click-modal="false" @closed="resetPwdForm" class="dialog-light" destroy-on-close>
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
      <el-button @click="showPwdDialog = false">取 消</el-button>
      <el-button type="primary" :loading="pwdLoading" @click="submitPwd">确 认</el-button>
    </template>
  </el-dialog>

  <!-- 修改用户名弹窗 -->
  <el-dialog v-model="showUsernameDialog" title="修改用户名" width="420px" :close-on-click-modal="false" @closed="resetUsernameForm" class="dialog-light" destroy-on-close>
    <el-form ref="usernameFormRef" :model="usernameForm" :rules="usernameRules" label-width="80px" label-position="right">
      <el-form-item label="当前用户">
        <el-input :model-value="auth.username" disabled />
      </el-form-item>
      <el-form-item label="新用户名" prop="newUsername">
        <el-input v-model="usernameForm.newUsername" placeholder="请输入新用户名（2-30个字符）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showUsernameDialog = false">取 消</el-button>
      <el-button type="primary" :loading="usernameLoading" @click="submitUsername">确 认</el-button>
    </template>
  </el-dialog>
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
const mainRef = ref<{ $el: HTMLElement } | null>(null)

// 路由变化时滚动到顶部
watch(() => route.path, () => {
  if (mainRef.value?.$el) {
    mainRef.value.$el.scrollTop = 0
  }
})

// 通知轮询
onMounted(() => { if (auth.token) notificationStore.startPolling() })
onUnmounted(() => { notificationStore.stopPolling() })

const active = computed(() => {
  if (route.path.startsWith('/teams')) return '/teams'
  if (route.path.startsWith('/players')) return '/players'
  if (route.path.startsWith('/news')) return '/news'
  if (route.path.startsWith('/community')) return '/community'
  if (route.path.startsWith('/trades')) return '/trades'
  if (route.path.startsWith('/drafts')) return '/drafts'
  if (route.path.startsWith('/history')) return '/history'
  return '/dashboard'
})

// 球队详情页标题显示球队名称
const detailTeamName = computed(() => {
  if (route.name === 'team-detail') {
    return (route.query.name as string) || '球队详情'
  }
  return null
})

const pageTitle = computed(() => (route.meta.title as string) || '')

const roleLabel = computed(() => (auth.isAdmin ? '管理员' : '普通用户'))
const userAvatar = computed(() => auth.avatarUrl)

function onLogout() {
  auth.logout()
  router.replace('/login')
}

/* -------- 修改密码 -------- */
const showPwdDialog = ref(false)
const pwdLoading = ref(false)
const pwdFormRef = ref<FormInstance>()

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const validateConfirm = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 128, message: '密码长度 6-128 个字符', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (value && value === pwdForm.oldPassword) {
          callback(new Error('新密码不能与原密码相同'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

function resetPwdForm() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdFormRef.value?.resetFields()
}

async function submitPwd() {
  await pwdFormRef.value?.validate()
  pwdLoading.value = true
  try {
    await changePasswordApi({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    showPwdDialog.value = false
    auth.logout()
    router.replace('/login')
  } catch (e: unknown) {
    const msg =
      typeof e === 'object' && e !== null && 'response' in e
        ? (e as { response?: { data?: { message?: string } } }).response?.data?.message
        : undefined
    ElMessage.error(msg || '密码修改失败')
  } finally {
    pwdLoading.value = false
  }
}

/* -------- 修改用户名 -------- */
const showUsernameDialog = ref(false)
const usernameLoading = ref(false)
const usernameFormRef = ref<FormInstance>()

const usernameForm = reactive({
  newUsername: '',
})

const usernameRules: FormRules = {
  newUsername: [
    { required: true, message: '请输入新用户名', trigger: 'blur' },
    { min: 2, max: 30, message: '用户名长度应为2-30个字符', trigger: 'blur' },
  ],
}

function resetUsernameForm() {
  usernameForm.newUsername = ''
  usernameFormRef.value?.resetFields()
}

async function submitUsername() {
  await usernameFormRef.value?.validate()
  usernameLoading.value = true
  try {
    const { data } = await changeUsernameApi({ newUsername: usernameForm.newUsername })
    // 更新本地存储的用户名和token
    auth.updateToken(data.token)
    auth.setUsername(data.username)
    ElMessage.success('用户名修改成功')
    showUsernameDialog.value = false
  } catch (e: unknown) {
    const msg =
      typeof e === 'object' && e !== null && 'response' in e
        ? (e as { response?: { data?: { message?: string } } }).response?.data?.message
        : undefined
    ElMessage.error(msg || '用户名修改失败')
  } finally {
    usernameLoading.value = false
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: var(--bg-sidebar);
  color: var(--text-primary);
  border-right: 1px solid var(--border-light);
  position: relative;
  overflow: hidden;
}

/* 侧边栏赛博朋克光效 */
.aside::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 1px;
  height: 100%;
  background: linear-gradient(180deg, transparent, var(--accent-glow), transparent);
  z-index: 10;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 16px;
  border-bottom: 1px solid var(--border-light);
  position: relative;
  z-index: 1;
}
.logo {
  width: 72px;
  height: 42px;
  border-radius: var(--radius-sm);
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2px 4px;
  box-sizing: border-box;
}
.logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}
.brand strong {
  display: block;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.brand span {
  display: block;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 11px;
  letter-spacing: 1.5px;
  text-transform: uppercase;
}

/* 侧边栏菜单 */
:deep(.el-menu) {
  background: transparent !important;
  border-right: none !important;
  position: relative;
  z-index: 1;
  padding: 8px 0;
}
:deep(.el-menu-item) {
  margin: 2px 12px;
  border-radius: var(--radius-md);
  color: var(--text-secondary) !important;
  font-size: 13px;
  font-family: var(--font-heading);
  font-weight: 600;
  transition: all var(--duration-fast) var(--ease-smooth) !important;
  position: relative;
  letter-spacing: 0.8px;
  text-transform: uppercase;
  height: 42px;
  line-height: 42px;
}
:deep(.el-menu-item .el-icon) {
  font-size: 18px;
  width: 18px;
  height: 18px;
}
:deep(.el-menu-item:hover) {
  background: var(--bg-hover) !important;
  color: var(--text-primary) !important;
}
:deep(.el-menu-item.is-active) {
  background: var(--accent-lighter) !important;
  color: var(--accent) !important;
  font-weight: 700;
  box-shadow: 0 0 20px rgba(0, 255, 136, 0.1);
}
:deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: linear-gradient(180deg, var(--purple-light), var(--purple));
  border-radius: 0 3px 3px 0;
}

/* 顶部栏 */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  background: var(--bg-header);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--border-light);
  padding: 0 28px;
  position: relative;
  z-index: 1;
}

/* 顶部栏底部光效 */
.header::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--accent-glow), transparent);
  opacity: 0.5;
}

.header-left {
  flex: 0 0 auto;
}
.title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 1px;
  text-transform: uppercase;
}
.right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.role-tag {
  border-radius: 20px !important;
  font-size: 11px !important;
  padding: 0 10px !important;
  font-family: var(--font-heading);
  font-weight: 700;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), var(--cyan));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  font-family: var(--font-heading);
  flex-shrink: 0;
  overflow: hidden;
}
.user-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.user {
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
}
.more-btn {
  color: var(--text-muted) !important;
  padding: 4px !important;
}
.more-btn svg {
  width: 20px;
  height: 20px;
}
.more-btn:hover {
  color: var(--text-primary) !important;
}

/* 主内容区 */
.main {
  background: var(--bg-page);
  min-height: calc(100vh - 60px);
  padding: 24px;
  position: relative;
  z-index: 1;
  overflow-y: auto;
  overflow-x: hidden;
}
.main::before {
  content: '';
  position: fixed;
  top: 60px;
  left: 220px;
  right: 0;
  bottom: 0;
  background: url('/images/login-bg.png') center / cover no-repeat;
  opacity: 0.06;
  pointer-events: none;
  z-index: 0;
}
.main::after {
  content: '';
  position: fixed;
  top: 60px;
  left: 220px;
  right: 0;
  bottom: 0;
  background-image:
    linear-gradient(rgba(108, 92, 231, 0.015) 1px, transparent 1px),
    linear-gradient(90deg, rgba(108, 92, 231, 0.015) 1px, transparent 1px);
  background-size: 80px 80px;
  pointer-events: none;
  z-index: 0;
  mask-image: radial-gradient(ellipse at 50% 30%, rgba(0,0,0,0.5) 0%, transparent 70%);
  -webkit-mask-image: radial-gradient(ellipse at 50% 30%, rgba(0,0,0,0.5) 0%, transparent 70%);
}
.main > * {
  position: relative;
  z-index: 1;
}
</style>
