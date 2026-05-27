import request from '@/utils/request'
import type { PageResponse, Player } from '@/api/types'

export interface PlayerPayload {
  name: string
  teamId: number
  position: string
  pointsPerGame: number
  reboundsPerGame: number
  assistsPerGame: number
  stealsPerGame: number
}

export function fetchPlayers(params: { q?: string; teamId?: number; position?: string; page: number; size: number }) {
  return request.get<PageResponse<Player>>('/players', { params })
}

export function createPlayer(data: PlayerPayload) {
  return request.post<Player>('/players', data)
}

export function updatePlayer(id: number, data: PlayerPayload) {
  return request.put<Player>(`/players/${id}`, data)
}

export function deletePlayer(id: number) {
  return request.delete(`/players/${id}`)
}
