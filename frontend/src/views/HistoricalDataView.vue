<template>
  <div class="page-wrapper animated-bg">
    <div class="bg-particles">
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
    </div>
    <div class="bg-grid"></div>

    <div class="page-inner">
      <el-card shadow="never" class="card slide-up-enter">
        <div class="section-head">
          <div>
            <h2>历史数据</h2>
            <p>赛季数据总览，查看联盟各项数据领袖与球队排名。</p>
          </div>
        </div>

        <!-- 赛季选择器 -->
        <div class="toolbar">
          <span class="toolbar-label">赛季</span>
          <el-select v-model="selectedSeason" style="width: 180px" @change="onSeasonChange">
            <el-option v-for="s in availableSeasons" :key="s" :value="s">
              <span>{{ formatSeasonLabel(s) }}</span>
              <el-tag v-if="s === currentSeason" size="small" type="success" style="margin-left: 8px">
                当前
              </el-tag>
            </el-option>
          </el-select>
          <span class="toolbar-label" style="margin-left: 12px;">球队</span>
          <el-select v-model="selectedTeam" clearable filterable placeholder="全部球队" style="width: 200px" @change="onTeamChange" :loading="teamsLoading">
            <el-option v-for="t in teamOptions" :key="t.id" :label="t.name" :value="t.name" />
          </el-select>
          <el-button type="primary" plain @click="refreshData" :loading="playersLoading || teamsLoading">
            刷新数据
          </el-button>
          <el-button v-if="auth.isAdmin" type="warning" plain @click="syncCurrentSeason" :loading="syncing">
            同步当前赛季
          </el-button>
        </div>

        <!-- 数据领袖卡片 -->
        <div class="leaders-grid stagger-in">
          <div class="leader-card" v-for="cat in leaderCategories" :key="cat.key">
            <div class="leader-card-header">
              <span class="leader-icon">{{ cat.icon }}</span>
              <span class="leader-title">{{ cat.label }}</span>
              <span class="leader-unit">{{ cat.unit }}</span>
            </div>
            <div class="leader-list">
              <div v-for="(p, i) in cat.players" :key="p.id" class="leader-row">
                <span class="leader-rank" :class="{ 'rank-top3': i < 3 }">{{ i + 1 }}</span>
                <router-link :to="{ path: '/players/detail', query: { id: p.nbaPlayerId || p.id, returnTo: '/history' } }" class="leader-name leader-link">{{ p.playerName || p.name }}</router-link>
                <router-link :to="{ path: '/teams/detail', query: { name: p.teamName } }" class="leader-team leader-link">{{ p.teamName }}</router-link>
                <span class="leader-value">{{ cat.getValue(p) }}</span>
              </div>
              <div v-if="cat.players.length === 0" class="leader-empty">暂无数据</div>
            </div>
          </div>
        </div>

        <!-- 球队战绩图表 -->
        <div class="chart-section">
          <div class="section-head" style="margin-top: 32px;">
            <div>
              <h2>球队战绩概览</h2>
              <p>按胜场数排序的全联盟球队战绩柱状图。</p>
            </div>
          </div>
          <div class="chart-container">
            <v-chart class="chart" :option="winsChartOption" autoresize v-if="teams.length > 0" />
            <el-empty v-else description="暂无球队数据" />
          </div>
        </div>

        <!-- 完整战绩表 -->
        <div class="standings-section">
          <div class="section-head" style="margin-top: 32px;">
            <div>
              <h2>完整战绩表</h2>
              <p>全联盟球队胜负场、胜率和排名。</p>
            </div>
          </div>
          <el-table :data="standingsData" border stripe v-loading="teamsLoading" class="standings-table">
            <el-table-column label="排名" width="70" align="center">
              <template #default="{ $index }">
                <span class="standings-rank" :class="{ 'rank-gold': $index < 3 }">{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="球队" min-width="160">
              <template #default="{ row }">
                <router-link :to="{ path: '/teams/detail', query: { name: row.teamName || row.name } }" class="standings-team-link">
                  <img v-if="getTeamLogo(row.teamName || row.name)" :src="getTeamLogo(row.teamName || row.name)" class="standings-logo" alt="" />
                  <span class="standings-team-name">{{ row.teamName || row.name }}</span>
                </router-link>
              </template>
            </el-table-column>
            <el-table-column prop="conference" label="分区" width="80" align="center" />
            <el-table-column prop="wins" label="胜" width="70" align="center" sortable />
            <el-table-column prop="losses" label="负" width="70" align="center" sortable />
            <el-table-column label="胜率" width="90" align="center" sortable :sort-method="(a: any, b: any) => winPctNum(a.wins, a.losses) - winPctNum(b.wins, b.losses)">
              <template #default="{ row }">
                <span class="win-pct" :class="{ 'win-pct-high': winPctNum(row.wins, row.losses) >= 0.6 }">{{ winPct(row.wins, row.losses) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="胜场差" width="90" align="center">
              <template #default="{ row }">
                {{ gamesBehind(row.wins, row.losses) }}
              </template>
            </el-table-column>
            <el-table-column label="胜场条" min-width="160">
              <template #default="{ row }">
                <div class="win-bar-wrap">
                  <div class="win-bar" :style="{ width: winBarWidth(row.wins, row.losses) }"></div>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { fetchPlayers } from '@/api/player'
import { fetchTeams } from '@/api/team'
import { getTeamLogo } from '@/utils/teamLogos'
import { getAvailableSeasons, getPlayersBySeason, getTeamsBySeason, syncHistoricalSeason } from '@/api/historical'
import { useAuthStore } from '@/stores/auth'
import type { Player, Team } from '@/api/types'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent, LegendComponent])

const router = useRouter()
const auth = useAuthStore()
const teamsLoading = ref(false)
const playersLoading = ref(false)
const syncing = ref(false)
const teamOptions = ref<Team[]>([])
const selectedTeam = ref('')
const players = ref<any[]>([])
const teams = ref<any[]>([])
const isHistoricalMode = ref(false)

// 赛季选择器
const currentSeason = ref('')
const availableSeasons = ref<string[]>([])
const selectedSeason = ref('')

// 格式化赛季标签
function formatSeasonLabel(season: string): string {
  const parts = season.split('-')
  return `${parts[0]}-${parts[1]} 赛季`
}

// 根据球队名获取球队ID
function getTeamIdByName(teamName: string): number | undefined {
  // 先从 teamOptions 中查找
  const option = teamOptions.value.find(t => t.name === teamName)
  if (option?.id) return option.id

  // 如果是历史数据模式，从 teams 中查找
  if (isHistoricalMode.value) {
    const team = teams.value.find((t: any) => t.teamName === teamName || t.name === teamName)
    if (team?.id) return team.id
  }

  return undefined
}

const leaderCategories = computed(() => {
  const sorted = (key: string, desc = true) => {
    const arr = [...players.value].filter((p: any) => typeof p[key] === 'number' && p[key] > 0)
    arr.sort((a: any, b: any) => desc ? b[key] - a[key] : a[key] - b[key])
    return arr.slice(0, 10)
  }

  // 历史数据和当前数据使用相同的字段名
  return [
    {
      key: 'ppg', icon: '🏀', label: '得分王', unit: 'PPG',
      players: sorted('pointsPerGame'),
      getValue: (p: any) => (p.pointsPerGame || 0).toFixed(1),
    },
    {
      key: 'rpg', icon: '🏅', label: '篮板王', unit: 'RPG',
      players: sorted('reboundsPerGame'),
      getValue: (p: any) => (p.reboundsPerGame || 0).toFixed(1),
    },
    {
      key: 'apg', icon: '🎯', label: '助攻王', unit: 'APG',
      players: sorted('assistsPerGame'),
      getValue: (p: any) => (p.assistsPerGame || 0).toFixed(1),
    },
    {
      key: 'spg', icon: '🖐️', label: '抢断王', unit: 'SPG',
      players: sorted('stealsPerGame'),
      getValue: (p: any) => (p.stealsPerGame || 0).toFixed(1),
    },
    {
      key: 'bpg', icon: '🛡️', label: '盖帽王', unit: 'BPG',
      players: sorted('blocksPerGame'),
      getValue: (p: any) => (p.blocksPerGame || 0).toFixed(1),
    },
  ]
})

const standingsData = computed(() => {
  return [...teams.value].sort((a: any, b: any) => b.wins - a.wins || a.losses - b.losses)
})

const winsChartOption = computed(() => {
  const sorted = [...teams.value].sort((a, b) => b.wins - a.wins)
  const names = sorted.map(t => t.teamName || t.name)
  const wins = sorted.map(t => t.wins)
  const losses = sorted.map(t => t.losses)
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(22, 27, 34, 0.95)',
      borderColor: '#1E2130',
      textStyle: { color: '#F0F2F5', fontFamily: 'Rajdhani, Space Grotesk, sans-serif' },
    },
    legend: {
      data: ['胜场', '负场'],
      textStyle: { color: '#9CA3B0', fontFamily: 'Rajdhani, Space Grotesk, sans-serif' },
      top: 8,
    },
    grid: { left: '3%', right: '4%', bottom: '8%', top: '15%', containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: {
        color: '#9CA3B0',
        rotate: 45,
        fontSize: 11,
        fontFamily: 'Rajdhani, Space Grotesk, sans-serif',
      },
      axisLine: { lineStyle: { color: '#1E2130' } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#6B7280', fontFamily: 'Rajdhani, Space Grotesk, sans-serif' },
      splitLine: { lineStyle: { color: '#1E2130', type: 'dashed' } },
    },
    series: [
      {
        name: '胜场',
        type: 'bar',
        stack: 'record',
        data: wins,
        itemStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#00FF88' },
              { offset: 1, color: '#00CC6A' },
            ],
          },
          borderRadius: [4, 4, 0, 0],
        },
        barMaxWidth: 24,
      },
      {
        name: '负场',
        type: 'bar',
        stack: 'record',
        data: losses,
        itemStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#6B7280' },
              { offset: 1, color: '#4B5563' },
            ],
          },
          borderRadius: [0, 0, 4, 4],
        },
        barMaxWidth: 24,
      },
    ],
  }
})

function winPct(w: number, l: number) {
  const t = w + l
  if (t === 0) return '0.0%'
  return ((w / t) * 100).toFixed(1) + '%'
}

function winPctNum(w: number, l: number) {
  const t = w + l
  return t === 0 ? 0 : w / t
}

function gamesBehind(w: number, l: number) {
  if (teams.value.length === 0) return '—'
  const best = Math.max(...teams.value.map(t => t.wins - t.losses))
  const diff = best - (w - l)
  return diff === 0 ? '—' : (diff / 2).toFixed(1)
}

function winBarWidth(w: number, _l: number) {
  const maxWins = Math.max(...teams.value.map(t => t.wins), 1)
  return ((w / maxWins) * 100) + '%'
}

async function loadTeams() {
  teamsLoading.value = true
  try {
    const { data } = await fetchTeams({ page: 0, size: 100 })
    teamOptions.value = data.content
    if (!isHistoricalMode.value) {
      teams.value = data.content
    }
  } catch {
    ElMessage.error('加载球队数据失败')
  } finally {
    teamsLoading.value = false
  }
}

// 更新球队选项（从历史数据中提取）
function updateTeamOptionsFromHistorical() {
  if (isHistoricalMode.value && teams.value.length > 0) {
    // 从历史球队数据中提取球队名
    const historicalTeams = teams.value.map((t: any) => ({
      id: t.nbaTeamId || t.id,
      name: t.teamName || t.name,
      conference: t.conference,
      wins: t.wins,
      losses: t.losses
    }))
    teamOptions.value = historicalTeams
  }
}

async function loadPlayers() {
  playersLoading.value = true
  try {
    if (isHistoricalMode.value) {
      // 历史模式：从历史数据API加载
      const playersData = await getPlayersBySeason(selectedSeason.value, 'pointsPerGame', 500)
      let list = playersData
      if (selectedTeam.value) {
        list = list.filter((p: any) => p.teamName === selectedTeam.value)
      }
      players.value = list
    } else {
      // 当前赛季模式：从当前赛季API加载
      const params: Record<string, unknown> = {
        page: 0,
        size: 500,
      }
      const { data } = await fetchPlayers(params as { page: number; size: number; q?: string; teamId?: number; position?: string })
      let list = data.content
      if (selectedTeam.value) {
        list = list.filter((p: Player) => p.teamName === selectedTeam.value)
      }
      players.value = list
    }
  } catch {
    ElMessage.error('加载球员数据失败')
  } finally {
    playersLoading.value = false
  }
}

// 加载历史赛季数据
async function loadHistoricalData(season: string) {
  playersLoading.value = true
  teamsLoading.value = true
  isHistoricalMode.value = true

  try {
    const [playersData, teamsData] = await Promise.all([
      getPlayersBySeason(season, 'pointsPerGame', 500),
      getTeamsBySeason(season)
    ])

    // 应用球队过滤
    let list = playersData
    if (selectedTeam.value) {
      list = list.filter((p: any) => p.teamName === selectedTeam.value)
    }

    players.value = list
    teams.value = teamsData

    // 更新球队选项
    updateTeamOptionsFromHistorical()

    ElMessage.success(`已加载 ${formatSeasonLabel(season)} 数据`)
  } catch (error) {
    ElMessage.error(`加载 ${formatSeasonLabel(season)} 数据失败`)
    // 回退到当前赛季
    selectedSeason.value = currentSeason.value
    isHistoricalMode.value = false
    await refreshData()
  } finally {
    playersLoading.value = false
    teamsLoading.value = false
  }
}

// 赛季切换
async function onSeasonChange() {
  if (selectedSeason.value === currentSeason.value) {
    isHistoricalMode.value = false
    await refreshData()
  } else {
    await loadHistoricalData(selectedSeason.value)
  }
}

// 球队切换（本地过滤，不重新请求API）
function onTeamChange() {
  // 重新加载数据（会应用过滤）
  loadPlayers()
}

// 刷新数据
async function refreshData() {
  await Promise.all([loadTeams(), loadPlayers()])
}

// 同步当前赛季
async function syncCurrentSeason() {
  syncing.value = true
  try {
    const result = await syncHistoricalSeason(currentSeason.value)
    ElMessage.success(result.message)
    // 延迟刷新
    setTimeout(() => loadHistoricalData(selectedSeason.value), 3000)
  } catch (error) {
    ElMessage.error('同步失败')
  } finally {
    syncing.value = false
  }
}

// 初始化
onMounted(async () => {
  try {
    const seasonsData = await getAvailableSeasons()
    currentSeason.value = seasonsData.current
    availableSeasons.value = seasonsData.available
    selectedSeason.value = currentSeason.value

    await Promise.all([loadTeams(), loadPlayers()])
  } catch (error) {
    ElMessage.error('加载赛季列表失败')
    await Promise.all([loadTeams(), loadPlayers()])
  }
})
</script>

<style scoped>
.page-wrapper {
  min-height: calc(100vh - 108px);
  position: relative;
  border-radius: var(--radius-lg);
  padding: 0;
  animation: pageFadeIn 0.4s var(--ease-smooth) forwards;
  opacity: 0;
  transform: translateY(12px);
}
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
  align-items: center;
}
.toolbar-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  font-family: var(--font-heading);
}
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-light);
  position: relative;
}
.section-head::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 40px;
  height: 2px;
  background: linear-gradient(90deg, var(--purple), transparent);
  border-radius: 1px;
}
.section-head h2 {
  margin: 0 0 6px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.3px;
}
.section-head p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}
.card {
  max-width: 1300px;
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24) !important;
  transition: all var(--duration-normal) var(--ease-smooth);
  position: relative;
  z-index: 1;
  overflow: hidden;
}
.card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--purple), var(--accent), var(--cyan));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}

/* Leaders grid */
.leaders-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 16px;
  margin-bottom: 8px;
}
.leader-card {
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: all var(--duration-fast) var(--ease-smooth);
}
.leader-card:hover {
  border-color: var(--purple);
  transform: translateY(-2px);
  box-shadow: 0 4px 16px var(--purple-glow);
}
.leader-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-light);
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.06), transparent);
}
.leader-icon {
  font-size: 18px;
}
.leader-title {
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.3px;
}
.leader-unit {
  margin-left: auto;
  font-family: var(--font-heading);
  font-size: 10px;
  font-weight: 600;
  color: var(--text-muted);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  letter-spacing: 0.5px;
}
.leader-list {
  padding: 6px 0;
}
.leader-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 14px;
  font-size: 13px;
  transition: background var(--duration-fast) var(--ease-smooth);
}
.leader-row:hover {
  background: rgba(139, 92, 246, 0.04);
}
.leader-rank {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-heading);
  font-size: 11px;
  font-weight: 700;
  color: var(--text-muted);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 50%;
  flex-shrink: 0;
}
.rank-top3 {
  background: linear-gradient(135deg, var(--purple), var(--accent));
  color: #fff;
  border-color: var(--purple);
}
.leader-name {
  flex: 1;
  color: var(--text-primary);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.leader-team {
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
}
.leader-link {
  text-decoration: none;
  cursor: pointer;
  transition: color 0.2s ease;
}
.leader-link:hover {
  color: var(--accent);
  text-decoration: underline;
}
.leader-value {
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 700;
  color: var(--accent);
  min-width: 40px;
  text-align: right;
}
.leader-empty {
  padding: 16px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}

/* Chart */
.chart-container {
  min-height: 400px;
  padding: 16px 0;
}
.chart {
  width: 100%;
  height: 400px;
}

/* Standings */
.standings-logo {
  width: 22px;
  height: 22px;
  object-fit: contain;
  margin-right: 8px;
  vertical-align: middle;
}
.standings-team-name {
  font-weight: 600;
  color: var(--text-primary);
}
.standings-team-link {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  cursor: pointer;
  transition: opacity 0.2s ease;
}
.standings-team-link:hover {
  opacity: 0.8;
}
.standings-team-link:hover .standings-team-name {
  color: var(--accent);
  text-decoration: underline;
}
.standings-rank {
  font-family: var(--font-heading);
  font-weight: 700;
  color: var(--text-secondary);
}
.rank-gold {
  color: var(--accent);
}
.win-pct {
  font-family: var(--font-heading);
  font-weight: 600;
  color: var(--text-secondary);
}
.win-pct-high {
  color: var(--accent);
}
.win-bar-wrap {
  height: 8px;
  background: var(--bg-hover);
  border-radius: 4px;
  overflow: hidden;
}
.win-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--purple), var(--accent));
  border-radius: 4px;
  transition: width 0.6s var(--ease-smooth);
}

/* Form overrides */
:deep(.el-input__wrapper) {
  background: var(--bg-input) !important;
  border: 1.5px solid var(--border-light) !important;
  box-shadow: none !important;
  border-radius: 8px !important;
}
:deep(.el-input__wrapper:hover) {
  border-color: var(--border-medium) !important;
}
:deep(.el-input__wrapper.is-focus) {
  border-color: var(--accent) !important;
  box-shadow: 0 0 0 3px var(--accent-glow) !important;
}
:deep(.el-input__inner) { color: var(--text-primary) !important; font-family: var(--font-body); }
:deep(.el-input__inner::placeholder) { color: var(--text-dim) !important; }

:deep(.el-select .el-select__wrapper) {
  background: var(--bg-input) !important;
  border: 1.5px solid var(--border-light) !important;
  box-shadow: none !important;
  border-radius: 8px !important;
}
:deep(.el-select .el-select__wrapper:hover) {
  border-color: var(--border-medium) !important;
}
:deep(.el-select .el-select__wrapper.is-focused) {
  border-color: var(--accent) !important;
  box-shadow: 0 0 0 3px var(--accent-glow) !important;
}
:deep(.el-select .el-select__placeholder),
:deep(.el-select .el-select__selected-item span) {
  color: var(--text-primary) !important;
}
:deep(.el-select .el-select__placeholder.is-transparent) {
  color: var(--text-dim) !important;
}
</style>
