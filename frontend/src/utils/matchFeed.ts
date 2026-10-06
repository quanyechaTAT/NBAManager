import type { MatchRecord } from '@/api/types'

/**
 * 赛事展示状态：把 /match-records 的原始记录整理成前端可复用的展示分类。
 * 首页转播面板与全站赛况条共用同一套判定，避免两处口径不一致。
 */
export type MatchFeedKind = 'live' | 'today' | 'recent' | 'none'

export interface MatchFeedGroup {
  kind: MatchFeedKind
  /** 分组标题：直播中 / 今日赛程 / 最近赛果 */
  label: string
  matches: MatchRecord[]
}

/** 本地时区的 YYYY-MM-DD */
export function toLocalDateKey(value: string | Date | null | undefined): string {
  if (value == null) return ''
  const d = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 是否有可展示的比分：未开赛或缺失都不算 */
export function hasScore(
  match: Pick<MatchRecord, 'status' | 'homeScore' | 'awayScore'> | null | undefined
): boolean {
  if (!match || match.status === 'SCHEDULED') return false
  return (
    typeof match.homeScore === 'number' &&
    typeof match.awayScore === 'number' &&
    !Number.isNaN(match.homeScore) &&
    !Number.isNaN(match.awayScore) &&
    match.homeScore >= 0 &&
    match.awayScore >= 0
  )
}

/**
 * 按状态分类比赛。真实 status 优先，日期只用于区分「今日」与「最近」。
 * 规则：
 * - live：status === 'LIVE'（不依赖日期，直播可能跨天）
 * - today：今天的比赛（未开赛或已完赛）
 * - recent：今天以前的完赛记录，按日期倒序，最多 limit 场
 */
export function classifyMatches(
  matches: MatchRecord[] | null | undefined,
  now: Date = new Date(),
  limit = 12
): MatchFeedGroup[] {
  const list = Array.isArray(matches) ? matches : []
  const todayKey = toLocalDateKey(now)

  const live = list.filter((m) => m.status === 'LIVE')
  const today = list.filter((m) => m.status !== 'LIVE' && toLocalDateKey(m.matchDate) === todayKey)
  const recent = list
    .filter((m) => m.status === 'FINISHED' && toLocalDateKey(m.matchDate) < todayKey)
    .sort((a, b) => (b.matchDate || '').localeCompare(a.matchDate || ''))
    .slice(0, limit)

  const groups: MatchFeedGroup[] = []
  if (live.length) groups.push({ kind: 'live', label: '直播中', matches: live })
  if (today.length) groups.push({ kind: 'today', label: '今日赛程', matches: today })
  if (recent.length) groups.push({ kind: 'recent', label: '最近赛果', matches: recent })
  return groups
}

/**
 * 赛况条的展示列表：优先直播，其次今日，再次最近赛果。
 * 返回顺序与「优先级」一致，便于观众一眼看到正在进行的比赛。
 */
export function buildTickerMatches(
  matches: MatchRecord[] | null | undefined,
  now: Date = new Date(),
  limit = 12
): MatchRecord[] {
  const groups = classifyMatches(matches, now, limit)
  return groups.flatMap((g) => g.matches).slice(0, limit)
}

/**
 * 赛况条的整体标题：依据实际数据来源标注，避免把历史赛果称作「实时赛况」。
 */
export function tickerLabel(matches: MatchRecord[] | null | undefined, now: Date = new Date()): string {
  const groups = classifyMatches(matches, now)
  if (groups.some((g) => g.kind === 'live')) return '直播中'
  if (groups.some((g) => g.kind === 'today')) return '今日赛程'
  if (groups.some((g) => g.kind === 'recent')) return '最近赛果'
  return '赛事'
}

/** 只保留真正直播的比赛，用于橙色脉冲等强调效果 */
export function liveMatches(matches: MatchRecord[] | null | undefined): MatchRecord[] {
  return (Array.isArray(matches) ? matches : []).filter((m) => m.status === 'LIVE')
}
