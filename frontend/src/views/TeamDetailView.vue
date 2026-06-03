<template>
  <div class="page animated-bg">
    <div class="bg-particles">
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
    </div>
    <div class="bg-grid"></div>

    <div class="page-inner stagger-in">
      <!-- 返回按钮 -->
      <div class="back-row">
        <el-button class="back-btn" link @click="router.push('/teams')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="m15 18-6-6 6-6"/></svg>
          返回球队战绩
        </el-button>
      </div>

      <!-- 球队信息卡片 -->
      <div class="team-hero" v-loading="teamLoading">
        <div class="team-hero-inner">
          <div class="team-hero-left">
            <div class="team-icon">
              <img v-if="teamInfo?.logoUrl" :src="teamInfo.logoUrl" alt="" class="team-logo-img" />
              <span v-else>🏀</span>
            </div>
            <div>
              <h1 class="team-name">{{ teamInfo?.name }}</h1>
              <p class="team-sub">{{ teamInfo?.city }} · {{ teamInfo?.conference }}</p>
              <el-button v-if="auth.token" :type="isFollowed ? 'success' : 'primary'" plain size="small" @click="toggleFollow" class="follow-btn">
                {{ isFollowed ? '★ 已关注' : '+ 关注' }}
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

      <!-- 对战记录 -->
      <div class="section-header">
        <div class="section-header-left">
          <h2>⚔️ 对战记录</h2>
          <span class="section-sub">共 {{ matchRecords.length }} 场比赛</span>
        </div>
      </div>

      <el-card shadow="never" class="match-card">
        <el-table :data="matchRecords" stripe v-loading="matchLoading" empty-text="暂无对战记录">
          <el-table-column label="日期" width="120">
            <template #default="{ row }">{{ formatDate(row.matchDate) }}</template>
          </el-table-column>
          <el-table-column label="主队" min-width="120">
            <template #default="{ row }">
              <span class="match-team" :class="{
                'match-team--self': row.homeTeam === teamName,
              }">{{ row.homeTeam }}</span>
            </template>
          </el-table-column>
          <el-table-column label="比分" width="130" align="center">
            <template #default="{ row }">
              <div class="score-block">
                <span class="match-score" :class="{ 'score-win': row.homeScore > row.awayScore }">{{ row.homeScore }}</span>
                <span class="match-sep">:</span>
                <span class="match-score" :class="{ 'score-win': row.awayScore > row.homeScore }">{{ row.awayScore }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="客队" min-width="120">
            <template #default="{ row }">
              <span class="match-team" :class="{
                'match-team--self': row.awayTeam === teamName,
              }">{{ row.awayTeam }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="100" align="center">
            <template #default="{ row }">
              <el-tag
                :type="getResultType(row)"
                size="small"
                effect="light"
              >{{ getResultLabel(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="赛季" width="100" prop="season" />
          <el-table-column label="详情" width="80" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="goToMatchDetail(row)">📊</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 交锋统计 -->
      <div class="section-header slide-up-enter" style="margin-top: 28px; animation-delay: 0.2s;">
        <div class="section-header-left">
          <h2>📊 交锋统计</h2>
          <span class="section-sub">与各队的交手记录汇总</span>
        </div>
      </div>
      <el-row :gutter="16" class="stagger-in">
        <el-col :xs="24" :sm="12" :md="8" v-for="stat in headToHeadStats" :key="stat.opponent">
          <div class="h2h-card">
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
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchTeams } from '@/api/team'
import { fetchMatchRecords } from '@/api/matchRecord'
import type { Team, MatchRecord } from '@/api/types'
import { toggleFavorite, fetchFollowedTeamIds } from '@/api/favorite'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const teamName = computed(() => (route.query.name as string) || '')
const teamInfo = ref<Team | null>(null)
const teamLoading = ref(false)
const matchRecords = ref<MatchRecord[]>([])
const isFollowed = ref(false)
const matchLoading = ref(false)

async function loadTeamInfo() {
  if (!teamName.value) return
  teamLoading.value = true
  try {
    const { data } = await fetchTeams({ q: teamName.value, page: 0, size: 1 })
    teamInfo.value = data.content.find((t) => t.name === teamName.value) || null
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
    // 过滤掉无效比赛（0:0比分）并限制为最近10场、1个月内
    const now = new Date()
    const oneMonthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000)
    matchRecords.value = data
      .filter((m: MatchRecord) => !(m.homeScore === 0 && m.awayScore === 0 && m.status === 'FINISHED'))
      .filter((m: MatchRecord) => new Date(m.matchDate) >= oneMonthAgo)
      .slice(0, 10)
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
  if (row.homeTeam === teamName.value) {
    return row.homeScore > row.awayScore ? 'success' : 'danger'
  }
  return row.awayScore > row.homeScore ? 'success' : 'danger'
}

function getResultLabel(row: MatchRecord) {
  const isHome = row.homeTeam === teamName.value
  const won = isHome ? row.homeScore > row.awayScore : row.awayScore > row.homeScore
  return won ? '胜' : '负'
}

function goToMatchDetail(row: MatchRecord) {
  if (!row.nbaGameId) {
    ElMessage.warning('该比赛暂无详细数据')
    return
  }
  router.push({
    path: '/match-detail',
    query: {
      gameId: row.nbaGameId,
      status: row.status,
      homeTeam: row.homeTeam,
      awayTeam: row.awayTeam,
      homeScore: String(row.homeScore),
      awayScore: String(row.awayScore),
      returnTo: `/teams/detail?name=${teamName.value}`,
    },
  })
}

/* 交锋统计 */
const headToHeadStats = computed(() => {
  const map = new Map<string, { wins: number; losses: number; totalScore: number; games: number }>()
  for (const m of matchRecords.value) {
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
  min-height: calc(100vh - 108px);
  position: relative;
  border-radius: var(--radius-lg);
  animation: pageFadeIn 0.4s var(--ease-smooth) forwards;
  opacity: 0;
  transform: translateY(12px);
}
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }

/* 返回按钮 */
.back-row {
  margin-bottom: 16px;
}
.back-btn {
  color: var(--text-muted) !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  padding: 6px 12px !important;
  border-radius: var(--radius-md) !important;
  transition: all var(--duration-fast) var(--ease-smooth) !important;
}
.back-btn:hover {
  color: var(--accent) !important;
  background: var(--accent-lighter) !important;
}
.back-btn svg {
  margin-right: 4px;
}

/* 球队信息卡片 */
.team-hero {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  padding: 28px 32px;
  margin-bottom: 24px;
  position: relative;
  overflow: hidden;
  animation: heroSlideIn 0.5s var(--ease-smooth) forwards;
  opacity: 0;
  transform: translateY(16px);
}
@keyframes heroSlideIn { to { opacity: 1; transform: translateY(0); } }
.team-hero::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), var(--accent-light), var(--purple));
}
.team-hero::after {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, var(--purple-glow) 0%, transparent 70%);
  opacity: 0.3;
  pointer-events: none;
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
  width: 60px;
  height: 60px;
  border-radius: var(--radius-lg);
  background: var(--purple-dim);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  flex-shrink: 0;
  overflow: hidden;
  border: 2px solid var(--border-light);
  transition: all var(--duration-normal) var(--ease-smooth);
}
.team-hero:hover .team-icon {
  border-color: var(--purple);
  box-shadow: 0 0 16px var(--purple-glow);
}
.team-logo-img {
  width: 48px;
  height: 48px;
  object-fit: contain;
}
.team-name {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
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
.hero-stat {
  text-align: center;
}
.hero-stat-num {
  display: block;
  font-size: 34px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  line-height: 1.1;
  letter-spacing: -0.5px;
}
.hero-stat-num--win {
  color: var(--accent);
  text-shadow: 0 0 20px var(--accent-glow);
}
.hero-stat-num--loss {
  color: var(--danger);
  text-shadow: 0 0 20px var(--danger-glow);
}
.hero-stat-label {
  font-size: 12px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-top: 6px;
  display: block;
  font-weight: 600;
}
.hero-stat-divider {
  width: 1px;
  height: 36px;
  background: var(--border-light);
}

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
.section-header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.section-header h2 {
  margin: 0;
  font-size: 18px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-weight: 700;
  letter-spacing: 0.3px;
}
.section-sub {
  font-size: 12px;
  color: var(--text-muted);
}

/* 对战记录表格 */
.match-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  position: relative;
  overflow: hidden;
}
.match-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), var(--purple));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}
.match-team {
  font-weight: 600;
  color: var(--text-secondary);
  font-family: var(--font-heading);
  letter-spacing: 0.2px;
}
.match-team--win {
  color: var(--accent);
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
  font-size: 20px;
  font-weight: 700;
  color: var(--text-secondary);
  font-family: var(--font-heading);
  letter-spacing: -0.5px;
}
.score-win {
  color: var(--accent);
  text-shadow: 0 0 12px var(--accent-glow);
}
.match-sep {
  margin: 0 8px;
  color: var(--text-dim);
  font-weight: 300;
  font-size: 16px;
}

/* 交锋统计卡片 */
.h2h-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 16px 20px;
  margin-bottom: 16px;
  transition: all var(--duration-normal) var(--ease-smooth);
  position: relative;
  overflow: hidden;
}
.h2h-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 3px;
  height: 0;
  background: linear-gradient(180deg, var(--accent), var(--purple));
  transition: height var(--duration-normal) var(--ease-smooth);
  border-radius: 0 2px 2px 0;
}
.h2h-card:hover::before {
  height: 100%;
}
.h2h-card:hover {
  border-color: var(--border-medium);
  box-shadow: var(--shadow-md);
  transform: translateX(4px);
}
.h2h-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.h2h-opponent {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.2px;
}
.h2h-record {
  font-size: 13px;
  color: var(--text-muted);
  font-weight: 500;
  font-family: var(--font-heading);
}
.h2h-bar {
  height: 8px;
  background: var(--bg-hover);
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 8px;
}
.h2h-bar-win {
  height: 100%;
  background: linear-gradient(90deg, var(--accent), var(--accent-light));
  border-radius: 4px;
  transition: width 0.8s var(--ease-smooth);
  box-shadow: 0 0 8px var(--accent-glow);
}
.h2h-detail {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-muted);
  font-family: var(--font-heading);
}
</style>
