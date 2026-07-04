import request from '@/utils/request'
import type { TimelineEvent, MomentumSnapshot, Highlight, TurningPoint, GameSummary, EventExplain } from './types'

export function fetchTimeline(gameId: string, period?: number): Promise<TimelineEvent[]> {
  const params: Record<string, any> = {}
  if (period !== undefined) params.period = period
  return request.get(`/ai-replay/games/${gameId}/timeline`, { params, timeout: 60000 })
}

export function fetchMomentum(gameId: string): Promise<MomentumSnapshot[]> {
  return request.get(`/ai-replay/games/${gameId}/momentum`, { timeout: 60000 })
}

export function fetchHighlights(gameId: string): Promise<Highlight[]> {
  return request.get(`/ai-replay/games/${gameId}/highlights`, { timeout: 60000 })
}

export function fetchTurningPoints(gameId: string): Promise<TurningPoint[]> {
  return request.get(`/ai-replay/games/${gameId}/turning-points`, { timeout: 60000 })
}

export function fetchGameSummary(gameId: string): Promise<GameSummary> {
  return request.get(`/ai-replay/games/${gameId}/summary`, { timeout: 60000 })
}

export function explainEvent(gameId: string, eventId: number): Promise<EventExplain> {
  return request.post(`/ai-replay/games/${gameId}/events/${eventId}/explain`, {}, { timeout: 60000 })
}
