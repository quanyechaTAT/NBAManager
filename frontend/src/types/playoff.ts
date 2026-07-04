export interface Team {
  id: string
  name: string
  logo: string
  seed?: number
}

export interface Matchup {
  id: string | number
  round: number
  team1Name: string
  team2Name: string
  team1Wins?: number
  team2Wins?: number
  winnerName?: string
}

export interface PlayoffBracket {
  season?: string
  eastern: Matchup[]
  western: Matchup[]
  finals?: {
    team1Name?: string
    team2Name?: string
    team1Wins?: number
    team2Wins?: number
    winnerName?: string
    // Legacy aliases (used by some views)
    t1?: string
    t2?: string
    w1?: number
    w2?: number
    winner?: string
  }
}

export interface RoundData {
  1: Matchup[]
  2: Matchup[]
  3: Matchup[]
}
