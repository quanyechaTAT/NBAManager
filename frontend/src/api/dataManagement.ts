import request from '@/utils/request'

export interface ModuleInfo {
  key: string
  name: string
  icon: string
  count: number
  status: 'idle' | 'syncing' | 'success' | 'error' | 'pending' | 'paused'
  progress: number
  lastSyncTime: string
  errorMessage?: string
  paused?: boolean
}

export interface OverviewData {
  modules: ModuleInfo[]
  currentSeason: string
  syncing: boolean
  lastSyncTime: string
}

export interface ProgressData {
  status: string
  progress: number
  lastSyncTime: string
  errorMessage: string
  paused?: boolean
}

/** 获取数据管理概览 */
export function getDataOverview() {
  return request.get<OverviewData>('/admin/data/overview')
}

/** 同步指定模块 */
export function syncModule(module: string) {
  return request.post(`/admin/data/sync/${module}`)
}

/** 全量同步 */
export function syncAllModules() {
  return request.post('/admin/data/sync-all')
}

/** 暂停指定模块 */
export function pauseModule(module: string) {
  return request.post(`/admin/data/pause/${module}`)
}

/** 继续指定模块 */
export function resumeModule(module: string) {
  return request.post(`/admin/data/resume/${module}`)
}

/** 取消指定模块 */
export function cancelModule(module: string) {
  return request.post(`/admin/data/cancel/${module}`)
}

/** 暂停所有模块 */
export function pauseAllModules() {
  return request.post('/admin/data/pause-all')
}

/** 取消所有模块 */
export function cancelAllModules() {
  return request.post('/admin/data/cancel-all')
}

/** 获取指定模块进度 */
export function getModuleProgress(module: string) {
  return request.get<ProgressData>(`/admin/data/progress/${module}`)
}

/** 获取所有模块进度 */
export function getAllProgress() {
  return request.get<Record<string, ProgressData>>('/admin/data/progress')
}
