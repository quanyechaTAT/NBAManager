<template>
  <div class="dashboard">
    <!-- Hero — Apple Style -->
    <section class="hero-section">
      <div class="hero-inner">
        <div class="hero-left">
          <template v-if="loading && !stats">
            <div class="skeleton skeleton-text" style="width: 140px; height: 12px; margin-bottom: 12px;"></div>
            <div class="skeleton skeleton-text" style="width: 200px; height: 32px; margin-bottom: 8px;"></div>
            <div class="skeleton skeleton-text" style="width: 160px; height: 14px; margin-bottom: 20px;"></div>
            <div style="display: flex; gap: 10px;">
              <div class="skeleton" style="width: 100px; height: 36px; border-radius: 980px;"></div>
              <div class="skeleton" style="width: 80px; height: 36px; border-radius: 980px;"></div>
            </div>
          </template>
          <template v-else>
            <p class="hero-eyebrow">NBA DATA CENTER</p>
            <h1 class="hero-title">赛季数据中心</h1>
            <p class="hero-season">2025-26 NBA Season</p>
            <div class="hero-actions">
              <button class="hero-btn primary" @click="$router.push('/news')">查看赛程</button>
              <button class="hero-btn secondary" @click="$router.push('/smart-search')">AI 助手</button>
            </div>
          </template>
        </div>
        <div class="hero-metrics">
          <template v-if="loading && !stats">
            <div v-for="i in 4" :key="i" class="metric-card">
              <div class="skeleton" style="width: 48px; height: 28px; margin: 0 auto 6px;"></div>
              <div class="skeleton" style="width: 56px; height: 11px; margin: 0 auto;"></div>
            </div>
          </template>
          <template v-else>
            <div class="metric-card">
              <span class="metric-value">{{ todayGames.length }}</span>
              <span class="metric-label">今日比赛</span>
            </div>
            <div class="metric-card">
              <span class="metric-value">{{ stats?.teamCount ?? 30 }}</span>
              <span class="metric-label">活跃球队</span>
            </div>
            <div class="metric-card">
              <span class="metric-value">{{ docCount.toLocaleString() }}</span>
              <span class="metric-label">已索引文档</span>
            </div>
            <div class="metric-card">
              <span class="metric-value">2026</span>
              <span class="metric-label">当前赛季</span>
            </div>
          </template>
        </div>
      </div>
    </section>

    <!-- 今日比赛 -->
    <section class="section" v-if="todayGames.length > 0">
      <div class="section-header">
        <h2 class="section-title">今日比赛</h2>
        <router-link to="/news" class="section-link">查看全部</router-link>
      </div>
      <div class="games-scroll">
        <div v-for="g in todayGames" :key="g.id" class="game-card" @click="goMatch(g)">
          <div class="game-status" :class="g.status?.toLowerCase()">{{ statusLabel(g.status) }}</div>
          <div class="game-teams">
            <div class="game-team">
              <img v-if="getLogo(g.homeTeam)" :src="getLogo(g.homeTeam)" class="game-logo" alt="" />
              <span v-else class="game-logo-fb">{{ g.homeTeam?.charAt(0) }}</span>
              <span class="game-team-name">{{ g.homeTeam }}</span>
            </div>
            <div class="game-score">
              <span :class="{ win: g.homeScore > g.awayScore }">{{ g.homeScore ?? '-' }}</span>
              <span class="game-sep">:</span>
              <span :class="{ win: g.awayScore > g.homeScore }">{{ g.awayScore ?? '-' }}</span>
            </div>
            <div class="game-team">
              <img v-if="getLogo(g.awayTeam)" :src="getLogo(g.awayTeam)" class="game-logo" alt="" />
              <span v-else class="game-logo-fb">{{ g.awayTeam?.charAt(0) }}</span>
              <span class="game-team-name">{{ g.awayTeam }}</span>
            </div>
          </div>
          <button class="game-ai-btn" @click.stop="goMatchAI(g)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5"/></svg>
            AI 分析
          </button>
        </div>
      </div>
    </section>

    <!-- 摘要区：排名 / 热门球员 / 球队趋势 -->
    <section class="section">
      <div class="summary-grid">
        <!-- 排名摘要 -->
        <div class="summary-card">
          <div class="summary-header">
            <h3>排名摘要</h3>
            <router-link to="/teams" class="section-link">完整排名</router-link>
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
              <div v-for="(t, i) in currentRank" :key="t.teamName" class="rank-row clickable" @click="goTeamDetail(t.teamName)">
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
        <div class="summary-card">
          <div class="summary-header">
            <h3>得分榜</h3>
            <router-link to="/players" class="section-link">球员排行</router-link>
          </div>
          <template v-if="loading && topScorers.length === 0">
            <div v-for="i in 8" :key="i" class="skeleton-row">
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
                <span class="player-rank" :class="{ top3: i < 3 }">{{ i + 1 }}</span>
                <div class="player-avatar-mini">
                  <img v-if="p.nbaPlayerId" :src="getHeadshotUrl(p.nbaPlayerId)" :alt="p.playerName" class="player-headshot-mini" @error="onHeadshotError" loading="lazy" />
                  <span class="player-avatar-fallback" :style="{ display: p.nbaPlayerId ? 'none' : 'flex' }">{{ p.playerName?.charAt(0) }}</span>
                </div>
                <div class="player-info">
                  <span class="player-name">{{ p.playerName }}</span>
                  <span class="player-team">{{ p.teamName }}</span>
                </div>
                <span class="player-stat">{{ p.ppg.toFixed(1) }}</span>
                <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M9 18l6-6-6-6"/></svg>
              </div>
            </div>
            <router-link to="/players" class="view-all-link">查看全部球员 →</router-link>
          </template>
        </div>

        <!-- 热门讨论 -->
        <div class="summary-card">
          <div class="summary-header">
            <h3>热门讨论</h3>
            <router-link to="/community" class="section-link">社区</router-link>
          </div>
          <div class="hot-list">
            <div v-for="p in hotPosts" :key="p.id" class="hot-row" @click="$router.push({ path: '/community/post', query: { id: String(p.id) } })">
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
        <h2 class="section-title">核心功能</h2>
      </div>
      <div class="features-grid">
        <router-link to="/playoff" class="feature-card">
          <div class="feature-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22"/><path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22"/><path d="M18 2H6v7a6 6 0 0 0 12 0V2Z"/></svg>
          </div>
          <span class="feature-title">季后赛对阵图</span>
          <span class="feature-desc">查看完整晋级路径与系列赛比分</span>
        </router-link>

        <router-link to="/teams" class="feature-card">
          <div class="feature-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>
          </div>
          <span class="feature-title">球队分析</span>
          <span class="feature-desc">战绩、趋势与核心数据</span>
        </router-link>

        <router-link to="/players" class="feature-card">
          <div class="feature-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
          </div>
          <span class="feature-title">球员排行榜</span>
          <span class="feature-desc">得分、篮板、助攻等榜单</span>
        </router-link>

        <router-link to="/news" class="feature-card">
          <div class="feature-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
          </div>
          <span class="feature-title">赛程中心</span>
          <span class="feature-desc">今日、近期和完整赛程</span>
        </router-link>

        <router-link to="/smart-search" class="feature-card">
          <div class="feature-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5"/><path d="M8.5 8.5v.01"/><path d="M16 15.5v.01"/><path d="M12 12v.01"/></svg>
          </div>
          <span class="feature-title">AI 数据助手</span>
          <span class="feature-desc">自然语言查询 NBA 数据</span>
        </router-link>

        <router-link to="/history" class="feature-card">
          <div class="feature-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>
          </div>
          <span class="feature-title">历史数据</span>
          <span class="feature-desc">多赛季数据查询与对比</span>
        </router-link>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getTeamLogo } from '@/utils/teamLogos'
import { useDashboardCache } from '@/composables/useDashboardCache'

const router = useRouter()
const { stats, todayGames, westStandings, eastStandings, topScorers, hotPosts, docCount, loading } = useDashboardCache()

const rankTab = ref<'west' | 'east'>('west')

const currentRank = computed(() => rankTab.value === 'west' ? westStandings.value : eastStandings.value)

const getLogo = (name: string) => getTeamLogo(name) || undefined

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

function statusLabel(s?: string) {
  const map: Record<string, string> = { LIVE: '进行中', FINISHED: '已结束', SCHEDULED: '未开始', UPCOMING: '未开始' }
  return map[s || ''] || s || '未开始'
}

function goMatch(g: GameNews) {
  if (g.nbaGameId) router.push({ path: '/match-detail', query: { gameId: String(g.nbaGameId) } })
}

function goMatchAI(g: GameNews) {
  router.push({ path: '/smart-search', query: { q: `分析${g.homeTeam} vs ${g.awayTeam}这场比赛的关键看点` } })
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
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px 20px 60px;
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

/* ===== Hero — Apple Style ===== */
.hero-section {
  margin-bottom: var(--space-6, 24px);
}
.hero-inner {
  display: flex;
  align-items: center;
  gap: 40px;
  background: #000;
  padding: 32px 40px;
  border-radius: 12px;
  position: relative;
  overflow: hidden;
}
.hero-left {
  flex: 1;
}
.hero-eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: #86868B;
  font-family: var(--font-body);
}
.hero-title {
  margin: 0 0 6px;
  font-size: 36px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.5px;
  color: #F5F5F7;
  font-family: var(--font-heading);
}
.hero-season {
  margin: 0 0 20px;
  font-size: 15px;
  color: #86868B;
  font-family: var(--font-body);
}
.hero-actions {
  display: flex;
  gap: 10px;
}
.hero-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 24px;
  border-radius: 980px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  border: none;
  font-family: var(--font-body);
}
.hero-btn.primary {
  background: var(--accent);
  color: #fff;
}
.hero-btn.primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px var(--accent-glow);
}
.hero-btn.secondary {
  background: rgba(255, 255, 255, 0.08);
  color: #F5F5F7;
}
.hero-btn.secondary:hover {
  background: rgba(255, 255, 255, 0.12);
  transform: translateY(-1px);
}
.hero-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  width: 240px;
}
.metric-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 16px;
  text-align: center;
  transition: all 0.2s ease;
}
.metric-card:hover {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.1);
}
.metric-value {
  display: block;
  font-size: 28px;
  font-weight: 700;
  font-family: var(--font-heading);
  color: #F5F5F7;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.5px;
}
.metric-label {
  display: block;
  font-size: 11px;
  color: #86868B;
  margin-top: 4px;
  font-family: var(--font-body);
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
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
}
.section-link {
  font-size: 13px;
  color: var(--accent);
  text-decoration: none;
  font-weight: 500;
}
.section-link:hover { text-decoration: underline; }

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
  border-radius: 10px;
  padding: 14px;
  cursor: pointer;
  transition: all 0.15s;
  position: relative;
}
.game-card:hover {
  border-color: var(--border-medium);
  box-shadow: var(--shadow-sm);
}
.game-status {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 10px;
}
.game-status.live { background: rgba(239,68,68,0.1); color: var(--danger); }
.game-status.finished { background: var(--bg-hover); color: var(--text-muted); }
.game-status.scheduled, .game-status.upcoming { background: rgba(37,99,235,0.08); color: #2563eb; }
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
.game-ai-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 6px;
  background: var(--bg-page);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  color: var(--text-muted);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
  justify-content: center;
}
.game-ai-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

/* ===== 摘要区 ===== */
.summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16px;
}
.summary-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 10px;
  padding: 16px;
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
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
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
  width: 20px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-muted);
  text-align: center;
}
.player-rank.top3 { color: var(--accent); }
.player-avatar-mini {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--purple) 0%, #5A4BD1 100%);
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
.player-name { display: block; font-size: 13px; font-weight: 500; color: var(--text-primary); }
.player-team { font-size: 11px; color: var(--text-muted); }
.player-stat {
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
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}
.feature-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 10px;
  text-decoration: none;
  transition: all 0.15s;
  text-align: center;
}
.feature-card:hover {
  border-color: var(--accent);
  box-shadow: var(--shadow-sm);
  transform: translateY(-2px);
}
.feature-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: var(--accent-lighter);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
}
.feature-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}
.feature-desc {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.4;
}

/* ===== 响应式 ===== */
@media (max-width: 960px) {
  .hero-inner {
    flex-direction: column;
    text-align: center;
    padding: 28px 24px;
    gap: 24px;
  }
  .hero-actions {
    justify-content: center;
  }
  .hero-metrics {
    width: 100%;
    max-width: 320px;
  }
  .summary-grid { grid-template-columns: 1fr; }
  .features-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 768px) {
  .dashboard { padding: 16px 12px 80px; }
  .hero-inner {
    padding: 24px 20px;
  }
  .hero-title { font-size: 28px; }
  .metric-value { font-size: 24px; }
  .features-grid { grid-template-columns: repeat(2, 1fr); }
  .game-card { flex: 0 0 180px; }
}
@media (max-width: 480px) {
  .hero-inner {
    padding: 20px 16px;
  }
  .hero-title { font-size: 24px; }
  .hero-season { font-size: 13px; }
  .metric-card { padding: 12px; }
  .metric-value { font-size: 22px; }
  .metric-label { font-size: 10px; }
}

/* ===== Light Theme Hero ===== */
[data-theme="light"] .hero-inner {
  background: #F5F5F7;
}
[data-theme="light"] .hero-eyebrow {
  color: #86868B;
}
[data-theme="light"] .hero-title {
  color: #1D1D1F;
}
[data-theme="light"] .hero-season {
  color: #86868B;
}
[data-theme="light"] .hero-btn.primary {
  background: var(--accent);
  color: #fff;
}
[data-theme="light"] .hero-btn.secondary {
  background: rgba(0, 0, 0, 0.06);
  color: #1D1D1F;
}
[data-theme="light"] .hero-btn.secondary:hover {
  background: rgba(0, 0, 0, 0.1);
}
[data-theme="light"] .metric-card {
  background: rgba(0, 0, 0, 0.04);
  border-color: rgba(0, 0, 0, 0.08);
}
[data-theme="light"] .metric-value {
  color: #1D1D1F;
}
[data-theme="light"] .metric-label {
  color: #86868B;
}
</style>
