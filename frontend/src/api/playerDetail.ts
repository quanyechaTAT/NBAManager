import request from '@/utils/request'
import type { PageResponse } from '@/api/types'

export interface PlayerDetail {
  id: number
  name: string
  teamId: number
  teamName: string
  position: string
  jerseyNumber: number
  height: string
  weight: number
  country: string
  gamesPlayed: number
  minutesPerGame: number
  pointsPerGame: number
  reboundsPerGame: number
  assistsPerGame: number
  stealsPerGame: number
  blocksPerGame: number
  turnoversPerGame: number
  fieldGoalPct: number
  threePointPct: number
  freeThrowPct: number
  efficiency: number
  trueShootingPct: number
  usagePct: number
}

export interface PlayerGameLogItem {
  gameId: string
  matchDate: string
  opponent: string
  minutes: string
  points: number
  rebounds: number
  assists: number
  steals: number
  blocks: number
  turnovers: number
  fgPct: number
  threePct: number
  ftPct: number
  plusMinus: number
  result: string
}

export interface PlayerCareerStat {
  season: string
  teamName: string
  gamesPlayed: number
  minutesPerGame: number
  pointsPerGame: number
  reboundsPerGame: number
  assistsPerGame: number
  stealsPerGame: number
  blocksPerGame: number
  fgPct: number
  threePointPct: number
  freeThrowPct: number
  efficiency: number
}

/** 获取球员详情 */
export function fetchPlayerDetail(id: number) {
  return request.get<PlayerDetail>(`/players/${id}/detail`)
}

/** 获取球员生涯数据 */
export function fetchPlayerCareer(id: number) {
  return request.get<PlayerCareerStat[]>(`/players/${id}/career`)
}

/** 获取球员比赛日志 */
export function fetchPlayerGameLog(id: number, params: { season?: string; page: number; size: number }) {
  return request.get<PageResponse<PlayerGameLogItem>>(`/players/${id}/gamelog`, { params })
}
