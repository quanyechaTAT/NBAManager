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
        <el-button class="back-btn" link @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="m15 18-6-6 6-6"/></svg>
          {{ returnTo ? '返回球队详情' : '返回' }}
        </el-button>
      </div>

      <!-- 比赛头部 -->
      <div class="match-hero" v-loading="loading">
        <div class="match-hero-inner">
          <div class="match-hero-team">
            <div class="hero-team-icon">
              <img v-if="homeTeamLogo" :src="homeTeamLogo" alt="" class="team-logo-img" />
              <span v-else>🏀</span>
            </div>
            <div class="hero-team-info">
              <span class="hero-team-name">{{ boxScore?.homeTeam || queryHomeTeam || '--' }}</span>
              <span class="hero-team-label">主场</span>
            </div>
          </div>
          <div class="match-hero-center">
            <div class="hero-score-display">
              <span class="hero-score">{{ homeTotalScore }}</span>
              <span class="hero-score-sep">:</span>
              <span class="hero-score">{{ awayTotalScore }}</span>
            </div>
            <el-tag
              :type="statusType(status)"
              size="small"
              :class="{ 'live-badge': status === 'LIVE' }"
            >{{ statusLabel(status) }}</el-tag>
          </div>
          <div class="match-hero-team match-hero-team--away">
            <div class="hero-team-info hero-team-info--right">
              <span class="hero-team-name">{{ boxScore?.awayTeam || queryAwayTeam || '--' }}</span>
              <span class="hero-team-label">客场</span>
            </div>
            <div class="hero-team-icon">
              <img v-if="awayTeamLogo" :src="awayTeamLogo" alt="" class="team-logo-img" />
              <span v-else>🏀</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 逐节比分 -->
      <div class="section-header">
        <div class="section-header-left">
          <h2>📊 逐节比分</h2>
        </div>
      </div>
      <el-card shadow="never" class="quarter-card">
        <el-table :data="quarterScores" stripe v-loading="quarterLoading" empty-text="暂无逐节比分数据">
          <el-table-column label="节次" width="120" align="center">
            <template #default="{ row }">
              <span class="period-label">{{ periodLabel(row.period) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="boxScore?.homeTeam || '主队'" align="center">
            <template #default="{ row }">
              <span class="quarter-score" :class="{ 'quarter-score--win': row.homeScore > row.awayScore }">{{ row.homeScore }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="boxScore?.awayTeam || '客队'" align="center">
            <template #default="{ row }">
              <span class="quarter-score" :class="{ 'quarter-score--win': row.awayScore > row.homeScore }">{{ row.awayScore }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 球员统计 -->
      <div class="section-header">
        <div class="section-header-left">
          <h3 class="team-table-title">{{ boxScore?.homeTeam || '主队' }}</h3>
        </div>
      </div>
          <el-card shadow="never" class="boxscore-card">
            <el-table :data="sortedHomePlayers" stripe v-loading="boxscoreLoading" empty-text="暂无数据" size="small">
              <el-table-column label="球员" min-width="140" fixed="left">
                <template #default="{ row }">
                  <div class="player-cell">
                    <span class="player-name" :class="{ 'player-starter': row.starter }">{{ row.playerName }}</span>
                    <el-tag v-if="row.starter" size="small" type="success" effect="dark" class="starter-tag">首发</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="分钟" width="70" align="center" prop="minutes" />
              <el-table-column label="得分" width="65" align="center" prop="points" sortable />
              <el-table-column label="篮板" width="65" align="center" prop="rebounds" sortable />
              <el-table-column label="助攻" width="65" align="center" prop="assists" sortable />
              <el-table-column label="抢断" width="65" align="center" prop="steals" />
              <el-table-column label="盖帽" width="65" align="center" prop="blocks" />
              <el-table-column label="失误" width="65" align="center" prop="turnovers" />
              <el-table-column label="投篮" width="110" align="center">
                <template #default="{ row }">{{ formatShooting(row.fgMade, row.fgAttempted, row.fgPct) }}</template>
              </el-table-column>
              <el-table-column label="三分" width="110" align="center">
                <template #default="{ row }">{{ formatShooting(row.threeMade, row.threeAttempted, row.threePct) }}</template>
              </el-table-column>
              <el-table-column label="罚球" width="110" align="center">
                <template #default="{ row }">{{ formatShooting(row.ftMade, row.ftAttempted, row.ftPct) }}</template>
              </el-table-column>
              <el-table-column label="+/-" width="70" align="center" prop="plusMinus" sortable>
                <template #default="{ row }">
                  <span :class="row.plusMinus >= 0 ? 'plus-positive' : 'plus-negative'">{{ row.plusMinus >= 0 ? '+' : '' }}{{ row.plusMinus }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <div class="section-header" style="margin-top: 24px;">
            <div class="section-header-left">
              <h3 class="team-table-title">{{ boxScore?.awayTeam || '客队' }}</h3>
            </div>
          </div>
          <el-card shadow="never" class="boxscore-card">
            <el-table :data="sortedAwayPlayers" stripe v-loading="boxscoreLoading" empty-text="暂无数据" size="small">
              <el-table-column label="球员" min-width="140" fixed="left">
                <template #default="{ row }">
                  <div class="player-cell">
                    <span class="player-name" :class="{ 'player-starter': row.starter }">{{ row.playerName }}</span>
                    <el-tag v-if="row.starter" size="small" type="success" effect="dark" class="starter-tag">首发</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="分钟" width="70" align="center" prop="minutes" />
              <el-table-column label="得分" width="65" align="center" prop="points" sortable />
              <el-table-column label="篮板" width="65" align="center" prop="rebounds" sortable />
              <el-table-column label="助攻" width="65" align="center" prop="assists" sortable />
              <el-table-column label="抢断" width="65" align="center" prop="steals" />
              <el-table-column label="盖帽" width="65" align="center" prop="blocks" />
              <el-table-column label="失误" width="65" align="center" prop="turnovers" />
              <el-table-column label="投篮" width="110" align="center">
                <template #default="{ row }">{{ formatShooting(row.fgMade, row.fgAttempted, row.fgPct) }}</template>
              </el-table-column>
              <el-table-column label="三分" width="110" align="center">
                <template #default="{ row }">{{ formatShooting(row.threeMade, row.threeAttempted, row.threePct) }}</template>
              </el-table-column>
              <el-table-column label="罚球" width="110" align="center">
                <template #default="{ row }">{{ formatShooting(row.ftMade, row.ftAttempted, row.ftPct) }}</template>
              </el-table-column>
              <el-table-column label="+/-" width="70" align="center" prop="plusMinus" sortable>
                <template #default="{ row }">
                  <span :class="row.plusMinus >= 0 ? 'plus-positive' : 'plus-negative'">{{ row.plusMinus >= 0 ? '+' : '' }}{{ row.plusMinus }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchBoxScore, fetchQuarterScores } from '@/api/matchDetail'
import type { BoxScore, BoxScorePlayer, QuarterScore } from '@/api/types'
import { getTeamLogo } from '@/utils/teamLogos'

const route = useRoute()
const router = useRouter()

const gameId = computed(() => (route.query.gameId as string) || '')
const status = computed(() => (route.query.status as string) || 'FINISHED')
const returnTo = computed(() => (route.query.returnTo as string) || '')
const queryHomeTeam = computed(() => (route.query.homeTeam as string) || '')
const queryAwayTeam = computed(() => (route.query.awayTeam as string) || '')
const queryHomeScore = computed(() => {
  const s = route.query.homeScore as string
  return s ? Number(s) : null
})
const queryAwayScore = computed(() => {
  const s = route.query.awayScore as string
  return s ? Number(s) : null
})

function goBack() {
  if (returnTo.value) {
    router.push(returnTo.value)
  } else {
    router.push('/news')
  }
}

const boxScore = ref<BoxScore | null>(null)

// 当boxScore加载完成后，如果quarterScores为空，则从boxScore中获取
watch(boxScore, (newVal) => {
  if (newVal?.quarterScores?.length && !quarterScores.value.length) {
    quarterScores.value = newVal.quarterScores
  }
})
const quarterScores = ref<QuarterScore[]>([])
const loading = ref(false)
const boxscoreLoading = ref(false)
const quarterLoading = ref(false)

const homeTotalScore = computed(() => {
  // 优先使用查询参数中的官方分数
  if (queryHomeScore.value !== null) return String(queryHomeScore.value)
  if (!boxScore.value) return '--'
  return boxScore.value.homePlayers.reduce((sum, p) => sum + p.points, 0)
})

const homeTeamLogo = computed(() => {
  const team = boxScore.value?.homeTeam || queryHomeTeam.value
  return team ? getTeamLogo(team) : null
})
const awayTeamLogo = computed(() => {
  const team = boxScore.value?.awayTeam || queryAwayTeam.value
  return team ? getTeamLogo(team) : null
})

const awayTotalScore = computed(() => {
  // 优先使用查询参数中的官方分数
  if (queryAwayScore.value !== null) return String(queryAwayScore.value)
  if (!boxScore.value) return '--'
  return boxScore.value.awayPlayers.reduce((sum, p) => sum + p.points, 0)
})

function sortPlayers(players: BoxScorePlayer[]): BoxScorePlayer[] {
  return [...players].sort((a, b) => {
    if (a.starter !== b.starter) return a.starter ? -1 : 1
    return b.points - a.points
  })
}

const sortedHomePlayers = computed(() => sortPlayers(boxScore.value?.homePlayers ?? []))
const sortedAwayPlayers = computed(() => sortPlayers(boxScore.value?.awayPlayers ?? []))

function statusType(s: string) {
  const map: Record<string, string> = { SCHEDULED: 'warning', LIVE: 'danger', FINISHED: 'info' }
  return map[s] || 'info'
}

function statusLabel(s: string) {
  const map: Record<string, string> = { SCHEDULED: '未开始', LIVE: '进行中', FINISHED: '已结束' }
  return map[s] || s
}

function formatShooting(made: number, attempted: number, pct: number) {
  if (attempted === 0) return '0/0'
  return `${made}/${attempted} (${(pct * 100).toFixed(1)}%)`
}

function periodLabel(p: number) {
  if (p <= 4) return `第${p}节`
  return `OT${p - 4}`
}

async function loadBoxScore() {
  if (!gameId.value) return
  boxscoreLoading.value = true
  loading.value = true
  try {
    const { data } = await fetchBoxScore(gameId.value)
    boxScore.value = data
    // 如果boxScore包含quarterScores，直接使用
    if (data.quarterScores?.length) {
      quarterScores.value = data.quarterScores
    }
  } catch {
    ElMessage.error('加载比赛数据失败')
  } finally {
    boxscoreLoading.value = false
    loading.value = false
  }
}

async function loadQuarterScores() {
  if (!gameId.value) return
  // 如果boxScore已经加载了quarterScores，跳过单独的API调用
  if (quarterScores.value.length > 0) {
    return
  }
  quarterLoading.value = true
  try {
    const { data } = await fetchQuarterScores(gameId.value)
    if (data && data.length > 0) {
      quarterScores.value = data
    }
  } catch {
    // 加载失败时不做处理
  } finally {
    quarterLoading.value = false
  }
}


onMounted(async () => {
  // 并行加载数据
  await Promise.all([
    loadBoxScore(),
    loadQuarterScores()
  ])
})
</script>

<style scoped>
.page {
  max-width: 1200px;
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

/* 比赛头部 */
.match-hero {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  padding: 28px 32px;
  margin-bottom: 24px;
  position: relative;
  overflow: hidden;
  animation: matchHeroIn 0.6s var(--ease-smooth) forwards;
  opacity: 0;
  transform: translateY(16px);
}
@keyframes matchHeroIn { to { opacity: 1; transform: translateY(0); } }
.match-hero::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), var(--accent-light), var(--purple));
}
.match-hero::after {
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
.match-hero-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 1;
  flex-wrap: wrap;
  gap: 20px;
}
.match-hero-team {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
}
.match-hero-team--away {
  justify-content: flex-end;
}
.hero-team-icon {
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
.match-hero:hover .hero-team-icon {
  border-color: var(--purple);
  box-shadow: 0 0 16px var(--purple-glow);
}
.team-logo-img {
  width: 48px;
  height: 48px;
  object-fit: contain;
}
.hero-team-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hero-team-info--right {
  text-align: right;
}
.hero-team-name {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
}
.hero-team-label {
  font-size: 12px;
  color: var(--text-muted);
}
.match-hero-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.hero-score-display {
  display: flex;
  align-items: center;
  gap: 16px;
}
.hero-score {
  font-size: 48px;
  font-weight: 700;
  color: var(--accent);
  font-family: var(--font-heading);
  text-shadow: 0 0 30px var(--accent-glow);
  line-height: 1;
  letter-spacing: -1px;
}
.hero-score-sep {
  font-size: 32px;
  font-weight: 300;
  color: var(--text-dim);
}

/* LIVE 脉冲动画 */
.live-badge {
  animation: livePulse 1.5s ease-in-out infinite;
}
@keyframes livePulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 8px var(--danger-glow); }
  50% { opacity: 0.5; box-shadow: 0 0 16px var(--danger-glow); }
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
.team-table-title {
  margin: 0;
  font-size: 16px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-weight: 700;
  letter-spacing: 0.2px;
}

/* 逐节比分卡片 */
.quarter-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  margin-bottom: 24px;
  position: relative;
  overflow: hidden;
}
.quarter-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--warning), var(--accent));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}
.period-label {
  font-weight: 600;
  color: var(--text-secondary);
  font-family: var(--font-heading);
  letter-spacing: 0.2px;
}
.quarter-score {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-secondary);
  font-family: var(--font-heading);
}
.quarter-score--win {
  color: var(--accent);
  text-shadow: 0 0 12px var(--accent-glow);
}

/* Tabs */
.detail-tabs {
  margin-top: 4px;
}
.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}
.detail-tabs :deep(.el-tabs__nav-wrap::after) {
  background-color: var(--border-light) !important;
}
.detail-tabs :deep(.el-tabs__item) {
  color: var(--text-muted) !important;
  font-family: var(--font-heading);
  font-weight: 600;
  font-size: 14px;
  letter-spacing: 0.3px;
  transition: color var(--duration-fast) var(--ease-smooth);
}
.detail-tabs :deep(.el-tabs__item.is-active) {
  color: var(--accent) !important;
  font-weight: 700;
}
.detail-tabs :deep(.el-tabs__active-bar) {
  background-color: var(--accent) !important;
}

/* Box Score 表格卡片 */
.boxscore-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  overflow: hidden;
  position: relative;
}
.boxscore-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--purple), var(--accent));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}
.player-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.player-name {
  font-weight: 500;
  color: var(--text-secondary);
}
.player-starter {
  color: var(--text-primary);
  font-weight: 700;
}
.starter-tag {
  font-size: 10px !important;
  padding: 0 4px !important;
  height: 16px !important;
  line-height: 16px !important;
}
.plus-positive {
  color: var(--accent);
  font-weight: 600;
  text-shadow: 0 0 8px var(--accent-glow);
}
.plus-negative {
  color: var(--danger);
  font-weight: 600;
  text-shadow: 0 0 8px var(--danger-glow);
}

/* 节次筛选 */
.period-filter {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

/* Play-by-Play 卡片 */
.pbp-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  position: relative;
  overflow: hidden;
}
.pbp-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--cyan), var(--purple));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}
.pbp-list {
  max-height: 600px;
  overflow-y: auto;
}
.pbp-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
  transition: all var(--duration-fast) var(--ease-smooth);
}
.pbp-item:last-child {
  border-bottom: none;
}
.pbp-item:hover {
  background: var(--bg-hover);
  padding-left: 20px;
}
.pbp-clock {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 70px;
  flex-shrink: 0;
}
.pbp-period-tag {
  font-size: 10px;
  font-weight: 600;
  color: var(--text-muted);
  background: var(--bg-hover);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.pbp-time {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  font-family: var(--font-heading);
}
.pbp-desc {
  flex: 1;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  letter-spacing: 0.2px;
}
.pbp-score {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.pbp-score-home,
.pbp-score-away {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
}
.pbp-score-sep {
  color: var(--text-dim);
  font-weight: 300;
}
</style>
