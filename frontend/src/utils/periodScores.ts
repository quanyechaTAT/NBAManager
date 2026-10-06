import type { QuarterScore } from '@/api/types'

export interface PeriodScoreRow {
  label: string
  homeScore: number | null
  awayScore: number | null
  isTotal: boolean
}

function validScore(score: number | null | undefined): score is number {
  return score != null && Number.isInteger(score) && score >= 0
}

function periodPoints(current: number | undefined, previous: number | undefined): number | null {
  if (!validScore(current) || !validScore(previous) || current < previous) return null
  return current - previous
}

/** 接口保存节末累计比分；单节得分只能由相邻两节的累计比分相减得到。 */
export function buildPeriodScoreRows(
  cumulativeScores: readonly QuarterScore[],
  total: { status: string; homeScore: number | null; awayScore: number | null },
): PeriodScoreRow[] {
  if (total.status !== 'FINISHED' && total.status !== 'LIVE') return []

  const byPeriod = new Map(cumulativeScores
    .filter(score => Number.isInteger(score.period) && score.period > 0)
    .map(score => [score.period, score]))
  const rows: PeriodScoreRow[] = [...byPeriod.values()]
    .sort((a, b) => a.period - b.period)
    .map(score => {
      const previous = score.period === 1
        ? { homeScore: 0, awayScore: 0 }
        : byPeriod.get(score.period - 1)
      return {
        label: score.period <= 4 ? `第${score.period}节` : `加时${score.period - 4}`,
        homeScore: periodPoints(score.homeScore, previous?.homeScore),
        awayScore: periodPoints(score.awayScore, previous?.awayScore),
        isTotal: false,
      }
    })

  const homeScore = validScore(total.homeScore) ? total.homeScore : null
  const awayScore = validScore(total.awayScore) ? total.awayScore : null
  if (rows.length || homeScore != null || awayScore != null) {
    rows.push({
      label: total.status === 'FINISHED' ? '全场' : '当前比分',
      homeScore,
      awayScore,
      isTotal: true,
    })
  }
  return rows
}
