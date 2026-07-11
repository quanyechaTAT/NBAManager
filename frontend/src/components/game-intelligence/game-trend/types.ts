import type { MomentumSnapshot, TimelineEvent } from '@/api/types'

export type TrendEventKind =
  | 'score'
  | 'three'
  | 'dunk'
  | 'block'
  | 'steal'
  | 'turnover'
  | 'timeout'
  | 'foul'
  | 'substitution'
  | 'run'
  | 'other'

export type TrendSide = 'home' | 'away' | 'tie'
export type TrendDisplayMode = 'lead' | 'win' | 'momentum'

export interface TrendPoint extends MomentumSnapshot {
  signedMargin: number
  event?: TimelineEvent
  index: number
  x: number
  y: number
  side: TrendSide
  isSelected: boolean
  isKeyMoment: boolean
  keyLabel?: string
}

export interface TrendEventMarker {
  event: TimelineEvent
  kind: TrendEventKind
  side: TrendSide
  x: number
  y: number
  color: string
  icon: string
  isSelected: boolean
  isKeyMoment: boolean
  label?: string
}

export interface MomentumRun {
  id: string
  teamName: string
  side: TrendSide
  label: string
  startOrder: number
  endOrder: number
  startX: number
  width: number
  strength: 'normal' | 'strong'
}

export interface QuarterBand {
  period: number
  label: string
  startOrder: number
  endOrder: number
  x: number
  width: number
}

export interface TrendMetrics {
  maxHomeLead: number
  maxAwayLead: number
  homeLeadChanges: number
  tieCount: number
  longestRunLabel: string
  finalFiveTrend: number
  homeWinSignal: number
  awayWinSignal: number
}

export interface TrendModel {
  points: TrendPoint[]
  markers: TrendEventMarker[]
  runs: MomentumRun[]
  quarterBands: QuarterBand[]
  metrics: TrendMetrics
  summaryText: string
  latestScore: {
    home: number
    away: number
  }
  latestClockLabel: string
  yMax: number
}
