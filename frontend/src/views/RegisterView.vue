<template>
  <div class="wrap animated-bg">
    <!-- 主题切换按钮 -->
    <div class="theme-toggle-wrapper">
      <ThemeToggle />
    </div>

    <!-- 浮动光晕粒子 -->
    <div class="bg-particles">
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
    </div>

    <div class="hero slide-up-enter">
      <p class="eyebrow">NBA DATA CENTER</p>
      <h1>NBA 数据分析系统</h1>
      <p class="desc">集中管理球队与球员基础数据，提供清晰的列表维护、权限控制和可视化看板。</p>
      <div class="features">
        <span>球队管理</span>
        <span>球员管理</span>
        <span>数据看板</span>
      </div>
    </div>

    <el-card class="card slide-up-enter" shadow="always" style="animation-delay: 0.15s;">
      <div class="card-head">
        <h2>注册新账号</h2>
        <p>创建账号即可开始使用系统</p>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" size="large" autocomplete="username" placeholder="请输入用户名（3-64 个字符）" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            autocomplete="new-password"
            placeholder="请输入密码（至少 6 个字符）"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            size="large"
            type="password"
            autocomplete="new-password"
            placeholder="请再次输入密码"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button class="login-btn" type="primary" size="large" :loading="loading" @click="submit">注 册</el-button>
        <div class="link-row">
          <span>已有账号？</span>
          <router-link to="/login">返回登录</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { registerApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import ThemeToggle from '@/components/ThemeToggle.vue'

const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
})

const validateConfirm = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '用户名长度 3-64 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 128, message: '密码长度 6-128 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

async function submit() {
  await formRef.value?.validate().catch(() => Promise.reject())
  loading.value = true
  try {
    const { data } = await registerApi({ username: form.username, password: form.password })
    auth.login(data.token, { username: data.username, role: data.role })
    ElMessage.success('注册成功，已自动登录')
    await router.replace('/dashboard')
  } catch (e: unknown) {
    const msg =
      typeof e === 'object' && e !== null && 'response' in e
        ? (e as { response?: { data?: { message?: string } } }).response?.data?.message
        : undefined
    ElMessage.error(msg || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.theme-toggle-wrapper {
  position: absolute;
  top: 24px;
  right: 24px;
  z-index: 10;
}

.wrap {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 72px;
  padding: 48px;
  box-sizing: border-box;
  position: relative;
  background: url('/images/login-bg.png') center / cover no-repeat;
}
.wrap::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(13, 17, 23, 0.92) 0%, rgba(13, 17, 23, 0.78) 100%);
  z-index: 0;
}
.hero {
  width: 480px;
  color: var(--text-primary);
  position: relative;
  z-index: 1;
}
.eyebrow {
  margin: 0 0 18px;
  color: var(--accent);
  letter-spacing: 5px;
  font-size: 12px;
  font-weight: 700;
  font-family: var(--font-body);
  text-transform: uppercase;
}
h1 {
  margin: 0;
  font-size: 44px;
  line-height: 1.15;
  font-weight: 700;
  font-family: var(--font-heading);
  color: var(--text-primary);
  letter-spacing: -0.5px;
}
.desc {
  margin: 20px 0 28px;
  color: var(--text-secondary);
  font-size: 15px;
  line-height: 1.8;
}
.features {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.features span {
  padding: 6px 14px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 13px;
  font-family: var(--font-body);
  font-weight: 500;
  transition: all var(--duration-fast) var(--ease-smooth);
  cursor: default;
}
.features span:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: rgba(0, 230, 118, 0.05);
}
.card {
  width: 420px;
  padding: 8px 12px 16px;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  background: var(--bg-card) !important;
  box-shadow: var(--shadow-lg) !important;
  position: relative;
  z-index: 1;
  overflow: hidden;
}
.card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), var(--accent-light), var(--accent));
  z-index: 2;
}
.card::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 120px;
  background: linear-gradient(180deg, rgba(0, 230, 118, 0.04) 0%, transparent 100%);
  pointer-events: none;
  z-index: 0;
}
.card-head {
  margin-bottom: 24px;
  text-align: center;
  position: relative;
  z-index: 1;
}
.card-head h2 {
  margin: 0 0 8px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.3px;
}
.card-head p {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}
:deep(.el-form-item__label) {
  color: var(--text-secondary) !important;
  font-weight: 500;
}
:deep(.el-input__wrapper) {
  background: var(--bg-input) !important;
  border: 1.5px solid var(--border-light) !important;
  border-radius: 8px !important;
  box-shadow: none !important;
  transition: all var(--duration-fast) var(--ease-smooth);
}
:deep(.el-input__wrapper:hover) {
  border-color: var(--border-medium) !important;
}
:deep(.el-input__wrapper.is-focus) {
  border-color: var(--accent) !important;
  box-shadow: 0 0 0 3px var(--accent-glow) !important;
}
:deep(.el-input__inner) {
  color: var(--text-primary) !important;
  font-family: var(--font-body);
}
:deep(.el-input__inner::placeholder) {
  color: var(--text-dim) !important;
}
.login-btn {
  width: 100%;
  margin-top: 8px;
  height: 44px !important;
  border-radius: var(--radius-md) !important;
  font-size: 15px !important;
  font-weight: 700 !important;
  font-family: var(--font-body);
  letter-spacing: 0.5px;
  background: var(--accent) !important;
  border-color: var(--accent) !important;
  color: #FFFFFF !important;
  transition: all var(--duration-fast) var(--ease-smooth) !important;
  box-shadow: 0 2px 12px var(--accent-glow);
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.15);
}
.login-btn:hover {
  background: linear-gradient(135deg, #33EB91 0%, #00E676 100%) !important;
  border-color: #33EB91 !important;
  box-shadow: 0 6px 24px var(--accent-glow-strong);
  transform: translateY(-2px);
}
.login-btn:active {
  transform: translateY(0) scale(0.98);
  box-shadow: 0 1px 4px var(--accent-glow);
}
.link-row {
  text-align: center;
  margin-top: 16px;
  position: relative;
  z-index: 1;
}
.link-row span {
  color: var(--text-muted);
  font-size: 14px;
}
.link-row a {
  color: var(--accent);
  font-size: 14px;
  text-decoration: none;
  margin-left: 4px;
  transition: color var(--duration-fast);
  font-weight: 500;
}
.link-row a:hover {
  color: #33EB91;
}
@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(30px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
