import request from '@/utils/request'
import type {
  EventExplain,
  GameSummary,
  Highlight,
  MomentumSnapshot,
  ReplayBootstrap,
  ReplayPreparation,
  TimelineEvent,
  TurningPoint,
} from './types'

const REPLAY_TIMEOUT_MS = 60_000

function gamePath(gameId: string): string {
  return `/ai-replay/games/${encodeURIComponent(gameId)}`
}

function parseRetryAfterSeconds(value: unknown): number | null {
  if (typeof value === 'number') {
    return Number.isInteger(value) && value >= 0 ? value : null
  }
  if (typeof value !== 'string' || !/^\d+$/.test(value.trim())) return null
  const seconds = Number(value.trim())
  return Number.isSafeInteger(seconds) ? seconds : null
}

export function fetchReplayBootstrap(gameId: string, signal?: AbortSignal): Promise<ReplayBootstrap> {
  return request
    .get(`${gamePath(gameId)}/bootstrap`, { signal, timeout: REPLAY_TIMEOUT_MS })
    .then(res => res.data)
}

export function prepareReplay(gameId: string, signal?: AbortSignal): Promise<ReplayPreparation> {
  return request
    .post(`${gamePath(gameId)}/prepare`, {}, { signal, timeout: REPLAY_TIMEOUT_MS })
    .then((res) => {
      const headerValue = res.headers?.['retry-after']
      const headerSeconds = parseRetryAfterSeconds(headerValue)
      return {
        ...res.data,
        retryAfterSeconds: headerSeconds ?? parseRetryAfterSeconds(res.data.retryAfterSeconds),
      }
    })
}

export function fetchTimeline(gameId: string, period?: number, signal?: AbortSignal): Promise<TimelineEvent[]> {
  const params: Record<string, number> = {}
  if (period !== undefined) params.period = period
  return request.get(`${gamePath(gameId)}/timeline`, { params, signal, timeout: REPLAY_TIMEOUT_MS }).then(res => res.data)
}

export function fetchMomentum(gameId: string, signal?: AbortSignal): Promise<MomentumSnapshot[]> {
  return request.get(`${gamePath(gameId)}/momentum`, { signal, timeout: REPLAY_TIMEOUT_MS }).then(res => res.data)
}

export function fetchHighlights(gameId: string, signal?: AbortSignal): Promise<Highlight[]> {
  return request.get(`${gamePath(gameId)}/highlights`, { signal, timeout: REPLAY_TIMEOUT_MS }).then(res => res.data)
}

export function fetchTurningPoints(gameId: string, signal?: AbortSignal): Promise<TurningPoint[]> {
  return request.get(`${gamePath(gameId)}/turning-points`, { signal, timeout: REPLAY_TIMEOUT_MS }).then(res => res.data)
}

export function fetchGameSummary(gameId: string, signal?: AbortSignal): Promise<GameSummary> {
  return request.get(`${gamePath(gameId)}/summary`, { signal, timeout: REPLAY_TIMEOUT_MS }).then(res => res.data)
}

export function generateGameSummary(gameId: string, signal?: AbortSignal): Promise<GameSummary> {
  return request.post(`${gamePath(gameId)}/summary`, {}, { signal, timeout: REPLAY_TIMEOUT_MS }).then(res => res.data)
}

export function explainEvent(gameId: string, eventId: number, signal?: AbortSignal): Promise<EventExplain> {
  return request
    .post(`${gamePath(gameId)}/events/${encodeURIComponent(String(eventId))}/explain`, {}, { signal, timeout: REPLAY_TIMEOUT_MS })
    .then(res => res.data)
}
