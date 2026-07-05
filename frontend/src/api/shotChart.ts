import request from '@/utils/request'
import type { GameShotChartResponse } from './types'

export function getGameShotChart(gameId: string): Promise<GameShotChartResponse> {
  return request.get(`/shot-chart/game/${gameId}`, { timeout: 60000 })
}
