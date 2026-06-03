import request from '@/utils/request'
import type { BoxScore, PlayByPlayEvent, QuarterScore } from '@/api/types'

/** 获取比赛Box Score（需要更长超时时间，因为可能需要从NBA API获取数据） */
export function fetchBoxScore(gameId: string) {
  return request.get<BoxScore>(`/match-detail/${gameId}/boxscore`, { timeout: 60000 })
}

/** 获取比赛Play-by-Play */
export function fetchPlayByPlay(gameId: string, period?: number) {
  return request.get<PlayByPlayEvent[]>(`/match-detail/${gameId}/playbyplay`, {
    params: period !== undefined ? { period } : {},
    timeout: 60000,
  })
}

/** 获取逐节比分 */
export function fetchQuarterScores(gameId: string) {
  return request.get<QuarterScore[]>(`/match-detail/${gameId}/quarters`, { timeout: 60000 })
}
