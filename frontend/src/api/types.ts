export type Role = 'ADMIN' | 'USER'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Team {
  id: number
  name: string
  city: string
  conference: string
  wins: number
  losses: number
}

export interface Player {
  id: number
  name: string
  teamId: number
  teamName: string
  position: string
  pointsPerGame: number
  reboundsPerGame: number
  assistsPerGame: number
  stealsPerGame: number
}

export interface TeamRank {
  teamName: string
  wins: number
  losses: number
  gamesBehind: number
  conference: string
}

export interface DashboardStats {
  teamWinRows: { name: string; wins: number; losses: number }[]
  topScorers: { playerName: string; ppg: number; teamName: string }[]
}

export interface GameNews {
  id: number
  title: string
  summary: string
  content: string
  homeTeam: string
  awayTeam: string
  homeScore: number | null
  awayScore: number | null
  gameStartTime: string
  gameEndTime: string
  status: 'SCHEDULED' | 'LIVE' | 'FINISHED'
  createTime: string
}

export interface MatchRecord {
  id: number
  homeTeam: string
  awayTeam: string
  homeScore: number
  awayScore: number
  matchDate: string
  season: string
  status: 'SCHEDULED' | 'LIVE' | 'FINISHED'
}
