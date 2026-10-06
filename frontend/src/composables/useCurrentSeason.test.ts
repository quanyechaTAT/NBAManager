import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useCurrentSeason } from './useCurrentSeason'

vi.mock('@/api/historical', () => ({
  getAvailableSeasons: vi.fn(),
}))

import { getAvailableSeasons } from '@/api/historical'

describe('useCurrentSeason composable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useCurrentSeason().resetSeasonForTesting()
  })

  it('provides currentSeason reactive ref with default 2025-26', () => {
    const { currentSeason } = useCurrentSeason()
    expect(currentSeason.value).toBe('2025-26')
  })

  it('setSeason updates the season value', () => {
    const { currentSeason, setSeason } = useCurrentSeason()
    setSeason('2026-27')
    expect(currentSeason.value).toBe('2026-27')
  })

  it('loadSeason fetches available seasons and updates currentSeason', async () => {
    vi.mocked(getAvailableSeasons).mockResolvedValueOnce({
      current: '2025-26',
      available: ['2025-26', '2024-25'],
    })

    const { currentSeason, loadSeason } = useCurrentSeason()
    const season = await loadSeason()
    expect(getAvailableSeasons).toHaveBeenCalledTimes(1)
    expect(season).toBe('2025-26')
    expect(currentSeason.value).toBe('2025-26')
  })

  it('loadSeason gracefully handles API errors and keeps fallback 2025-26', async () => {
    vi.mocked(getAvailableSeasons).mockRejectedValueOnce(new Error('Network error'))

    const { currentSeason, loadSeason } = useCurrentSeason()
    const season = await loadSeason()
    expect(season).toBe('2025-26')
    expect(currentSeason.value).toBe('2025-26')
  })
})
