import request from '@/utils/request'

export type DiscoveryKind = 'PLAYER' | 'TEAM' | 'MATCH' | 'NEWS' | 'POST'
export type DiscoveryScene = 'HOME' | 'NEWS_FEED' | 'COMMUNITY_FEED' | 'NEWS_DETAIL' | 'POST_DETAIL'
export const discoveryLabels: Record<DiscoveryKind, string> = { PLAYER: '球员', TEAM: '球队', MATCH: '比赛', NEWS: '资讯', POST: '社区' }
export interface DiscoveryItem {
  itemKey: string; type: DiscoveryKind; id: number; title: string; summary: string
  imageUrl: string | null; publishedAt: string | null; reason: string | null
  target: { path: string; query: Record<string, string> }
}
export interface DiscoveryResponse {
  requestId: string; items: DiscoveryItem[]; nextCursor: string | null; strategyVersion: string; degraded: boolean
}
export interface DiscoveryPreferences { personalized: boolean; revision: number; clearedAt: string | null }
export interface DiscoveryQuery {
  q?: string; types?: string; team?: string; season?: string; dateFrom?: string; dateTo?: string
  cursor?: string; limit?: number; mode?: 'hybrid' | 'lexical'; scene?: DiscoveryScene; itemKey?: string
}
export const discover = (operation: 'search' | 'suggest' | 'recommendations', params: DiscoveryQuery, signal?: AbortSignal) =>
  request.get<DiscoveryResponse>(`/discovery/${operation}`, { params, signal })
export const discoveryPreferences = () => request.get<DiscoveryPreferences>('/discovery/preferences')
export const updateDiscoveryPreferences = (personalized: boolean) => request.put<DiscoveryPreferences>('/discovery/preferences', { personalized })
export const clearDiscoveryPreferences = () => request.delete<DiscoveryPreferences>('/discovery/preferences/history')
export const discoveryFeedback = (itemKey: string, active: boolean) => request.post('/discovery/feedback', { itemKey, active })
export const discoveryStatus = () => request.get<DiscoveryStatus>('/admin/discovery/status')
export const discoveryJobs = () => request.get<DiscoveryJob[]>('/admin/discovery/jobs')
export const createDiscoveryJob = (kind: string, version?: string, traffic?: number) => request.post('/admin/discovery/jobs', { kind, version, traffic })
export const cancelDiscoveryJob = (id: string) => request.post(`/admin/discovery/jobs/${id}/cancel`)
export interface DiscoveryJob { id: string; kind: string; status: string; progress: number; detail_json: string; created_at: string; cancel_requested: boolean }
export interface DiscoveryStatus {
  indexedItems: number; pendingEmbeddings: number
  controls: { control_key: string; value_json: string; updated_at: string }[]
  models: { version: string; status: string; metrics_json: string; created_at: string }[]
}
