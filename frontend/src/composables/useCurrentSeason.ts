import { ref } from 'vue'
import { getAvailableSeasons } from '@/api/historical'

// 全局单例当前赛季状态，默认系统当前 2025-26 赛季
const currentSeason = ref('2025-26')
let isLoaded = false
let pendingPromise: Promise<string> | null = null

export function useCurrentSeason() {
  async function loadSeason(force = false): Promise<string> {
    if (isLoaded && !force) return currentSeason.value
    if (pendingPromise) return pendingPromise

    pendingPromise = (async () => {
      try {
        const data = await getAvailableSeasons()
        if (data && typeof data.current === 'string' && data.current.trim()) {
          currentSeason.value = data.current.trim()
        }
        isLoaded = true
      } catch {
        // 请求失败时保持默认系统赛季 '2025-26'
      } finally {
        pendingPromise = null
      }
      return currentSeason.value
    })()

    return pendingPromise
  }

  function setSeason(season: string) {
    if (season && typeof season === 'string' && season.trim()) {
      currentSeason.value = season.trim()
    }
  }

  function resetSeasonForTesting() {
    currentSeason.value = '2025-26'
    isLoaded = false
    pendingPromise = null
  }

  return {
    currentSeason,
    loadSeason,
    setSeason,
    resetSeasonForTesting,
  }
}
