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
  logoUrl?: string
}

export interface Player {
  id: number
  nbaPlayerId: number | null
  name: string
  teamId: number
  teamName: string
  position: string
  pointsPerGame: number
  reboundsPerGame: number
  assistsPerGame: number
  stealsPerGame: number
  gamesPlayed: number
  minutesPerGame: number
  fieldGoalPct: number
  threePointPct: number
  freeThrowPct: number
  blocksPerGame: number
  turnoversPerGame: number
  efficiency: number
  trueShootingPct: number
  usagePct: number
  jerseyNumber: string
  height: string
  weight: number
  country: string
}

export interface TeamRank {
  teamName: string
  wins: number
  losses: number
  gamesBehind: number
  conference: string
}

export interface DashboardStats {
  teamCount: number
  playerCount: number
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
  nbaGameId: string | null
  category: string
  sourceUrl: string | null
  imageUrl: string | null
  viewCount: number
  favoriteCount: number
  favoritedByMe: boolean
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
  nbaGameId: string | null
}

export interface BoxScorePlayer {
  playerId: number
  playerName: string
  teamName: string
  minutes: string
  points: number
  rebounds: number
  assists: number
  steals: number
  blocks: number
  turnovers: number
  fgMade: number
  fgAttempted: number
  fgPct: number
  threeMade: number
  threeAttempted: number
  threePct: number
  ftMade: number
  ftAttempted: number
  ftPct: number
  plusMinus: number
  starter: boolean
}

export interface BoxScore {
  gameId: string
  homeTeam: string
  awayTeam: string
  homePlayers: BoxScorePlayer[]
  awayPlayers: BoxScorePlayer[]
  quarterScores: QuarterScore[]
}

export interface PlayByPlayEvent {
  period: number
  gameClock: string
  description: string
  homeScore: number
  awayScore: number
  eventType: string
  playerId: number
  playerName: string
}

export interface QuarterScore {
  period: number
  homeScore: number
  awayScore: number
}

export interface PlayerComparison {
  player1: Player
  player2: Player
}

export interface ShotData {
  x: number
  y: number
  made: boolean
  period?: number
}

export interface Trade {
  id: number
  tradeDate: string
  season: string
  team1Name: string
  team2Name: string
  playersInvolved: string
  draftPicksInvolved: string
  details: string
  detailsCn: string
  createTime: string
}

export interface TradeRequest {
  tradeDate: string
  season: string
  team1Name: string
  team2Name: string
  playersInvolved: string
  draftPicksInvolved: string
  details: string
}

export interface DraftPick {
  id: number
  year: number
  round: number
  pickNumber: number
  teamName: string
  playerName: string
  notes: string
  createTime: string
}

export interface DraftPickRequest {
  year: number
  round: number
  pickNumber: number
  teamName: string
  playerName: string
  notes: string
}
