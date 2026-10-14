import { ref, onMounted, onUnmounted } from 'vue'
import { fetchDashboardOverview } from '@/api/dashboard'
import type { DashboardStats, DashboardOverview, TeamRank, GameNews, Post } from '@/api/types'
import { queryCache } from '@/utils/queryCache'
import { getProfile, getToken } from '@/utils/authStorage'

const DASHBOARD_KEY = 'nba_dashboard_cache_v3'
const TODAY_KEY = 'nba_today_games_cache_v3'
const DASHBOARD_TTL = 5 * 60 * 1000
const DASHBOARD_FRESH_TTL = 60 * 1000
const TODAY_TTL = 60 * 1000

function accountScope(): string {
  return getToken() ? `user:${getProfile()?.username || 'unknown'}` : 'anonymous'
}

function todayTtl(games: GameNews[]): number {
  return games.some(game => game.status === 'LIVE') ? 10_000 : TODAY_TTL
}

interface DashboardCache {
  stats: DashboardStats | null
  westStandings: TeamRank[]
  eastStandings: TeamRank[]
  topScorers: { id: number; playerName: string; ppg: number; teamName: string; nbaPlayerId: number | null }[]
  hotPosts: Post[]
  docCount: number
  timestamp: number
  scope: string
}

interface TodayCache {
  games: GameNews[]
  dateKey: string // Asia/Shanghai yyyy-MM-dd
  timestamp: number
  scope: string
}

function getDateKey(): string {
  return new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' })
}

function loadDashboardCache(): DashboardCache | null {
  try {
    const raw = sessionStorage.getItem(DASHBOARD_KEY)
    if (!raw) return null
    const cached = JSON.parse(raw) as DashboardCache
    if (cached.scope !== accountScope() || !Number.isFinite(cached.timestamp)
      || cached.timestamp > Date.now() || Date.now() - cached.timestamp > DASHBOARD_TTL) return null
    return cached
  } catch { return null }
}

function saveDashboardCache(data: Omit<DashboardCache, 'timestamp' | 'scope'>, timestamp: number) {
  try {
    sessionStorage.setItem(DASHBOARD_KEY, JSON.stringify({ ...data, timestamp, scope: accountScope() }))
  } catch { /* ignore */ }
}

function loadTodayCache(): TodayCache | null {
  try {
    const raw = sessionStorage.getItem(TODAY_KEY)
    if (!raw) return null
    const cached = JSON.parse(raw) as TodayCache
    const today = getDateKey()
    if (cached.dateKey !== today || cached.scope !== accountScope() || !Number.isFinite(cached.timestamp)
      || cached.timestamp > Date.now()) return null
    if (Date.now() - cached.timestamp > TODAY_TTL) return null
    return cached
  } catch { return null }
}

function saveTodayCache(games: GameNews[], timestamp: number) {
  try {
    sessionStorage.setItem(TODAY_KEY, JSON.stringify({
      games,
      dateKey: getDateKey(),
      timestamp,
      scope: accountScope(),
    }))
  } catch { /* ignore */ }
}

export function useDashboardCache() {
  const pageRequests = new AbortController()
  const stats = ref<DashboardStats | null>(null)
  const todayGames = ref<GameNews[]>([])
  const westStandings = ref<TeamRank[]>([])
  const eastStandings = ref<TeamRank[]>([])
  const topScorers = ref<{ id: number; playerName: string; ppg: number; teamName: string; nbaPlayerId: number | null }[]>([])
  const hotPosts = ref<Post[]>([])
  const docCount = ref(0)
  const loading = ref(true)
  const failed = ref(false)

  const INVALID_TEAMS = new Set(['待定', 'TBD', 'tbd', ''])

  function isValidGame(g: GameNews): boolean {
    const home = g.homeTeam?.trim()
    const away = g.awayTeam?.trim()
    return Boolean(g.nbaGameId && home && !INVALID_TEAMS.has(home) && away && !INVALID_TEAMS.has(away))
  }

  async function fetchTodayGames(force = false) {
    const scope = accountScope()
    const dateKey = getDateKey()
    const key = `today:${scope}:${dateKey}`
    try {
      const games = await queryCache.fetch(key, async signal => {
        const { data } = await import('@/api/news').then(m => m.fetchTodayNews(signal))
        return (data ?? []).filter(isValidGame)
      }, { ttlMs: todayTtl, force, signal: pageRequests.signal })
      if (pageRequests.signal.aborted || scope !== accountScope() || dateKey !== getDateKey()) return
      todayGames.value = games
      saveTodayCache(games, queryCache.snapshot(key)?.updatedAt ?? Date.now())
    } catch {
      // Keep existing todayGames on failure
    }
  }

  async function fetchDashboard(force = false) {
    const scope = accountScope()
    const key = `dashboard:${scope}`
    const data = await queryCache.fetch(key, async signal => (await fetchDashboardOverview(signal)).data,
      { ttlMs: DASHBOARD_FRESH_TTL, signal: pageRequests.signal, force })
    if (pageRequests.signal.aborted || scope !== accountScope()) return
    stats.value = data.stats ?? null
    westStandings.value = data.westStandings ?? []
    eastStandings.value = data.eastStandings ?? []
    topScorers.value = data.topScorers ?? data.stats?.topScorers ?? []
    hotPosts.value = data.hotPosts ?? []
    docCount.value = data.docCount || 0

    if (Array.isArray(data.todayGames) && data.todayGames.length > 0 && todayGames.value.length === 0) {
      todayGames.value = data.todayGames.filter(isValidGame)
    }

    saveDashboardCache({
      stats: stats.value,
      westStandings: westStandings.value,
      eastStandings: eastStandings.value,
      topScorers: topScorers.value,
      hotPosts: hotPosts.value,
      docCount: docCount.value,
    }, queryCache.snapshot(key)?.updatedAt ?? Date.now())
    failed.value = false
  }

  async function fetchData(force = false) {
    // Start both in parallel — todayGames is independent
    fetchTodayGames(force)
    await fetchDashboard(force)
  }

  async function refresh() {
    loading.value = !stats.value
    try { await fetchData(true) } catch { if (!pageRequests.signal.aborted) failed.value = true }
    finally { loading.value = false }
  }

  // Visibility refresh: when tab becomes visible again
  function onVisibilityChange() {
    if (document.visibilityState === 'visible') {
      void fetchTodayGames()
      void fetchDashboard().catch(() => { if (!pageRequests.signal.aborted) failed.value = true })
    }
  }

  onMounted(async () => {
    document.addEventListener('visibilitychange', onVisibilityChange)
    // Load todayGames from cache immediately
    const todayCached = loadTodayCache()
    if (todayCached) {
      todayGames.value = (todayCached.games || []).filter(isValidGame)
      const key = `today:${accountScope()}:${getDateKey()}`
      if (!queryCache.snapshot(key)) queryCache.seed(key, todayGames.value, todayTtl(todayGames.value), todayCached.timestamp)
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
      const key = `dashboard:${accountScope()}`
      if (!queryCache.snapshot(key)) queryCache.seed<DashboardOverview>(key, {
        stats: dashCached.stats, westStandings: dashCached.westStandings, eastStandings: dashCached.eastStandings,
        topScorers: dashCached.topScorers, hotPosts: dashCached.hotPosts, docCount: dashCached.docCount,
        todayGames: [], generatedAt: new Date(dashCached.timestamp).toISOString(), partialErrors: [],
      }, DASHBOARD_FRESH_TTL, dashCached.timestamp)
      loading.value = false
      // Background refresh
      fetchData().catch(() => { if (!pageRequests.signal.aborted) failed.value = true })
    } else {
      loading.value = true
      try {
        await fetchData()
      } catch {
        if (!pageRequests.signal.aborted) failed.value = true
      } finally {
        loading.value = false
      }
    }

  })

  onUnmounted(() => {
    pageRequests.abort()
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
    failed,
    refresh,
    refreshTodayGames: () => fetchTodayGames(true),
  }
}
