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

    <div class="hero reveal-rise">
      <p class="eyebrow">NBA ANALYTICS PLATFORM</p>
      <h1>加入<span class="accent-word">NBA 赛事数据中心</span></h1>
      <p class="desc">注册账号即可掌握全联盟实时赛况、深入球员高阶技术统计，并利用 AI 智能助手进行战术深度复盘。</p>
      <div class="features">
        <span>实时赛况</span>
        <span>高阶数据</span>
        <span>AI 战术分析</span>
      </div>
    </div>

    <el-card class="card reveal-scale" shadow="always" style="animation-delay: 0.08s;">
      <div class="card-head">
        <h2>注册 NBA 数据中心账号</h2>
        <p>几步完成注册，开启专业数据分析体验</p>
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
    auth.login(data.token, { username: data.username, role: data.role, avatarUrl: data.avatarUrl })
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
  background:
    radial-gradient(900px 480px at 78% 12%, rgba(34, 211, 238, 0.14), transparent 60%),
    linear-gradient(135deg, rgba(11, 15, 23, 0.94) 0%, rgba(11, 15, 23, 0.82) 55%, rgba(11, 15, 23, 0.7) 100%);
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
  font-weight: 800;
  font-family: var(--font-heading);
  color: var(--text-primary);
  letter-spacing: -1px;
}
.accent-word {
  background: var(--accent-text-gradient, linear-gradient(120deg, #A5F3FC, #22D3EE));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 2px 14px rgba(34, 211, 238, 0.35));
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
  padding: 7px 15px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-secondary);
  font-size: 13px;
  font-family: var(--font-body);
  font-weight: 500;
  transition: all var(--duration-fast) var(--ease-smooth);
  cursor: default;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}
.features span:hover {
  border-color: rgba(34, 211, 238, 0.5);
  color: var(--accent-light);
  background: rgba(34, 211, 238, 0.12);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 4px 16px -4px rgba(34, 211, 238, 0.4);
  transform: translateY(-1px);
}
.card {
  width: 420px;
  padding: 8px 12px 16px;
  border: 1px solid rgba(255, 255, 255, 0.12) !important;
  border-radius: 20px !important;
  background: rgba(15, 23, 42, 0.55) !important;
  backdrop-filter: blur(24px) saturate(160%) !important;
  -webkit-backdrop-filter: blur(24px) saturate(160%) !important;
  box-shadow:
    0 24px 64px -12px rgba(0, 0, 0, 0.55),
    0 4px 16px rgba(0, 0, 0, 0.25),
    0 0 0 1px rgba(34, 211, 238, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.12) !important;
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
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent-light) 30%, var(--accent) 50%, var(--accent-light) 70%, transparent);
  box-shadow: 0 0 18px rgba(34, 211, 238, 0.5);
  z-index: 2;
}
.card::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 120px;
  background: linear-gradient(180deg, var(--accent-lighter) 0%, transparent 100%);
  pointer-events: none;
  z-index: 0;
}
.card-head {
  display: block; /* 覆盖全局 .card-head 的 flex */
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
  color: var(--accent-contrast, #04202A) !important;
  transition: all var(--duration-fast) var(--ease-smooth) !important;
  box-shadow: 0 2px 12px var(--accent-glow);
}
.login-btn:hover {
  background: linear-gradient(135deg, #0E7490 0%, #0891B2 100%) !important;
  border-color: #0E7490 !important;
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
  color: var(--accent-light);
}
@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(30px); }
  to { opacity: 1; transform: translateY(0); }
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
@media (max-width: 480px) {
  .wrap {
    padding: 24px 12px;
    gap: 20px;
  }
  .hero {
    width: 100%;
  }
  .eyebrow {
    font-size: 10px;
    letter-spacing: 3px;
  }
  h1 {
    font-size: 24px;
  }
  .desc {
    font-size: 13px;
  }
  .features span {
    font-size: 11px;
    padding: 4px 10px;
  }
  .card {
    width: 100%;
    padding: 4px 8px 12px;
  }
  .card-head h2 {
    font-size: 20px;
  }
}

/* 浅色主题覆盖 */
[data-theme="light"] .wrap::before {
  background:
    radial-gradient(900px 480px at 78% 12%, rgba(34, 211, 238, 0.1), transparent 60%),
    linear-gradient(105deg, rgba(250, 248, 245, 0.96) 0%, rgba(250, 248, 245, 0.9) 40%, rgba(250, 248, 245, 0.62) 72%, rgba(250, 248, 245, 0.55) 100%);
}

[data-theme="light"] .card {
  background: rgba(255, 255, 255, 0.86) !important;
  border-color: rgba(200, 195, 188, 0.5) !important;
  backdrop-filter: blur(20px) !important;
  -webkit-backdrop-filter: blur(20px) !important;
  box-shadow:
    0 20px 56px rgba(0, 0, 0, 0.12),
    0 4px 16px rgba(0, 0, 0, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.95) !important;
}

[data-theme="light"] .features span {
  background: rgba(255, 255, 255, 0.55);
  border-color: rgba(34, 211, 238, 0.3);
  color: #155E75;
}

[data-theme="light"] .card::after {
  background: linear-gradient(180deg, rgba(34, 211, 238, 0.03) 0%, transparent 100%);
}

[data-theme="light"] .login-btn:hover {
  background: linear-gradient(135deg, #0E7490 0%, #0891B2 100%) !important;
  border-color: #0E7490 !important;
  box-shadow: 0 6px 24px rgba(34, 211, 238, 0.3);
}

[data-theme="light"] .link-row a:hover {
  color: #0E7490;
}

[data-theme="light"] .features span:hover {
  background: rgba(34, 211, 238, 0.06);
}
</style>
