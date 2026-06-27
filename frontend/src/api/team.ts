import request from '@/utils/request'
import type { PageResponse, Team, TeamRank } from '@/api/types'

export function fetchTeams(params: { q?: string; page: number; size: number }) {
  return request.get<PageResponse<Team>>('/teams', { params })
}

export function fetchTeam(id: number) {
  return request.get<Team>(`/teams/${id}`)
}

export function fetchRankings() {
  return request.get<Record<string, TeamRank[]>>('/teams/rankings')
}

export function fetchDivisionRankings() {
  return request.get<Record<string, TeamRank[]>>('/teams/divisions')
}

export function createTeam(data: Omit<Team, 'id'>) {
  return request.post<Team>('/teams', data)
}

export function updateTeam(id: number, data: Omit<Team, 'id'>) {
  return request.put<Team>(`/teams/${id}`, data)
}

export function deleteTeam(id: number) {
  return request.delete(`/teams/${id}`)
}
