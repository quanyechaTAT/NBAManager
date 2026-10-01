import { describe, expect, it } from 'vitest'
import type { MatchRecord } from '@/api/types'
import {
  buildTickerMatches,
  classifyMatches,
  hasScore,
  liveMatches,
  tickerLabel,
  toLocalDateKey,
} from './matchFeed'

function match(partial: Partial<MatchRecord>): MatchRecord {
  return {
    id: 1,
    homeTeam: '湖人',
    awayTeam: '凯尔特人',
    homeScore: 100,
    awayScore: 98,
    matchDate: '2026-01-15',
    season: '2025-26',
    status: 'FINISHED',
    nbaGameId: '1',
    gameType: 'REGULAR_SEASON',
    ...partial,
  }
}

const NOW = new Date('2026-01-15T12:00:00')

describe('toLocalDateKey', () => {
  it('formats dates in local time and rejects invalid input', () => {
    expect(toLocalDateKey('2026-01-15')).toBe('2026-01-15')
    expect(toLocalDateKey(new Date(2026, 0, 5))).toBe('2026-01-05')
    expect(toLocalDateKey(null)).toBe('')
    expect(toLocalDateKey('not-a-date')).toBe('')
  })
})

describe('classifyMatches', () => {
  it('separates live, today and past results', () => {
    const groups = classifyMatches(
      [
        match({ id: 1, status: 'LIVE', matchDate: '2026-01-15' }),
        match({ id: 2, status: 'SCHEDULED', matchDate: '2026-01-15' }),
        match({ id: 3, status: 'FINISHED', matchDate: '2026-01-14' }),
      ],
      NOW
    )
    expect(groups.map((g) => g.kind)).toEqual(['live', 'today', 'recent'])
    expect(groups.map((g) => g.label)).toEqual(['直播中', '今日赛程', '最近赛果'])
    expect(groups[0].matches.map((m) => m.id)).toEqual([1])
    expect(groups[1].matches.map((m) => m.id)).toEqual([2])
    expect(groups[2].matches.map((m) => m.id)).toEqual([3])
  })

  it('never labels historical results as live', () => {
    const groups = classifyMatches([match({ matchDate: '2025-06-20' })], NOW)
    expect(groups.map((g) => g.kind)).toEqual(['recent'])
    expect(groups.some((g) => g.kind === 'live')).toBe(false)
  })

  it('sorts recent results by date descending and honours the limit', () => {
    const groups = classifyMatches(
      [
        match({ id: 1, matchDate: '2026-01-10' }),
        match({ id: 2, matchDate: '2026-01-13' }),
        match({ id: 3, matchDate: '2026-01-12' }),
      ],
      NOW,
      2
    )
    expect(groups[0].matches.map((m) => m.id)).toEqual([2, 3])
  })

  it('keeps live games regardless of date so overnight games still count', () => {
    const groups = classifyMatches([match({ id: 9, status: 'LIVE', matchDate: '2026-01-14' })], NOW)
    expect(groups[0].kind).toBe('live')
  })

  it('returns an empty list for missing data instead of throwing', () => {
    expect(classifyMatches(null, NOW)).toEqual([])
    expect(classifyMatches(undefined, NOW)).toEqual([])
    expect(classifyMatches([], NOW)).toEqual([])
  })
})

describe('buildTickerMatches', () => {
  it('orders live first, then today, then recent', () => {
    const feed = buildTickerMatches(
      [
        match({ id: 1, status: 'FINISHED', matchDate: '2026-01-13' }),
        match({ id: 2, status: 'SCHEDULED', matchDate: '2026-01-15' }),
        match({ id: 3, status: 'LIVE', matchDate: '2026-01-15' }),
      ],
      NOW
    )
    expect(feed.map((m) => m.id)).toEqual([3, 2, 1])
  })

  it('respects the display limit', () => {
    const many = Array.from({ length: 20 }, (_, i) =>
      match({ id: i + 1, status: 'LIVE', matchDate: '2026-01-15' })
    )
    expect(buildTickerMatches(many, NOW, 5)).toHaveLength(5)
  })
})

describe('tickerLabel', () => {
  it('describes the real data source rather than always claiming live', () => {
    expect(tickerLabel([match({ status: 'LIVE', matchDate: '2026-01-15' })], NOW)).toBe('直播中')
    expect(tickerLabel([match({ status: 'SCHEDULED', matchDate: '2026-01-15' })], NOW)).toBe('今日赛程')
    expect(tickerLabel([match({ status: 'FINISHED', matchDate: '2026-01-10' })], NOW)).toBe('最近赛果')
    expect(tickerLabel([], NOW)).toBe('赛事')
  })
})

describe('liveMatches', () => {
  it('returns only truly live games', () => {
    const all = [
      match({ id: 1, status: 'LIVE' }),
      match({ id: 2, status: 'FINISHED' }),
      match({ id: 3, status: 'SCHEDULED' }),
    ]
    expect(liveMatches(all).map((m) => m.id)).toEqual([1])
    expect(liveMatches(null)).toEqual([])
  })
})

describe('hasScore', () => {
  it('treats a real 0-0 as a score but not a scheduled game', () => {
    expect(hasScore({ status: 'FINISHED', homeScore: 0, awayScore: 0 })).toBe(true)
    expect(hasScore({ status: 'SCHEDULED', homeScore: 0, awayScore: 0 })).toBe(false)
    expect(hasScore({ status: 'FINISHED', homeScore: null as unknown as number, awayScore: 1 })).toBe(false)
  })
})
