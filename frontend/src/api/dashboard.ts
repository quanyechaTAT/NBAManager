import request from '@/utils/request'
import type { DashboardOverview, DashboardStats, Player, PageResponse } from '@/api/types'

export function fetchDashboardOverview(signal?: AbortSignal) {
  return request.get<DashboardOverview>('/dashboard/overview', { signal })
}

export function fetchDashboardStats() {
  return request.get<DashboardStats>('/dashboard/stats')
}

export function fetchTopScorers(page = 0, size = 20) {
  return request.get<PageResponse<Player>>('/dashboard/top-scorers', { params: { page, size } })
}
