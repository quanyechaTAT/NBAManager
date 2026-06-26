import request from '@/utils/request'
import type { DashboardStats, Player, PageResponse } from '@/api/types'

export function fetchDashboardStats() {
  return request.get<DashboardStats>('/dashboard/stats')
}

export function fetchTopScorers(page = 0, size = 20) {
  return request.get<PageResponse<Player>>('/dashboard/top-scorers', { params: { page, size } })
}
