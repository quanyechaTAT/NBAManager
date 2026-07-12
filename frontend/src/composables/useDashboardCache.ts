import { ref, onMounted, onUnmounted } from 'vue'
import { fetchDashboardOverview } from '@/api/dashboard'
import type { DashboardStats, TeamRank, GameNews, Post } from '@/api/types'

const DASHBOARD_KEY = 'nba_dashboard_cache_v2'
const TODAY_KEY = 'nba_today_games_cache'
const DASHBOARD_TTL = 5 * 60 * 1000
const TODAY_TTL = 60 * 1000

interface DashboardCache {
  stats: DashboardStats | null
  westStandings: TeamRank[]
  eastStandings: TeamRank[]
  topScorers: { id: number; playerName: string; ppg: number; teamName: string; nbaPlayerId: number | null }[]
  hotPosts: Post[]
  docCount: number
  timestamp: number
}

interface TodayCache {
  games: GameNews[]
  dateKey: string // Asia/Shanghai yyyy-MM-dd
  timestamp: number
}

function getDateKey(): string {
  return new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' })
}

function loadDashboardCache(): DashboardCache | null {
  try {
    const raw = sessionStorage.getItem(DASHBOARD_KEY)
    if (!raw) return null
    const cached = JSON.parse(raw) as DashboardCache
    if (Date.now() - cached.timestamp > DASHBOARD_TTL) return null
    return cached
  } catch { return null }
}

function saveDashboardCache(data: Omit<DashboardCache, 'timestamp'>) {
  try {
    sessionStorage.setItem(DASHBOARD_KEY, JSON.stringify({ ...data, timestamp: Date.now() }))
  } catch { /* ignore */ }
}

function loadTodayCache(): TodayCache | null {
  try {
    const raw = sessionStorage.getItem(TODAY_KEY)
    if (!raw) return null
    const cached = JSON.parse(raw) as TodayCache
    const today = getDateKey()
    if (cached.dateKey !== today) return null
    if (Date.now() - cached.timestamp > TODAY_TTL) return null
    return cached
  } catch { return null }
}

function saveTodayCache(games: GameNews[]) {
  try {
    sessionStorage.setItem(TODAY_KEY, JSON.stringify({
      games,
      dateKey: getDateKey(),
      timestamp: Date.now(),
    }))
  } catch { /* ignore */ }
}

export function useDashboardCache() {
  const stats = ref<DashboardStats | null>(null)
  const todayGames = ref<GameNews[]>([])
  const westStandings = ref<TeamRank[]>([])
  const eastStandings = ref<TeamRank[]>([])
  const topScorers = ref<{ id: number; playerName: string; ppg: number; teamName: string; nbaPlayerId: number | null }[]>([])
  const hotPosts = ref<Post[]>([])
  const docCount = ref(0)
  const loading = ref(true)

  let todayFetchInFlight = false

  async function fetchTodayGames() {
    if (todayFetchInFlight) return
    todayFetchInFlight = true
    try {
      const { data } = await import('@/api/news').then(m => m.fetchTodayNews())
      const games = data ?? []
      todayGames.value = games
      saveTodayCache(games)
    } catch {
      // Keep existing todayGames on failure
    } finally {
      todayFetchInFlight = false
    }
  }

  async function fetchDashboard() {
    const { data } = await fetchDashboardOverview()
    stats.value = data.stats ?? null
    westStandings.value = data.westStandings ?? []
    eastStandings.value = data.eastStandings ?? []
    topScorers.value = data.topScorers ?? data.stats?.topScorers ?? []
    hotPosts.value = data.hotPosts ?? []
    docCount.value = data.docCount || 0

    saveDashboardCache({
      stats: stats.value,
      westStandings: westStandings.value,
      eastStandings: eastStandings.value,
      topScorers: topScorers.value,
      hotPosts: hotPosts.value,
      docCount: docCount.value,
    })
  }

  async function fetchData() {
    // Start both in parallel — todayGames is independent
    fetchTodayGames()
    await fetchDashboard()
  }

  // Visibility refresh: when tab becomes visible again
  function onVisibilityChange() {
    if (document.visibilityState === 'visible') {
      const cached = loadTodayCache()
      if (!cached) {
        fetchTodayGames()
      }
    }
  }

  onMounted(async () => {
    // Load todayGames from cache immediately
    const todayCached = loadTodayCache()
    if (todayCached) {
      todayGames.value = todayCached.games
    }

    // Load dashboard from cache immediately
    const dashCached = loadDashboardCache()
    if (dashCached) {
      stats.value = dashCached.stats
      westStandings.value = dashCached.westStandings
      eastStandings.value = dashCached.eastStandings
      topScorers.value = dashCached.topScorers
      hotPosts.value = dashCached.hotPosts
      docCount.value = dashCached.docCount
      loading.value = false
      // Background refresh
      fetchData().catch(() => {})
    } else {
      loading.value = true
      try {
        await fetchData()
      } catch {
        // Keep empty on failure
      } finally {
        loading.value = false
      }
    }

    document.addEventListener('visibilitychange', onVisibilityChange)
  })

  onUnmounted(() => {
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })

  return {
    stats,
    todayGames,
    westStandings,
    eastStandings,
    topScorers,
    hotPosts,
    docCount,
    loading,
    refreshTodayGames: fetchTodayGames,
  }
}
