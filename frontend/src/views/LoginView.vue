<template>
  <div class="wrap">
    <div class="hero">
      <p class="eyebrow">NBA DATA CENTER</p>
      <h1>NBA 数据分析系统</h1>
      <p class="desc">集中管理球队与球员基础数据，提供清晰的列表维护、权限控制和可视化看板。</p>
      <div class="features">
        <span>球队管理</span>
        <span>球员管理</span>
        <span>数据看板</span>
      </div>
    </div>

    <el-card class="card" shadow="always">
      <div class="card-head">
        <h2>欢迎登录</h2>
        <p>请输入账号信息进入系统</p>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" size="large" autocomplete="username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button class="login-btn" type="primary" size="large" :loading="loading" @click="submit">登录系统</el-button>
        <div class="link-row">
          <span>还没有账号？</span>
          <router-link to="/register">立即注册</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { loginApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  await formRef.value?.validate().catch(() => Promise.reject())
  loading.value = true
  try {
    const { data } = await loginApi({ username: form.username, password: form.password })
    auth.login(data.token, { username: data.username, role: data.role })
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    await router.replace(redirect)
  } catch (e: unknown) {
    const msg =
      typeof e === 'object' && e !== null && 'response' in e
        ? (e as { response?: { data?: { message?: string } } }).response?.data?.message
        : undefined
    ElMessage.error(msg || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
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
  font-family: var(--font-body);
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
  background: #1C2333 !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-sm) !important;
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
  color: #0D1117 !important;
  transition: all var(--duration-fast) var(--ease-smooth) !important;
}
.login-btn:hover {
  background: #33EB91 !important;
  border-color: #33EB91 !important;
  box-shadow: 0 4px 16px var(--accent-glow);
  transform: translateY(-1px);
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
@media (max-width: 960px) {
  .wrap {
    flex-direction: column;
    gap: 28px;
    padding: 32px 20px;
  }
  .hero,
  .card {
    width: min(100%, 420px);
  }
  .hero {
    text-align: center;
  }
  .features {
    justify-content: center;
  }
  h1 {
    font-size: 32px;
  }
}
</style>
