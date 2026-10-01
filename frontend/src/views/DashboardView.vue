<template>
  <div class="dashboard">
    <!-- Hero — 转播面板：以比分与比赛状态为主角 -->
    <section class="hero-section">
      <div class="hero-grid">
        <BroadcastPanel :headline-match="feedHeadline" :feed-label="feedHeadlineLabel" @open-feed="$router.push('/news')" @open-detail="goMatch" />
        <div class="hero-metrics">
          <template v-if="loading && !stats">
            <div v-for="i in 4" :key="i" class="metric-card">
              <div class="skeleton" style="width: 48px; height: 32px; margin: 0 auto 8px;"></div>
              <div class="skeleton" style="width: 56px; height: 12px; margin: 0 auto;"></div>
            </div>
          </template>
          <template v-else>
            <div class="metric-card">
              <span class="metric-value" :class="{ 'is-zero': todayGames.length === 0 }" v-count-up="todayGames.length">{{ todayGames.length }}</span>
              <span class="metric-label">今日赛事</span>
              <span class="metric-accent-bar"></span>
            </div>
            <div class="metric-card">
              <span v-if="stats" class="metric-value" v-count-up="stats.teamCount">{{ stats.teamCount }}</span>
              <span v-else class="metric-value">—</span>
              <span class="metric-label">联盟球队</span>
              <span class="metric-accent-bar"></span>
            </div>
            <div class="metric-card">
              <span class="metric-value" :class="{ 'is-zero': docCount === 0 }" v-count-up:locale="docCount">{{ docCount.toLocaleString() }}</span>
              <span class="metric-label" title="涵盖全联盟赛事数据、球星档案与高阶战术指标">战术情报档案</span>
              <span class="metric-accent-bar"></span>
            </div>
            <div class="metric-card">
              <span class="metric-value metric-value--season">{{ feedHeadline?.season || '—' }}</span>
              <span class="metric-label">赛事赛季</span>
              <span class="metric-accent-bar"></span>
            </div>
          </template>
        </div>
      </div>
    </section>

    <!-- 赛事动态：直播中 / 今日赛程 / 最近赛果，按数据实际来源标注 -->
    <section class="section" v-if="feedMatches.length > 0">
      <div class="section-header">
        <h2 class="section-title">{{ feedSectionTitle }}</h2>
        <router-link to="/news" class="section-link">查看全部</router-link>
      </div>
      <div class="games-scroll">
        <div
          v-for="g in feedMatches"
          :key="g.id"
          class="game-card"
          :class="[gameCardClass(g), { 'is-disabled': !g.nbaGameId }]"
          :role="g.nbaGameId ? 'link' : undefined"
          :tabindex="g.nbaGameId ? 0 : -1"
          @click="goMatch(g)"
          @keydown.enter="goMatch(g)"
        >
          <div class="game-card-top">
            <span class="league-pill" :class="{ summer: g.gameType === 'SUMMER_LEAGUE' }">{{ gameLeagueLabel(g) }}</span>
            <span class="game-type">{{ gameTypeLabel(g) }}</span>
            <span class="game-time">{{ formatGameTime(g.matchDate) }}</span>
          </div>
          <div class="game-title">{{ g.awayTeam }} @ {{ g.homeTeam }}</div>
          <div class="game-teams">
            <div class="game-team">
              <img v-if="getLogo(g.awayTeam)" :src="getLogo(g.awayTeam)" class="game-logo" alt="" />
              <span v-else class="game-logo-fb">{{ g.awayTeam?.charAt(0) }}</span>
              <span class="game-team-name">{{ g.awayTeam }}</span>
            </div>
            <div class="game-score">
              <template v-if="showGameScore(g)">
                <span :class="{ win: isWinner(g, 'away') }">{{ g.awayScore }}</span>
                <span class="game-sep">-</span>
                <span :class="{ win: isWinner(g, 'home') }">{{ g.homeScore }}</span>
              </template>
              <span v-else class="game-vs">vs</span>
            </div>
            <div class="game-team">
              <img v-if="getLogo(g.homeTeam)" :src="getLogo(g.homeTeam)" class="game-logo" alt="" />
              <span v-else class="game-logo-fb">{{ g.homeTeam?.charAt(0) }}</span>
              <span class="game-team-name">{{ g.homeTeam }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 摘要区：排名 / 热门球员 / 球队趋势 -->
    <section class="section">
      <div class="summary-grid">
        <!-- 排名摘要 -->
        <div class="summary-card">
          <div class="summary-header">
            <h3>分区排位先锋榜</h3>
            <router-link to="/teams" class="section-link">完整战绩榜 →</router-link>
          </div>
          <template v-if="loading && currentRank.length === 0">
            <div v-for="i in 8" :key="i" class="skeleton-row">
              <div class="skeleton" style="width: 20px; height: 14px;"></div>
              <div class="skeleton" style="width: 20px; height: 20px; border-radius: 4px;"></div>
              <div class="skeleton" style="width: 60px; height: 14px;"></div>
              <div class="skeleton" style="width: 40px; height: 14px; margin-left: auto;"></div>
            </div>
          </template>
          <template v-else>
            <div class="rank-tabs">
              <button class="rank-tab" :class="{ active: rankTab === 'west' }" @click="rankTab = 'west'">西部</button>
              <button class="rank-tab" :class="{ active: rankTab === 'east' }" @click="rankTab = 'east'">东部</button>
            </div>
            <div class="rank-list">
              <div v-for="(t, i) in currentRank.slice(0, 5)" :key="t.teamName" class="rank-row clickable" @click="goTeamDetail(t.teamName)">
                <span class="rank-num" :class="{ top3: i < 3 }">{{ i + 1 }}</span>
                <img v-if="getLogo(t.teamName)" :src="getLogo(t.teamName)" class="rank-logo" alt="" />
                <span class="rank-name">{{ t.teamName }}</span>
                <span class="rank-record">{{ t.wins }}-{{ t.losses }}</span>
                <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M9 18l6-6-6-6"/></svg>
              </div>
            </div>
          </template>
        </div>

        <!-- 得分榜 -->
        <div class="summary-card summary-card--with-footer">
          <div class="summary-header">
            <h3>得分领袖风云榜 (PPG Leaders)</h3>
          </div>
          <template v-if="loading && topScorers.length === 0">
            <div v-for="i in 9" :key="i" class="skeleton-row">
              <div class="skeleton" style="width: 20px; height: 14px;"></div>
              <div class="skeleton" style="width: 36px; height: 36px; border-radius: 50%;"></div>
              <div style="flex: 1;">
                <div class="skeleton" style="width: 80px; height: 14px; margin-bottom: 4px;"></div>
                <div class="skeleton" style="width: 50px; height: 10px;"></div>
              </div>
              <div class="skeleton" style="width: 32px; height: 14px;"></div>
            </div>
          </template>
          <template v-else>
            <div class="player-list">
              <div v-for="(p, i) in topScorers" :key="p.playerName" class="player-row clickable" @click="goPlayerDetail(p.id)">
                <span class="player-rank" :class="{ top3: i < 3, 'rank-1': i === 0, 'rank-2': i === 1, 'rank-3': i === 2 }">{{ i + 1 }}</span>
                <div class="player-avatar-mini" :style="teamRingStyle(p.teamName)">
                  <img v-if="p.nbaPlayerId" :src="getHeadshotUrl(p.nbaPlayerId)" :alt="p.playerName" class="player-headshot-mini" @error="onHeadshotError" loading="lazy" />
                  <span class="player-avatar-fallback" :style="{ display: p.nbaPlayerId ? 'none' : 'flex' }">{{ p.playerName?.charAt(0) }}</span>
                </div>
                <div class="player-info">
                  <span class="player-name">{{ p.playerName }}</span>
                  <span class="player-team">{{ p.teamName }}</span>
                </div>
                <span class="player-stat">{{ p.ppg.toFixed(1) }} <small class="stat-unit" style="font-size:10px;opacity:0.75;font-weight:500;">PPG</small></span>
                <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M9 18l6-6-6-6"/></svg>
              </div>
            </div>
            <router-link to="/players" class="view-all-link">进入球星档案库 →</router-link>
          </template>
        </div>

        <!-- 热门讨论 -->
        <div class="summary-card" v-if="visibleHotPosts.length > 0">
          <div class="summary-header">
            <h3>球迷看台热议先锋</h3>
            <router-link to="/community" class="section-link">看台广场 →</router-link>
          </div>
          <div class="hot-list">
            <div v-for="p in visibleHotPosts" :key="p.id" class="hot-row" @click="$router.push({ path: '/community/post', query: { id: String(p.id) } })">
              <span class="hot-title">{{ p.title.slice(0, 24) }}{{ p.title.length > 24 ? '...' : '' }}</span>
              <span class="hot-meta">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                {{ p.viewCount }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 核心功能入口 -->
    <section class="section">
      <div class="section-header">
        <h2 class="section-title">联盟核心智能矩阵 (Core Analytics Matrix)</h2>
      </div>
      <div class="features-grid">
        <router-link to="/playoff" class="feature-card feature-card--playoff">
          <div class="feature-card-header">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22"/><path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22"/><path d="M18 2H6v7a6 6 0 0 0 12 0V2Z"/></svg>
            </div>
            <svg class="feature-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
          </div>
          <span class="feature-title">季后赛争霸树状图</span>
          <span class="feature-desc">决战奥布莱恩杯 · 分区突围与晋级总决赛路径</span>
        </router-link>

        <router-link to="/teams" class="feature-card feature-card--teams">
          <div class="feature-card-header">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>
            </div>
            <svg class="feature-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
          </div>
          <span class="feature-title">三十路豪强巡礼</span>
          <span class="feature-desc">攻防效率净值 (Net Rtg) · 分区阶梯与胜负走势</span>
        </router-link>

        <router-link to="/players" class="feature-card feature-card--players">
          <div class="feature-card-header">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
            </div>
            <svg class="feature-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
          </div>
          <span class="feature-title">MVP 竞逐与球星档案</span>
          <span class="feature-desc">真实正负值 (BPM) · 六维战术雷达与高阶风云榜</span>
        </router-link>

        <router-link to="/news" class="feature-card feature-card--news">
          <div class="feature-card-header">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
            </div>
            <svg class="feature-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
          </div>
          <span class="feature-title">赛事中心与即时战报</span>
          <span class="feature-desc">技术统计 (Box Score) · 关键回合与投篮热区复盘</span>
        </router-link>

        <router-link to="/smart-search" class="feature-card feature-card--ai">
          <div class="feature-card-header">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5"/><path d="M8.5 8.5v.01"/><path d="M16 15.5v.01"/><path d="M12 12v.01"/></svg>
            </div>
            <svg class="feature-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
          </div>
          <span class="feature-title">AI 战术智囊 (Copilot)</span>
          <span class="feature-desc">技战术攻防博弈推演 · 阵容克制与关键球透视</span>
        </router-link>

        <router-link to="/history" class="feature-card feature-card--history">
          <div class="feature-card-header">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>
            </div>
            <svg class="feature-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
          </div>
          <span class="feature-title">历史丰碑长卷 (NBA Vault)</span>
          <span class="feature-desc">数十年王朝兴衰 · 单场神迹与赛季历史极值</span>
        </router-link>

        <router-link to="/drafts" class="feature-card feature-card--draft">
          <div class="feature-card-header">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M12 20V10"/><path d="M18 20V4"/><path d="M6 20v-4"/></svg>
            </div>
            <svg class="feature-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
          </div>
          <span class="feature-title">历届选秀星河殿堂</span>
          <span class="feature-desc">乐透造星脉络 · 黄金一代与生涯起点溯源</span>
        </router-link>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { Directive } from 'vue'
import { useRouter } from 'vue-router'
import { getTeamLogo } from '@/utils/teamLogos'
import { getTeamBrandColor } from '@/utils/teamColors'
import { useDashboardCache } from '@/composables/useDashboardCache'
import { useMatchFeed } from '@/composables/useMatchFeed'
import BroadcastPanel from '@/components/dashboard/BroadcastPanel.vue'
import { hasScore, toLocalDateKey } from '@/utils/matchFeed'
import type { MatchRecord } from '@/api/types'

const router = useRouter()
const { stats, todayGames, westStandings, eastStandings, topScorers, hotPosts, docCount, loading } = useDashboardCache()
const { tickerMatches, groups, headline, headlineLabel, refresh: refreshFeed } = useMatchFeed()

/** 赛事看板数据：直播中 → 今日赛程 → 最近赛果 */
const feedMatches = computed(() => tickerMatches.value)
const feedHeadline = computed(() => headline.value)
const feedHeadlineLabel = computed(() => headlineLabel.value)
const feedSectionTitle = computed(() => groups.value[0]?.label ?? '赛事动态')

onMounted(() => { void refreshFeed() })

const rankTab = ref<'west' | 'east'>('west')

const currentRank = computed(() => rankTab.value === 'west' ? westStandings.value : eastStandings.value)

/** 过滤压测/内部测试帖，避免出现在普通用户首页 */
const TEST_TITLE_RE = /(压测|压宿|宿主|IDOR|注入|audit\d|loadtest|test[_-]?\d|测试帖)/i
const visibleHotPosts = computed(() => (hotPosts.value || []).filter((p: any) => !TEST_TITLE_RE.test(p.title || '')))

const getLogo = (name: string) => getTeamLogo(name) || undefined

/** 得分榜头像描边：球队主题色，未知球队回退到中性边框 */
function teamRingStyle(teamName: string) {
  const c = getTeamBrandColor(teamName)?.primary
  return c ? { '--team-ring': c } as Record<string, string> : undefined
}

/**
 * v-count-up[:locale]="number" — 大数字进入视口时 600ms 缓动计数。
 * 值为 0 或偏好减少动态时直接渲染终值。
 */
const vCountUp: Directive<HTMLElement, number> = {
  mounted(el, binding) {
    const target = Number(binding.value) || 0
    const useLocale = binding.arg === 'locale'
    const render = (n: number) => {
      el.textContent = useLocale ? Math.round(n).toLocaleString() : String(Math.round(n))
    }
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
    if (reduced || target === 0 || !('IntersectionObserver' in window)) {
      render(target)
      el.dataset.countDone = '1'
      return
    }
    render(0)
    const io = new IntersectionObserver((entries) => {
      if (!entries.some(e => e.isIntersecting)) return
      io.disconnect()
      const duration = 600
      const t0 = performance.now()
      const tick = (now: number) => {
        const p = Math.min(1, (now - t0) / duration)
        render(target * (1 - Math.pow(1 - p, 3)))
        if (p < 1) requestAnimationFrame(tick)
        else el.dataset.countDone = '1'
      }
      requestAnimationFrame(tick)
    }, { threshold: 0.4 })
    io.observe(el)
  },
  updated(el, binding) {
    // 动画结束后数据刷新：直接展示新值，避免重复计数
    if (el.dataset.countDone === '1' && binding.value !== binding.oldValue) {
      const target = Number(binding.value) || 0
      el.textContent = binding.arg === 'locale' ? Math.round(target).toLocaleString() : String(Math.round(target))
    }
  },
}

function getHeadshotUrl(nbaPlayerId: number | null): string {
  if (!nbaPlayerId) return ''
  return `https://ak-static.cms.nba.com/wp-content/uploads/headshots/nba/latest/260x190/${nbaPlayerId}.png`
}

function onHeadshotError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
  const fallback = img.nextElementSibling as HTMLElement
  if (fallback) fallback.style.display = 'flex'
}

function gameCardClass(g: MatchRecord) {
  return {
    'is-live': g.status === 'LIVE',
    'is-scheduled': g.status === 'SCHEDULED',
    'is-finished': g.status === 'FINISHED',
    'is-history': isPastResult(g),
  }
}

/** 已完赛且不属于今天的记录 → 历史赛果，用于弱化展示 */
function isPastResult(g: MatchRecord) {
  if (g.status !== 'FINISHED') return false
  return toLocalDateKey(g.matchDate) !== toLocalDateKey(new Date())
}

function gameLeagueLabel(g: MatchRecord) {
  if (g.gameType === 'SUMMER_LEAGUE') return '夏联'
  return 'NBA'
}

function gameTypeLabel(g: MatchRecord) {
  if (g.status === 'LIVE') return '进行中'
  if (g.status === 'SCHEDULED') return '未开赛'
  return '已结束'
}

/** 仅当存在真实比分时展示，未开赛不显示 0 - 0 */
function showGameScore(g: MatchRecord) {
  return hasScore(g)
}

function isWinner(g: MatchRecord, side: 'home' | 'away') {
  if (!hasScore(g)) return false
  const score = side === 'home' ? g.homeScore : g.awayScore
  const other = side === 'home' ? g.awayScore : g.homeScore
  return score > other
}

function formatGameTime(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return ''
  const today = new Date()
  if (d.toDateString() === today.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function goMatch(g: MatchRecord) {
  if (!g.nbaGameId) return
  router.push({
    path: '/match-detail',
    query: {
      gameId: String(g.nbaGameId),
      status: g.status,
      homeTeam: g.homeTeam,
      awayTeam: g.awayTeam,
      homeScore: hasScore(g) ? String(g.homeScore) : undefined,
      awayScore: hasScore(g) ? String(g.awayScore) : undefined,
      returnTo: '/dashboard'
    }
  })
}

function goTeamDetail(teamName: string) {
  router.push({ path: '/teams/detail', query: { name: teamName, returnTo: '/dashboard' } })
}

function goPlayerDetail(playerId: number) {
  router.push({ path: '/players/detail', query: { id: String(playerId), returnTo: '/dashboard' } })
}
</script>

<style scoped>
/* ===== 页面 ===== */
.dashboard {
  max-width: 1440px;
  margin: 0 auto;
  padding: 24px 24px 60px;
}
/* ===== Skeleton Loading ===== */
.skeleton {
  background: linear-gradient(90deg, var(--bg-hover) 25%, var(--bg-card) 50%, var(--bg-hover) 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  border-radius: 4px;
}
@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
.skeleton-text {
  height: 14px;
}
.skeleton-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 4px;
}
@keyframes fadeIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

/* ===== Hero — Season Data Center ===== */
.hero-section {
  margin-bottom: var(--space-6, 24px);
}
/* 转播面板 + 指标卡：宽屏并排，窄屏堆叠 */
.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  align-items: stretch;
  gap: 16px;
}
.hero-grid > * {
  animation: courtside-rise 0.5s var(--ease-out-expo, ease) both;
}
.hero-grid > :nth-child(2) {
  animation-delay: 0.06s;
}

.hero-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 320px;
  position: relative;
  z-index: 3;
  animation: courtside-rise 0.55s var(--ease-out-expo, ease) 0.08s both;
}
.metric-card {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.05), rgba(255, 255, 255, 0) 46%),
    var(--dashboard-hero-card-bg);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  border: 1px solid var(--dashboard-hero-card-border);
  border-radius: var(--radius-xl);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.07);
  padding: 14px 12px 12px;
  text-align: center;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    transform 0.22s var(--ease-out-quart, ease),
    box-shadow 0.22s var(--ease-out-quart, ease);
  position: relative;
  height: 116px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  overflow: hidden;
}
.metric-card:hover {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.07), rgba(255, 255, 255, 0) 46%),
    var(--dashboard-hero-card-hover);
  border-color: rgba(34, 211, 238, 0.35);
  transform: translateY(-3px);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.09),
    0 6px 22px -6px rgba(34, 211, 238, 0.35),
    0 12px 28px rgba(0, 0, 0, 0.18);
}
.metric-value {
  display: block;
  font-size: 32px;
  font-weight: 700;
  font-family: var(--font-heading);
  color: var(--dashboard-hero-title);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}
/* 数字为 0 的卡弱化，避免“假大空” */
.metric-value.is-zero {
  color: var(--text-tertiary);
}
.metric-label {
  display: block;
  font-size: 12px;
  color: var(--dashboard-hero-muted);
  margin-top: 4px;
  font-family: var(--font-body);
}
.metric-accent-bar {
  display: block;
  width: 24px;
  height: 2px;
  background: var(--accent-gradient, var(--accent));
  border-radius: 1px;
  margin: 10px auto 0;
  opacity: 0.85;
  box-shadow: 0 0 8px rgba(34, 211, 238, 0.55);
  transition: width 0.25s var(--ease-out-quart, ease);
}
.metric-card:hover .metric-accent-bar {
  width: 36px;
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .hero-grid > *,
  .metric-card,
  .game-card {
    animation: none !important;
    transition: none !important;
  }
  .metric-card:hover,
  .game-card:hover {
    transform: none !important;
  }
}

/* ===== 通用 Section ===== */
.section { margin-bottom: 28px; }
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.section-title {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: -0.3px;
  display: flex;
  align-items: center;
}
.section-title::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 16px;
  margin-right: 10px;
  border-radius: 2px;
  background: var(--accent-gradient, var(--accent));
  box-shadow: 0 0 10px rgba(34, 211, 238, 0.45);
}
.section-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 13px;
  color: var(--accent);
  text-decoration: none;
  font-weight: 600;
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.section-link::after {
  content: '\2192';
  transition: transform 0.15s ease;
}
.section-link:hover {
  opacity: 1;
  text-shadow: 0 0 14px rgba(34, 211, 238, 0.45);
}
.section-link:hover::after {
  transform: translateX(3px);
}
.section-link:hover::after {
  transform: translateX(2px);
}
/* 亮色主题：小号强调文字换用深青色 accent-text，保证 AA 4.5:1 */
[data-theme="light"] .section-link,
[data-theme="light"] .view-all-link,
[data-theme="light"] .hot-row:hover .hot-title {
  color: var(--accent-text, #0E7490);
}

/* ===== 今日比赛 ===== */
.games-scroll {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 4px;
  -webkit-overflow-scrolling: touch;
}
.games-scroll::-webkit-scrollbar { height: 4px; }
.games-scroll::-webkit-scrollbar-thumb { background: var(--border-medium); border-radius: 2px; }
.game-card {
  flex: 0 0 220px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 14px;
  padding: 14px;
  cursor: pointer;
  transition:
    transform 0.22s var(--ease-out-quart, ease),
    border-color 0.18s ease,
    box-shadow 0.22s var(--ease-out-quart, ease);
  position: relative;
  overflow: hidden;
}
.game-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(34, 211, 238, 0.55), transparent);
  opacity: 0.5;
}
.game-card:hover {
  border-color: rgba(34, 211, 238, 0.35);
  box-shadow: var(--shadow-md), 0 10px 24px rgba(34, 211, 238, 0.08);
  transform: translateY(-3px);
}
.game-teams {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}
.game-team {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
}
.game-logo { width: 32px; height: 32px; object-fit: contain; }
.game-logo-fb {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-hover);
  border-radius: 50%;
  font-size: 14px;
  font-weight: 700;
  color: var(--text-muted);
}
.game-team-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.game-score {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 20px;
  font-weight: 800;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}
.game-sep { font-size: 14px; color: var(--text-dim); }
.game-score .win { color: var(--accent); }

/* ===== 摘要区 ===== */
.game-card {
  flex-basis: 216px;
  min-height: 136px;
  border-radius: 6px;
  padding: 12px 10px 10px;
}
.game-card:hover {
  transform: translateY(-1px);
}
.game-card.is-history {
  border-style: dashed;
}
/* 直播：橙色描边 + 淡橙底，与已结束/未开赛明确区分 */
.game-card.is-live {
  border-color: rgba(249, 115, 22, 0.45);
  background:
    linear-gradient(180deg, rgba(249, 115, 22, 0.07), rgba(249, 115, 22, 0) 46%),
    var(--bg-card);
}
.game-card.is-live::before {
  background: linear-gradient(90deg, transparent, rgba(249, 115, 22, 0.75), transparent);
  opacity: 0.9;
}
/* 未开赛：无比分，弱化处理 */
.game-card.is-scheduled {
  opacity: 0.92;
}
.game-vs {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-tertiary);
  letter-spacing: 0.5px;
}
.game-card-top {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.league-pill {
  flex: 0 0 auto;
  padding: 2px 5px;
  border-radius: 4px;
  background: var(--pill-history-bg);
  color: var(--pill-history-text);
  font-size: 11px;
  font-weight: 800;
  line-height: 1.2;
}
.league-pill.summer {
  background: var(--pill-summer-bg);
  color: var(--pill-summer-text);
}
.game-type {
  min-width: 0;
  flex: 1;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.game-time {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--text-muted);
}
.game-title {
  height: 34px;
  margin-bottom: 8px;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
.game-logo,
.game-logo-fb {
  width: 34px;
  height: 34px;
}

.summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16px;
}
.summary-card {
  position: relative;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.028), rgba(255, 255, 255, 0) 40%),
    var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  padding: 20px;
  overflow: hidden;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05), var(--shadow-sm);
  transition: box-shadow var(--duration-normal, 0.18s) var(--ease-out-quart, ease),
    border-color var(--duration-normal, 0.18s) var(--ease-smooth, ease);
}
.summary-card:hover {
  border-color: var(--border-medium);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06), var(--shadow-md);
}
.summary-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), rgba(34, 211, 238, 0));
  opacity: 0.75;
  pointer-events: none;
}
.summary-card--with-footer {
  display: flex;
  flex-direction: column;
}
.summary-card--with-footer .player-list,
.summary-card--with-footer .hot-list,
.summary-card--with-footer .rank-list {
  flex: 1;
}
.summary-card--with-footer .view-all-link {
  margin-top: auto;
}
.summary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.summary-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

/* 排名 */
.rank-tabs { display: flex; gap: 0; margin-bottom: 10px; }
.rank-tab {
  flex: 1;
  padding: 6px;
  background: var(--bg-page);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  text-align: center;
}
.rank-tab.active {
  background: var(--accent-gradient, var(--accent));
  color: var(--accent-contrast, #04202A);
  border-color: transparent;
  box-shadow: 0 3px 12px -3px rgba(34, 211, 238, 0.55), inset 0 1px 0 rgba(255, 255, 255, 0.22);
}
.rank-list { display: flex; flex-direction: column; gap: 2px; }
.rank-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 4px;
  border-radius: 4px;
  transition: background 0.1s;
}
.rank-row:hover { background: var(--bg-hover); }
.rank-row.clickable {
  cursor: pointer;
}
.rank-row.clickable:hover {
  background: var(--bg-hover);
}
.arrow-icon {
  opacity: 0;
  transition: opacity 0.15s, transform 0.15s;
  color: var(--text-muted);
  flex-shrink: 0;
}
.rank-row.clickable:hover .arrow-icon,
.player-row.clickable:hover .arrow-icon {
  opacity: 1;
  transform: translateX(2px);
}
.rank-num {
  width: 20px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-muted);
  text-align: center;
}
.rank-num.top3 { color: var(--accent); }
.rank-logo { width: 20px; height: 20px; object-fit: contain; }
.rank-name { flex: 1; font-size: 13px; font-weight: 500; color: var(--text-primary); }
.rank-record {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}

/* 球员 */
.player-list { display: flex; flex-direction: column; gap: 2px; }
.scorers-card { max-height: 560px; overflow-y: auto; }
.view-all-link {
  display: block;
  text-align: center;
  padding: 10px 0 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
  text-decoration: none;
  border-top: 1px solid var(--border-light);
  margin-top: 8px;
  transition: color 0.15s;
}
.view-all-link:hover { color: var(--accent-light); text-decoration: underline; }
.player-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 4px;
  border-radius: 4px;
  transition: background 0.1s;
}
.player-row:hover { background: var(--bg-hover); }
.player-row.clickable {
  cursor: pointer;
}
.player-row.clickable:hover {
  background: var(--bg-hover);
}
.player-rank {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-muted);
  text-align: center;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.player-rank.rank-1 { background: var(--medal-gold); color: var(--medal-text); box-shadow: 0 2px 6px rgba(0, 0, 0, 0.18); }
.player-rank.rank-2 { background: var(--medal-silver); color: var(--medal-text); box-shadow: 0 2px 6px rgba(0, 0, 0, 0.18); }
.player-rank.rank-3 { background: var(--medal-bronze); color: var(--medal-text); box-shadow: 0 2px 6px rgba(0, 0, 0, 0.18); }
.player-avatar-mini {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--purple) 0%, color-mix(in srgb, var(--purple), black 20%) 100%);
  border: 2px solid var(--team-ring, var(--border-medium));
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
}
.player-headshot-mini {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.player-avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  font-family: var(--font-heading);
}
.player-info { flex: 1; min-width: 0; }
.player-name { display: block; font-size: 13px; font-weight: 500; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.player-team { font-size: 11px; color: var(--text-muted); }
.player-stat {
  min-width: 44px;
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-size: 15px;
  font-weight: 700;
  color: var(--accent);
  font-variant-numeric: tabular-nums;
}

/* 热门讨论 */
.hot-list { display: flex; flex-direction: column; gap: 2px; }
.hot-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 4px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.1s;
}
.hot-row:hover { background: var(--bg-hover); }
.hot-title {
  flex: 1;
  font-size: 13px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.hot-row:hover .hot-title { color: var(--accent); }
.hot-meta {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--text-dim);
  flex-shrink: 0;
}
.hot-meta svg { opacity: 0.4; }

/* ===== 核心功能入口 ===== */
.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 14px;
}
.feature-card {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
  gap: 10px;
  padding: 18px 18px 20px;
  background:
    radial-gradient(120% 100% at 90% 10%, rgba(var(--feature-color-rgb, 34, 211, 238), 0.08), transparent 60%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.03), rgba(255, 255, 255, 0) 50%),
    var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl, 14px);
  text-decoration: none;
  transition:
    transform 0.22s var(--ease-out-quart, ease),
    box-shadow 0.22s var(--ease-out-quart, ease),
    border-color 0.22s var(--ease-smooth, ease);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.045), var(--shadow-xs);
}
.feature-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, var(--feature-color, var(--accent)), transparent);
  opacity: 0.6;
  transition: opacity 0.22s ease, height 0.22s ease;
}
.feature-card:hover {
  transform: translateY(-4px);
  border-color: rgba(var(--feature-color-rgb, 34, 211, 238), 0.45);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 12px 28px -10px rgba(var(--feature-color-rgb, 34, 211, 238), 0.35),
    var(--shadow-md);
}
.feature-card:hover::before {
  opacity: 1;
  height: 3px;
}
.feature-card-header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.feature-arrow {
  color: var(--text-tertiary);
  opacity: 0.4;
  transform: translateX(-4px);
  transition: transform 0.22s var(--ease-spring, ease), opacity 0.22s ease, color 0.22s ease;
}
.feature-card:hover .feature-arrow {
  opacity: 1;
  color: var(--feature-color, var(--accent));
  transform: translateX(0);
}
.feature-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: rgba(var(--feature-color-rgb, 34, 211, 238), 0.12);
  border: 1px solid rgba(var(--feature-color-rgb, 34, 211, 238), 0.25);
  color: var(--feature-color, var(--accent));
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
  transition: transform 0.22s var(--ease-spring, ease), box-shadow 0.22s ease;
}
.feature-card:hover .feature-icon {
  transform: scale(1.08);
  box-shadow: 0 4px 14px rgba(var(--feature-color-rgb, 34, 211, 238), 0.35);
}
.feature-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.2px;
}
.feature-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.45;
}

/* 各卡片主题色 */
.feature-card--playoff {
  --feature-color: #F59E0B;
  --feature-color-rgb: 245, 158, 11;
}
.feature-card--teams {
  --feature-color: #0EA5E9;
  --feature-color-rgb: 14, 165, 233;
}
.feature-card--players {
  --feature-color: #10B981;
  --feature-color-rgb: 16, 185, 129;
}
.feature-card--news {
  --feature-color: #EF4444;
  --feature-color-rgb: 239, 68, 68;
}
.feature-card--ai {
  --feature-color: #8B5CF6;
  --feature-color-rgb: 139, 92, 246;
}
.feature-card--history {
  --feature-color: #D97706;
  --feature-color-rgb: 217, 119, 6;
}
.feature-card--draft {
  --feature-color: #EC4899;
  --feature-color-rgb: 236, 72, 153;
}

/* ===== 响应式 ===== */
@media (max-width: 1199px) and (min-width: 769px) {
  .hero-grid {
    grid-template-columns: minmax(0, 1fr) 280px;
    gap: 14px;
  }
  .hero-metrics {
    width: 280px;
  }
  .summary-grid { grid-template-columns: 1fr; }
  .features-grid { grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); }
}
@media (max-width: 1024px) {
  .summary-grid { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .dashboard { padding: 16px 12px 80px; }
  .hero-metrics {
    display: flex;
    width: 100%;
    max-width: 100%;
    overflow-x: auto;
    gap: 10px;
    scrollbar-width: none;
  }
  .hero-metrics::-webkit-scrollbar { display: none; }
  .hero-metrics .metric-card { flex: 0 0 148px; min-height: 84px; }
  .hero-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .hero-metrics {
    width: 100%;
    max-width: 100%;
  }
  .metric-card { height: auto; min-height: 88px; }
  .metric-value { font-size: 26px; }
  .features-grid { grid-template-columns: repeat(2, 1fr); }
  .game-card { flex: 0 0 180px; }
}
/* 移动端：放大次要可点区域的触控高度，便于手指点击 */
@media (max-width: 768px) {
  .section-link,
  .view-all-link {
    padding: 8px 4px;
    min-height: 36px;
    display: inline-flex;
    align-items: center;
  }
  .rank-tab {
    min-height: 40px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .metric-card { padding: 12px 10px 10px; height: auto; min-height: 80px; }
  .metric-value { font-size: 24px; }
  .metric-label { font-size: 11px; }
  .metric-accent-bar { width: 20px; margin-top: 6px; }
  .features-grid { grid-template-columns: 1fr; }
}
</style>
