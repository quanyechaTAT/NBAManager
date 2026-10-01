import { describe, expect, it } from 'vitest'
import {
  buildReplayPeriodOptions,
  compareReplayValues,
  formatAdvantageText,
  formatPercentage,
  formatReplayPeriod,
  replaySourceLabel,
} from './replayDomain'

describe('replayDomain', () => {
  it('builds distinct, sorted regulation and overtime options from real data', () => {
    expect(buildReplayPeriodOptions([{ period: 6 }, { period: 1 }, { period: 5 }, { period: 6 }])).toEqual([
      { value: 1, label: 'Q1' },
      { value: 5, label: 'OT1' },
      { value: 6, label: 'OT2' },
    ])
    expect(formatReplayPeriod(7)).toBe('OT3')
  })

  it('normalizes fractional and percentage-point values for display', () => {
    expect(formatPercentage(0.456)).toBe('45.6%')
    expect(formatPercentage('45.6%')).toBe('45.6%')
    expect(formatPercentage(null)).toBe('-')
  })

  it('compares numeric strings numerically and names the actual leader', () => {
    expect(compareReplayValues('9', '10')).toBe('away')
    expect(compareReplayValues('45.6%', '43.2%')).toBe('home')
    expect(compareReplayValues('-', 12)).toBe('unknown')
    expect(formatAdvantageText('9', '10', '湖人', '凯尔特人')).toBe('凯尔特人优势')
    expect(formatAdvantageText(7, 7, '湖人', '凯尔特人')).toBe('双方持平')
  })

  it('prefers a server label and otherwise maps stable provider identifiers', () => {
    expect(replaySourceLabel('NBA_PLAY_BY_PLAY', 'NBA 数据源')).toBe('NBA 数据源')
    expect(replaySourceLabel('ESPN_PLAY_BY_PLAY')).toBe('ESPN 逐回合')
    expect(replaySourceLabel('SCOREBOARD_PERIOD_SUMMARY')).toBe('比分牌节末摘要')
    expect(replaySourceLabel('ESPN_SCOREBOARD_SUMMARY')).toBe('ESPN 比分牌节末摘要')
    expect(replaySourceLabel('ESPN_NBA_SUMMER_SCOREBOARD')).toBe('ESPN 夏季联赛节末摘要')
    expect(replaySourceLabel('custom_feed')).toBe('custom feed')
  })
})
