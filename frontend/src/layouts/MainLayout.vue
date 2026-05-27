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
        <el-menu-item index="/dashboard">📊 数据看板</el-menu-item>
        <el-menu-item index="/teams">🏀 球队数据</el-menu-item>
        <el-menu-item index="/players">⭐ 球员数据</el-menu-item>
        <el-menu-item index="/news">📰 赛事资讯</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">{{ pageTitle }}</span>
        <div class="right">
          <el-tag :type="auth.isAdmin ? 'warning' : 'info'" effect="light">{{ roleLabel }}</el-tag>
          <span class="user">{{ auth.username }}</span>
          <el-button class="logout-btn" link @click="showPwdDialog = true">修改密码</el-button>
          <el-button class="logout-btn" link @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- 修改密码弹窗 -->
  <el-dialog v-model="showPwdDialog" title="修改密码" width="420px" :close-on-click-modal="false" @closed="resetPwdForm">
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
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { changePasswordApi } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const active = computed(() => {
  if (route.path.startsWith('/teams')) return '/teams'
  if (route.path.startsWith('/players')) return '/players'
  if (route.path.startsWith('/news')) return '/news'
  return '/dashboard'
})

const pageTitle = computed(() => (route.meta.title as string) || '')

const roleLabel = computed(() => (auth.isAdmin ? '管理员' : '普通用户'))

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
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: var(--bg-sidebar);
  color: var(--text-primary);
  border-right: 1px solid var(--border-light);
  box-shadow: 1px 0 8px rgba(0, 0, 0, 0.2);
  position: relative;
  overflow: hidden;
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
:deep(.el-menu) {
  background: transparent !important;
  border-right: none !important;
  position: relative;
  z-index: 1;
}
:deep(.el-menu-item) {
  margin: 4px 12px;
  border-radius: var(--radius-md);
  color: var(--text-secondary) !important;
  font-size: 14px;
  font-family: var(--font-body);
  font-weight: 500;
  transition: all var(--duration-fast) var(--ease-smooth) !important;
  position: relative;
  letter-spacing: 0.2px;
}
:deep(.el-menu-item:hover) {
  background: var(--bg-hover) !important;
  color: var(--text-primary) !important;
}
:deep(.el-menu-item.is-active) {
  background: rgba(0, 230, 118, 0.08) !important;
  color: var(--accent) !important;
  font-weight: 600;
}
:deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: var(--accent);
  border-radius: 0 3px 3px 0;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  background: var(--bg-header);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--border-light);
  padding: 0 28px;
  position: relative;
  z-index: 1;
}
.title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
}
.right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.user {
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
}
.logout-btn {
  color: var(--text-muted) !important;
  font-size: 14px;
  transition: color var(--duration-fast) var(--ease-smooth) !important;
}
.logout-btn:hover {
  color: var(--accent) !important;
}
.main {
  background: var(--bg-page);
  min-height: calc(100vh - 60px);
  padding: 24px;
  position: relative;
  z-index: 1;
}
</style>
