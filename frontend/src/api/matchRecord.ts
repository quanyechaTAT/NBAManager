import request from '@/utils/request'
import type { MatchRecord } from '@/api/types'

export function fetchMatchRecordById(id: number, signal?: AbortSignal) {
  return request.get<MatchRecord>(`/match-records/id/${id}`, { signal })
}

/** Read one game without downloading the full match archive. */
export function fetchMatchRecord(gameId: string, signal?: AbortSignal) {
  return request.get<MatchRecord>(`/match-records/game/${encodeURIComponent(gameId)}`, { signal })
}

/** 查询某支球队的比赛记录 */
export function fetchMatchRecords(teamName?: string, signal?: AbortSignal) {
  return request.get<MatchRecord[]>('/match-records', {
    params: teamName ? { teamName } : {},
    signal,
    paramsSerializer: (params) => {
      return Object.entries(params)
        .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
        .join('&')
    }
  })
}

/** 查询两队交锋记录 */
export function fetchHeadToHead(team1: string, team2: string) {
  return request.get<MatchRecord[]>('/match-records/head-to-head', {
    params: { team1, team2 },
    paramsSerializer: (params) => {
      return Object.entries(params)
        .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
        .join('&')
    }
  })
}
