import { ref, onMounted } from 'vue'
import { fetchDashboardOverview } from '@/api/dashboard'
import type { DashboardStats, TeamRank, GameNews, Post } from '@/api/types'

const CACHE_KEY = 'nba_dashboard_cache_v2_summer_games'
const CACHE_TTL = 5 * 60 * 1000

interface DashboardCache {
  stats: DashboardStats | null
  todayGames: GameNews[]
  westStandings: TeamRank[]
  eastStandings: TeamRank[]
  topScorers: { id: number; playerName: string; ppg: number; teamName: string; nbaPlayerId: number | null }[]
  hotPosts: Post[]
  docCount: number
  timestamp: number
}

function loadCache(): DashboardCache | null {
  try {
    const raw = sessionStorage.getItem(CACHE_KEY)
    if (!raw) return null
    const cached = JSON.parse(raw) as DashboardCache
    if (Date.now() - cached.timestamp > CACHE_TTL) {
      sessionStorage.removeItem(CACHE_KEY)
      return null
    }
    return cached
  } catch {
    return null
  }
}

function saveCache(data: Omit<DashboardCache, 'timestamp'>) {
  try {
    sessionStorage.setItem(CACHE_KEY, JSON.stringify({ ...data, timestamp: Date.now() }))
  } catch {
    // Ignore storage quota/private mode failures.
  }
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

  function applyCache(cached: Omit<DashboardCache, 'timestamp'>) {
    stats.value = cached.stats
    todayGames.value = cached.todayGames
    westStandings.value = cached.westStandings
    eastStandings.value = cached.eastStandings
    topScorers.value = cached.topScorers
    hotPosts.value = cached.hotPosts
    docCount.value = cached.docCount
  }

  async function fetchTodayGames() {
    try {
      const { data } = await import('@/api/news').then(m => m.fetchTodayNews())
      todayGames.value = data ?? []
    } catch {
      // Keep existing todayGames on failure
    }
  }

  async function fetchData() {
    // Load today games separately (slow, ~9s) — don't block main dashboard
    fetchTodayGames()

    const { data } = await fetchDashboardOverview()
    applyCache({
      stats: data.stats,
      todayGames: todayGames.value.length > 0 ? todayGames.value : (data.todayGames ?? []),
      westStandings: data.westStandings ?? [],
      eastStandings: data.eastStandings ?? [],
      topScorers: data.topScorers ?? data.stats?.topScorers ?? [],
      hotPosts: data.hotPosts ?? [],
      docCount: data.docCount || 0,
    })
    saveCache({
      stats: stats.value,
      todayGames: todayGames.value,
      westStandings: westStandings.value,
      eastStandings: eastStandings.value,
      topScorers: topScorers.value,
      hotPosts: hotPosts.value,
      docCount: docCount.value,
    })
  }

  onMounted(async () => {
    const cached = loadCache()
    if (cached) {
      applyCache(cached)
      loading.value = false
      fetchData().catch(() => {
        // Keep the warm cache visible when the background refresh fails.
      })
      return
    }

    loading.value = true
    try {
      await fetchData()
    } catch {
      applyCache({
        stats: null,
        todayGames: [],
        westStandings: [],
        eastStandings: [],
        topScorers: [],
        hotPosts: [],
        docCount: 0,
      })
    } finally {
      loading.value = false
    }
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
  }
}
