import request from '@/utils/request'
import type { PlayerDetail } from '@/api/playerDetail'

export interface PlayerCompare {
  player1: PlayerDetail
  player2: PlayerDetail
}

export function fetchPlayerCompare(id1: number, id2: number) {
  return request.get<PlayerCompare>('/players/compare', { params: { id1, id2 } })
}
