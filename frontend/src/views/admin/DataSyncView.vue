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
        <div class="header-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m13 2-9 12h7l-1 8 9-12h-7l1-8Z"/></svg>
        </div>
        <div>
          <h2>数据调度中心</h2>
          <p class="subtitle">NBA 全维度官方数据调度与同步控制台 · {{ currentSeason }} 赛季</p>
        </div>
      </div>
      <div class="header-actions">
        <button
          v-if="isAnySyncing"
          class="btn-action btn-cancel"
          @click="handleCancelAll"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>
          取消全部
        </button>
        <button
          class="btn-primary"
          :disabled="isAnySyncing"
          @click="handleSyncAll"
        >
          <svg class="btn-icon" :class="{ spinning: isAnySyncing }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M20 11a8 8 0 0 0-14.7-4L3 10"/><path d="M3 4v6h6"/><path d="M4 13a8 8 0 0 0 14.7 4L21 14"/><path d="M21 20v-6h-6"/></svg>
          <span>{{ isAnySyncing ? '同步中...' : '一键全量同步' }}</span>
        </button>
      </div>
    </div>

    <!-- 全局进度条 -->
    <Transition name="slide-down">
      <div v-if="isAnySyncing" class="global-progress">
        <div class="progress-info">
          <span class="progress-label">{{ fullSyncJobId ? '全量同步进度' : '任务进度' }}</span>
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

    <!-- 失败告警：把失败结果提到顶部，不必滚动查找 -->
    <Transition name="slide-down">
      <div v-if="failedModules.length > 0 && !isAnySyncing" class="failure-alert" role="alert">
        <div class="failure-alert-main">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="m12 3 9 17H3L12 3Z"/><path d="M12 9v5M12 17v.01"/></svg>
          <div class="failure-alert-text">
            <strong>{{ failedModules.length }} 个模块同步失败</strong>
            <span>{{ failedModules.map(m => m.name).join('、') }}</span>
          </div>
        </div>
        <button class="btn-sm btn-sm-retry" :disabled="isAnySyncing" @click="retryFailed">
          重试全部
        </button>
      </div>
    </Transition>

    <!-- 成功结果：与进度明确区分 -->
    <Transition name="slide-down">
      <div v-if="recentSuccess && !isAnySyncing && failedModules.length === 0" class="success-alert">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg>
        <span>{{ recentSuccess }}</span>
      </div>
    </Transition>

    <!-- 统计概览 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 19V5M4 19h16"/><path d="m7 15 3-4 3 2 5-7"/></svg></div>
        <div class="stat-info">
          <span class="stat-value">{{ totalCount.toLocaleString() }}</span>
          <span class="stat-label">总数据量</span>
        </div>
      </div>
      <div class="stat-card" :class="{ 'stat-active': syncingCount > 0 }">
        <div class="stat-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M20 11a8 8 0 0 0-14.7-4L3 10"/><path d="M3 4v6h6"/><path d="M4 13a8 8 0 0 0 14.7 4L21 14"/><path d="M21 20v-6h-6"/></svg></div>
        <div class="stat-info">
          <span class="stat-value">{{ syncingCount }}</span>
          <span class="stat-label">同步中</span>
        </div>
      </div>
      <div class="stat-card stat-success">
        <div class="stat-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m5 12 4 4L19 6"/></svg></div>
        <div class="stat-info">
          <span class="stat-value">{{ successCount }}</span>
          <span class="stat-label">已完成</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3 2"/></svg></div>
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
        :class="[`status-${module.status}`, { 'is-syncing': module.status === 'RUNNING' }]"
        :style="{ animationDelay: index * 0.05 + 's' }"
      >
        <!-- 状态指示条 -->
        <div class="card-indicator" :class="module.status"></div>

        <!-- 卡片内容 -->
        <div class="card-body">
          <!-- 头部：图标 + 名称 + 状态 -->
          <div class="card-header">
            <div class="card-icon-wrap">
              <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path :d="moduleIconPath(module.key)"/></svg>
              <span v-if="module.status === 'RUNNING'" class="icon-ring"></span>
            </div>
            <div class="card-meta">
              <h3>{{ module.name }}</h3>
              <span class="card-count">{{ module.count.toLocaleString() }} 条</span>
              <span v-if="module.triggerType" class="source-tag">
                {{ triggerLabel(module.triggerType) }}
              </span>
            </div>
            <div class="card-badge" :class="module.status">
              {{ getStatusText(module.status) }}
            </div>
          </div>

          <!-- 执行进度只在任务进行中显示；终态交给结果与错误提示。 -->
          <div v-if="ACTIVE_STATUSES.has(module.status)" class="card-progress">
            <span class="card-progress-label">执行</span>
            <div class="progress-track">
              <div class="progress-bar" :class="module.status" :style="{ width: module.progress + '%' }"></div>
            </div>
            <span class="progress-pct">{{ module.progress }}%</span>
          </div>
          <div v-else-if="TERMINAL_STATUSES.has(module.status)" class="card-result" :class="module.status">
            同步结果：{{ getStatusText(module.status) }}
          </div>
          <div v-if="module.activeFullStep" class="step-stage">
            全量步骤 {{ module.activeFullStep.stepOrder + 1 }} ·
            {{ module.activeFullStep.stage || getStatusText(module.activeFullStep.status) }}
          </div>

          <!-- 底部：时间 + 操作 -->
          <div class="card-footer">
            <span class="card-time">
              {{ module.lastSyncTime ? formatTime(module.lastSyncTime) : '尚未同步' }}
            </span>
            <div class="card-actions">
              <button
                v-if="!fullSyncJobId && ACTIVE_STATUSES.has(module.status)"
                class="btn-sm btn-sm-cancel"
                @click="handleCancelModule(module.key)"
                title="取消"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>
              </button>
              <button
                v-if="!ACTIVE_STATUSES.has(module.status)"
                class="btn-sm btn-sm-sync"
                :disabled="isAnySyncing"
                @click="handleSyncModule(module.key)"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M20 11a8 8 0 0 0-14.7-4L3 10"/><path d="M3 4v6h6"/><path d="M4 13a8 8 0 0 0 14.7 4L21 14"/><path d="M21 20v-6h-6"/></svg>
                同步
              </button>
            </div>
          </div>

          <!-- 错误信息 -->
          <Transition name="slide-down">
            <div v-if="(module.status === 'FAILED' || module.status === 'PARTIAL_FAILED') && module.errorMessage" class="card-error">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="m12 3 9 17H3L12 3Z"/><path d="M12 9v5M12 17v.01"/></svg>
              {{ module.errorMessage }}
            </div>
          </Transition>
        </div>
      </div>
    </div>

    <!-- 同步日志 -->
    <section class="history-panel">
      <div class="history-header">
        <div>
          <h3>同步历史</h3>
          <span>自动、手动与补偿任务使用同一记录</span>
        </div>
        <button class="btn-ghost" @click="loadHistory(0)" :disabled="historyLoading">刷新</button>
      </div>
      <div class="history-filters">
        <select v-model="historyModule" aria-label="模块筛选">
          <option value="">全部模块</option>
          <option v-for="(_, key) in MODULE_META" :key="key" :value="key">{{ MODULE_META[key].name }}</option>
          <option value="FULL_SYNC">全量同步</option>
        </select>
        <select v-model="historyTriggerType" aria-label="来源筛选">
          <option value="">全部来源</option>
          <option value="MANUAL">手动</option>
          <option value="SCHEDULED">定时</option>
          <option value="LOGIN_CATCHUP">登录补偿</option>
          <option value="STARTUP_CATCHUP">启动补偿</option>
        </select>
        <select v-model="historyStatus" aria-label="状态筛选">
          <option value="">全部状态</option>
          <option value="RUNNING">运行中</option>
          <option value="SUCCESS">成功</option>
          <option value="PARTIAL_FAILED">部分失败</option>
          <option value="FAILED">失败</option>
          <option value="CANCELLED">已取消</option>
        </select>
        <button class="btn-action history-query" @click="loadHistory(0)">查询</button>
      </div>
      <div class="history-table-wrap">
        <table class="history-table">
          <thead>
            <tr><th>任务</th><th>模块</th><th>来源</th><th>状态</th><th>进度</th><th>创建时间</th></tr>
          </thead>
          <tbody>
            <tr v-for="job in historyJobs" :key="job.jobId" @click="showJobDetail(job.jobId)">
              <td>#{{ job.jobId }}</td>
              <td>{{ job.module === 'FULL_SYNC' ? '全量同步' : (MODULE_META[job.module]?.name || job.module) }}</td>
              <td><span class="source-tag">{{ triggerLabel(job.triggerType) }}</span></td>
              <td><span class="history-status" :class="job.status">{{ getStatusText(job.status) }}</span></td>
              <td>{{ job.progress }}%</td>
              <td>{{ formatTime(job.createdAt) }}</td>
            </tr>
            <tr v-if="!historyLoading && historyJobs.length === 0"><td colspan="6" class="history-empty">暂无匹配任务</td></tr>
          </tbody>
        </table>
      </div>
      <div class="history-pagination">
        <button class="btn-ghost" :disabled="historyPage === 0" @click="loadHistory(historyPage - 1)">上一页</button>
        <span>第 {{ historyPage + 1 }} / {{ Math.max(historyTotalPages, 1) }} 页</span>
        <button class="btn-ghost" :disabled="historyPage + 1 >= historyTotalPages" @click="loadHistory(historyPage + 1)">下一页</button>
      </div>
      <div v-if="selectedJob" class="job-detail">
        <div class="job-detail-header">
          <strong>任务 #{{ selectedJob.jobId }}</strong>
          <span>{{ triggerLabel(selectedJob.triggerType) }} · {{ getStatusText(selectedJob.status) }}</span>
          <button class="btn-ghost" @click="selectedJob = null">关闭</button>
        </div>
        <div v-if="selectedJob.steps.length" class="step-list">
          <div v-for="step in selectedJob.steps" :key="step.id" class="step-row">
            <span>{{ step.stepOrder + 1 }}. {{ MODULE_META[step.module]?.name || step.module }}</span>
            <span>{{ getStatusText(step.status) }}</span>
            <span>{{ step.progress }}%</span>
          </div>
        </div>
        <p v-else class="history-empty">该任务没有子步骤</p>
      </div>
    </section>

    <div class="log-panel">
      <div class="log-header">
        <div class="log-title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M5 4h14v16H5zM8 8h8M8 12h8M8 16h5"/></svg>
          <span>同步日志</span>
          <span class="log-count" v-if="logs.length > 0">{{ logs.length }}</span>
        </div>
        <button class="btn-ghost" @click="logs = []" v-if="logs.length > 0">清空</button>
      </div>
      <div class="log-body" ref="logContainer">
        <div v-if="logs.length === 0" class="log-empty">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M8 4h8M9 3h6v3H9zM6 5H4v16h16V5h-2"/><path d="M8 11h8M8 15h6"/></svg>
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
  syncModule, syncAllModules, getRecentJobs, getSyncOverview, getJob, cancelJob,
  getJobHistory, getJobDetail
} from '@/api/dataManagement'
import type {
  SyncJobResponse, SyncJobStepResponse, SyncJobDetailResponse
} from '@/api/dataManagement'
import { publishDataRefresh } from '@/utils/dataRefresh'

interface ModuleDisplay {
  key: string
  name: string
  count: number
  status: string
  progress: number
  lastSyncTime: string
  errorMessage: string
  jobId: number | null
  triggerType: string
  activeFullStep: SyncJobStepResponse | null
}

const MODULE_META: Record<string, { name: string }> = {
  TEAMS: { name: '球队数据' },
  PLAYERS: { name: '球员数据' },
  MATCHES: { name: '比赛记录' },
  TODAY_GAMES: { name: '今日比赛' },
  NEWS: { name: '新闻资讯' },
  DRAFT: { name: '选秀数据' },
  PLAYOFF: { name: '季后赛' },
  HISTORICAL: { name: '历史赛季' },
  TRANSLATION: { name: '翻译重试' },
}

const MODULE_ICON_PATHS: Record<string, string> = {
  TEAMS: 'M4 20V9l8-5 8 5v11M7 20v-6h10v6M9 9h.01M12 9h.01M15 9h.01',
  PLAYERS: 'M20 21a8 8 0 0 0-16 0M12 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
  MATCHES: 'M4 19V5M4 19h16M7 15l3-4 3 2 5-7',
  TODAY_GAMES: 'M12 3v18M3 12h18M5.6 5.6l12.8 12.8M18.4 5.6 5.6 18.4',
  NEWS: 'M5 4h14v16H5zM8 8h8M8 12h8M8 16h5',
  DRAFT: 'M6 4h12v16H6zM9 8h6M9 12h6M9 16h4',
  PLAYOFF: 'M6 3h12v6a6 6 0 0 1-12 0V3ZM12 15v6M8 21h8',
  HISTORICAL: 'M5 5h14v14H5zM8 3v4M16 3v4M5 10h14M9 14h2M13 14h2',
  TRANSLATION: 'M4 5h16v14H4zM8 9h8M8 13h5',
}

const currentSeason = ref('')
const modules = ref<ModuleDisplay[]>([])
const logs = ref<Array<{ time: string; module: string; message: string; type: string }>>([])
const logContainer = ref<HTMLElement | null>(null)
const fullSyncJobId = ref<number | null>(null)
const fullSyncStatus = ref('')
const fullSyncProgress = ref(0)
const historyJobs = ref<SyncJobResponse[]>([])
const historyPage = ref(0)
const historyTotalPages = ref(0)
const historyModule = ref('')
const historyTriggerType = ref('')
const historyStatus = ref('')
const selectedJob = ref<SyncJobDetailResponse | null>(null)
const historyLoading = ref(false)
let pollTimer: number | null = null

// 初始化模块列表
function initModules() {
  modules.value = Object.entries(MODULE_META).map(([key, meta]) => ({
    key, name: meta.name, count: 0,
    status: 'idle', progress: 0, lastSyncTime: '', errorMessage: '', jobId: null,
    triggerType: '', activeFullStep: null
  }))
}

function moduleIconPath(key: string): string {
  return MODULE_ICON_PATHS[key] || MODULE_ICON_PATHS.MATCHES
}

const ACTIVE_STATUSES = new Set(['RUNNING', 'QUEUED', 'CANCELLING'])
const TERMINAL_STATUSES = new Set(['SUCCESS', 'PARTIAL_FAILED', 'FAILED', 'CANCELLED', 'INTERRUPTED'])

const isAnySyncing = computed(() => fullSyncJobId.value != null || modules.value.some(m => ACTIVE_STATUSES.has(m.status)))
const totalCount = computed(() => modules.value.reduce((sum, m) => sum + m.count, 0))
const syncingCount = computed(() => fullSyncJobId.value ? 1 : modules.value.filter(m => ACTIVE_STATUSES.has(m.status)).length)
const successCount = computed(() => modules.value.filter(m => m.status === 'SUCCESS').length)
/** 失败模块：用于顶部告警，让失败结果不必滚动查找 */
const failedModules = computed(() =>
  modules.value.filter(m => m.status === 'FAILED' || m.status === 'PARTIAL_FAILED')
)
/** 最近一次成功提示：仅在无失败时展示，避免与失败信息抢注意力 */
const recentSuccess = computed(() => {
  if (syncingCount.value > 0) return ''
  const done = modules.value.filter(m => m.status === 'SUCCESS')
  if (done.length === 0) return ''
  const latest = done
    .filter(m => m.lastSyncTime)
    .sort((a, b) => b.lastSyncTime.localeCompare(a.lastSyncTime))[0]
  if (!latest) return `${done.length} 个模块已完成同步`
  return `${done.length} 个模块已完成同步 · 最近 ${formatTime(latest.lastSyncTime)}`
})

/** 重试全部失败模块 */
async function retryFailed() {
  const targets = failedModules.value.map(m => m.key)
  for (const key of targets) {
    await handleSyncModule(key)
  }
}
const lastSyncTime = computed(() => {
  const times = modules.value.filter(m => m.lastSyncTime).map(m => m.lastSyncTime).sort().reverse()
  return times.length > 0 ? formatTime(times[0]) : ''
})

const globalProgress = computed(() => {
  if (fullSyncJobId.value) return fullSyncProgress.value
  const activeModules = modules.value.filter(m => ACTIVE_STATUSES.has(m.status))
  if (activeModules.length === 0) return 0
  return Math.round(activeModules.reduce((sum, m) => sum + m.progress, 0) / activeModules.length)
})

const currentSyncingModules = computed(() => {
  if (fullSyncJobId.value) return ['全量同步']
  return modules.value.filter(m => m.status === 'RUNNING').map(m => m.name)
})

// 获取状态文本
function getStatusText(status: string): string {
  const map: Record<string, string> = {
    idle: '待同步',
    QUEUED: '等待中',
    RUNNING: '同步中',
    SUCCESS: '已完成',
    PARTIAL_FAILED: '部分失败',
    FAILED: '失败',
    CANCELLED: '已取消',
    CANCELLING: '取消中',
    INTERRUPTED: '已中断'
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

function safeErrorMessage(message?: string | null): string {
  if (!message) return ''
  if (message.includes('could not execute statement') || message.includes('constraint [')) {
    return '数据写入冲突，请查看服务端任务日志'
  }
  return message.length > 240 ? `${message.slice(0, 240)}…` : message
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
    const [{ data: overview }, { data: jobsPage }] = await Promise.all([
      getSyncOverview(),
      getRecentJobs()
    ])
    currentSeason.value = overview.currentSeason
    for (const item of overview.modules) {
      const mod = modules.value.find(m => m.key === item.key)
      if (mod) {
        mod.count = item.count
        const job = item.activeJob || item.latestJob
        mod.activeFullStep = item.activeFullStep
        mod.lastSyncTime = item.lastSuccessfulAt || job?.finishedAt || job?.startedAt || job?.createdAt || ''
        if (item.activeFullStep) {
          mod.status = item.activeFullStep.status
          mod.progress = item.activeFullStep.progress
          mod.triggerType = 'FULL_SYNC'
          mod.jobId = null
          mod.errorMessage = safeErrorMessage(item.activeFullStep.errorMessage)
        } else if (job) {
          mod.status = job.status
          mod.progress = job.progress
          mod.errorMessage = safeErrorMessage(job.errorMessage)
          mod.jobId = job.jobId
          mod.triggerType = job.triggerType
        }
      }
    }

    const fullSyncJob = jobsPage.content.find(j => j.module === 'FULL_SYNC' && ACTIVE_STATUSES.has(j.status))
    if (fullSyncJob) {
      fullSyncJobId.value = fullSyncJob.jobId
      fullSyncStatus.value = fullSyncJob.status
      fullSyncProgress.value = fullSyncJob.progress
    } else {
      fullSyncJobId.value = null
      fullSyncStatus.value = ''
      fullSyncProgress.value = 0
    }

    if (fullSyncJob || modules.value.some(m => ACTIVE_STATUSES.has(m.status))) startPolling()
  } catch (error: any) {
    console.error('加载数据概览失败:', error)
    addLog('系统', '同步记录加载失败', 'error')
  }
}

// 同步单个模块
async function handleSyncModule(moduleKey: string) {
  const mod = modules.value.find(m => m.key === moduleKey)
  if (!mod) return

  try {
    mod.status = 'QUEUED'
    mod.progress = 0
    mod.errorMessage = ''
    addLog(mod.name, '开始同步...', 'info')

    const { data } = await syncModule(moduleKey)
    mod.jobId = data.jobId
    mod.status = data.status
    mod.triggerType = data.triggerType
    addLog(mod.name, data.reused
      ? `已关联活动任务: jobId=${data.jobId}`
      : `任务已提交: jobId=${data.jobId}`, 'info')
    startPolling()
  } catch (error: any) {
    const status = error?.response?.status
    const message =
      error.response?.data?.message ||
      error.response?.data?.errorMessage ||
      error.message ||
      '同步失败'
    if (status === 409) {
      mod.status = 'RUNNING'
      mod.errorMessage = message
      addLog(mod.name, message, 'info')
      loadOverview() // 刷新显示正在运行的任务
    } else if (status === 503) {
      mod.status = 'idle'
      mod.errorMessage = message
      addLog(mod.name, `服务暂不可用: ${message}`, 'error')
    } else {
      mod.status = 'FAILED'
      mod.errorMessage = message
      addLog(mod.name, `同步失败: ${message}`, 'error')
    }
  }
}

// 全量同步
async function handleSyncAll() {
  try {
    addLog('系统', '开始全量同步...', 'info')
    const { data } = await syncAllModules()
    fullSyncJobId.value = data.jobId
    fullSyncStatus.value = data.status
    fullSyncProgress.value = data.progress
    addLog('系统', data.reused
      ? `已关联活动全量任务: jobId=${data.jobId}`
      : `全量同步任务已提交: jobId=${data.jobId}`, 'info')
    startPolling()
  } catch (error: any) {
    const status = error?.response?.status
    const message =
      error.response?.data?.message ||
      error.response?.data?.errorMessage ||
      error.message ||
      '同步失败'
    if (status === 409) {
      addLog('系统', message, 'info')
      loadOverview()
    } else if (status === 503) {
      addLog('系统', `服务暂不可用: ${message}`, 'error')
    } else {
      addLog('系统', `全量同步失败: ${message}`, 'error')
    }
  }
}

// 取消全部
async function handleCancelAll() {
  // 收集去重的 jobId
  const jobIds = new Set<number>()
  if (fullSyncJobId.value) jobIds.add(fullSyncJobId.value)
  for (const m of modules.value) {
    if (m.jobId && ACTIVE_STATUSES.has(m.status)) {
      jobIds.add(m.jobId)
    }
  }
  if (jobIds.size === 0) return

  addLog('系统', `正在取消 ${jobIds.size} 个任务...`, 'info')

  const results = await Promise.allSettled(
    Array.from(jobIds).map(id => cancelJob(id))
  )

  let accepted = 0
  let failed = 0
  for (const r of results) {
    if (r.status === 'fulfilled') {
      if (r.value?.data?.accepted) accepted++
    } else {
      failed++
    }
  }

  if (fullSyncJobId.value) {
    fullSyncStatus.value = 'CANCELLING'
  } else {
    modules.value.forEach(m => {
      if (ACTIVE_STATUSES.has(m.status)) m.status = 'CANCELLING'
    })
  }

  if (accepted > 0) {
    addLog('系统', `${accepted} 个任务已提交取消`, 'info')
    // 继续轮询，等后端返回 CANCELLED
    startPolling()
  }
  if (failed > 0) {
    addLog('系统', `${failed} 个任务取消失败`, 'error')
  }
}

// 取消单个模块
async function handleCancelModule(moduleKey: string) {
  const mod = modules.value.find(m => m.key === moduleKey)
  if (!mod || !mod.jobId) return

  try {
    const { data } = await cancelJob(mod.jobId)
    if (data.accepted) {
      mod.status = 'CANCELLING'
      addLog(mod.name, '取消中...', 'info')
      // 继续轮询，等后端返回 CANCELLED
      startPolling()
    } else {
      addLog(mod.name, '任务已结束，无需取消', 'info')
      await loadOverview()
    }
  } catch (error: any) {
    const status = error?.response?.status
    if (status === 409) {
      // 已处于终态，刷新状态
      addLog(mod.name, '任务已结束，无法取消', 'info')
      loadOverview()
    } else {
      addLog(mod.name, `取消失败: ${error.message}`, 'error')
    }
  }
}

// 轮询进度（递归 setTimeout 避免重入）
let pollingInFlight = false
function startPolling() {
  if (pollTimer) return
  schedulePollTick()
}

function schedulePollTick() {
  pollTimer = window.setTimeout(async () => {
    pollTimer = null
    if (pollingInFlight) return
    pollingInFlight = true
    try {
      await pollTick()
    } catch (error) {
      console.error('轮询失败:', error)
    } finally {
      pollingInFlight = false
    }
    // 如果还有活跃任务，继续轮询
    if (fullSyncJobId.value || modules.value.some(m => ACTIVE_STATUSES.has(m.status))) {
      schedulePollTick()
    }
  }, 3000)
}

async function pollTick() {
  // FULL_SYNC：精确轮询 jobId
  if (fullSyncJobId.value) {
    try {
      const { data: fullJob } = await getJob(fullSyncJobId.value)
      if (fullJob) {
        fullSyncStatus.value = fullJob.status
        fullSyncProgress.value = fullJob.progress
        for (const step of fullJob.steps || []) {
          const mod = modules.value.find(module => module.key === step.module)
          if (!mod) continue
          mod.activeFullStep = step
          mod.status = step.status
          mod.progress = step.progress
          mod.triggerType = 'FULL_SYNC'
          mod.errorMessage = safeErrorMessage(step.errorMessage)
        }
        if (TERMINAL_STATUSES.has(fullJob.status)) {
          fullSyncJobId.value = null
          const label = fullJob.status === 'SUCCESS' ? '全量同步完成' : `全量同步${fullJob.status}`
          const errorMessage = safeErrorMessage(fullJob.errorMessage)
          addLog('系统', label + (errorMessage ? ': ' + errorMessage : ''), fullJob.status === 'SUCCESS' ? 'success' : 'error')
          await loadOverview()
          await loadHistory(historyPage.value)
        }
      }
    } catch { /* job not found */ }
    return
  }

  // 单模块：精确轮询每个活跃 jobId
  let allDone = true
  for (const mod of modules.value) {
    if (!ACTIVE_STATUSES.has(mod.status) || !mod.jobId) continue
    try {
      const { data: job } = await getJob(mod.jobId)
      if (!job) continue
      const prevStatus = mod.status
      mod.status = job.status
      mod.progress = job.progress
      mod.lastSyncTime = job.finishedAt || job.startedAt || job.createdAt
      mod.errorMessage = safeErrorMessage(job.errorMessage)

      if (prevStatus !== job.status) {
        if (job.status === 'SUCCESS') {
          const noNewNews = mod.key === 'NEWS' && job.insertedCount === 0
          addLog(mod.name, noNewNews
            ? '同步完成 · 上游暂无新资讯，现有数据已是最新'
            : `同步完成 · 新增${job.insertedCount} · 更新${job.updatedCount}`, 'success')
          if (noNewNews) {
            ElMessage.info('新闻源暂无新资讯，资讯列表已重新校验')
          }
        } else if (job.status === 'FAILED' || job.status === 'PARTIAL_FAILED') {
          addLog(mod.name, `同步失败: ${safeErrorMessage(job.errorMessage)}`, 'error')
        } else if (job.status === 'CANCELLED') {
          addLog(mod.name, '已取消', 'info')
        }
        if (job.status === 'SUCCESS' || job.status === 'PARTIAL_FAILED') {
          publishDataRefresh({
            module: mod.key,
            jobId: job.jobId,
            status: job.status,
          })
        }
      }
      if (ACTIVE_STATUSES.has(job.status)) allDone = false
    } catch {
      // 查询失败，保持活跃状态继续轮询
      allDone = false
    }
  }

  if (allDone) {
    addLog('系统', '所有同步任务已完成', 'success')
    await loadOverview()
    await loadHistory(historyPage.value)
  }
}

// 停止轮询
function stopPolling() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

function triggerLabel(triggerType: string): string {
  const labels: Record<string, string> = {
    MANUAL: '手动',
    SCHEDULED: '定时',
    LOGIN_CATCHUP: '登录补偿',
    STARTUP_CATCHUP: '启动补偿',
    FULL_SYNC: '全量步骤'
  }
  return labels[triggerType] || triggerType || '未知来源'
}

async function loadHistory(page = historyPage.value) {
  historyLoading.value = true
  try {
    const { data } = await getJobHistory({
      module: historyModule.value || undefined,
      triggerType: historyTriggerType.value || undefined,
      status: historyStatus.value || undefined,
      page,
      size: 10
    })
    historyJobs.value = data.content
    historyPage.value = data.page
    historyTotalPages.value = data.totalPages
  } finally {
    historyLoading.value = false
  }
}

async function showJobDetail(jobId: number) {
  const { data } = await getJobDetail(jobId)
  selectedJob.value = data
}

onMounted(() => {
  initModules()
  loadOverview()
  loadHistory(0)
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
  color: var(--accent-contrast, #04202A);
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
/* ===== 失败告警 / 成功提示 ===== */
.failure-alert,
.success-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
  padding: 13px 16px;
  border-radius: 12px;
  position: relative;
  z-index: 1;
}
.failure-alert {
  background: var(--danger-glow);
  border: 1px solid rgba(239, 68, 68, 0.35);
  color: var(--danger);
}
.success-alert {
  background: var(--green-dim, rgba(34, 197, 94, 0.12));
  border: 1px solid rgba(34, 197, 94, 0.3);
  color: var(--success);
  justify-content: flex-start;
  font-size: 13px;
  font-weight: 600;
}
.failure-alert-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.failure-alert-main svg {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}
.failure-alert-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.failure-alert-text strong {
  font-size: 14px;
  font-weight: 700;
}
.failure-alert-text span {
  font-size: 12px;
  opacity: 0.85;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.btn-sm-retry {
  flex-shrink: 0;
  white-space: nowrap;
  padding: 7px 14px;
  border-radius: 8px;
  border: 1px solid rgba(239, 68, 68, 0.45);
  background: transparent;
  color: var(--danger);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s ease;
}
.btn-sm-retry:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.14);
}
.btn-sm-retry:disabled { opacity: 0.5; cursor: not-allowed; }

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
.card-progress-label { color: var(--text-muted); font-size: 11px; white-space: nowrap; }
.card-result {
  margin-bottom: 14px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}
.card-result.SUCCESS { color: var(--success); }
.card-result.FAILED,
.card-result.PARTIAL_FAILED,
.card-result.INTERRUPTED { color: var(--danger); }

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

.card-progress .progress-bar.RUNNING,
.card-progress .progress-bar.QUEUED,
.card-progress .progress-bar.CANCELLING {
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
.source-tag {
  display: inline-flex;
  margin-left: 8px;
  padding: 2px 6px;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  color: var(--text-secondary);
  background: var(--bg-hover);
  font-size: 10px;
  line-height: 1.4;
  vertical-align: middle;
}

.step-stage {
  margin: -4px 0 12px;
  color: var(--text-muted);
  font-size: 12px;
}

.history-panel {
  position: relative;
  z-index: 1;
  margin-bottom: 24px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: var(--bg-card);
  overflow: hidden;
}

.history-header,
.job-detail-header,
.history-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.history-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
}

.history-header h3 {
  margin: 0 0 3px;
  color: var(--text-primary);
  font-size: 15px;
}

.history-header span,
.history-pagination,
.job-detail-header span {
  color: var(--text-muted);
  font-size: 12px;
}

.history-filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(130px, 1fr)) auto;
  gap: 10px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--border-light);
}

.history-filters select {
  min-width: 0;
  height: 36px;
  padding: 0 32px 0 10px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  color: var(--text-primary);
  background-color: var(--bg-hover);
  color-scheme: dark;
  outline: none;
}

.history-filters select:hover,
.history-filters select:focus {
  border-color: var(--accent);
  background-color: var(--bg-card);
}

.history-filters option {
  color: var(--text-primary);
  background: var(--bg-card);
}

.history-query {
  height: 36px;
  padding: 0 16px;
  color: var(--accent-contrast, #04202A);
  background: var(--accent);
}

.history-table-wrap {
  overflow-x: auto;
}

.history-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.history-table th,
.history-table td {
  padding: 11px 14px;
  border-bottom: 1px solid var(--border-light);
  color: var(--text-secondary);
  font-size: 12px;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-table th {
  color: var(--text-muted);
  background: var(--bg-hover);
  font-weight: 600;
}

.history-table tbody tr {
  cursor: pointer;
  transition: background 0.15s ease;
}

.history-table tbody tr:hover {
  background: var(--bg-hover);
}

.history-status.SUCCESS { color: var(--success); }
.history-status.FAILED,
.history-status.PARTIAL_FAILED { color: var(--danger); }
.history-status.RUNNING,
.history-status.QUEUED { color: var(--accent); }

.history-pagination {
  justify-content: flex-end;
  padding: 12px 20px;
}

.job-detail {
  padding: 16px 20px;
  border-top: 1px solid var(--border-light);
  background: var(--bg-hover);
}

.job-detail-header strong {
  color: var(--text-primary);
  font-size: 13px;
}

.job-detail-header span {
  margin-right: auto;
}

.step-list {
  display: grid;
  gap: 6px;
  margin-top: 12px;
}

.step-row {
  display: grid;
  grid-template-columns: 1fr 100px 48px;
  gap: 12px;
  padding: 8px 10px;
  border-left: 2px solid var(--accent);
  color: var(--text-secondary);
  background: var(--bg-card);
  font-size: 12px;
}

.history-empty {
  padding: 20px !important;
  color: var(--text-dim) !important;
  text-align: center !important;
}

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

  .history-filters {
    grid-template-columns: 1fr 1fr;
  }

  .history-table {
    min-width: 720px;
  }
}

/* Operations console visual system */
.data-management-page {
  max-width: 1480px;
  min-height: calc(100vh - 64px);
  margin: 0 auto;
  padding: 30px 36px 56px;
  background: var(--bg-page);
  color: var(--text-primary);
}

.bg-decoration { display: none; }

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 0 0 24px;
  border-bottom: 1px solid var(--border-light);
}
.header-left { display: flex; align-items: center; gap: 14px; }
.header-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(34, 211, 238, .35);
  border-radius: 8px;
  color: var(--accent);
  background: rgba(34, 211, 238, .1);
}
.header-icon svg { width: 22px; height: 22px; }
.page-header h2 { margin: 0; font-size: 22px; line-height: 1.2; letter-spacing: 0; }
.subtitle { margin: 6px 0 0; color: var(--text-dim); font-size: 12px; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.btn-primary, .btn-action, .btn-ghost, .btn-sm { transition: background .16s ease, border-color .16s ease, color .16s ease, transform .16s ease; }
.btn-primary {
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  border: 1px solid var(--accent);
  border-radius: 6px;
  background: var(--accent);
  color: var(--accent-contrast, #04202A);
  font-size: 13px;
  font-weight: 650;
}
.btn-primary:hover:not(:disabled) { background: var(--accent-hover); border-color: var(--accent-hover); transform: translateY(-1px); }
.btn-primary:disabled { opacity: .55; cursor: not-allowed; }
.btn-icon { width: 16px; height: 16px; }
.btn-action, .btn-ghost {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 12px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
}
.btn-action svg, .btn-ghost svg, .btn-sm svg { width: 14px; height: 14px; flex: 0 0 auto; }
.btn-action:hover, .btn-ghost:hover { border-color: var(--accent); color: var(--accent); background: var(--accent-lighter); }
.btn-cancel:hover { border-color: var(--danger); color: var(--danger); background: var(--danger-glow); }

.global-progress {
  margin: 18px 0 0;
  padding: 14px 16px;
  border: 1px solid rgba(34, 211, 238, .32);
  border-radius: 8px;
  background: var(--bg-card);
}
.progress-info, .progress-status { color: var(--text-secondary); font-size: 12px; }
.progress-info { display: flex; justify-content: space-between; margin-bottom: 8px; }
.progress-value { color: var(--accent); font-weight: 700; }
.progress-track { height: 5px; border-radius: 999px; background: var(--bg-hover); overflow: hidden; }
.progress-bar { height: 100%; border-radius: inherit; background: var(--accent); transition: width .35s ease; }
.progress-status { display: flex; align-items: center; gap: 7px; margin-top: 9px; color: var(--text-dim); }
.pulse-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 0 4px rgba(34, 211, 238,.12); }

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 22px 0;
}
.stat-card {
  min-height: 84px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: var(--bg-card);
}
.stat-card.stat-active { border-color: rgba(34, 211, 238, .52); }
.stat-card.stat-success { border-color: rgba(34, 197, 94, .46); }
.stat-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  background: var(--bg-hover);
  color: var(--text-secondary);
}
.stat-icon svg { width: 19px; height: 19px; }
.stat-success .stat-icon { color: #4ade80; background: rgba(34,197,94,.1); }
.stat-info { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.stat-value { color: var(--text-primary); font: 700 19px/1 var(--font-heading); font-variant-numeric: tabular-nums; }
.stat-label { color: var(--text-dim); font-size: 11px; }
.stat-time { font-size: 14px; }

.modules-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.module-card {
  position: relative;
  min-width: 0;
  overflow: hidden;
  opacity: 1;
  transform: none;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: var(--bg-card);
  box-shadow: none;
  animation: none;
}
.module-card:hover { border-color: var(--border-medium); background: var(--bg-hover); }
.module-card.status-RUNNING { border-color: rgba(34, 211, 238,.6); }
.module-card.status-SUCCESS { border-color: var(--border-light); }
.card-indicator { height: 2px; background: var(--border-light); }
.card-indicator.RUNNING { background: var(--accent); }
.card-indicator.SUCCESS { background: #22c55e; }
.card-indicator.FAILED, .card-indicator.PARTIAL_FAILED { background: var(--danger); }
.card-body { padding: 15px 16px 14px; }
.card-header { display: flex; align-items: flex-start; gap: 11px; min-height: 48px; }
.card-icon-wrap {
  position: relative;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 7px;
  color: var(--text-secondary);
  background: var(--bg-hover);
}
.card-icon { width: 18px; height: 18px; }
.card-meta { min-width: 0; flex: 1; }
.card-meta h3 { margin: 1px 0 4px; color: var(--text-primary); font-size: 14px; font-weight: 650; }
.card-count { color: var(--text-dim); font-size: 11px; }
.source-tag { display: inline-flex; margin-left: 5px; padding: 2px 5px; border: 1px solid var(--border-light); border-radius: 4px; color: var(--text-dim); font-size: 10px; }
.card-badge { padding-top: 3px; color: var(--text-dim); font-size: 11px; white-space: nowrap; }
.card-badge.RUNNING, .card-badge.QUEUED, .card-badge.CANCELLING { color: var(--accent); }
.card-badge.SUCCESS { color: #4ade80; }
.card-badge.FAILED, .card-badge.PARTIAL_FAILED { color: var(--danger); }
.card-progress { display: flex; align-items: center; gap: 9px; margin-top: 14px; }
.card-progress .progress-track { flex: 1; height: 4px; }
.card-progress .progress-bar { background: var(--text-muted); }
.card-progress .progress-bar.RUNNING { background: var(--accent); }
.card-progress .progress-bar.SUCCESS { background: var(--text-muted); }
.progress-pct { min-width: 34px; color: var(--text-secondary); font-size: 11px; font-variant-numeric: tabular-nums; text-align: right; }
.step-stage { margin-top: 9px; color: var(--accent); font-size: 11px; }
.card-footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 15px; padding-top: 12px; border-top: 1px solid var(--border-light); }
.card-time { color: var(--text-dim); font-size: 10px; }
.card-actions { display: flex; gap: 6px; }
.btn-sm { min-height: 30px; display: inline-flex; align-items: center; justify-content: center; gap: 5px; padding: 0 10px; border: 1px solid var(--border-light); border-radius: 6px; background: var(--bg-hover); color: var(--text-secondary); font-size: 11px; }
.btn-sm:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
.btn-sm-cancel { width: 30px; padding: 0; color: var(--danger); }
.btn-sm-sync:disabled { opacity: .45; cursor: not-allowed; }
.card-error { display: flex; align-items: flex-start; gap: 6px; margin: 12px -1px -1px; padding: 10px; border: 1px solid rgba(239,68,68,.24); border-radius: 6px; background: rgba(239,68,68,.08); color: #fca5a5; font-size: 11px; line-height: 1.5; }
.card-error svg { width: 15px; height: 15px; flex: 0 0 auto; color: var(--danger); }

.history-panel, .log-panel { margin-top: 22px; border: 1px solid var(--border-light); border-radius: 8px; background: var(--bg-card); overflow: hidden; }
.history-header, .log-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 17px 18px; border-bottom: 1px solid var(--border-light); }
.history-header h3 { margin: 0 0 4px; font-size: 15px; }
.history-header span { color: var(--text-dim); font-size: 11px; }
.history-filters { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)) auto; gap: 8px; padding: 12px 18px; border-bottom: 1px solid var(--border-light); }
.history-filters select { min-width: 0; height: 34px; padding: 0 9px; border: 1px solid var(--border-light); border-radius: 6px; background: var(--bg-hover); color: var(--text-secondary); font-size: 12px; outline: none; }
.history-filters select:focus { border-color: var(--accent); }
.history-query { height: 34px; }
.history-table-wrap { overflow-x: auto; }
.history-table { width: 100%; min-width: 680px; border-collapse: collapse; }
.history-table th { padding: 10px 18px; background: var(--bg-hover); color: var(--text-dim); font-size: 10px; font-weight: 600; text-align: left; white-space: nowrap; }
.history-table td { padding: 12px 18px; border-top: 1px solid var(--border-light); color: var(--text-secondary); font-size: 11px; white-space: nowrap; }
.history-table tbody tr { cursor: pointer; transition: background .16s ease; }
.history-table tbody tr:hover { background: var(--bg-hover); }
.history-status { font-weight: 650; }
.history-status.SUCCESS { color: #4ade80; }
.history-status.RUNNING, .history-status.QUEUED { color: var(--accent); }
.history-status.FAILED, .history-status.PARTIAL_FAILED { color: var(--danger); }
.history-pagination { display: flex; align-items: center; justify-content: flex-end; gap: 12px; padding: 12px 18px; border-top: 1px solid var(--border-light); color: var(--text-dim); font-size: 11px; }
.job-detail { border-top: 1px solid var(--border-light); background: var(--bg-hover); }
.job-detail-header { display: flex; align-items: center; gap: 12px; }
.job-detail-header strong { color: var(--text-primary); }
.step-row { border-left-color: var(--accent); background: var(--bg-card); }

.log-title { display: flex; align-items: center; gap: 8px; color: var(--text-primary); font-size: 14px; font-weight: 650; }
.log-title > svg { width: 17px; height: 17px; color: var(--text-secondary); }
.log-count { min-width: 18px; padding: 2px 5px; border-radius: 4px; background: var(--bg-hover); color: var(--text-dim); font-size: 10px; text-align: center; }
.log-body { max-height: 260px; padding: 8px 18px; overflow-y: auto; }
.log-entry { display: grid; grid-template-columns: 66px 100px 1fr; gap: 10px; align-items: baseline; padding: 8px 0; border-bottom: 1px solid var(--border-light); font-size: 11px; }
.log-entry:last-child { border-bottom: 0; }
.log-time { color: var(--text-dim); font-variant-numeric: tabular-nums; }
.log-badge { color: var(--text-secondary); font-weight: 650; }
.log-msg { min-width: 0; color: var(--text-dim); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.log-entry.success .log-msg { color: #86efac; }
.log-entry.error .log-msg { color: #fca5a5; }
.log-empty { display: flex; align-items: center; justify-content: center; min-height: 110px; color: var(--text-dim); font-size: 12px; }
.log-empty svg { width: 17px; height: 17px; margin-right: 8px; }

@media (max-width: 960px) {
  .data-management-page { padding: 24px 20px 44px; }
  .modules-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .data-management-page { padding: 18px 14px 36px; }
  .page-header { align-items: flex-start; flex-direction: column; gap: 16px; }
  .header-actions { width: 100%; }
  .header-actions > * { flex: 1; }
  .stats-row { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin: 16px 0; }
  .stat-card { min-height: 72px; padding: 12px; }
  .stat-value { font-size: 17px; }
  .modules-grid { grid-template-columns: 1fr; gap: 10px; }
  .history-filters { grid-template-columns: 1fr 1fr; padding: 12px; }
  .history-query { grid-column: span 2; }
  .history-header, .log-header { padding: 14px 12px; }
  .log-body { padding: 8px 12px; }
  .log-entry { grid-template-columns: 56px 78px 1fr; gap: 6px; }
}
</style>
