import request from '@/utils/request'
import type { DraftPick, DraftPickRequest } from '@/api/types'

export function fetchDraftPicks(params: { year: number }) {
  return request.get<DraftPick[]>('/drafts', { params })
}

export function fetchDraftByTeam(teamName: string) {
  return request.get<DraftPick[]>(`/drafts/team/${teamName}`)
}

export function createDraftPick(data: DraftPickRequest) {
  return request.post<DraftPick>('/drafts', data)
}

export function updateDraftPick(id: number, data: DraftPickRequest) {
  return request.put<DraftPick>(`/drafts/${id}`, data)
}

export function deleteDraftPick(id: number) {
  return request.delete(`/drafts/${id}`)
}
