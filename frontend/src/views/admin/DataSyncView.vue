<template>
  <div class="data-management-page">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="bg-orb bg-orb-1"></div>
      <div class="bg-orb bg-orb-2"></div>
      <div class="bg-grid"></div>
    </div>

    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-left">
        <div class="header-icon">⚡</div>
        <div>
          <h2>数据管理中心</h2>
          <p class="subtitle">NBA 数据同步控制台 · {{ currentSeason }} 赛季</p>
        </div>
      </div>
      <div class="header-actions">
        <button
          v-if="isAnySyncing"
          class="btn-action btn-pause"
          @click="handlePauseAll"
        >
          {{ isPaused ? '▶ 继续全部' : '⏸ 暂停全部' }}
        </button>
        <button
          v-if="isAnySyncing"
          class="btn-action btn-cancel"
          @click="handleCancelAll"
        >
          ✕ 取消全部
        </button>
        <button
          class="btn-primary"
          :disabled="isAnySyncing"
          @click="handleSyncAll"
        >
          <span class="btn-icon" :class="{ spinning: isAnySyncing }">⟳</span>
          <span>{{ isAnySyncing ? '同步中...' : '一键全量同步' }}</span>
        </button>
      </div>
    </div>

    <!-- 全局进度条 -->
    <Transition name="slide-down">
      <div v-if="isAnySyncing" class="global-progress">
        <div class="progress-info">
          <span class="progress-label">全量同步进度</span>
          <span class="progress-value">{{ globalProgress }}%</span>
        </div>
        <div class="progress-track">
          <div class="progress-bar" :style="{ width: globalProgress + '%' }"></div>
        </div>
        <div class="progress-status">
          <span class="pulse-dot"></span>
          正在同步: {{ currentSyncingModules.join(', ') || '准备中...' }}
        </div>
      </div>
    </Transition>

    <!-- 统计概览 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon">📊</div>
        <div class="stat-info">
          <span class="stat-value">{{ totalCount.toLocaleString() }}</span>
          <span class="stat-label">总数据量</span>
        </div>
      </div>
      <div class="stat-card" :class="{ 'stat-active': syncingCount > 0 }">
        <div class="stat-icon">⟳</div>
        <div class="stat-info">
          <span class="stat-value">{{ syncingCount }}</span>
          <span class="stat-label">同步中</span>
        </div>
      </div>
      <div class="stat-card stat-success">
        <div class="stat-icon">✓</div>
        <div class="stat-info">
          <span class="stat-value">{{ successCount }}</span>
          <span class="stat-label">已完成</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">🕐</div>
        <div class="stat-info">
          <span class="stat-value stat-time">{{ lastSyncTime || '从未' }}</span>
          <span class="stat-label">上次同步</span>
        </div>
      </div>
    </div>

    <!-- 模块卡片网格 -->
    <div class="modules-grid">
      <div
        v-for="(module, index) in modules"
        :key="module.key"
        class="module-card"
        :class="[`status-${module.status}`, { 'is-syncing': module.status === 'syncing' }]"
        :style="{ animationDelay: index * 0.05 + 's' }"
      >
        <!-- 状态指示条 -->
        <div class="card-indicator" :class="module.status"></div>

        <!-- 卡片内容 -->
        <div class="card-body">
          <!-- 头部：图标 + 名称 + 状态 -->
          <div class="card-header">
            <div class="card-icon-wrap">
              <span class="card-icon">{{ module.icon }}</span>
              <span v-if="module.status === 'syncing'" class="icon-ring"></span>
            </div>
            <div class="card-meta">
              <h3>{{ module.name }}</h3>
              <span class="card-count">{{ module.count.toLocaleString() }} 条</span>
            </div>
            <div class="card-badge" :class="module.status">
              {{ getStatusText(module.status) }}
            </div>
          </div>

          <!-- 进度条 -->
          <div class="card-progress">
            <div class="progress-track">
              <div class="progress-bar" :class="module.status" :style="{ width: module.progress + '%' }"></div>
            </div>
            <span class="progress-pct">{{ module.progress }}%</span>
          </div>

          <!-- 底部：时间 + 操作 -->
          <div class="card-footer">
            <span class="card-time">
              {{ module.lastSyncTime ? formatTime(module.lastSyncTime) : '尚未同步' }}
            </span>
            <div class="card-actions">
              <button
                v-if="module.status === 'syncing' || module.status === 'paused'"
                class="btn-sm btn-sm-pause"
                @click="handlePauseModule(module.key)"
                :title="module.paused ? '继续' : '暂停'"
              >
                {{ module.paused ? '▶' : '⏸' }}
              </button>
              <button
                v-if="module.status === 'syncing' || module.status === 'paused'"
                class="btn-sm btn-sm-cancel"
                @click="handleCancelModule(module.key)"
                title="取消"
              >
                ✕
              </button>
              <button
                v-if="module.status !== 'syncing' && module.status !== 'paused'"
                class="btn-sm btn-sm-sync"
                :disabled="isAnySyncing && !module.paused"
                @click="handleSyncModule(module.key)"
              >
                ⟳ 同步
              </button>
            </div>
          </div>

          <!-- 错误信息 -->
          <Transition name="slide-down">
            <div v-if="module.status === 'error' && module.errorMessage" class="card-error">
              ⚠️ {{ module.errorMessage }}
            </div>
          </Transition>
        </div>
      </div>
    </div>

    <!-- 同步日志 -->
    <div class="log-panel">
      <div class="log-header">
        <div class="log-title">
          <span>📝</span>
          <span>同步日志</span>
          <span class="log-count" v-if="logs.length > 0">{{ logs.length }}</span>
        </div>
        <button class="btn-ghost" @click="logs = []" v-if="logs.length > 0">清空</button>
      </div>
      <div class="log-body" ref="logContainer">
        <div v-if="logs.length === 0" class="log-empty">
          <span>📋</span>
          <span>暂无同步记录</span>
        </div>
        <div
          v-for="(log, i) in logs"
          :key="i"
          class="log-entry"
          :class="log.type"
        >
          <span class="log-time">{{ log.time }}</span>
          <span class="log-badge">{{ log.module }}</span>
          <span class="log-msg">{{ log.message }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getDataOverview, syncModule, syncAllModules, getAllProgress,
  pauseModule, resumeModule, cancelModule, pauseAllModules, cancelAllModules
} from '@/api/dataManagement'
import type { ModuleInfo } from '@/api/dataManagement'

// 扩展 ModuleInfo 添加 paused 状态
interface ExtendedModuleInfo extends ModuleInfo {
  paused?: boolean
}

const currentSeason = ref('')
const modules = ref<ExtendedModuleInfo[]>([])
const logs = ref<Array<{ time: string; module: string; message: string; type: string }>>([])
const logContainer = ref<HTMLElement | null>(null)
const isPaused = ref(false)
let pollTimer: number | null = null

// 计算属性
const isAnySyncing = computed(() => modules.value.some(m => m.status === 'syncing' || m.status === 'pending' || m.status === 'paused'))
const totalCount = computed(() => modules.value.reduce((sum, m) => sum + m.count, 0))
const syncingCount = computed(() => modules.value.filter(m => m.status === 'syncing' || m.status === 'pending').length)
const successCount = computed(() => modules.value.filter(m => m.status === 'success').length)
const lastSyncTime = computed(() => {
  const times = modules.value
    .filter(m => m.lastSyncTime)
    .map(m => m.lastSyncTime)
    .sort()
    .reverse()
  return times.length > 0 ? formatTime(times[0]) : ''
})

const globalProgress = computed(() => {
  if (modules.value.length === 0) return 0
  const total = modules.value.reduce((sum, m) => sum + m.progress, 0)
  return Math.round(total / modules.value.length)
})

const currentSyncingModules = computed(() => {
  return modules.value.filter(m => m.status === 'syncing').map(m => m.name)
})

// 获取状态文本
function getStatusText(status: string): string {
  const map: Record<string, string> = {
    idle: '待同步',
    pending: '等待中',
    syncing: '同步中',
    paused: '已暂停',
    success: '已完成',
    error: '失败'
  }
  return map[status] || status
}

// 格式化时间
function formatTime(time: string): string {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 添加日志
function addLog(module: string, message: string, type: string = 'info') {
  const now = new Date()
  const time = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  logs.value.push({ time, module, message, type })
  if (logs.value.length > 100) logs.value.shift()
  nextTick(() => {
    if (logContainer.value) {
      logContainer.value.scrollTop = logContainer.value.scrollHeight
    }
  })
}

// 加载概览数据
async function loadOverview() {
  try {
    const { data } = await getDataOverview()
    currentSeason.value = data.currentSeason
    modules.value = data.modules.map((m: ModuleInfo) => ({ ...m, paused: false }))

    // 检查是否有正在进行的同步，如果有则开始轮询
    const hasActiveSync = modules.value.some(m => m.status === 'syncing' || m.status === 'pending')
    if (hasActiveSync || data.syncing) {
      startPolling()
      addLog('系统', '检测到正在进行的同步任务，开始监控进度...', 'info')
    }
  } catch (error) {
    console.error('加载数据概览失败:', error)
  }
}

// 同步单个模块
async function handleSyncModule(moduleKey: string) {
  const module = modules.value.find(m => m.key === moduleKey)
  if (!module) return

  try {
    module.status = 'syncing'
    module.progress = 0
    module.paused = false
    addLog(module.name, '开始同步...', 'info')

    const { data } = await syncModule(moduleKey)

    if (data.status === 'started' || data.status === 'syncing') {
      addLog(module.name, data.message, 'info')
      startPolling()
    } else {
      module.status = 'error'
      module.errorMessage = data.message
      addLog(module.name, data.message, 'error')
    }
  } catch (error: any) {
    module.status = 'error'
    module.errorMessage = error.message || '同步失败'
    addLog(module.name, `同步失败: ${error.message}`, 'error')
  }
}

// 全量同步
async function handleSyncAll() {
  try {
    isPaused.value = false
    modules.value.forEach(m => {
      m.status = 'pending'
      m.progress = 0
      m.paused = false
    })
    addLog('系统', '开始全量同步...', 'info')

    const { data } = await syncAllModules()

    if (data.status === 'started') {
      addLog('系统', data.message, 'info')
      startPolling()
    }
  } catch (error: any) {
    addLog('系统', `全量同步失败: ${error.message}`, 'error')
  }
}

// 暂停/继续全部
async function handlePauseAll() {
  isPaused.value = !isPaused.value
  if (isPaused.value) {
    try {
      await pauseAllModules()
      stopPolling()
      addLog('系统', '所有同步已暂停', 'info')
      modules.value.forEach(m => {
        if (m.status === 'syncing') {
          m.status = 'paused'
          m.paused = true
        }
      })
    } catch (error: any) {
      addLog('系统', `暂停失败: ${error.message}`, 'error')
    }
  } else {
    try {
      // 继续所有暂停的模块
      for (const m of modules.value) {
        if (m.paused) {
          await resumeModule(m.key)
          m.status = 'syncing'
          m.paused = false
        }
      }
      addLog('系统', '同步已继续', 'info')
      startPolling()
    } catch (error: any) {
      addLog('系统', `继续失败: ${error.message}`, 'error')
    }
  }
}

// 取消全部
async function handleCancelAll() {
  try {
    await cancelAllModules()
    stopPolling()
    isPaused.value = false
    modules.value.forEach(m => {
      if (m.status === 'syncing' || m.status === 'pending' || m.status === 'paused') {
        m.status = 'idle'
        m.progress = 0
        m.paused = false
      }
    })
    addLog('系统', '所有同步已取消', 'info')
    ElMessage.warning('同步已取消')
  } catch (error: any) {
    addLog('系统', `取消失败: ${error.message}`, 'error')
  }
}

// 暂停单个模块
async function handlePauseModule(moduleKey: string) {
  const module = modules.value.find(m => m.key === moduleKey)
  if (!module) return

  try {
    if (module.paused) {
      // 继续
      await resumeModule(moduleKey)
      module.status = 'syncing'
      module.paused = false
      addLog(module.name, '已继续', 'info')
      startPolling()
    } else {
      // 暂停
      await pauseModule(moduleKey)
      module.status = 'paused'
      module.paused = true
      addLog(module.name, '已暂停', 'info')
    }
  } catch (error: any) {
    addLog(module.name, `操作失败: ${error.message}`, 'error')
  }
}

// 取消单个模块
async function handleCancelModule(moduleKey: string) {
  const module = modules.value.find(m => m.key === moduleKey)
  if (!module) return

  try {
    await cancelModule(moduleKey)
    module.status = 'idle'
    module.progress = 0
    module.paused = false
    addLog(module.name, '已取消', 'info')
  } catch (error: any) {
    addLog(module.name, `取消失败: ${error.message}`, 'error')
  }
}

// 轮询进度
function startPolling() {
  if (pollTimer) return

  pollTimer = window.setInterval(async () => {
    try {
      const { data } = await getAllProgress()

      let allDone = true
      Object.entries(data).forEach(([key, progress]) => {
        const module = modules.value.find(m => m.key === key)
        if (module) {
          const prevStatus = module.status
          module.status = progress.status as any
          module.progress = progress.progress
          module.lastSyncTime = progress.lastSyncTime
          module.errorMessage = progress.errorMessage
          module.paused = progress.paused || false

          if (prevStatus !== progress.status) {
            if (progress.status === 'success') {
              addLog(module.name, '同步完成 ✓', 'success')
            } else if (progress.status === 'error') {
              addLog(module.name, `同步失败: ${progress.errorMessage}`, 'error')
            } else if (progress.status === 'paused') {
              addLog(module.name, '已暂停', 'info')
            }
          }

          if (progress.status === 'syncing' || progress.status === 'pending') {
            allDone = false
          }
        }
      })

      if (allDone && !isPaused.value) {
        stopPolling()
        addLog('系统', '所有同步任务已完成 ✓', 'success')
      }
    } catch (error) {
      console.error('获取进度失败:', error)
    }
  }, 1500)
}

// 停止轮询
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(() => {
  loadOverview()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
/* ===== 页面基础 ===== */
.data-management-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 28px;
  position: relative;
  min-height: 100vh;
}

/* ===== 背景装饰 ===== */
.bg-decoration {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.15;
}

.bg-orb-1 {
  width: 400px;
  height: 400px;
  top: -100px;
  right: -100px;
  background: var(--accent);
}

.bg-orb-2 {
  width: 300px;
  height: 300px;
  bottom: -50px;
  left: -50px;
  background: var(--purple);
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--border-light) 1px, transparent 1px),
    linear-gradient(90deg, var(--border-light) 1px, transparent 1px);
  background-size: 60px 60px;
  opacity: 0.3;
}

/* ===== 页面标题 ===== */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
  position: relative;
  z-index: 1;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-icon {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--accent), var(--gold));
  border-radius: 14px;
  font-size: 26px;
  box-shadow: 0 4px 16px var(--accent-glow);
}

.header-left h2 {
  font-size: 26px;
  font-weight: 800;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: -0.5px;
}

.subtitle {
  font-size: 14px;
  color: var(--text-muted);
  margin: 4px 0 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* ===== 按钮样式 ===== */
.btn-action {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-pause {
  background: var(--warning);
  color: #fff;
}

.btn-pause:hover {
  background: #d97706;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.3);
}

.btn-cancel {
  background: var(--danger);
  color: #fff;
}

.btn-cancel:hover {
  background: #dc2626;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
}

.btn-primary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: linear-gradient(135deg, var(--accent), var(--gold));
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 16px var(--accent-glow);
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px var(--accent-glow);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-ghost {
  padding: 6px 14px;
  background: transparent;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-ghost:hover {
  background: var(--danger);
  border-color: var(--danger);
  color: #fff;
}

/* ===== 全局进度条 ===== */
.global-progress {
  background: var(--bg-card);
  border: 1px solid var(--accent);
  border-radius: 14px;
  padding: 20px 24px;
  margin-bottom: 24px;
  position: relative;
  z-index: 1;
  box-shadow: 0 0 30px var(--accent-dim);
}

.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.progress-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.progress-value {
  font-size: 28px;
  font-weight: 800;
  color: var(--accent);
  font-family: var(--font-heading);
}

.progress-track {
  height: 10px;
  background: var(--bg-hover);
  border-radius: 5px;
  overflow: hidden;
  margin-bottom: 10px;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--accent), var(--gold));
  border-radius: 5px;
  transition: width 0.4s ease;
  position: relative;
}

.progress-bar::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent);
  animation: shimmer 2s infinite;
}

@keyframes shimmer {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

.progress-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-muted);
}

.pulse-dot {
  width: 8px;
  height: 8px;
  background: var(--accent);
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.3); }
}

/* ===== 统计卡片 ===== */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 28px;
  position: relative;
  z-index: 1;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stat-card.stat-active {
  border-color: var(--accent);
  box-shadow: 0 0 20px var(--accent-dim);
}

.stat-card.stat-success {
  border-color: var(--success);
}

.stat-icon {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-hover);
  border-radius: 10px;
  font-size: 20px;
}

.stat-active .stat-icon {
  background: var(--accent-dim);
  animation: spin 2s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-value {
  font-size: 22px;
  font-weight: 800;
  color: var(--text-primary);
  font-family: var(--font-heading);
}

.stat-time {
  font-size: 14px;
  font-weight: 600;
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* ===== 模块卡片网格 ===== */
.modules-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 18px;
  margin-bottom: 28px;
  position: relative;
  z-index: 1;
}

.module-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 14px;
  overflow: hidden;
  transition: all 0.3s ease;
  animation: cardIn 0.4s ease forwards;
  opacity: 0;
  transform: translateY(10px);
}

@keyframes cardIn {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.module-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-3px);
}

.module-card.is-syncing {
  border-color: var(--accent);
  box-shadow: 0 0 24px var(--accent-dim);
}

/* 状态指示条 */
.card-indicator {
  height: 3px;
  width: 100%;
}

.card-indicator.idle {
  background: var(--border-light);
}

.card-indicator.pending {
  background: var(--warning);
}

.card-indicator.syncing {
  background: linear-gradient(90deg, var(--accent), var(--gold));
  animation: indicatorPulse 1.5s infinite;
}

.card-indicator.paused {
  background: var(--warning);
  animation: indicatorPulse 2s infinite;
}

@keyframes indicatorPulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.card-indicator.success {
  background: var(--success);
}

.card-indicator.error {
  background: var(--danger);
}

/* 卡片内容 */
.card-body {
  padding: 18px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.card-icon-wrap {
  position: relative;
  width: 46px;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-hover);
  border-radius: 12px;
}

.card-icon {
  font-size: 22px;
}

.icon-ring {
  position: absolute;
  inset: -3px;
  border: 2px solid var(--accent);
  border-radius: 14px;
  opacity: 0;
  animation: ringPulse 1.5s infinite;
}

@keyframes ringPulse {
  0% { opacity: 0; transform: scale(0.95); }
  50% { opacity: 0.5; }
  100% { opacity: 0; transform: scale(1.1); }
}

.card-meta {
  flex: 1;
}

.card-meta h3 {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 2px;
}

.card-count {
  font-size: 12px;
  color: var(--text-muted);
}

.card-badge {
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.card-badge.idle {
  background: var(--bg-hover);
  color: var(--text-dim);
}

.card-badge.pending {
  background: rgba(245, 158, 11, 0.1);
  color: var(--warning);
}

.card-badge.syncing {
  background: var(--accent-dim);
  color: var(--accent);
}

.card-badge.paused {
  background: rgba(245, 158, 11, 0.1);
  color: var(--warning);
}

.card-badge.success {
  background: rgba(16, 185, 129, 0.1);
  color: var(--success);
}

.card-badge.error {
  background: rgba(239, 68, 68, 0.1);
  color: var(--danger);
}

/* 进度条 */
.card-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.card-progress .progress-track {
  flex: 1;
  height: 6px;
  background: var(--bg-hover);
  border-radius: 3px;
  overflow: hidden;
}

.card-progress .progress-bar {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease;
  background: var(--text-dim);
}

.card-progress .progress-bar.syncing {
  background: linear-gradient(90deg, var(--accent), var(--gold));
}

.card-progress .progress-bar.success {
  background: var(--success);
}

.card-progress .progress-bar.error {
  background: var(--danger);
}

.progress-pct {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  min-width: 36px;
  text-align: right;
}

/* 卡片底部 */
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-time {
  font-size: 11px;
  color: var(--text-dim);
}

.card-actions {
  display: flex;
  gap: 6px;
}

.btn-sm {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-sm:hover:not(:disabled) {
  transform: scale(1.1);
}

.btn-sm:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn-sm-pause {
  background: rgba(245, 158, 11, 0.1);
  border-color: rgba(245, 158, 11, 0.3);
  color: var(--warning);
}

.btn-sm-pause:hover:not(:disabled) {
  background: var(--warning);
  color: #fff;
}

.btn-sm-cancel {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
  color: var(--danger);
}

.btn-sm-cancel:hover:not(:disabled) {
  background: var(--danger);
  color: #fff;
}

.btn-sm-sync {
  width: auto;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
}

.btn-sm-sync:hover:not(:disabled) {
  background: var(--accent-dim);
  border-color: var(--accent);
  color: var(--accent);
}

/* 错误信息 */
.card-error {
  margin-top: 12px;
  padding: 10px 12px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.15);
  border-radius: 8px;
  font-size: 12px;
  color: var(--danger);
}

/* ===== 同步日志 ===== */
.log-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 14px;
  overflow: hidden;
  position: relative;
  z-index: 1;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
}

.log-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.log-count {
  padding: 2px 8px;
  background: var(--accent-dim);
  border-radius: 10px;
  font-size: 11px;
  color: var(--accent);
}

.log-body {
  max-height: 280px;
  overflow-y: auto;
  padding: 12px 20px;
}

.log-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px;
  color: var(--text-dim);
  font-size: 13px;
}

.log-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-light);
  font-size: 13px;
}

.log-entry:last-child {
  border-bottom: none;
}

.log-time {
  color: var(--text-dim);
  font-size: 11px;
  min-width: 70px;
  font-family: var(--font-mono);
}

.log-badge {
  padding: 2px 8px;
  background: var(--bg-hover);
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  color: var(--text-secondary);
  min-width: 50px;
  text-align: center;
}

.log-msg {
  flex: 1;
  color: var(--text-secondary);
}

.log-entry.success .log-msg {
  color: var(--success);
}

.log-entry.error .log-msg {
  color: var(--danger);
}

/* ===== 过渡动画 ===== */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .data-management-page {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .modules-grid {
    grid-template-columns: 1fr;
  }
}
</style>
