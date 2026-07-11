import type { MomentumSnapshot, TimelineEvent } from '@/api/types'
import type {
  MomentumRun,
  QuarterBand,
  TrendEventKind,
  TrendEventMarker,
  TrendMetrics,
  TrendModel,
  TrendPoint,
  TrendSide,
} from './types'

const HOME_COLOR = 'var(--trend-home)'
const AWAY_COLOR = 'var(--trend-away)'

const EVENT_META: Record<TrendEventKind, { color: string; icon: string; priority: number }> = {
  score: { color: 'var(--accent)', icon: '●', priority: 2 },
  three: { color: 'var(--success)', icon: '3', priority: 8 },
  dunk: { color: 'var(--cyan)', icon: '▾', priority: 7 },
  block: { color: 'var(--purple)', icon: '✦', priority: 7 },
  steal: { color: 'var(--green)', icon: '◇', priority: 7 },
  turnover: { color: 'var(--danger)', icon: '!', priority: 7 },
  timeout: { color: 'var(--warning)', icon: 'T', priority: 6 },
  foul: { color: 'var(--orange)', icon: 'F', priority: 4 },
  substitution: { color: 'var(--cyan)', icon: '↔', priority: 3 },
  run: { color: 'var(--warning)', icon: 'R', priority: 9 },
  other: { color: 'var(--text-muted)', icon: '•', priority: 1 },
}

export function buildTrendModel(args: {
  data: MomentumSnapshot[]
  events: TimelineEvent[]
  homeTeam: string
  awayTeam: string
  selectedEvent?: TimelineEvent | null
  width: number
  height: number
  padding: { top: number; right: number; bottom: number; left: number }
}): TrendModel {
  const sortedSnapshots = [...args.data].sort((a, b) => a.eventOrder - b.eventOrder)
  const sortedEvents = [...args.events].sort((a, b) => a.eventOrder - b.eventOrder)
  const eventByOrder = new Map(sortedEvents.map((event) => [event.eventOrder, event]))
  const allOrders = [...sortedSnapshots.map((item) => item.eventOrder), ...sortedEvents.map((item) => item.eventOrder)]
  const minOrder = Math.min(...allOrders, 0)
  const maxOrder = Math.max(...allOrders, 1)
  const yMax = calculateYMax(sortedSnapshots)
  const plot = {
    width: Math.max(1, args.width - args.padding.left - args.padding.right),
    height: Math.max(1, args.height - args.padding.top - args.padding.bottom),
  }

  let prevHome = 0
  let prevAway = 0
  const points = sortedSnapshots.map<TrendPoint>((snapshot, index) => {
    // Carry forward scores when both are 0 (API may omit scores for non-scoring events)
    const homeScore = (snapshot.homeScore === 0 && snapshot.awayScore === 0 && index > 0) ? prevHome : snapshot.homeScore
    const awayScore = (snapshot.homeScore === 0 && snapshot.awayScore === 0 && index > 0) ? prevAway : snapshot.awayScore
    prevHome = homeScore
    prevAway = awayScore

    const signedMargin = homeScore - awayScore
    const x = scaleOrder(snapshot.eventOrder, minOrder, maxOrder, args.padding.left, plot.width)
    const y = scaleMargin(signedMargin, yMax, args.padding.top, plot.height)
    const event = eventByOrder.get(snapshot.eventOrder)

    return {
      ...snapshot,
      homeScore,
      awayScore,
      signedMargin,
      event,
      index,
      x,
      y,
      side: sideFromMargin(signedMargin),
      isSelected: args.selectedEvent?.eventOrder === snapshot.eventOrder,
      isKeyMoment: false,
    }
  })

  const keyOrders = detectKeyMomentOrders(points, sortedEvents)
  points.forEach((point) => {
    const label = keyOrders.get(point.eventOrder)
    if (label) {
      point.isKeyMoment = true
      point.keyLabel = label
    }
  })

  const markers = buildMarkers({
    events: sortedEvents,
    points,
    minOrder,
    maxOrder,
    padding: args.padding,
    plot,
    selectedEvent: args.selectedEvent,
  })

  const quarterBands = buildQuarterBands(points, minOrder, maxOrder, args.padding.left, plot.width)
  const runs = buildMomentumRuns(sortedEvents, minOrder, maxOrder, args.padding.left, plot.width, args.homeTeam, args.awayTeam)
  const latest = sortedSnapshots.at(-1)
  const metrics = buildMetrics(points, sortedEvents, args.homeTeam, args.awayTeam)

  return {
    points,
    markers,
    runs,
    quarterBands,
    metrics,
    summaryText: buildSummaryText(points, metrics, args.homeTeam, args.awayTeam),
    latestScore: {
      home: latest?.homeScore ?? 0,
      away: latest?.awayScore ?? 0,
    },
    latestClockLabel: latest ? `${periodLabel(latest.period)} ${latest.gameClock || '0:00'}` : '暂无数据',
    yMax,
  }
}

export function scoreMargin(item: Pick<MomentumSnapshot | TimelineEvent, 'homeScore' | 'awayScore'>): number {
  return Number(item.homeScore ?? 0) - Number(item.awayScore ?? 0)
}

export function sideFromMargin(margin: number): TrendSide {
  if (margin > 0) return 'home'
  if (margin < 0) return 'away'
  return 'tie'
}

export function periodLabel(period: number): string {
  return period <= 4 ? `Q${period}` : `OT${period - 4}`
}

export function eventKind(event?: TimelineEvent): TrendEventKind {
  if (!event) return 'other'
  const text = `${event.eventType || ''} ${event.actionType || ''} ${event.description || ''} ${event.descriptionCn || ''}`.toLowerCase()

  if (text.includes('3pt') || text.includes('three') || text.includes('三分')) return 'three'
  if (text.includes('dunk') || text.includes('扣篮')) return 'dunk'
  if (text.includes('block') || text.includes('盖帽')) return 'block'
  if (text.includes('steal') || text.includes('抢断')) return 'steal'
  if (text.includes('turnover') || text.includes('失误')) return 'turnover'
  if (text.includes('timeout') || text.includes('暂停')) return 'timeout'
  if (text.includes('foul') || text.includes('犯规')) return 'foul'
  if (text.includes('substitution') || text.includes('sub') || text.includes('换人')) return 'substitution'
  if (event.isScoringPlay || text.includes('made') || text.includes('命中')) return 'score'
  return 'other'
}

export function eventColor(kind: TrendEventKind): string {
  return EVENT_META[kind].color
}

export function eventIcon(kind: TrendEventKind): string {
  return EVENT_META[kind].icon
}

export function eventPriority(kind: TrendEventKind): number {
  return EVENT_META[kind].priority
}

export function displayDescription(event?: TimelineEvent): string {
  if (!event) return '该节点暂无事件描述'
  return event.descriptionCn || event.description || event.descriptionEn || event.actionType || event.eventType || '该节点暂无事件描述'
}

export function leaderText(homeTeam: string, awayTeam: string, margin: number): string {
  if (margin === 0) return '双方打平'
  const team = margin > 0 ? homeTeam || '主队' : awayTeam || '客队'
  return `${team}领先${Math.abs(margin)}分`
}

export function clockToSeconds(clock: string): number {
  const [minutes, seconds] = String(clock || '0:00')
    .split(':')
    .map((part) => Number(part))
  return (Number.isFinite(minutes) ? minutes : 0) * 60 + (Number.isFinite(seconds) ? seconds : 0)
}

export function inferPoints(event: TimelineEvent): number {
  const text = `${event.actionType || ''} ${event.description || ''} ${event.descriptionCn || ''}`.toLowerCase()
  if (text.includes('3pt') || text.includes('three') || text.includes('三分')) return 3
  if (text.includes('free throw') || text.includes('罚球')) return 1
  return event.isScoringPlay ? 2 : 0
}

export function findNearestPoint(points: TrendPoint[], eventOrder: number): TrendPoint | undefined {
  return points.reduce<TrendPoint | undefined>((best, point) => {
    if (!best) return point
    return Math.abs(point.eventOrder - eventOrder) < Math.abs(best.eventOrder - eventOrder) ? point : best
  }, undefined)
}

function calculateYMax(points: MomentumSnapshot[]): number {
  const maxAbs = Math.max(20, ...points.map((item) => Math.abs(scoreMargin(item))))
  return Math.min(40, Math.max(20, Math.ceil(maxAbs / 5) * 5))
}

function scaleOrder(order: number, minOrder: number, maxOrder: number, left: number, width: number): number {
  return left + ((order - minOrder) / Math.max(1, maxOrder - minOrder)) * width
}

function scaleMargin(margin: number, yMax: number, top: number, height: number): number {
  return top + ((yMax - margin) / (yMax * 2)) * height
}

function buildMarkers(args: {
  events: TimelineEvent[]
  points: TrendPoint[]
  minOrder: number
  maxOrder: number
  padding: { top: number; right: number; bottom: number; left: number }
  plot: { width: number; height: number }
  selectedEvent?: TimelineEvent | null
}): TrendEventMarker[] {
  const importantEvents = args.events
    .map((event) => {
      const kind = eventKind(event)
      const nearest = findNearestPoint(args.points, event.eventOrder)
      const priority = eventPriority(kind) + (event.isHighlightCandidate ? 5 : 0) + (event.isScoringPlay ? 1 : 0)
      return { event, kind, nearest, priority }
    })
    .filter((item) => item.nearest && item.priority >= 5)
    .sort((a, b) => b.priority - a.priority || a.event.eventOrder - b.event.eventOrder)

  const selectedOrder = args.selectedEvent?.eventOrder
  const selected = selectedOrder
    ? importantEvents.find((item) => item.event.eventOrder === selectedOrder) ??
      args.events
        .filter((event) => event.eventOrder === selectedOrder)
        .map((event) => ({ event, kind: eventKind(event), nearest: findNearestPoint(args.points, event.eventOrder), priority: 100 }))
        .find((item) => item.nearest)
    : undefined

  const chosen = uniqueByOrder([...(selected ? [selected] : []), ...importantEvents]).slice(0, 22)

  return chosen
    .sort((a, b) => a.event.eventOrder - b.event.eventOrder)
    .map((item) => {
      const point = item.nearest as TrendPoint
      const side = sideFromMargin(scoreMargin(item.event))
      return {
        event: item.event,
        kind: item.kind,
        side,
        x: scaleOrder(item.event.eventOrder, args.minOrder, args.maxOrder, args.padding.left, args.plot.width),
        y: point.y,
        color: eventColor(item.kind),
        icon: eventIcon(item.kind),
        isSelected: selectedOrder === item.event.eventOrder,
        isKeyMoment: point.isKeyMoment || item.priority >= 8,
        label: point.keyLabel,
      }
    })
}

function uniqueByOrder<T extends { event: TimelineEvent }>(items: T[]): T[] {
  const seen = new Set<number>()
  return items.filter((item) => {
    if (seen.has(item.event.eventOrder)) return false
    seen.add(item.event.eventOrder)
    return true
  })
}

function detectKeyMomentOrders(points: TrendPoint[], events: TimelineEvent[]): Map<number, string> {
  const labels = new Map<number, string>()

  points.forEach((point, index) => {
    const prev = points[index - 1]
    if (!prev) return

    const swing = Math.abs(point.signedMargin - prev.signedMargin)
    if (swing >= 8) labels.set(point.eventOrder, `${swing}分摆动`)
    if (point.signedMargin === 0 && prev.signedMargin !== 0) labels.set(point.eventOrder, '追平')
    if (Math.sign(point.signedMargin) !== Math.sign(prev.signedMargin) && point.signedMargin !== 0 && prev.signedMargin !== 0) {
      labels.set(point.eventOrder, '反超')
    }
  })

  const finalPeriod = Math.max(4, ...points.map((point) => point.period))
  events.forEach((event) => {
    if (event.period === finalPeriod && clockToSeconds(event.gameClock) <= 120 && event.isScoringPlay) {
      labels.set(event.eventOrder, '最后2分钟关键球')
    }
  })

  return new Map([...labels.entries()].slice(0, 6))
}

function buildMomentumRuns(events: TimelineEvent[], minOrder: number, maxOrder: number, left: number, width: number, homeTeam = '主队', awayTeam = '客队'): MomentumRun[] {
  const scoringEvents = events
    .filter((event) => event.isScoringPlay)
    .sort((a, b) => a.eventOrder - b.eventOrder)

  // Infer team identity from score changes (API often omits teamName/teamId)
  const withTeam = scoringEvents.map((event, i) => {
    const prev = i > 0 ? scoringEvents[i - 1] : null
    const team = event.teamName || inferScoringTeam(event, prev)
    return { event, team, points: inferPoints(event) }
  })

  const runs: MomentumRun[] = []
  let runTeam = ''
  let runStart: TimelineEvent | null = null
  let runEnd: TimelineEvent | null = null
  let runPoints = 0
  let opponentPoints = 0

  function pushRun() {
    if (!runStart || !runEnd || runPoints < 8) return
    const startX = scaleOrder(runStart.eventOrder, minOrder, maxOrder, left, width)
    const endX = scaleOrder(runEnd.eventOrder, minOrder, maxOrder, left, width)
    const side = sideFromMargin(scoreMargin(runEnd))
    runs.push({
      id: `${runStart.eventOrder}-${runEnd.eventOrder}`,
      teamName: runTeam === 'home' ? homeTeam : runTeam === 'away' ? awayTeam : runTeam,
      side,
      label: `${runPoints}-${opponentPoints}`,
      startOrder: runStart.eventOrder,
      endOrder: runEnd.eventOrder,
      startX,
      width: Math.max(42, endX - startX),
      strength: runPoints >= 10 && opponentPoints <= 2 ? 'strong' : 'normal',
    })
  }

  withTeam.forEach(({ event, team, points }, index) => {
    if (!team) return

    if (!runTeam) {
      runTeam = team
      runStart = event
      runEnd = event
      runPoints = points
      opponentPoints = 0
    } else if (team === runTeam) {
      runEnd = event
      runPoints += points
    } else if (opponentPoints + points <= 2) {
      runEnd = event
      opponentPoints += points
    } else {
      pushRun()
      runTeam = team
      runStart = event
      runEnd = event
      runPoints = points
      opponentPoints = 0
    }

    if (index === withTeam.length - 1) pushRun()
  })

  return runs
    .sort((a, b) => (b.strength === 'strong' ? 1 : 0) - (a.strength === 'strong' ? 1 : 0) || b.width - a.width)
    .slice(0, 6)
}

/** Infer which team scored by checking which team's score increased */
function inferScoringTeam(event: TimelineEvent, prev: TimelineEvent | null): string {
  if (!prev) return 'home'
  const homeDelta = event.homeScore - prev.homeScore
  const awayDelta = event.awayScore - prev.awayScore
  if (homeDelta > 0) return 'home'
  if (awayDelta > 0) return 'away'
  return 'home' // fallback
}

function buildQuarterBands(points: TrendPoint[], minOrder: number, maxOrder: number, left: number, width: number): QuarterBand[] {
  const periods = new Map<number, TrendPoint[]>()
  points.forEach((point) => {
    const list = periods.get(point.period) ?? []
    list.push(point)
    periods.set(point.period, list)
  })

  return [...periods.entries()]
    .sort(([a], [b]) => a - b)
    .map(([period, periodPoints]) => {
      const startOrder = Math.min(...periodPoints.map((point) => point.eventOrder))
      const endOrder = Math.max(...periodPoints.map((point) => point.eventOrder))
      const x1 = scaleOrder(startOrder, minOrder, maxOrder, left, width)
      const x2 = scaleOrder(endOrder, minOrder, maxOrder, left, width)
      return {
        period,
        label: periodLabel(period),
        startOrder,
        endOrder,
        x: x1,
        width: Math.max(28, x2 - x1),
      }
    })
}

function buildMetrics(points: TrendPoint[], events: TimelineEvent[], homeTeam: string, awayTeam: string): TrendMetrics {
  const maxHomeLead = Math.max(0, ...points.map((point) => point.signedMargin))
  const maxAwayLead = Math.max(0, ...points.map((point) => -point.signedMargin))
  let homeLeadChanges = 0
  let tieCount = 0

  points.forEach((point, index) => {
    if (point.signedMargin === 0) tieCount += 1
    const prev = points[index - 1]
    if (prev && Math.sign(point.signedMargin) !== Math.sign(prev.signedMargin) && point.signedMargin !== 0 && prev.signedMargin !== 0) {
      homeLeadChanges += 1
    }
  })

  const longestRun = detectLongestRun(events)
  const lastFive = points.filter((point) => point.period >= Math.max(4, points.at(-1)?.period ?? 4) && clockToSeconds(point.gameClock) <= 300)
  const finalFiveTrend = lastFive.length >= 2 ? lastFive.at(-1)!.signedMargin - lastFive[0].signedMargin : 0
  const finalMargin = points.at(-1)?.signedMargin ?? 0
  const homeWinSignal = Math.max(5, Math.min(95, 50 + finalMargin * 4))

  return {
    maxHomeLead,
    maxAwayLead,
    homeLeadChanges,
    tieCount,
    longestRunLabel: longestRun || `${finalMargin >= 0 ? homeTeam || '主队' : awayTeam || '客队'} ${Math.abs(finalMargin)}分优势`,
    finalFiveTrend,
    homeWinSignal,
    awayWinSignal: 100 - homeWinSignal,
  }
}

function detectLongestRun(events: TimelineEvent[]): string {
  let bestTeam = ''
  let bestPoints = 0
  let bestAgainst = 0
  let currentTeam = ''
  let currentPoints = 0
  let currentAgainst = 0

  events
    .filter((event) => event.isScoringPlay)
    .sort((a, b) => a.eventOrder - b.eventOrder)
    .forEach((event) => {
      const points = inferPoints(event)
      if (event.teamName && event.teamName === currentTeam) {
        currentPoints += points
      } else if (currentAgainst + points <= 2 && currentTeam) {
        currentAgainst += points
      } else {
        currentTeam = event.teamName || '球队'
        currentPoints = points
        currentAgainst = 0
      }

      if (currentPoints - currentAgainst > bestPoints - bestAgainst) {
        bestTeam = currentTeam
        bestPoints = currentPoints
        bestAgainst = currentAgainst
      }
    })

  return bestPoints >= 8 ? `${bestTeam} ${bestPoints}-${bestAgainst} Run` : ''
}

function buildSummaryText(points: TrendPoint[], metrics: TrendMetrics, homeTeam: string, awayTeam: string): string {
  const last = points.at(-1)
  if (!last) return '暂无足够的比赛走势数据。'

  const finalTeam = last.signedMargin >= 0 ? homeTeam || '主队' : awayTeam || '客队'
  const finalMargin = Math.abs(last.signedMargin)
  const finalFiveTeam = metrics.finalFiveTrend >= 0 ? homeTeam || '主队' : awayTeam || '客队'
  const finalFiveAbs = Math.abs(metrics.finalFiveTrend)

  if (metrics.finalFiveTrend !== 0) {
    return `${periodLabel(last.period)}最后5分钟，${finalFiveTeam}净胜${finalFiveAbs}分，${finalTeam}最终以${finalMargin}分优势结束比赛。`
  }

  const maxLead = last.signedMargin >= 0 ? metrics.maxHomeLead : metrics.maxAwayLead
  return `${finalTeam}在全场走势中保持${finalMargin}分优势，最大领先分差达到${maxLead}分。`
}

export const trendColors = {
  home: HOME_COLOR,
  away: AWAY_COLOR,
}
