import request from '@/utils/request'
import type { BoxScore, PlayByPlayEvent, QuarterScore } from '@/api/types'

/** 获取比赛Box Score */
export function fetchBoxScore(gameId: string) {
  return request.get<BoxScore>(`/match-detail/${gameId}/boxscore`)
}

/** 获取比赛Play-by-Play */
export function fetchPlayByPlay(gameId: string, period?: number) {
  return request.get<PlayByPlayEvent[]>(`/match-detail/${gameId}/playbyplay`, {
    params: period !== undefined ? { period } : {},
  })
}

/** 获取逐节比分 */
export function fetchQuarterScores(gameId: string) {
  return request.get<QuarterScore[]>(`/match-detail/${gameId}/quarters`)
}
