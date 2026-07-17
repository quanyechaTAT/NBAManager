import request from '@/utils/request'

export type SyncTriggerType = 'SCHEDULED' | 'MANUAL' | 'LOGIN_CATCHUP'

export interface SyncJobResponse {
  jobId: number
  module: string
  triggerType: SyncTriggerType
  status: string
  progress: number
  stage: string | null
  requestedBy: string
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  heartbeatAt: string | null
  dataAsOf: string | null
  source: string | null
  fetchedCount: number
  insertedCount: number
  updatedCount: number
  skippedCount: number
  errorCode: string | null
  errorMessage: string | null
  cancelRequested: boolean
  reused: boolean
}

export interface SyncJobStepResponse {
  id: number
  module: string
  stepOrder: number
  status: string
  progress: number
  stage: string | null
  startedAt: string | null
  finishedAt: string | null
  fetchedCount: number
  insertedCount: number
  updatedCount: number
  skippedCount: number
  errorCode: string | null
  errorMessage: string | null
}

export interface SyncJobDetailResponse extends SyncJobResponse {
  steps: SyncJobStepResponse[]
}

export interface SyncModuleOverview {
  key: string
  count: number
  lastSuccessfulAt: string | null
  latestJob: SyncJobResponse | null
  activeJob: SyncJobResponse | null
  activeFullStep: SyncJobStepResponse | null
}

export interface JobHistoryFilters {
  module?: string
  triggerType?: string
  status?: string
  page?: number
  size?: number
}

export interface SyncJobPageResponse {
  content: SyncJobResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface SyncOverviewResponse {
  currentSeason: string
  modules: SyncModuleOverview[]
}

export function getSyncOverview() {
  return request.get<SyncOverviewResponse>('/admin/overview')
}

/** 同步指定模块 → 返回 202 + jobId */
export function syncModule(module: string) {
  return request.post<SyncJobResponse>(`/admin/sync/${module}`)
}

/** 全量同步 */
export function syncAllModules() {
  return request.post<SyncJobResponse>('/admin/sync-all')
}

/** 查询任务状态 */
export function getJob(jobId: number) {
  return request.get<SyncJobResponse>(`/admin/jobs/${jobId}`)
}

/** 最近任务列表 */
export function getRecentJobs() {
  return request.get<SyncJobResponse[]>('/admin/jobs')
}

/** 模块最新任务 */
export function getLatestJob(module: string) {
  return request.get<SyncJobResponse>(`/admin/jobs/module/${module}`)
}

/** 取消任务 */
export function cancelJob(jobId: number) {
  return request.post<{ accepted: boolean; job: SyncJobResponse }>(`/admin/jobs/${jobId}/cancel`)
}

/** 查询任务详情（含步骤） */
export function getJobDetail(jobId: number) {
  return request.get<SyncJobDetailResponse>(`/admin/jobs/${jobId}`)
}

/** 任务历史分页查询 */
export function getJobHistory(filters: JobHistoryFilters) {
  return request.get<SyncJobPageResponse>('/admin/jobs', { params: filters })
}

/** 旧接口兼容：同步状态 */
export function getSyncStatus() {
  return request.get<{ syncing: boolean; jobs: SyncJobResponse[] }>('/admin/sync-status')
}

/** 旧接口兼容：触发同步 */
export function triggerSync() {
  return request.post<SyncJobResponse>('/admin/sync')
}
