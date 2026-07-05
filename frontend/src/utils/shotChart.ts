import type { ShotData } from '@/api/types'

/** 筛选投篮 */
export function filterShots(
  shots: ShotData[],
  opts: { team?: 'all' | 'home' | 'away'; result?: 'all' | 'made' | 'missed'; period?: number | 'all'; homeTeamId?: number | null; awayTeamId?: number | null; homeTeamName?: string; awayTeamName?: string }
): ShotData[] {
  return shots.filter(s => {
    if (opts.team === 'home') {
      if (opts.homeTeamId != null && s.teamId != null) { if (s.teamId !== opts.homeTeamId) return false }
      else if (opts.homeTeamName) { if (s.teamName !== opts.homeTeamName) return false }
    }
    if (opts.team === 'away') {
      if (opts.awayTeamId != null && s.teamId != null) { if (s.teamId !== opts.awayTeamId) return false }
      else if (opts.awayTeamName) { if (s.teamName !== opts.awayTeamName) return false }
    }
    if (opts.result === 'made' && !s.made) return false
    if (opts.result === 'missed' && s.made) return false
    if (opts.period !== 'all' && opts.period != null && s.period !== opts.period) return false
    return true
  })
}

/** 判断是否三分 */
export function isThreePointShot(shot: ShotData): boolean {
  if (shot.points === 3) return true
  if (shot.shotType?.includes('3PT')) return true
  return false
}

/** 计算统计摘要 */
export function calculateShotSummary(shots: ShotData[]) {
  const total = shots.length
  const made = shots.filter(s => s.made).length
  const threeAttempted = shots.filter(isThreePointShot).length
  const threeMade = shots.filter(s => s.made && isThreePointShot(s)).length
  return {
    total,
    made,
    fgPct: total > 0 ? ((made / total) * 100).toFixed(1) : '0.0',
    threeAttempted,
    threeMade,
    threePct: threeAttempted > 0 ? ((threeMade / threeAttempted) * 100).toFixed(1) : '0.0'
  }
}

/** 区域分类 */
export type ShotZone = 'restricted' | 'paint' | 'mid-range' | 'long-two' | 'three-left' | 'three-right' | 'three-top'

export function classifyShotZone(shot: ShotData): ShotZone {
  const d = shot.distance ?? 0
  if (isThreePointShot(shot)) {
    const x = shot.x ?? 25
    if (x < 17) return 'three-left'
    if (x > 33) return 'three-right'
    return 'three-top'
  }
  if (d <= 3) return 'restricted'
  if (d <= 10) return 'paint'
  if (d <= 16) return 'mid-range'
  return 'long-two'
}

export const ZONE_LABELS: Record<ShotZone, string> = {
  'restricted': '篮下 0-3ft',
  'paint': '近距离 3-10ft',
  'mid-range': '中距离 10-16ft',
  'long-two': '长两分 16ft+',
  'three-left': '三分 左侧',
  'three-right': '三分 右侧',
  'three-top': '三分 正面'
}

export const ZONE_ORDER: ShotZone[] = ['restricted', 'paint', 'mid-range', 'long-two', 'three-left', 'three-right', 'three-top']

/** 区域命中率统计 */
export interface ZoneStat {
  zone: ShotZone
  label: string
  attempted: number
  made: number
  pct: string
  pctNum: number
}

export function calculateZoneStats(shots: ShotData[]): ZoneStat[] {
  const map = new Map<ShotZone, { attempted: number; made: number }>()
  for (const z of ZONE_ORDER) map.set(z, { attempted: 0, made: 0 })
  for (const s of shots) {
    const z = classifyShotZone(s)
    const entry = map.get(z)!
    entry.attempted++
    if (s.made) entry.made++
  }
  return ZONE_ORDER.map(z => {
    const e = map.get(z)!
    const pctNum = e.attempted > 0 ? (e.made / e.attempted) * 100 : 0
    return { zone: z, label: ZONE_LABELS[z], attempted: e.attempted, made: e.made, pct: pctNum.toFixed(1), pctNum }
  })
}
