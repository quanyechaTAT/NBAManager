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
        <SportsHeader
          eyebrow="NBA HISTORICAL ARCHIVES"
          title="历史长卷与数据丰碑"
          subtitle="重温数十年辉煌赛季，探寻联盟各项数据王座与历史战绩沉淀"
        />

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

        <!-- 窄屏按类别阅读，避免五组榜单挤在同一行 -->
        <div class="leader-category-tabs" role="tablist" aria-label="数据领袖类别">
          <button
            v-for="cat in leaderCategories"
            :key="cat.key"
            type="button"
            role="tab"
            :aria-selected="activeLeaderCategory === cat.key"
            :class="{ 'is-active': activeLeaderCategory === cat.key }"
            @click="activeLeaderCategory = cat.key"
          >{{ cat.label }}</button>
        </div>
        <!-- 数据领袖卡片 -->
        <div class="leaders-grid stagger-in">
          <div class="leader-card" v-for="cat in leaderCategories" :key="cat.key" :class="{ 'is-active': activeLeaderCategory === cat.key }">
            <div class="leader-card-header">
              <span class="leader-icon" v-html="cat.icon" />
              <span class="leader-title">{{ cat.label }}</span>
              <span class="leader-unit">{{ cat.unit }}</span>
            </div>
            <div class="leader-list">
              <div v-for="(p, i) in cat.players" :key="p.id" class="leader-row">
                <span
                  class="leader-row-bar"
                  :style="{ width: leaderBarWidth(cat, p) + '%' }"
                  aria-hidden="true"
                />
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
              <h2>全联盟胜场分布对比</h2>
              <p>横向对比联盟各队常规赛胜场与胜率梯度分布</p>
            </div>
          </div>
          <div class="chart-container">
            <v-chart class="chart" :option="winsChartOption" autoresize v-if="teams.length > 0" />
            <AppEmpty v-else description="暂无球队数据" />
          </div>
        </div>

        <!-- 完整战绩表 -->
        <div class="standings-section">
          <div class="section-head" style="margin-top: 32px;">
            <div>
              <h2>赛季完整战绩排位表</h2>
              <p>全联盟胜负场次、胜率及胜场差（GB）综合数据统计</p>
            </div>
          </div>
          <p class="mobile-scroll-hint">左右滑动可查看完整战绩</p>
          <el-table :data="standingsData" border stripe v-loading="teamsLoading" class="standings-table">
            <el-table-column label="排名" width="70" align="center" fixed="left">
              <template #default="{ $index }">
                <span class="standings-rank" :class="{ 'rank-gold': $index < 3 }">{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="球队" min-width="160" fixed="left">
              <template #default="{ row }">
                <router-link :to="{ path: '/teams/detail', query: { name: row.teamName || row.name } }" class="standings-team-link">
                  <img v-if="getTeamLogo(row.teamName || row.name)" :src="getTeamLogo(row.teamName || row.name)" class="standings-logo" alt="" />
                  <span class="standings-team-name">{{ row.teamName || row.name }}</span>
                </router-link>
              </template>
            </el-table-column>
            <el-table-column prop="conference" label="分区 CONF" width="96" align="center" />
            <el-table-column prop="wins" label="胜 W" width="76" align="center" sortable />
            <el-table-column prop="losses" label="负 L" width="76" align="center" sortable />
            <el-table-column label="胜率 PCT" width="96" align="center" sortable :sort-method="(a: any, b: any) => winPctNum(a.wins, a.losses) - winPctNum(b.wins, b.losses)">
              <template #default="{ row }">
                <span class="win-pct" :class="{ 'win-pct-high': winPctNum(row.wins, row.losses) >= 0.6 }">{{ winPct(row.wins, row.losses) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="胜场差 GB" width="96" align="center">
              <template #default="{ row }">
                {{ gamesBehind(row.wins, row.losses) }}
              </template>
            </el-table-column>
            <el-table-column label="胜场阶梯 WIN RATIO" min-width="170">
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
import { useChartTheme } from '@/composables/useChartTheme'
import AppEmpty from '@/components/common/AppEmpty.vue'
import SportsHeader from '@/components/common/SportsHeader.vue'
import { fetchAllPages } from '@/utils/fetchAllPages'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent, LegendComponent])

const auth = useAuthStore()
const { colors, tooltipStyle, legendStyle, xAxisStyle, yAxisStyle } = useChartTheme()
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
const activeLeaderCategory = ref('ppg')

// 格式化赛季标签
function formatSeasonLabel(season: string): string {
  const parts = season.split('-')
  return `${parts[0]}-${parts[1]} 赛季`
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
      key: 'ppg', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>', label: '得分王', unit: 'PPG',
      players: sorted('pointsPerGame'),
      getValue: (p: any) => (p.pointsPerGame || 0).toFixed(1),
      getRaw: (p: any) => p.pointsPerGame || 0,
    },
    {
      key: 'rpg', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15"><path d="M7.21 15 2.66 7.14a2 2 0 0 1 .13-2.2L4.4 2.8A2 2 0 0 1 6 2h12a2 2 0 0 1 1.6.8l1.6 2.14a2 2 0 0 1 .14 2.2L16.79 15"/><path d="M11 12 5.12 2.2"/><path d="m13 12 5.88-9.8"/><path d="M8 7h8"/><circle cx="12" cy="17" r="5"/><path d="M12 18v-2h-.5"/></svg>', label: '篮板王', unit: 'RPG',
      players: sorted('reboundsPerGame'),
      getValue: (p: any) => (p.reboundsPerGame || 0).toFixed(1),
      getRaw: (p: any) => p.reboundsPerGame || 0,
    },
    {
      key: 'apg', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/></svg>', label: '助攻王', unit: 'APG',
      players: sorted('assistsPerGame'),
      getValue: (p: any) => (p.assistsPerGame || 0).toFixed(1),
      getRaw: (p: any) => p.assistsPerGame || 0,
    },
    {
      key: 'spg', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15"><path d="M18 11V6a2 2 0 0 0-2-2v0a2 2 0 0 0-2 2v0"/><path d="M14 10V4a2 2 0 0 0-2-2v0a2 2 0 0 0-2 2v2"/><path d="M10 10.5V6a2 2 0 0 0-2-2v0a2 2 0 0 0-2 2v8"/><path d="M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15"/></svg>', label: '抢断王', unit: 'SPG',
      players: sorted('stealsPerGame'),
      getValue: (p: any) => (p.stealsPerGame || 0).toFixed(1),
      getRaw: (p: any) => p.stealsPerGame || 0,
    },
    {
      key: 'bpg', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15"><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/></svg>', label: '盖帽王', unit: 'BPG',
      players: sorted('blocksPerGame'),
      getValue: (p: any) => (p.blocksPerGame || 0).toFixed(1),
      getRaw: (p: any) => p.blocksPerGame || 0,
    },
  ]
})

/** 榜单行背景条宽度：相对该榜第一名数值的比例 */
function leaderBarWidth(cat: { players: any[]; getRaw: (p: any) => number }, p: any): number {
  const leader = cat.players[0]
  const max = leader ? cat.getRaw(leader) : 0
  if (max <= 0) return 0
  return Math.min((cat.getRaw(p) / max) * 100, 100)
}

const standingsData = computed(() => {
  return [...teams.value].sort((a: any, b: any) => b.wins - a.wins || a.losses - b.losses)
})

const winsChartOption = computed(() => {
  const sorted = [...teams.value].sort((a, b) => b.wins - a.wins)
  const names = sorted.map(t => t.teamName || t.name)
  const wins = sorted.map(t => t.wins)
  const losses = sorted.map(t => t.losses)
  const c = colors.value
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      ...tooltipStyle.value,
    },
    legend: {
      data: ['胜场', '负场'],
      ...legendStyle.value,
      top: 8,
    },
    grid: { left: '3%', right: '4%', bottom: '8%', top: '15%', containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      ...xAxisStyle.value,
      axisLabel: { ...xAxisStyle.value.axisLabel, rotate: 45 },
    },
    yAxis: {
      type: 'value',
      ...yAxisStyle.value,
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
              { offset: 0, color: c.barWin1 },
              { offset: 1, color: c.barWin2 },
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
              { offset: 0, color: c.barLose1 },
              { offset: 1, color: c.barLose2 },
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
      city: t.city || t.teamCity || '',
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
      // 当前赛季模式：从当前赛季API加载（size 上限 100，逐页取全量）
      let list = await fetchAllPages<Player>(p => fetchPlayers({ page: p, size: 100 }).then(r => r.data))
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
  background: linear-gradient(90deg, var(--purple), var(--cyan), var(--accent));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}

/* Leaders grid */
.leaders-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 16px;
  margin-bottom: 8px;
}
.leader-category-tabs,
.mobile-scroll-hint { display: none; }
.leader-card {
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: all var(--duration-fast) var(--ease-smooth);
}
.leader-card:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: 0 4px 16px var(--accent-glow);
}
.leader-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-light);
  background: linear-gradient(135deg, var(--accent-lighter), transparent);
}
.leader-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  background: var(--accent-lighter);
  color: var(--accent);
  flex-shrink: 0;
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
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 14px;
  font-size: 13px;
  transition: background var(--duration-fast) var(--ease-smooth);
}
.leader-row > * {
  position: relative;
  z-index: 1;
}
.leader-row-bar {
  position: absolute;
  left: 6px;
  top: 3px;
  bottom: 3px;
  z-index: 0;
  border-radius: 4px;
  background: color-mix(in srgb, var(--accent) 9%, transparent);
  pointer-events: none;
}
.leader-row:nth-child(1) .leader-row-bar {
  background: color-mix(in srgb, var(--accent) 16%, transparent);
}
.leader-row:hover {
  background: var(--bg-hover);
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
  background: linear-gradient(135deg, var(--accent-light), var(--accent));
  color: var(--accent-contrast, #04202A);
  border-color: var(--accent);
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

@media (max-width: 900px) {
  .leader-category-tabs {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    margin: 16px 0 12px;
    padding-bottom: 4px;
    scrollbar-width: none;
  }
  .leader-category-tabs::-webkit-scrollbar { display: none; }
  .leader-category-tabs button {
    flex: 0 0 auto;
    min-height: 40px;
    padding: 0 14px;
    border: 1px solid var(--border-light);
    border-radius: 999px;
    background: var(--bg-hover);
    color: var(--text-secondary);
    font: inherit;
    font-size: 13px;
    cursor: pointer;
  }
  .leader-category-tabs button.is-active {
    border-color: var(--accent);
    background: var(--accent-soft);
    color: var(--accent);
    font-weight: 700;
  }
  .leaders-grid { display: block; }
  .leader-card { display: none; }
  .leader-card.is-active { display: block; }
  .leader-row { min-height: 40px; }
}

@media (max-width: 768px) {
  .mobile-scroll-hint {
    display: block;
    margin: 0 0 10px;
    color: var(--text-muted);
    font-size: 12px;
  }
  .chart-container { overflow-x: auto; }
  .chart { min-width: 640px; }
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
