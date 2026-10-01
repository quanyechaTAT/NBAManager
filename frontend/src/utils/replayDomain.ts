import type { TimelineEvent } from '@/api/types'

export type ReplayValueComparison = 'home' | 'away' | 'tie' | 'unknown'

export interface ReplayPeriodOption {
  value: number
  label: string
}

const SOURCE_LABELS: Readonly<Record<string, string>> = {
  NBA_PLAY_BY_PLAY: 'NBA 官方逐回合',
  ESPN_PLAY_BY_PLAY: 'ESPN 逐回合',
  SUMMER_LEAGUE_PLAY_BY_PLAY: 'NBA 夏季联赛逐回合',
  SCOREBOARD_PERIOD_SUMMARY: '比分牌节末摘要',
  NBA_SCOREBOARD_SUMMARY: 'NBA 比分牌节末摘要',
  ESPN_SCOREBOARD_SUMMARY: 'ESPN 比分牌节末摘要',
  ESPN_NBA_SUMMER_SCOREBOARD: 'ESPN 夏季联赛节末摘要',
}

export function formatReplayPeriod(period: number): string {
  if (!Number.isInteger(period) || period < 1) return 'Q?'
  return period <= 4 ? `Q${period}` : `OT${period - 4}`
}

export function buildReplayPeriodOptions(
  values: Iterable<number | Pick<TimelineEvent, 'period'>>,
): ReplayPeriodOption[] {
  const periods = new Set<number>()
  for (const value of values) {
    const period = typeof value === 'number' ? value : value.period
    if (Number.isInteger(period) && period > 0) periods.add(period)
  }
  return [...periods]
    .sort((left, right) => left - right)
    .map(value => ({ value, label: formatReplayPeriod(value) }))
}

export function toReplayNumber(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value !== 'string') return null
  const normalized = value.trim().replace(/,/g, '').replace(/%$/, '').trim()
  if (!normalized) return null
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : null
}

export function formatPercentage(value: unknown, fractionDigits = 1): string {
  const parsed = toReplayNumber(value)
  if (parsed === null) return '-'
  const hadPercentSuffix = typeof value === 'string' && value.trim().endsWith('%')
  const percentage = !hadPercentSuffix && Math.abs(parsed) <= 1 ? parsed * 100 : parsed
  const digits = Math.max(0, Math.min(4, Math.trunc(fractionDigits)))
  return `${Number(percentage.toFixed(digits))}%`
}

export function compareReplayValues(homeValue: unknown, awayValue: unknown): ReplayValueComparison {
  const home = toReplayNumber(homeValue)
  const away = toReplayNumber(awayValue)
  if (home === null || away === null) return 'unknown'
  if (home === away) return 'tie'
  return home > away ? 'home' : 'away'
}

export function formatAdvantageText(
  homeValue: unknown,
  awayValue: unknown,
  homeTeam = '主队',
  awayTeam = '客队',
): string {
  const comparison = compareReplayValues(homeValue, awayValue)
  if (comparison === 'home') return `${homeTeam}优势`
  if (comparison === 'away') return `${awayTeam}优势`
  if (comparison === 'tie') return '双方持平'
  return '暂无可比数据'
}

export function replaySourceLabel(source: string | null | undefined, serverLabel?: string | null): string {
  const explicit = serverLabel?.trim()
  if (explicit) return explicit
  const normalized = source?.trim()
  if (!normalized) return '未知来源'
  return SOURCE_LABELS[normalized.toUpperCase()]
    ?? normalized.toLowerCase().replace(/[_-]+/g, ' ')
}
