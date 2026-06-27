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
  homeRecord?: string
  awayRecord?: string
  pointsPerGame?: number
  oppPointsPerGame?: number
  netRating?: number
  streak?: string
}

export interface DashboardStats {
  teamCount: number
  playerCount: number
  teamWinRows: { name: string; wins: number; losses: number }[]
  topScorers: { id: number; playerName: string; ppg: number; teamName: string; nbaPlayerId: number | null }[]
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
  teamNameEn: string
  playerName: string
  playerNameEn: string
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

/**
 * 仪表盘指标项
 */
export interface MetricItem {
  /** 指标标题 */
  title: string
  /** 指标值 */
  value: number | string
  /** 指标单位（可选） */
  unit?: string
  /** 趋势变化（可选，正数为上升，负数为下降） */
  trend?: number
  /** 图标类型（可选） */
  icon?: string
}

/**
 * 排行榜项目
 */
export interface RankItem {
  /** 排名序号 */
  rank: number
  /** 显示名称 */
  name: string
  /** 关联团队名称 */
  teamName?: string
  /** 统计值 */
  value: number | string
  /** 副标题/描述 */
  subtitle?: string
  /** 额外数据字段 */
  extra?: Record<string, unknown>
}

/**
 * 排行榜列定义
 */
export interface RankColumn {
  /** 字段名 */
  key: string
  /** 列标题 */
  title: string
  /** 列宽（可选） */
  width?: string | number
  /** 对齐方式（可选） */
  align?: 'left' | 'center' | 'right'
  /** 自定义渲染函数（可选） */
  render?: (item: RankItem) => string
}

/**
 * 关联实体
 */
export interface RelatedEntity {
  /** 实体ID */
  id: number
  /** 实体类型 */
  type: 'team' | 'player' | 'game' | 'news'
  /** 实体名称 */
  name: string
  /** 实体描述 */
  description?: string
  /** 实体图标/头像URL */
  imageUrl?: string
}

/**
 * 比赛记录（用于实时比分滚动条）
 */
export interface LiveMatch {
  /** 比赛ID */
  id: number
  /** 主队名称 */
  homeTeam: string
  /** 客队名称 */
  awayTeam: string
  /** 主队得分 */
  homeScore: number
  /** 客队得分 */
  awayScore: number
  /** 比赛状态 */
  status: 'SCHEDULED' | 'LIVE' | 'FINISHED'
  /** 比赛时间/状态描述 */
  timeStatus?: string
}
