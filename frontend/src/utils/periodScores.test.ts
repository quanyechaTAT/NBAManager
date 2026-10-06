import { describe, expect, it } from 'vitest'
import type { QuarterScore } from '@/api/types'
import { buildPeriodScoreRows } from './periodScores'

const cumulative: QuarterScore[] = [
  { period: 1, homeScore: 35, awayScore: 32 },
  { period: 2, homeScore: 61, awayScore: 81 },
  { period: 3, homeScore: 98, awayScore: 110 },
  { period: 4, homeScore: 123, awayScore: 132 },
]

describe('per-period scores', () => {
  it('shows the supplied game as individual periods followed by the final score', () => {
    const original = structuredClone(cumulative)
    expect(buildPeriodScoreRows(cumulative, { status: 'FINISHED', homeScore: 123, awayScore: 132 })).toEqual([
      { label: '第1节', homeScore: 35, awayScore: 32, isTotal: false },
      { label: '第2节', homeScore: 26, awayScore: 49, isTotal: false },
      { label: '第3节', homeScore: 37, awayScore: 29, isTotal: false },
      { label: '第4节', homeScore: 25, awayScore: 22, isTotal: false },
      { label: '全场', homeScore: 123, awayScore: 132, isTotal: true },
    ])
    expect(cumulative).toEqual(original)
  })

  it('sorts periods and calculates multiple overtimes against the preceding period', () => {
    const scores = [...cumulative.slice(0, 3),
      { period: 4, homeScore: 123, awayScore: 123 },
      { period: 5, homeScore: 132, awayScore: 132 },
      { period: 6, homeScore: 144, awayScore: 140 },
    ].reverse()
    expect(buildPeriodScoreRows(scores, { status: 'FINISHED', homeScore: 144, awayScore: 140 }).slice(-3)).toEqual([
      { label: '加时1', homeScore: 9, awayScore: 9, isTotal: false },
      { label: '加时2', homeScore: 12, awayScore: 8, isTotal: false },
      { label: '全场', homeScore: 144, awayScore: 140, isTotal: true },
    ])
  })

  it('does not attribute multiple periods to a single period when the preceding score is missing', () => {
    const scores = [cumulative[0], cumulative[2], cumulative[3]]
    expect(buildPeriodScoreRows(scores, { status: 'FINISHED', homeScore: 123, awayScore: 132 })[1]).toEqual({
      label: '第3节', homeScore: null, awayScore: null, isTotal: false,
    })
  })

  it('preserves zero, hides invalid differences and uses recorded totals instead of partial data', () => {
    const scores = [
      { period: 1, homeScore: 0, awayScore: 9 },
      { period: 2, homeScore: 10, awayScore: 8 },
    ]
    expect(buildPeriodScoreRows(scores, { status: 'FINISHED', homeScore: 105, awayScore: 129 })).toEqual([
      { label: '第1节', homeScore: 0, awayScore: 9, isTotal: false },
      { label: '第2节', homeScore: 10, awayScore: null, isTotal: false },
      { label: '全场', homeScore: 105, awayScore: 129, isTotal: true },
    ])
    expect(buildPeriodScoreRows([], { status: 'FINISHED', homeScore: null, awayScore: null })).toEqual([])
  })

  it('labels live totals as current scores and suppresses scores before tip-off', () => {
    expect(buildPeriodScoreRows([], { status: 'LIVE', homeScore: 0, awayScore: 9 })).toEqual([
      { label: '当前比分', homeScore: 0, awayScore: 9, isTotal: true },
    ])
    expect(buildPeriodScoreRows(cumulative, { status: 'SCHEDULED', homeScore: 123, awayScore: 132 })).toEqual([])
  })
})
