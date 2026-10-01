import { describe, expect, it } from 'vitest'
import type { BoxScore, BoxScorePlayer } from '@/api/types'
import { assessBoxScoreStats, meaningfulPlayers, playerHasStats } from './boxScoreStats'

function player(partial: Partial<BoxScorePlayer>): BoxScorePlayer {
  return {
    playerId: 1,
    playerName: '测试球员',
    teamName: '湖人',
    minutes: '32:00',
    points: 0,
    rebounds: 0,
    assists: 0,
    steals: 0,
    blocks: 0,
    turnovers: 0,
    fgMade: 0,
    fgAttempted: 0,
    fgPct: 0,
    threeMade: 0,
    threeAttempted: 0,
    threePct: 0,
    ftMade: 0,
    ftAttempted: 0,
    ftPct: 0,
    plusMinus: 0,
    starter: false,
    ...partial,
  }
}

function box(partial: Partial<BoxScore>): BoxScore {
  return {
    gameId: '1',
    homeTeam: '湖人',
    awayTeam: '凯尔特人',
    homeScore: 0,
    awayScore: 0,
    homePlayers: [],
    awayPlayers: [],
    ...partial,
  } as BoxScore
}

describe('playerHasStats', () => {
  it('counts real minutes as data', () => {
    expect(playerHasStats(player({ minutes: '32:00', points: 0 }))).toBe(true)
  })

  it('counts any non-zero box score line as data', () => {
    expect(playerHasStats(player({ minutes: '0', points: 12 }))).toBe(true)
    expect(playerHasStats(player({ minutes: '', rebounds: 3 }))).toBe(true)
  })

  it('treats an all-zero line with no minutes as missing', () => {
    expect(playerHasStats(player({ minutes: '0' }))).toBe(false)
    expect(playerHasStats(player({ minutes: '' }))).toBe(false)
  })

  it('handles null input', () => {
    expect(playerHasStats(null)).toBe(false)
    expect(playerHasStats(undefined)).toBe(false)
  })
})

describe('assessBoxScoreStats', () => {
  it('reports unavailable when both teams have empty player lists', () => {
    const result = assessBoxScoreStats(box({}))
    expect(result.available).toBe(false)
    expect(result.hasPlayers).toBe(false)
  })

  it('reports unavailable when every player row is all zeros', () => {
    const result = assessBoxScoreStats(
      box({
        homePlayers: [player({ minutes: '0' }), player({ playerId: 2, minutes: '' })],
        awayPlayers: [player({ playerId: 3, minutes: '0' })],
      })
    )
    expect(result.available).toBe(false)
  })

  it('keeps one-team data without treating the missing opponent as zero', () => {
    const result = assessBoxScoreStats(box({ homePlayers: [player({ points: 28 })] }))
    expect(result.available).toBe(false)
    expect(result.hasPlayers).toBe(true)
  })

  it('allows comparison when both teams have real data', () => {
    const result = assessBoxScoreStats(box({
      homePlayers: [player({ points: 28 })],
      awayPlayers: [player({ playerId: 2, teamName: '凯尔特人', points: 24 })],
    }))
    expect(result.available).toBe(true)
  })

  it('handles null box score', () => {
    expect(assessBoxScoreStats(null).available).toBe(false)
    expect(assessBoxScoreStats(undefined).available).toBe(false)
  })
})

describe('meaningfulPlayers', () => {
  it('drops rows without any stats', () => {
    const players = [
      player({ playerId: 1, minutes: '30:00', points: 20 }),
      player({ playerId: 2, minutes: '0' }),
      player({ playerId: 3, minutes: '', points: 5 }),
    ]
    expect(meaningfulPlayers(players).map((p) => p.playerId)).toEqual([1, 3])
  })

  it('handles empty and null input', () => {
    expect(meaningfulPlayers([])).toEqual([])
    expect(meaningfulPlayers(null)).toEqual([])
  })
})
