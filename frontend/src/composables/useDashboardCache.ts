import { ref, onMounted } from 'vue'
import { fetchDashboardStats } from '@/api/dashboard'
import { fetchTodayNews } from '@/api/news'
import { fetchRankings } from '@/api/team'
import { fetchHotPosts } from '@/api/community'
import request from '@/utils/request'
import type { DashboardStats, TeamRank, GameNews, Post } from '@/api/types'

const CACHE_KEY = 'nba_dashboard_cache'
const CACHE_TTL = 5 * 60 * 1000 // 5 minutes

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
    // ignore quota errors
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

  async function fetchData() {
    const [statsRes, todayRes, rankRes, hotRes, ragRes] = await Promise.allSettled([
      fetchDashboardStats(),
      fetchTodayNews(),
      fetchRankings(),
      fetchHotPosts({ page: 0, size: 5 }),
      request.get('/rag/stats'),
    ])
    if (statsRes.status === 'fulfilled') {
      stats.value = statsRes.value.data
      topScorers.value = stats.value?.topScorers ?? []
    }
    if (todayRes.status === 'fulfilled') {
      const INVALID = new Set(['待定', 'TBD', 'tbd', ''])
      todayGames.value = (todayRes.value.data ?? [])
        .filter((g: GameNews) => g.nbaGameId && !INVALID.has(g.homeTeam?.trim()) && !INVALID.has(g.awayTeam?.trim()))
        .slice(0, 10)
    }
    if (rankRes.status === 'fulfilled') {
      const r = rankRes.value.data
      westStandings.value = r?.西部 ?? r?.western ?? []
      eastStandings.value = r?.东部 ?? r?.eastern ?? []
    }
    if (hotRes.status === 'fulfilled') hotPosts.value = (hotRes.value.data?.content ?? []).slice(0, 5)
    if (ragRes.status === 'fulfilled') docCount.value = ragRes.value.data?.documentCount || 0

    // Save to cache
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
    // Try loading from cache first
    const cached = loadCache()
    if (cached) {
      stats.value = cached.stats
      todayGames.value = cached.todayGames
      westStandings.value = cached.westStandings
      eastStandings.value = cached.eastStandings
      topScorers.value = cached.topScorers
      hotPosts.value = cached.hotPosts
      docCount.value = cached.docCount
      loading.value = false
      // Silent refresh in background
      fetchData()
    } else {
      // No cache, show loading
      loading.value = true
      await fetchData()
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
