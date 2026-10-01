import request from '@/utils/request'
import type { GameShotChartResponse } from './types'

export function getGameShotChart(gameId: string) {
  return request.get<GameShotChartResponse>(`/shot-chart/game/${gameId}`, { timeout: 60000 })
}
