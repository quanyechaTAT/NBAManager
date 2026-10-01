<template>
  <div class="page">
    <div class="page-inner">
      <!-- 返回按钮 -->
      <div class="back-row">
        <el-button class="back-btn" link @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="m15 18-6-6 6-6"/></svg>
          返回
        </el-button>
      </div>

      <!-- 球队信息卡片 -->
      <div class="team-hero" v-loading="teamLoading" :style="teamBrandStyle">
        <div class="team-hero-inner">
          <div class="team-hero-left">
            <div class="team-icon">
              <img v-if="teamLogo" :src="teamLogo" alt="" class="team-logo-img" />
              <span v-else>{{ teamInfo?.name?.charAt(0) || '?' }}</span>
            </div>
            <div>
              <h1 class="team-name">{{ teamInfo?.name }}</h1>
              <p class="team-sub">{{ teamInfo?.city }} · {{ teamInfo?.conference }}</p>
              <el-button v-if="auth.token" :type="isFollowed ? 'success' : 'primary'" plain size="small" @click="toggleFollow" class="follow-btn">
                {{ isFollowed ? '已关注' : '+ 关注' }}
              </el-button>
            </div>
          </div>
          <div class="team-hero-stats">
            <div class="hero-stat">
              <span class="hero-stat-num hero-stat-num--win">{{ teamInfo?.wins }}</span>
              <span class="hero-stat-label">胜</span>
            </div>
            <div class="hero-stat-divider"></div>
            <div class="hero-stat">
              <span class="hero-stat-num hero-stat-num--loss">{{ teamInfo?.losses }}</span>
              <span class="hero-stat-label">负</span>
            </div>
            <div class="hero-stat-divider"></div>
            <div class="hero-stat">
              <span class="hero-stat-num">{{ winPct(teamInfo?.wins ?? 0, teamInfo?.losses ?? 0) }}</span>
              <span class="hero-stat-label">胜率</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 核心球员 -->
      <template v-if="roster.length > 0">
        <div class="section-header">
          <div class="section-header-left">
            <h2>核心球员</h2>
            <span class="section-sub">按场均得分排序 · 点击查看详情</span>
          </div>
          <div v-if="roster.length > 3" class="roster-controls">
            <button type="button" aria-label="向前浏览球员" @click="scrollRoster(-1)">‹</button>
            <button type="button" aria-label="向后浏览球员" @click="scrollRoster(1)">›</button>
          </div>
        </div>
        <div ref="rosterStripRef" class="roster-strip">
          <router-link
            v-for="p in roster"
            :key="p.id"
            class="roster-chip"
            :to="{ path: '/players/detail', query: { id: String(p.id) } }"
          >
            <span class="roster-avatar">
              <img
                v-if="p.nbaPlayerId && !brokenRosterAvatars.has(p.id)"
                :src="`https://cdn.nba.com/headshots/nba/latest/260x190/${p.nbaPlayerId}.png`"
                loading="lazy"
                @error="brokenRosterAvatars.add(p.id)"
              />
              <i v-else>{{ p.name?.charAt(0) }}</i>
            </span>
            <span class="roster-meta">
              <strong>{{ p.name }}</strong>
              <small>{{ p.position }} · {{ p.pointsPerGame?.toFixed(1) ?? '—' }} 分</small>
            </span>
          </router-link>
        </div>
      </template>

      <!-- 对战记录 -->
      <div class="section-header">
        <div class="section-header-left">
          <h2>对战记录</h2>
          <span class="section-sub">共 {{ allMatchRecords.length }} 场比赛</span>
        </div>
        <div class="section-header-right">
          <SyncButton v-if="auth.isAdmin" module="matches" label="同步比赛" compact @sync-success="loadMatchRecords" />
        </div>
      </div>

      <div class="match-card">
        <el-table :data="showAllMatches ? allMatchRecords : matchRecords" stripe v-loading="matchLoading" empty-text="暂无对战记录">
          <el-table-column label="日期" width="120">
            <template #default="{ row }">{{ formatDate(row.matchDate) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="112" align="center">
            <template #default="{ row }">
              <span class="game-type-tag" :class="`game-type-tag--${row.gameType.toLowerCase()}`">
                {{ getGameTypeLabel(row.gameType) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="主队" min-width="120">
            <template #default="{ row }">
              <span class="match-team" :class="{ 'match-team--self': row.homeTeam === teamName }">{{ row.homeTeam }}</span>
            </template>
          </el-table-column>
          <el-table-column label="比分" width="130" align="center">
            <template #default="{ row }">
              <span v-if="row.status === 'SCHEDULED'" class="match-pending">未开赛</span>
              <div v-else class="score-block">
                <span class="match-score" :class="{ 'score-win': row.homeScore > row.awayScore }">{{ row.homeScore }}</span>
                <span class="match-sep">:</span>
                <span class="match-score" :class="{ 'score-win': row.awayScore > row.homeScore }">{{ row.awayScore }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="客队" min-width="120">
            <template #default="{ row }">
              <span class="match-team" :class="{ 'match-team--self': row.awayTeam === teamName }">{{ row.awayTeam }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getResultType(row)" size="small" effect="light">{{ getResultLabel(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="赛季" width="100" prop="season" />
          <el-table-column label="详情" width="80" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="goToMatchDetail(row)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 交锋统计 -->
      <div class="section-header" style="margin-top: 28px;">
        <div class="section-header-left">
          <h2>交锋统计</h2>
          <span class="section-sub">与各队的交手记录汇总</span>
        </div>
      </div>
      <div class="h2h-grid">
        <div v-for="stat in headToHeadStats" :key="stat.opponent" class="h2h-card">
          <div class="h2h-header">
            <span class="h2h-opponent">{{ stat.opponent }}</span>
            <span class="h2h-record">{{ stat.wins }}胜 {{ stat.losses }}负</span>
          </div>
          <div class="h2h-bar">
            <div class="h2h-bar-win" :style="{ width: stat.winPct + '%' }"></div>
          </div>
          <div class="h2h-detail">
            <span>胜率 {{ stat.winPct.toFixed(0) }}%</span>
            <span>场均得分 {{ stat.avgScore.toFixed(1) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchTeams } from '@/api/team'
import { fetchMatchRecords } from '@/api/matchRecord'
import { fetchPlayers } from '@/api/player'
import type { Team, MatchRecord, Player } from '@/api/types'
import { toggleFavorite, fetchFollowedTeamIds } from '@/api/favorite'
import { useAuthStore } from '@/stores/auth'
import { getTeamLogo } from '@/utils/teamLogos'
import { getTeamBrandColor } from '@/utils/teamColors'
import SyncButton from '@/components/common/SyncButton.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const rosterStripRef = ref<HTMLElement | null>(null)

function scrollRoster(direction: -1 | 1) {
  const strip = rosterStripRef.value
  if (!strip) return
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  strip.scrollBy({ left: direction * Math.max(220, strip.clientWidth * 0.75), behavior: reduced ? 'instant' : 'smooth' })
}

function goBack() {
  const returnTo = route.query.returnTo as string
  if (returnTo) {
    router.replace(returnTo)
  } else {
    router.replace('/teams')
  }
}

const teamName = computed(() => (route.query.name as string) || '')
const teamInfo = ref<Team | null>(null)
const teamLoading = ref(false)
const allMatchRecords = ref<MatchRecord[]>([])
const matchRecords = ref<MatchRecord[]>([])
const isFollowed = ref(false)
const matchLoading = ref(false)
const showAllMatches = ref(false)

const teamLogo = computed(() => teamInfo.value ? getTeamLogo(teamInfo.value.name) : undefined)

/* 球队品牌色：注入 CSS 变量驱动 hero 主题 */
const teamBrand = computed(() => teamInfo.value ? getTeamBrandColor(teamInfo.value.name) : undefined)
const teamBrandStyle = computed(() => {
  const c = teamBrand.value
  if (!c) return {}
  return { '--team-color': c.primary, '--team-color-soft': `color-mix(in srgb, ${c.primary} 14%, transparent)` }
})

const roster = ref<Player[]>([])
const brokenRosterAvatars = ref<Set<number>>(new Set())

async function loadRoster(teamId: number) {
  try {
    const { data } = await fetchPlayers({ teamId, page: 0, size: 8, sort: 'pointsPerGame,desc' })
    roster.value = (data.content || []).filter((p: Player) => (p.pointsPerGame ?? 0) > 0).slice(0, 6)
  } catch { /* 名单加载失败时隐藏该区块 */ }
}

async function loadTeamInfo() {
  if (!teamName.value) return
  teamLoading.value = true
  try {
    const { data } = await fetchTeams({ q: teamName.value, page: 0, size: 1 })
    teamInfo.value = data.content.find((t) => t.name === teamName.value) || null
    if (teamInfo.value) void loadRoster(teamInfo.value.id)
  } catch {
    ElMessage.error('加载球队信息失败')
  } finally {
    teamLoading.value = false
  }
}

async function loadMatchRecords() {
  if (!teamName.value) return
  matchLoading.value = true
  try {
    const { data } = await fetchMatchRecords(teamName.value)
    allMatchRecords.value = data
      .filter((m: MatchRecord) => !(m.homeScore === 0 && m.awayScore === 0 && m.status === 'FINISHED'))
    matchRecords.value = allMatchRecords.value.slice(0, 5)
  } catch {
    ElMessage.error('加载对战记录失败')
  } finally {
    matchLoading.value = false
  }
}

function formatDate(d: string) {
  return new Date(d).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function winPct(w: number, l: number) {
  const total = w + l
  if (total === 0) return '0.0%'
  return ((w / total) * 100).toFixed(1) + '%'
}

function getResultType(row: MatchRecord) {
  if (row.status !== 'FINISHED') return 'info'
  if (row.homeScore === row.awayScore) return 'info'
  if (row.homeTeam === teamName.value) {
    return row.homeScore > row.awayScore ? 'success' : 'danger'
  }
  return row.awayScore > row.homeScore ? 'success' : 'danger'
}

function getResultLabel(row: MatchRecord) {
  if (row.status === 'SCHEDULED') return '未开赛'
  if (row.status === 'LIVE') return '进行中'
  if (row.homeScore === row.awayScore) return '平'
  const isHome = row.homeTeam === teamName.value
  const won = isHome ? row.homeScore > row.awayScore : row.awayScore > row.homeScore
  return won ? '胜' : '负'
}

function getGameTypeLabel(gameType: MatchRecord['gameType']) {
  const labels: Record<MatchRecord['gameType'], string> = {
    SUMMER_LEAGUE: '夏季联赛',
    REGULAR_SEASON: '常规赛',
    PLAYOFFS: '季后赛',
    NBA_CUP: '季中杯赛',
  }
  return labels[gameType] || '常规赛'
}

function goToMatchDetail(row: MatchRecord) {
  if (!row.nbaGameId) {
    ElMessage.warning('该比赛暂无详细数据')
    return
  }
  const query: Record<string, string> = {
    gameId: row.nbaGameId,
    status: row.status,
    homeTeam: row.homeTeam,
    awayTeam: row.awayTeam,
    returnTo: `/teams/detail?name=${teamName.value}`,
  }
  // 只有比分有效时才传递
  if (row.homeScore != null && row.homeScore > 0) {
    query.homeScore = String(row.homeScore)
  }
  if (row.awayScore != null && row.awayScore > 0) {
    query.awayScore = String(row.awayScore)
  }
  router.push({ path: '/match-detail', query })
}

const headToHeadStats = computed(() => {
  const map = new Map<string, { wins: number; losses: number; totalScore: number; games: number }>()
  for (const m of allMatchRecords.value.filter(record => record.status === 'FINISHED')) {
    const isHome = m.homeTeam === teamName.value
    const opponent = isHome ? m.awayTeam : m.homeTeam
    const myScore = isHome ? m.homeScore : m.awayScore
    const theirScore = isHome ? m.awayScore : m.homeScore
    const won = myScore > theirScore
    if (!map.has(opponent)) {
      map.set(opponent, { wins: 0, losses: 0, totalScore: 0, games: 0 })
    }
    const s = map.get(opponent)!
    if (won) s.wins++
    else s.losses++
    s.totalScore += myScore
    s.games++
  }
  return Array.from(map.entries())
    .map(([opponent, s]) => ({
      opponent,
      wins: s.wins,
      losses: s.losses,
      winPct: s.games > 0 ? (s.wins / s.games) * 100 : 0,
      avgScore: s.games > 0 ? s.totalScore / s.games : 0,
    }))
    .sort((a, b) => b.wins - a.wins)
})

async function toggleFollow() {
  if (!auth.token || !teamInfo.value) { ElMessage.warning('请先登录'); return }
  try {
    const { data } = await toggleFavorite('TEAM', teamInfo.value.id)
    isFollowed.value = data.favorited
  } catch { /* ignore */ }
}

async function checkFollowStatus() {
  if (!auth.token || !teamInfo.value) return
  try {
    const { data } = await fetchFollowedTeamIds()
    isFollowed.value = data.includes(teamInfo.value.id)
  } catch { /* ignore */ }
}

watch(teamName, () => {
  if (teamName.value) {
    loadTeamInfo().then(() => checkFollowStatus())
    loadMatchRecords()
  }
}, { immediate: false })

onMounted(() => {
  loadTeamInfo().then(() => checkFollowStatus())
  loadMatchRecords()
})
</script>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
  min-height: calc(100vh - 108px);
  position: relative;
  animation: pageFadeIn 0.3s ease forwards;
  opacity: 0;
  transform: translateY(8px);
}
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }

.back-row { margin-bottom: 16px; }
.back-btn {
  color: var(--text-muted) !important;
  font-size: 13px !important;
}
.back-btn:hover { color: var(--accent) !important; }

/* 球队信息卡片 */
.team-hero {
  background:
    radial-gradient(420px 160px at 12% 0%, var(--team-color-soft, transparent), transparent 70%),
    var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 18px;
  padding: 24px 28px;
  margin-bottom: 24px;
  position: relative;
  overflow: hidden;
  animation: courtside-rise 0.5s var(--ease-out-expo, ease) both;
  box-shadow: var(--shadow-md);
}
.team-hero::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--team-color, var(--accent)), transparent 85%);
}
.team-hero-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 1;
  flex-wrap: wrap;
  gap: 20px;
}
.team-hero-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.team-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  background: var(--bg-hover);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  color: var(--text-muted);
  flex-shrink: 0;
  overflow: hidden;
  border: 1px solid var(--team-color, var(--border-light));
  box-shadow: 0 0 0 1px var(--team-color-soft, transparent);
}
.team-logo-img {
  width: 48px;
  height: 48px;
  object-fit: contain;
}
.team-name {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
}
.team-sub {
  margin: 4px 0 0;
  font-size: 14px;
  color: var(--text-muted);
}
.follow-btn { margin-top: 8px !important; }
.team-hero-stats {
  display: flex;
  align-items: center;
  gap: 24px;
}
.hero-stat { text-align: center; }
.hero-stat-num {
  display: block;
  font-size: 32px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  line-height: 1.1;
}
.hero-stat-num--win { color: var(--team-color, var(--accent)); }
.hero-stat-num--loss { color: var(--danger); }
.hero-stat-label {
  font-size: 12px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-top: 4px;
  display: block;
  font-weight: 600;
}
.hero-stat-divider {
  width: 1px;
  height: 36px;
  background: var(--border-light);
}

/* 查看全部按钮 */
.show-all-btn {
  padding: 4px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--accent);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}
.show-all-btn:hover { background: var(--accent-lighter); }

/* section header */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}
.section-header-left { display: flex; flex-direction: column; gap: 4px; }
.section-header-right { display: flex; align-items: center; gap: 8px; }
.section-header h2 {
  margin: 0;
  font-size: 18px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-weight: 700;
}
.section-sub { font-size: 12px; color: var(--text-muted); }

/* 对战记录表格 */
.match-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
  margin-bottom: 24px;
}
.match-team {
  font-weight: 600;
  color: var(--text-secondary);
}
.match-team--self {
  color: var(--text-primary);
  font-weight: 700;
}
.score-block {
  display: flex;
  align-items: center;
  justify-content: center;
}
.match-score {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-secondary);
  font-family: var(--font-heading);
}
.score-win { color: var(--accent); }
.match-sep {
  margin: 0 8px;
  color: var(--text-dim);
  font-weight: 300;
}
.game-type-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 68px;
  height: 24px;
  padding: 0 8px;
  border: 1px solid var(--border-medium);
  border-radius: 4px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}
.game-type-tag--summer_league {
  border-color: color-mix(in srgb, #38bdf8 42%, transparent);
  background: color-mix(in srgb, #0ea5e9 14%, transparent);
  color: #38bdf8;
}
.game-type-tag--regular_season {
  border-color: color-mix(in srgb, #94a3b8 36%, transparent);
  color: var(--text-secondary);
}
.game-type-tag--playoffs {
  border-color: color-mix(in srgb, var(--accent) 45%, transparent);
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  color: var(--accent-light);
}
.game-type-tag--nba_cup {
  border-color: color-mix(in srgb, #fbbf24 42%, transparent);
  background: color-mix(in srgb, #f59e0b 13%, transparent);
  color: #fbbf24;
}

/* 核心球员名单 */
.roster-strip {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  overflow-x: auto;
  padding-bottom: 4px;
  scrollbar-width: thin;
}
.roster-controls { display: flex; gap: 8px; }
.roster-controls button {
  width: 36px;
  height: 36px;
  border: 1px solid var(--border-light);
  border-radius: 9px;
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 22px;
  cursor: pointer;
}
.roster-controls button:hover { border-color: var(--accent); color: var(--accent); }
.match-pending { color: var(--text-muted); font-size: 12px; }
.roster-chip {
  flex: 0 0 200px;
}
.roster-chip {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  text-decoration: none;
  transition:
    transform var(--duration-lift, 0.22s) var(--ease-out-quart, ease),
    border-color var(--duration-normal) var(--ease-smooth),
    box-shadow var(--duration-normal) var(--ease-smooth);
}
.roster-chip:hover {
  transform: translateY(-2px);
  border-color: var(--team-color, var(--border-medium));
  box-shadow: var(--shadow-sm);
}
.roster-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  overflow: hidden;
  flex-shrink: 0;
}
.roster-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.roster-avatar i {
  font-style: normal;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-muted);
}
.roster-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.roster-meta strong {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.roster-meta small {
  font-size: 12px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

/* 交锋统计 */
.h2h-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}
.h2h-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  transition: all 0.15s ease;
}
.h2h-card:hover {
  border-color: var(--border-medium);
  box-shadow: var(--shadow-sm);
}
.h2h-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.h2h-opponent {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}
.h2h-record {
  font-size: 12px;
  color: var(--text-muted);
}
.h2h-bar {
  height: 6px;
  background: var(--bg-hover);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 6px;
}
.h2h-bar-win {
  height: 100%;
  background: var(--team-color, var(--accent));
  border-radius: 3px;
  transition: width 0.6s ease;
}
.h2h-detail {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-muted);
}

/* 移动端 */
@media (max-width: 768px) {
  .team-hero-inner {
    flex-direction: column;
    align-items: flex-start;
  }
  .team-hero-stats {
    width: 100%;
    justify-content: space-around;
  }
  .h2h-grid {
    grid-template-columns: 1fr;
  }
}
</style>
