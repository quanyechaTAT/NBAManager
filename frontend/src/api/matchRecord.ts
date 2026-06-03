import request from '@/utils/request'
import type { MatchRecord } from '@/api/types'

/** 查询某支球队的比赛记录 */
export function fetchMatchRecords(teamName?: string) {
  return request.get<MatchRecord[]>('/match-records', {
    params: teamName ? { teamName } : {},
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
