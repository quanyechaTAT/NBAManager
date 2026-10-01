import type { BoxScore, BoxScorePlayer } from '@/api/types'

/** 技术统计可用性判定：用于区分「真实 0」与「统计缺失」 */
export interface StatAvailability {
  /** 主客两队是否都存在可展示的球员统计 */
  available: boolean
  /** 是否存在任何有意义的球员行（有出场或任意非零数据） */
  hasPlayers: boolean
}

function hasMeaningfulData(p: BoxScorePlayer): boolean {
  // 上场时间非空，或任意统计项非零，才认为这名球员有真实数据
  const minutes = (p.minutes ?? '').trim()
  if (minutes && minutes !== '0' && minutes !== '0:00') return true
  const fields: (keyof BoxScorePlayer)[] = [
    'points', 'rebounds', 'assists', 'steals', 'blocks', 'turnovers',
    'fgMade', 'fgAttempted', 'threeMade', 'threeAttempted', 'ftMade', 'ftAttempted',
  ]
  return fields.some((f) => Number(p[f] ?? 0) !== 0)
}

export function playerHasStats(player: BoxScorePlayer | null | undefined): boolean {
  return !!player && hasMeaningfulData(player)
}

/**
 * 判断技术统计是否可作为对比展示。
 * 两队球员列表都为空、或全部行都是零值时，视为「统计暂未提供」，
 * 避免渲染一屏 0 让用户误以为比赛真的零数据。
 */
export function assessBoxScoreStats(boxScore: BoxScore | null | undefined): StatAvailability {
  if (!boxScore) return { available: false, hasPlayers: false }
  const home = boxScore.homePlayers ?? []
  const away = boxScore.awayPlayers ?? []
  const hasHome = home.some(hasMeaningfulData)
  const hasAway = away.some(hasMeaningfulData)
  return {
    // 只拿到一队数据时仍可展示该队球员表，但不能把另一队当作 0 来对比。
    available: hasHome && hasAway,
    hasPlayers: hasHome || hasAway,
  }
}

/** 过滤掉没有任何统计的球员行，避免表格里出现整行 0 */
export function meaningfulPlayers(players: BoxScorePlayer[] | null | undefined): BoxScorePlayer[] {
  return (players ?? []).filter(hasMeaningfulData)
}
