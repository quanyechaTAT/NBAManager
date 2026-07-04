export type ScoreValue = number | null | undefined

export function formatGameScoreValue(score: ScoreValue): string {
  return score == null ? '-' : String(score)
}
