import { describe, expect, it } from 'vitest'
import { formatGameScoreValue } from './gameScore'

describe('formatGameScoreValue', () => {
  it('keeps real zero scores and uses a placeholder only for missing values', () => {
    expect(formatGameScoreValue(0)).toBe('0')
    expect(formatGameScoreValue(123)).toBe('123')
    expect(formatGameScoreValue(null)).toBe('-')
    expect(formatGameScoreValue(undefined)).toBe('-')
  })
})
