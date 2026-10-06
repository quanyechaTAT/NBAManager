import { computed, onMounted, onUnmounted, ref } from 'vue'
import { cancelJob, getJobDetail, getJobHistory, getSyncOverview, syncAllModules, syncModule } from '@/api/dataManagement'
import type { SyncJobResponse, SyncJobDetailResponse, SyncOverviewResponse } from '@/api/dataManagement'
import { publishDataRefresh } from '@/utils/dataRefresh'

export const MODULE_META: Record<string, { name: string }> = {
  TEAMS: { name: '球队数据' }, PLAYERS: { name: '球员数据' }, MATCHES: { name: '比赛记录' },
  TODAY_GAMES: { name: '今日比赛' }, NEWS: { name: '新闻资讯' }, DRAFT: { name: '选秀数据' },
  PLAYOFF: { name: '季后赛' }, HISTORICAL: { name: '历史赛季' }, TRANSLATION: { name: '翻译重试' },
}
export const ACTIVE_STATUSES = new Set(['QUEUED', 'RUNNING', 'CANCELLING'])
export const TERMINAL_STATUSES = new Set(['SUCCESS', 'PARTIAL_FAILED', 'FAILED', 'CANCELLED', 'INTERRUPTED'])
const FAILED_STATUSES = new Set(['FAILED', 'PARTIAL_FAILED', 'INTERRUPTED'])
export function getStatusText(status: string) {
  return ({ idle: '待同步', QUEUED: '等待中', RUNNING: '同步中', CANCELLING: '取消中',
    SUCCESS: '已完成', PARTIAL_FAILED: '部分失败', FAILED: '失败', CANCELLED: '已取消',
    INTERRUPTED: '已中断' } as Record<string, string>)[status] || status
}
export function triggerLabel(trigger: string) {
  return ({ MANUAL: '手动', SCHEDULED: '定时', LOGIN_CATCHUP: '登录补偿',
    STARTUP_CATCHUP: '启动补偿', FULL_SYNC: '全量步骤' } as Record<string, string>)[trigger] || trigger || '未知来源'
}
export function formatTime(time: string) {
  if (!time) return ''
  const date = new Date(time)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  })
}
export function safeErrorMessage(message?: string | null) {
  if (!message) return ''
  if (message.includes('could not execute statement') || message.includes('constraint [')) return '数据写入冲突，请查看服务端任务日志'
  return message.length > 240 ? `${message.slice(0, 240)}…` : message
}
function errorMessage(error: unknown) {
  const e = error as { response?: { data?: { message?: string; errorMessage?: string } }; message?: string }
  return safeErrorMessage(e.response?.data?.message || e.response?.data?.errorMessage || e.message || '请求失败')
}

/** Server snapshots own task state; pending HTTP commands are tracked separately from job status. */
export function useSyncCenter() {
  const overview = ref<SyncOverviewResponse | null>(null)
  const initialLoading = ref(true)
  const refreshError = ref('')
  const pendingModules = ref(new Set<string>())
  const submittingAll = ref(false)
  const retrying = ref(false)
  const cancellingIds = ref(new Set<number>())
  const acceptedJobs = ref(new Map<number, SyncJobResponse>())
  const logs = ref<Array<{ time: string; module: string; message: string; type: string }>>([])
  const historyJobs = ref<SyncJobResponse[]>([])
  const historyPage = ref(0)
  const historyTotalPages = ref(0)
  const historyModule = ref('')
  const historyTriggerType = ref('')
  const historyStatus = ref('')
  const historyLoading = ref(false)
  const historyError = ref('')
  const selectedJob = ref<SyncJobDetailResponse | null>(null)
  const detailLoading = ref(false)
  const detailError = ref('')
  let disposed = false
  let timer: ReturnType<typeof setTimeout> | undefined
  let refreshInFlight: Promise<void> | undefined
  let refreshAgain = false
  let commandVersion = 0
  let historySequence = 0
  let detailSequence = 0
  const reads = new Set<AbortController>()
  let previousJobs: Map<number, SyncJobResponse> | undefined

  const currentSeason = computed(() => overview.value?.currentSeason || '加载中')
  const fullJob = computed(() => [...acceptedJobs.value.values()].find(j => j.module === 'FULL_SYNC' && ACTIVE_STATUSES.has(j.status))
    || overview.value?.activeFullJob || null)
  const fullSyncJobId = computed(() => fullJob.value?.jobId ?? null)
  const modules = computed(() => Object.entries(MODULE_META).map(([key, meta]) => {
    const item = overview.value?.modules.find(m => m.key === key)
    const accepted = [...acceptedJobs.value.values()].find(j => j.module === key)
    const job = accepted || item?.activeJob || item?.latestJob
    const step = fullJob.value ? item?.activeFullStep : (!accepted && !item?.activeJob ? item?.latestFullStep : null)
    const fullPending = !!fullJob.value && !step
    return {
      key, name: meta.name, count: item?.count ?? 0,
      status: fullPending ? 'QUEUED' : step?.status || job?.status || 'idle',
      progress: Math.min(100, Math.max(0, step?.progress ?? (fullPending ? 0 : job?.progress ?? 0))),
      lastSyncTime: item?.lastSuccessfulAt || '',
      errorMessage: safeErrorMessage(step?.errorMessage || (!fullPending ? job?.errorMessage : '')),
      jobId: step || fullPending ? fullSyncJobId.value || overview.value?.latestFullJob?.jobId || null : job?.jobId ?? null,
      triggerType: step || fullPending ? 'FULL_SYNC' : job?.triggerType || '',
      activeFullStep: fullJob.value ? step ?? null : null,
    }
  }))
  const activeIds = computed(() => new Set([
    ...(fullJob.value ? [fullJob.value.jobId] : []),
    ...modules.value.filter(m => ACTIVE_STATUSES.has(m.status) && m.jobId != null).map(m => m.jobId!),
  ]))
  const isAnySyncing = computed(() => activeIds.value.size > 0)
  const isSubmitting = computed(() => submittingAll.value || pendingModules.value.size > 0 || retrying.value)
  const isCancelling = computed(() => cancellingIds.value.size > 0)
  const canSyncAll = computed(() => !initialLoading.value && !!overview.value && !refreshError.value && !isAnySyncing.value && !isSubmitting.value)
  function canSyncModule(key: string) {
    return !initialLoading.value && !!overview.value && !refreshError.value && !fullJob.value && !submittingAll.value
      && !pendingModules.value.has(key) && !ACTIVE_STATUSES.has(modules.value.find(m => m.key === key)?.status || '')
  }
  const totalCount = computed(() => modules.value.reduce((sum, m) => sum + m.count, 0))
  const syncingCount = computed(() => activeIds.value.size)
  const successCount = computed(() => modules.value.filter(m => m.status === 'SUCCESS').length)
  const failedModules = computed(() => modules.value.filter(m => FAILED_STATUSES.has(m.status)))
  const lastSyncTime = computed(() => formatTime(modules.value.map(m => m.lastSyncTime).sort().at(-1) || ''))
  const recentSuccess = computed(() => successCount.value ? `${successCount.value} 个模块已完成同步${lastSyncTime.value ? ` · 最近 ${lastSyncTime.value}` : ''}` : '')
  const globalProgress = computed(() => {
    if (fullJob.value) return fullJob.value.progress
    const active = modules.value.filter(m => ACTIVE_STATUSES.has(m.status))
    return active.length ? Math.round(active.reduce((sum, m) => sum + m.progress, 0) / active.length) : 0
  })
  const currentSyncingModules = computed(() => fullJob.value
    ? [`全量同步 · ${getStatusText(fullJob.value.status)}${fullJob.value.stage ? ` · ${MODULE_META[fullJob.value.stage]?.name || fullJob.value.stage}` : ''}`]
    : modules.value.filter(m => ACTIVE_STATUSES.has(m.status)).map(m => `${m.name} · ${getStatusText(m.status)}`))

  function addLog(module: string, message: string, type = 'info') {
    if (disposed) return
    logs.value.push({ time: new Date().toLocaleTimeString('zh-CN'), module, message, type })
    if (logs.value.length > 100) logs.value.shift()
  }
  async function read<T>(fn: (options: { signal: AbortSignal; timeout: number }) => Promise<{ data: T }>) {
    const controller = new AbortController()
    reads.add(controller)
    try { return (await fn({ signal: controller.signal, timeout: 15000 })).data }
    finally { reads.delete(controller) }
  }
  function schedule() {
    clearTimeout(timer)
    if (!disposed) timer = setTimeout(() => { void loadOverview() }, isAnySyncing.value || refreshError.value ? 3000 : 15000)
  }
  function observeJobs(snapshot: SyncOverviewResponse) {
    const jobs = new Map<number, SyncJobResponse>()
    for (const item of snapshot.modules) for (const job of [item.activeJob, item.latestJob]) if (job) jobs.set(job.jobId, job)
    for (const job of [snapshot.activeFullJob, snapshot.latestFullJob]) if (job) jobs.set(job.jobId, job)
    let changed = false
    if (previousJobs) for (const job of jobs.values()) {
      const old = previousJobs.get(job.jobId)
      if (old?.status === job.status) continue
      changed = true
      if (ACTIVE_STATUSES.has(job.status)) continue
      addLog(MODULE_META[job.module]?.name || '全量同步', `任务 #${job.jobId} ${getStatusText(job.status)}${job.errorMessage ? `：${safeErrorMessage(job.errorMessage)}` : ''}`, FAILED_STATUSES.has(job.status) ? 'error' : 'info')
      if (job.module === 'FULL_SYNC') {
        for (const step of snapshot.latestFullJob?.jobId === job.jobId ? snapshot.latestFullJob.steps : []) {
          if (step.status === 'SUCCESS' || step.status === 'PARTIAL_FAILED') publishDataRefresh({ module: step.module, jobId: job.jobId, status: step.status })
        }
      } else if (job.status === 'SUCCESS' || job.status === 'PARTIAL_FAILED') publishDataRefresh({ module: job.module, jobId: job.jobId, status: job.status })
    }
    previousJobs = jobs
    if (changed) void loadHistory()
  }
  function loadOverview(): Promise<void> {
    if (disposed) return Promise.resolve()
    if (refreshInFlight) { refreshAgain = true; return refreshInFlight }
    refreshInFlight = (async () => {
      do {
        refreshAgain = false
        const version = commandVersion
        try {
          const snapshot = await read(getSyncOverview)
          if (disposed) return
          if (version !== commandVersion) { refreshAgain = true; continue }
          observeJobs(snapshot)
          overview.value = snapshot
          acceptedJobs.value.clear()
          refreshError.value = ''
          if (selectedJob.value && !detailLoading.value && ACTIVE_STATUSES.has(selectedJob.value.status)) void showJobDetail(selectedJob.value.jobId)
        } catch (error) {
          if (!disposed) refreshError.value = `状态刷新失败：${errorMessage(error)}。正在重试，当前显示最后一次成功状态。`
        } finally { if (!disposed) initialLoading.value = false }
      } while (refreshAgain && !disposed)
    })().finally(() => { refreshInFlight = undefined; schedule() })
    return refreshInFlight
  }
  async function submit(key?: string) {
    if (disposed || (key ? !canSyncModule(key) : !canSyncAll.value)) return
    if (key) pendingModules.value.add(key); else submittingAll.value = true
    const name = key ? MODULE_META[key]?.name || key : '全量同步'
    try {
      const { data } = await (key ? syncModule(key) : syncAllModules())
      if (disposed) return
      commandVersion++
      acceptedJobs.value.set(data.jobId, data)
      addLog(name, `${data.reused ? '已关联活动任务' : '任务已提交'} #${data.jobId}`)
      await loadOverview()
      void loadHistory()
    } catch (error) {
      addLog(name, `提交失败：${errorMessage(error)}`, 'error')
      await loadOverview()
    } finally { if (key) pendingModules.value.delete(key); else submittingAll.value = false }
  }
  async function retryFailed() {
    if (retrying.value || isAnySyncing.value || submittingAll.value) return
    const keys = failedModules.value.map(m => m.key)
    retrying.value = true
    try { for (const key of keys) { if (disposed) break; await submit(key) } }
    finally { retrying.value = false }
  }
  async function cancel(ids: number[]) {
    const targets = [...new Set(ids)].filter(id => !cancellingIds.value.has(id))
    targets.forEach(id => cancellingIds.value.add(id))
    await Promise.all(targets.map(async id => {
      try {
        const { data } = await cancelJob(id)
        if (disposed) return
        commandVersion++
        acceptedJobs.value.set(data.job.jobId, data.job)
        addLog('系统', data.accepted ? `任务 #${id} 已请求取消，等待后端确认结束` : `任务 #${id} ${getStatusText(data.job.status)}，无需取消`)
      } catch (error) { addLog('系统', `取消任务 #${id} 失败：${errorMessage(error)}`, 'error') }
      finally { cancellingIds.value.delete(id) }
    }))
    await loadOverview()
  }
  async function loadHistory(page = historyPage.value) {
    if (disposed) return
    const sequence = ++historySequence
    historyLoading.value = true
    try {
      const data = await read(options => getJobHistory({ module: historyModule.value || undefined,
        triggerType: historyTriggerType.value || undefined, status: historyStatus.value || undefined, page, size: 10 }, options))
      if (disposed || sequence !== historySequence) return
      historyJobs.value = data.content; historyPage.value = data.page; historyTotalPages.value = data.totalPages
      historyError.value = ''
    } catch (error) { if (!disposed && sequence === historySequence) historyError.value = `历史加载失败：${errorMessage(error)}` }
    finally { if (!disposed && sequence === historySequence) historyLoading.value = false }
  }
  async function showJobDetail(id: number) {
    const sequence = ++detailSequence
    detailLoading.value = true
    detailError.value = ''
    try {
      const data = await read(options => getJobDetail(id, options))
      if (!disposed && sequence === detailSequence) selectedJob.value = data
    } catch (error) { if (!disposed && sequence === detailSequence) detailError.value = `详情加载失败：${errorMessage(error)}` }
    finally { if (!disposed && sequence === detailSequence) detailLoading.value = false }
  }
  function closeDetail() { detailSequence++; selectedJob.value = null; detailError.value = ''; detailLoading.value = false }
  onMounted(() => { void loadOverview(); void loadHistory(0) })
  onUnmounted(() => { disposed = true; clearTimeout(timer); reads.forEach(controller => controller.abort()); reads.clear() })
  return { currentSeason, modules, logs, initialLoading, refreshError, loadOverview,
    fullSyncJobId, isAnySyncing, isSubmitting, submittingAll, pendingModules, isCancelling, cancellingIds,
    canSyncAll, canSyncModule, totalCount, syncingCount, successCount, failedModules, recentSuccess,
    lastSyncTime, globalProgress, currentSyncingModules, retryFailed,
    handleSyncAll: () => submit(), handleSyncModule: (key: string) => submit(key),
    handleCancelAll: () => cancel([...activeIds.value]),
    handleCancelModule: (key: string) => { const id = modules.value.find(m => m.key === key)?.jobId; return id != null ? cancel([id]) : Promise.resolve() },
    historyJobs, historyPage, historyTotalPages, historyModule, historyTriggerType, historyStatus, historyLoading, historyError, loadHistory,
    selectedJob, detailLoading, detailError, showJobDetail, closeDetail,
  }
}
