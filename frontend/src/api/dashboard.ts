import request from '@/utils/request'
import type { DashboardStats } from '@/api/types'

export function fetchDashboardStats() {
  return request.get<DashboardStats>('/dashboard/stats')
}
