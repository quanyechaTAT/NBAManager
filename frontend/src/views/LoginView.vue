<template>
  <div class="wrap">
    <!-- 主题切换按钮 -->
    <div class="theme-toggle-wrapper">
      <ThemeToggle />
    </div>

    <div class="hero reveal-rise">
      <p class="eyebrow">NBA ANALYTICS PLATFORM</p>
      <h1>NBA<span class="accent-word">赛事数据中心</span></h1>
      <p class="desc">专业级 NBA 赛事数据、实时比分、球员高阶分析与 AI 技战术复盘一体化平台。</p>
      <div class="features">
        <span>实时赛况</span>
        <span>高阶数据</span>
        <span>AI 战术分析</span>
      </div>
    </div>

    <el-card class="card reveal-scale" shadow="always">
      <div class="card-head">
        <h2>登录 NBA 数据中心</h2>
        <p>登录后查看实时赛况、球员高阶数据与战术复盘</p>
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
import ThemeToggle from '@/components/ThemeToggle.vue'

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
    auth.login(data.token, { username: data.username, role: data.role, avatarUrl: data.avatarUrl })
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
.theme-toggle-wrapper {
  position: absolute;
  top: 24px;
  right: 24px;
  z-index: 10;
}

.wrap {
  height: 100vh;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 64px;
  padding: 48px;
  box-sizing: border-box;
  position: relative;
  overflow: hidden;
  background: url('/images/login-bg.png') center / cover no-repeat fixed;
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
.wrap::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(120% 90% at 50% 108%, rgba(11, 15, 23, 0.6), transparent 55%);
  pointer-events: none;
  z-index: 0;
}
.hero {
  width: 440px;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  color: var(--text-primary);
  position: relative;
  z-index: 1;
}
.eyebrow {
  margin: 0 0 18px;
  color: var(--accent);
  letter-spacing: 0.18em;
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-heading);
  text-transform: uppercase;
}
h1 {
  margin: 0;
  font-size: 44px;
  line-height: 1.1;
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
  margin: 16px 0 24px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
  font-family: var(--font-body);
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
  width: 400px;
  padding: 12px 16px 16px;
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
  animation-delay: 0.08s;
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
  display: block; /* 覆盖全局 .card-head 的 flex，避免标题与副标题并排 */
  margin-bottom: 20px;
  text-align: center;
  position: relative;
  z-index: 1;
}
.card-head h2 {
  margin: 0 0 6px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.3px;
}
.card-head p {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}
:deep(.el-form-item) {
  margin-bottom: 18px;
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
:deep(.el-card__body) {
  padding: 24px 20px 20px;
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
  margin-top: 4px;
  height: 40px !important;
  border-radius: 8px !important;
  font-size: 14px !important;
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
  opacity: 0.9;
  box-shadow: 0 6px 24px var(--accent-glow-strong);
  transform: translateY(-2px);
}
.login-btn:active {
  transform: translateY(0) scale(0.98);
  box-shadow: 0 1px 4px var(--accent-glow);
}
.link-row {
  text-align: center;
  margin-top: 12px;
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
  opacity: 0.8;
}
@media (max-width: 960px) {
  .wrap {
    flex-direction: column;
    gap: 24px;
    padding: 32px 20px;
    align-items: center;
  }
  .hero,
  .card {
    width: min(100%, 360px);
  }
  .hero {
    text-align: center;
    justify-content: flex-start;
  }
  .features {
    justify-content: center;
  }
  h1 {
    font-size: 28px;
  }
}
@media (max-width: 480px) {
  .wrap {
    padding: 24px 12px;
    gap: 16px;
  }
  .hero {
    width: 100%;
  }
  .eyebrow {
    font-size: 10px;
    letter-spacing: 3px;
  }
  h1 {
    font-size: 22px;
  }
  .desc {
    font-size: 13px;
    margin: 12px 0 16px;
  }
  .features span {
    font-size: 11px;
    padding: 4px 10px;
  }
  .card {
    width: 100%;
    padding: 4px 8px 12px;
  }
  :deep(.el-card__body) {
    padding: 16px 14px 14px;
  }
  .card-head {
    margin-bottom: 16px;
  }
  .card-head h2 {
    font-size: 18px;
  }
}

/* ===== 亮色主题登录页覆盖 ===== */
[data-theme="light"] .wrap::before {
  background:
    radial-gradient(900px 480px at 78% 12%, rgba(34, 211, 238, 0.1), transparent 60%),
    linear-gradient(105deg, rgba(250, 248, 245, 0.96) 0%, rgba(250, 248, 245, 0.9) 40%, rgba(250, 248, 245, 0.62) 72%, rgba(250, 248, 245, 0.55) 100%) !important;
}
[data-theme="light"] .wrap::after {
  background: radial-gradient(120% 90% at 50% 112%, rgba(120, 90, 60, 0.18), transparent 55%);
}
[data-theme="light"] .hero { color: #1A1A1A; }
[data-theme="light"] h1 { color: #1A1A1A; text-shadow: 0 1px 2px rgba(255,255,255,0.3); }
[data-theme="light"] .eyebrow { color: #0891B2; }
[data-theme="light"] .desc { color: #4A4540; }
[data-theme="light"] .features span {
  background: rgba(34, 211, 238, 0.1);
  border: 1px solid rgba(34, 211, 238, 0.3);
  color: #155E75;
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}
[data-theme="light"] .features span:hover {
  border-color: #0891B2;
  color: #0891B2;
  background: rgba(34, 211, 238, 0.15);
}
[data-theme="light"] .card {
  background: rgba(255, 255, 255, 0.85) !important;
  border-color: rgba(200, 195, 188, 0.5) !important;
  backdrop-filter: blur(20px) !important;
  -webkit-backdrop-filter: blur(20px) !important;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.1), 0 4px 16px rgba(0, 0, 0, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.9) !important;
}
[data-theme="light"] .card::before {
  background: linear-gradient(90deg, #0891B2, #22D3EE, #0891B2);
}
[data-theme="light"] .card::after {
  background: linear-gradient(180deg, rgba(34, 211, 238, 0.06) 0%, transparent 100%);
}
[data-theme="light"] .card-head h2 { color: #1A1A1A; }
[data-theme="light"] .card-head p { color: #78716C; }
[data-theme="light"] :deep(.el-form-item__label) { color: #4A4540 !important; }
[data-theme="light"] :deep(.el-input__wrapper) {
  background: rgba(245, 243, 240, 0.8) !important;
  border: 1.5px solid #D4CFC8 !important;
}
[data-theme="light"] :deep(.el-input__wrapper:hover) {
  border-color: #B0A89E !important;
}
[data-theme="light"] :deep(.el-input__wrapper.is-focus) {
  border-color: #0891B2 !important;
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.12) !important;
}
[data-theme="light"] :deep(.el-input__inner) { color: #1A1A1A !important; }
[data-theme="light"] :deep(.el-input__inner::placeholder) { color: #A8A29E !important; }
[data-theme="light"] .link-row span { color: #78716C; }
[data-theme="light"] .link-row a { color: #0891B2; }
[data-theme="light"] .login-btn {
  background: #0891B2 !important;
  border-color: #0891B2 !important;
  color: var(--accent-contrast, #04202A) !important;
  box-shadow: 0 2px 12px rgba(34, 211, 238, 0.25);
}
[data-theme="light"] .login-btn:hover {
  background: #0E7490 !important;
  border-color: #0E7490 !important;
  box-shadow: 0 6px 24px rgba(34, 211, 238, 0.3);
}
</style>
