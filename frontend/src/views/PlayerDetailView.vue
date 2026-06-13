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
          返回
        </el-button>
      </div>

      <!-- 球员信息卡片 -->
      <div class="player-hero" v-loading="detailLoading">
        <div class="player-hero-inner">
          <div class="player-hero-left">
            <div class="player-avatar" @click="showHeadshotPreview = true" :class="{ 'player-avatar--clickable': player?.nbaPlayerId }">
              <img v-if="player?.nbaPlayerId" :src="headshotUrl" :alt="player?.name" class="player-headshot" @error="onHeadshotError" />
              <span v-else>{{ player?.name?.charAt(0) || '?' }}</span>
              <div v-if="player?.nbaPlayerId" class="avatar-zoom-hint">🔍</div>
            </div>
            <div>
              <h1 class="player-name">{{ player?.name }}</h1>
              <p class="player-sub">{{ player?.teamName }} · {{ player?.position }} · #{{ player?.jerseyNumber }}</p>
              <p class="player-extra">{{ player?.height }} · {{ player?.weight }}磅 · {{ player?.country }}</p>
              <el-button v-if="auth.token" :type="isFollowed ? 'success' : 'primary'" plain size="small" @click="toggleFollow" class="follow-btn">
                {{ isFollowed ? '★ 已关注' : '+ 关注' }}
              </el-button>
              <el-button plain size="small" @click="goCompare" class="follow-btn">🔄 对比</el-button>
            </div>
          </div>
          <div class="player-hero-stats">
            <div class="hero-stat">
              <span class="hero-stat-num hero-stat-num--pts">{{ player?.pointsPerGame?.toFixed(1) }}</span>
              <span class="hero-stat-label">场均得分</span>
            </div>
            <div class="hero-stat-divider"></div>
            <div class="hero-stat">
              <span class="hero-stat-num">{{ player?.reboundsPerGame?.toFixed(1) }}</span>
              <span class="hero-stat-label">场均篮板</span>
            </div>
            <div class="hero-stat-divider"></div>
            <div class="hero-stat">
              <span class="hero-stat-num">{{ player?.assistsPerGame?.toFixed(1) }}</span>
              <span class="hero-stat-label">场均助攻</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 关键数据卡片 -->
      <el-row :gutter="16" class="stat-row">
        <el-col :xs="12" :sm="8" :md="4" v-for="item in keyStats" :key="item.label">
          <div class="key-stat-card">
            <span class="key-stat-label">{{ item.label }}</span>
            <strong class="key-stat-num">{{ item.value }}</strong>
          </div>
        </el-col>
      </el-row>

      <!-- 图表区域 - Tabs -->
      <div class="chart-tabs" style="margin-top: 20px;">
        <div class="chart-tabs-header">
          <button
            v-for="tab in chartTabs"
            :key="tab.key"
            class="chart-tab-btn"
            :class="{ 'chart-tab-btn--active': activeChartTab === tab.key }"
            @click="activeChartTab = tab.key"
          >
            <span class="chart-tab-icon">{{ tab.icon }}</span>
            {{ tab.label }}
          </button>
        </div>

        <!-- 能力分析 + 生涯趋势 -->
        <div v-show="activeChartTab === 'analysis'" class="chart-tab-content">
          <el-row :gutter="16">
            <el-col :xs="24" :md="10">
              <el-card shadow="never" class="chart-card">
                <div class="card-head">
                  <div class="card-head-title">
                    <span>雷达图</span>
                    <small>能力六维分析</small>
                  </div>
                </div>
                <div class="chart-body">
                  <v-chart class="chart" :option="radarOption" autoresize v-if="detailLoaded" />
                  <el-skeleton v-else :rows="6" animated />
                </div>
              </el-card>
            </el-col>
            <el-col :xs="24" :md="14">
              <el-card shadow="never" class="chart-card">
                <div class="card-head">
                  <div class="card-head-title">
                    <span>生涯趋势</span>
                    <small>场均数据变化</small>
                  </div>
                </div>
                <div class="chart-body">
                  <v-chart class="chart" :option="careerOption" autoresize v-if="careerLoaded" />
                  <el-skeleton v-else :rows="6" animated />
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>

        <!-- 投篮热图 -->
        <div v-show="activeChartTab === 'shotchart'" class="chart-tab-content">
          <el-card shadow="never" class="chart-card">
            <div class="card-head">
              <div class="card-head-title">
                <span>投篮热图</span>
                <small>模拟投篮分布</small>
              </div>
            </div>
            <div class="chart-body shotchart-body">
              <div class="shotchart-layout">
                <div class="shotchart-court">
                  <ShotChart :shots="displayShots" :size="440" />
                </div>
                <div class="shotchart-sidebar">
                  <div class="shotchart-summary">
                    <h4 class="shotchart-summary-title">投篮摘要</h4>
                    <div class="shotchart-stat-grid">
                      <div class="shotchart-stat-item">
                        <span class="shotchart-stat-label">总出手</span>
                        <span class="shotchart-stat-value">{{ displayShots.length }}</span>
                      </div>
                      <div class="shotchart-stat-item">
                        <span class="shotchart-stat-label">命中</span>
                        <span class="shotchart-stat-value shotchart-stat-value--made">{{ displayShots.filter(s => s.made).length }}</span>
                      </div>
                      <div class="shotchart-stat-item">
                        <span class="shotchart-stat-label">命中率</span>
                        <span class="shotchart-stat-value">{{ displayShots.length ? ((displayShots.filter(s => s.made).length / displayShots.length) * 100).toFixed(1) : '0.0' }}%</span>
                      </div>
                    </div>
                    <div class="shotchart-zone-list">
                      <div class="shotchart-zone-item">
                        <span class="zone-color zone-color--paint"></span>
                        <span class="zone-name">禁区</span>
                        <span class="zone-count">{{ paintShots.length }}次</span>
                      </div>
                      <div class="shotchart-zone-item">
                        <span class="zone-color zone-color--mid"></span>
                        <span class="zone-name">中距离</span>
                        <span class="zone-count">{{ midRangeShots.length }}次</span>
                      </div>
                      <div class="shotchart-zone-item">
                        <span class="zone-color zone-color--three"></span>
                        <span class="zone-name">三分球</span>
                        <span class="zone-count">{{ threePointShots.length }}次</span>
                      </div>
                      <div class="shotchart-zone-item">
                        <span class="zone-color zone-color--ft"></span>
                        <span class="zone-name">罚球</span>
                        <span class="zone-count">{{ freeThrowShots.length }}次</span>
                      </div>
                    </div>
                  </div>
                  <p class="shotchart-note">* 基于球员投篮命中率生成的模拟数据</p>
                </div>
              </div>
            </div>
          </el-card>
        </div>
      </div>

      <!-- 近期比赛日志 -->
      <div class="section-header" style="margin-top: 28px;">
        <div class="section-header-left">
          <h2>近期比赛</h2>
        </div>
      </div>
      <el-card shadow="never" class="gamelog-card">
        <el-table :data="gameLog" stripe v-loading="gameLogLoading" empty-text="暂无比赛记录" size="small">
          <el-table-column label="日期" width="110">
            <template #default="{ row }">{{ formatDate(row.matchDate) }}</template>
          </el-table-column>
          <el-table-column label="主客场" width="65" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isHome ? 'success' : 'info'" size="small" effect="light">
                {{ row.isHome ? '主场' : '客场' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="对手" min-width="90">
            <template #default="{ row }">
              <router-link
                v-if="getTeamIdByName(row.opponent)"
                :to="{ path: '/teams/detail', query: { id: getTeamIdByName(row.opponent) } }"
                class="opponent-link"
              >{{ row.opponent }}</router-link>
              <span v-else>{{ row.opponent }}</span>
            </template>
          </el-table-column>
          <el-table-column label="比分" width="120" align="center">
            <template #default="{ row }">
              <div class="score-cell">
                <span class="score-team" :class="{ 'score-win': row.result === 'W' }">{{ row.teamScore || '-' }}</span>
                <span class="score-sep">:</span>
                <span class="score-opp">{{ row.opponentScore || '-' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="65" align="center">
            <template #default="{ row }">
              <el-tag :type="row.result === 'W' ? 'success' : 'danger'" size="small" effect="light">
                {{ row.result === 'W' ? '胜' : '负' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="详情" width="60" align="center">
            <template #default="{ row }">
              <el-button
                v-if="row.gameId"
                link type="primary" size="small"
                @click="goToMatchDetail(row)"
              >📊</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            background
            layout="total, prev, pager, next"
            :total="gameLogTotal"
            v-model:current-page="gameLogPage"
            :page-size="10"
            @current-change="loadGameLog"
          />
        </div>
      </el-card>
    </div>
  </div>

    <!-- 头像放大预览 - 使用 Teleport 移到 body 层级 -->
    <Teleport to="body">
      <Transition name="headshot-fade">
        <div v-if="showHeadshotPreview" class="headshot-overlay" @click.self="showHeadshotPreview = false">
          <div class="headshot-dialog">
            <div class="headshot-dialog-header">
              <span class="headshot-dialog-title">{{ player?.name }}</span>
              <button class="headshot-dialog-close" @click="showHeadshotPreview = false">✕</button>
            </div>
            <div class="headshot-dialog-body">
              <img :src="headshotUrl" :alt="player?.name" class="headshot-preview-img" @error="onHeadshotError" />
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { RadarChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { fetchPlayerDetail, fetchPlayerCareer, fetchPlayerGameLog } from '@/api/playerDetail'
import type { PlayerDetail, PlayerCareerStat, PlayerGameLogItem } from '@/api/playerDetail'
import { toggleFavorite, fetchFollowedPlayerIds } from '@/api/favorite'
import { useAuthStore } from '@/stores/auth'
import ShotChart from '@/components/ShotChart.vue'
import type { ShotData } from '@/api/types'
import request from '@/utils/request'

use([RadarChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent, CanvasRenderer])

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const playerId = computed(() => Number(route.query.id) || 0)
const headshotUrl = computed(() => {
  const id = player.value?.nbaPlayerId
  if (!id) return ''
  // 使用可靠的CDN
  return `https://ak-static.cms.nba.com/wp-content/uploads/headshots/nba/latest/260x190/${id}.png`
})

const player = ref<PlayerDetail | null>(null)
const isFollowed = ref(false)
const career = ref<PlayerCareerStat[]>([])
const gameLog = ref<PlayerGameLogItem[]>([])
const gameLogTotal = ref(0)
const gameLogPage = ref(1)
const gameLogRequestVersion = ref(0) // 用于跟踪最新请求，防止并发冲突

const detailLoading = ref(false)
const showHeadshotPreview = ref(false)
const detailLoaded = ref(false)
const careerLoaded = ref(false)
const gameLogLoading = ref(false)

/* 图表 Tabs */
const activeChartTab = ref<'analysis' | 'shotchart'>('analysis')
const chartTabs = [
  { key: 'analysis' as const, label: '能力分析', icon: '◉' },
  { key: 'shotchart' as const, label: '投篮热图', icon: '●' },
]

/* 关键数据卡片 */
const keyStats = computed(() => {
  const p = player.value
  if (!p) return []
  return [
    { label: '得分', value: p.pointsPerGame.toFixed(1) },
    { label: '篮板', value: p.reboundsPerGame.toFixed(1) },
    { label: '助攻', value: p.assistsPerGame.toFixed(1) },
    { label: '命中率', value: (p.fieldGoalPct * 100).toFixed(1) + '%' },
    { label: '三分', value: (p.threePointPct * 100).toFixed(1) + '%' },
    { label: '效率值', value: p.efficiency.toFixed(1) },
  ]
})

/* 雷达图 */
const radarOption = computed(() => {
  const p = player.value
  if (!p) return {}
  const normalize = (val: number, max: number) => Math.min((val / max) * 100, 100)
  return {
    tooltip: {
      backgroundColor: '#1C2333',
      borderColor: '#30363D',
      textStyle: { color: '#E6EDF3', fontSize: 12 },
    },
    radar: {
      indicator: [
        { name: '得分', max: 100 },
        { name: '篮板', max: 100 },
        { name: '助攻', max: 100 },
        { name: '抢断', max: 100 },
        { name: '盖帽', max: 100 },
        { name: '命中率', max: 100 },
      ],
      shape: 'polygon',
      splitNumber: 4,
      axisName: { color: '#8B949E', fontSize: 12 },
      splitLine: { lineStyle: { color: '#30363D' } },
      splitArea: { areaStyle: { color: ['rgba(108,92,231,0.02)', 'rgba(108,92,231,0.05)'] } },
      axisLine: { lineStyle: { color: '#30363D' } },
    },
    series: [{
      type: 'radar',
      data: [{
        value: [
          normalize(p.pointsPerGame, 35),
          normalize(p.reboundsPerGame, 15),
          normalize(p.assistsPerGame, 12),
          normalize(p.stealsPerGame, 3),
          normalize(p.blocksPerGame, 4),
          normalize(p.fieldGoalPct, 0.65),
        ],
        name: p.name,
        areaStyle: { color: 'rgba(108, 92, 231, 0.25)' },
        lineStyle: { color: '#6C5CE7', width: 2 },
        itemStyle: { color: '#A29BFE' },
      }],
    }],
  }
})

/* 生涯趋势图 */
const careerOption = computed(() => {
  const rows = career.value
  if (!rows.length) return {}
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#1C2333',
      borderColor: '#30363D',
      textStyle: { color: '#E6EDF3', fontSize: 12 },
    },
    legend: {
      data: ['得分', '篮板', '助攻'],
      textStyle: { color: '#8B949E', fontSize: 12 },
      top: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.season),
      axisLabel: { color: '#6E7681', fontSize: 11 },
      axisLine: { lineStyle: { color: '#30363D' } },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#6E7681', fontSize: 11 },
      splitLine: { lineStyle: { color: '#1C2333', type: 'dashed' } },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: [
      {
        name: '得分',
        type: 'line',
        smooth: true,
        data: rows.map((r) => r.pointsPerGame),
        lineStyle: { color: '#6C5CE7', width: 2 },
        itemStyle: { color: '#6C5CE7' },
        symbol: 'circle',
        symbolSize: 6,
      },
      {
        name: '篮板',
        type: 'line',
        smooth: true,
        data: rows.map((r) => r.reboundsPerGame),
        lineStyle: { color: '#00D2FF', width: 2 },
        itemStyle: { color: '#00D2FF' },
        symbol: 'circle',
        symbolSize: 6,
      },
      {
        name: '助攻',
        type: 'line',
        smooth: true,
        data: rows.map((r) => r.assistsPerGame),
        lineStyle: { color: '#00E676', width: 2 },
        itemStyle: { color: '#00E676' },
        symbol: 'circle',
        symbolSize: 6,
      },
    ],
  }
})

function formatDate(d: string) {
  return new Date(d).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

// 球队名称到ID的映射（用于跳转）
const teamNameToId: Record<string, number> = {}
async function loadTeamOptions() {
  try {
    const { data } = await import('@/api/team').then(m => m.fetchTeams({ page: 0, size: 100 }))
    for (const t of data.content) {
      teamNameToId[t.name] = t.id
    }
  } catch { /* ignore */ }
}

function getTeamIdByName(teamName: string): number | undefined {
  return teamNameToId[teamName]
}

// 返回上一页
function goBack() {
  const returnTo = route.query.returnTo as string
  if (returnTo) {
    router.push(returnTo)
  } else {
    router.back()
  }
}

function goToMatchDetail(row: any) {
  if (!row.gameId) return
  const params = new URLSearchParams({
    gameId: row.gameId,
    status: 'FINISHED',
    homeTeam: row.isHome ? (player.value?.teamName || '') : row.opponent,
    awayTeam: row.isHome ? row.opponent : (player.value?.teamName || ''),
    homeScore: String(row.isHome ? (row.teamScore || 0) : (row.opponentScore || 0)),
    awayScore: String(row.isHome ? (row.opponentScore || 0) : (row.teamScore || 0)),
    returnTo: `/players/detail?id=${playerId.value}`,
  })
  router.push(`/match-detail?${params.toString()}`)
}

/* 加载数据 */
async function loadDetail() {
  if (!playerId.value) return
  detailLoading.value = true
  try {
    const { data } = await fetchPlayerDetail(playerId.value)
    player.value = data
    // 加载投篮数据
    loadShotChart()
  } catch {
    ElMessage.error('加载球员详情失败')
  } finally {
    detailLoading.value = false
    detailLoaded.value = true
  }
}

async function loadCareer() {
  if (!playerId.value) return
  try {
    const { data } = await fetchPlayerCareer(playerId.value)
    career.value = data
  } catch {
    ElMessage.error('加载生涯数据失败')
  } finally {
    careerLoaded.value = true
  }
}

async function loadGameLog() {
  if (!playerId.value) return

  // 递增请求版本号，用于跟踪最新请求
  const currentVersion = ++gameLogRequestVersion.value
  gameLogLoading.value = true

  try {
    const { data } = await fetchPlayerGameLog(playerId.value, { page: gameLogPage.value - 1, size: 10 })

    // 检查是否是最新的请求，如果不是则忽略响应
    if (currentVersion !== gameLogRequestVersion.value) {
      return
    }

    gameLog.value = data.content
    gameLogTotal.value = data.totalElements
  } catch {
    // 只有当请求仍然是最新请求时才显示错误
    if (currentVersion === gameLogRequestVersion.value) {
      ElMessage.error('加载比赛日志失败')
    }
  } finally {
    // 只有当请求仍然是最新请求时才更新loading状态
    if (currentVersion === gameLogRequestVersion.value) {
      gameLogLoading.value = false
    }
  }
}

function goCompare() {
  if (!player.value) return
  router.push({ path: '/players/compare', query: { id1: String(player.value.id) } })
}

function onHeadshotError(e: Event) {
  const img = e.target as HTMLImageElement
  const id = player.value?.nbaPlayerId
  if (!id) {
    // 显示占位符
    img.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODgiIGhlaWdodD0iODgiIHZpZXdCb3g9IjAgMCA4OCA4OCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48Y2lyY2xlIGN4PSI0NCIgY3k9IjQ0IiByPSI0NCIgZmlsbD0iIzJBMkQzNSIvPjx0ZXh0IHg9IjQ0IiB5PSI1MiIgZm9udC1mYW1pbHk9IkFyaWFsIiBmb250LXNpemU9IjI0IiBmaWxsPSIjNjY2IiB0ZXh0LWFuY2hvcj0ibWlkZGxlIj5OPC90ZXh0Pjwvc3ZnPg=='
    return
  }
  // 尝试备用CDN
  const currentSrc = img.src
  if (currentSrc.includes('cdn.nba.com')) {
    img.src = `https://ak-static.cms.nba.com/wp-content/uploads/headshots/nba/latest/260x190/${id}.png`
  } else if (currentSrc.includes('cms.nba.com')) {
    img.src = `https://www.nba.com/.element/img/2.0/sect/statscube/players/large/${id}.png`
  } else {
    // 显示占位符
    img.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODgiIGhlaWdodD0iODgiIHZpZXdCb3g9IjAgMCA4OCA4OCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48Y2lyY2xlIGN4PSI0NCIgY3k9IjQ0IiByPSI0NCIgZmlsbD0iIzJBMkQzNSIvPjx0ZXh0IHg9IjQ0IiB5PSI1MiIgZm9udC1mYW1pbHk9IkFyaWFsIiBmb250LXNpemU9IjI0IiBmaWxsPSIjNjY2IiB0ZXh0LWFuY2hvcj0ibWlkZGxlIj5OPC90ZXh0Pjwvc3ZnPg=='
  }
}

async function toggleFollow() {
  if (!auth.token) { ElMessage.warning('请先登录'); return }
  try {
    const { data } = await toggleFavorite('PLAYER', playerId.value)
    isFollowed.value = data.favorited
  } catch { /* ignore */ }
}

async function checkFollowStatus() {
  if (!auth.token) return
  try {
    const { data } = await fetchFollowedPlayerIds()
    isFollowed.value = data.includes(playerId.value)
  } catch { /* ignore */ }
}

/* ---- Shot Chart Mock Data ---- */
function seededRandom(seed: number) {
  let s = seed
  return () => {
    s = (s * 16807 + 0) % 2147483647
    return (s - 1) / 2147483646
  }
}

const mockShots = computed<ShotData[]>(() => {
  const p = player.value
  if (!p) return []

  const totalShots = 100
  const rand = seededRandom(p.id * 7 + 13)
  const fgPct = p.fieldGoalPct || 0.45
  const tPct = p.threePointPct || 0.35
  const ftPct = p.freeThrowPct || 0.78
  const shots: ShotData[] = []

  // Court coordinates: x 0-50, y 0-47 (half court, baseline at y=0)
  const cx = 25 // center x
  const basketY = 5.25

  // Paint zone: x in [17, 33], y in [0, 19] — 40% of shots
  const paintCount = Math.round(totalShots * 0.4)
  for (let i = 0; i < paintCount; i++) {
    const x = cx + (rand() - 0.5) * 12
    const y = rand() * 15 + 2
    const makeProb = Math.min(fgPct + 0.1, 0.75) // paint is higher % than avg
    shots.push({ x, y, made: rand() < makeProb, period: Math.floor(rand() * 4) + 1 })
  }

  // Mid-range: x in [8, 42], y in [5, 22] but outside paint — 20%
  const midCount = Math.round(totalShots * 0.2)
  for (let i = 0; i < midCount; i++) {
    let x: number, y: number
    do {
      x = 5 + rand() * 40
      y = 3 + rand() * 20
    } while (x > 17 && x < 33 && y < 19) // avoid paint
    const dist = Math.sqrt((x - cx) ** 2 + (y - basketY) ** 2)
    const makeProb = Math.max(fgPct - 0.05 - dist * 0.005, 0.25)
    shots.push({ x, y, made: rand() < makeProb, period: Math.floor(rand() * 4) + 1 })
  }

  // 3-point: around the arc — 30%
  const threeCount = Math.round(totalShots * 0.3)
  for (let i = 0; i < threeCount; i++) {
    let x: number, y: number
    if (rand() < 0.2) {
      // Corner 3
      x = rand() < 0.5 ? 3.5 + rand() * 2 : 44.5 + rand() * 2
      y = rand() * 12 + 2
    } else {
      // Arc 3
      const a = -0.15 + rand() * 1.1 // angle range for arc portion
      const r = 22 + rand() * 3
      x = cx + r * Math.sin(a * Math.PI)
      y = basketY + r * Math.cos(a * Math.PI) * 0.85
    }
    shots.push({
      x: Math.max(1, Math.min(49, x)),
      y: Math.max(1, Math.min(46, y)),
      made: rand() < tPct,
      period: Math.floor(rand() * 4) + 1,
    })
  }

  // Free throws: at the FT line — 10%
  const ftCount = Math.round(totalShots * 0.1)
  for (let i = 0; i < ftCount; i++) {
    const x = cx + (rand() - 0.5) * 4
    const y = 18.5 + (rand() - 0.5) * 3
    shots.push({ x, y, made: rand() < ftPct, period: Math.floor(rand() * 4) + 1 })
  }

  return shots
})

const paintShots = computed(() => {
  return displayShots.value.filter(s => s.x > 17 && s.x < 33 && s.y < 19)
})
const midRangeShots = computed(() => {
  return displayShots.value.filter(s => {
    if (s.x > 17 && s.x < 33 && s.y < 19) return false
    const dist = Math.sqrt((s.x - 25) ** 2 + (s.y - 5.25) ** 2)
    return dist < 23
  })
})
const threePointShots = computed(() => {
  return displayShots.value.filter(s => {
    const dist = Math.sqrt((s.x - 25) ** 2 + (s.y - 5.25) ** 2)
    return dist >= 22
  })
})
const freeThrowShots = computed(() => {
  return displayShots.value.filter(s => s.y > 17 && s.y < 21 && s.x > 17 && s.x < 33)
})

watch(playerId, () => {
  if (playerId.value) {
    loadDetail()
    loadCareer()
    loadGameLog()
    checkFollowStatus()
  }
}, { immediate: false })

onMounted(() => {
  loadTeamOptions()
  loadDetail()
  loadCareer()
  loadGameLog()
  checkFollowStatus()
})

// 投篮数据
const shotChartShots = ref<ShotData[]>([])
const shotChartLoading = ref(false)

async function loadShotChart() {
  if (!player.value?.id) {
    console.log('loadShotChart: player.value.id is null')
    return
  }

  console.log('loadShotChart: loading for player', player.value.id)
  shotChartLoading.value = true
  try {
    const { data } = await request.get(`/shot-chart/player/${player.value.id}`, {
      params: { season: '2025-26' }
    })
    console.log('loadShotChart: received data', data)
    shotChartShots.value = data.shots || []
    console.log('loadShotChart: shots count', shotChartShots.value.length)
  } catch (error) {
    console.error('加载投篮数据失败:', error)
    shotChartShots.value = []
  } finally {
    shotChartLoading.value = false
  }
}

// 使用真实数据或回退到模拟数据
const displayShots = computed<ShotData[]>(() => {
  console.log('displayShots: shotChartShots.length =', shotChartShots.value.length)
  if (shotChartShots.value.length > 0) {
    return shotChartShots.value
  }
  return mockShots.value
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

/* 球员信息卡片 */
.player-hero {
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
.player-hero::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), var(--accent-light), var(--purple));
}
.player-hero::after {
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
.player-hero-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 1;
  flex-wrap: wrap;
  gap: 20px;
}
.player-hero-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.player-avatar {
  width: 88px;
  height: 88px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--purple) 0%, #5A4BD1 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  overflow: hidden;
  flex-shrink: 0;
  border: 3px solid var(--border-light);
  transition: all var(--duration-normal) var(--ease-smooth);
}
.player-hero:hover .player-avatar {
  border-color: var(--purple);
  box-shadow: 0 0 20px var(--purple-glow);
  transform: scale(1.05);
}
.player-headshot {
  width: 100%;
  height: 100%;
  object-fit: cover;
  color: #fff;
  flex-shrink: 0;
  font-family: var(--font-heading);
}
.player-avatar--clickable {
  cursor: pointer;
  position: relative;
}
.avatar-zoom-hint {
  position: absolute;
  bottom: 4px;
  right: 4px;
  font-size: 14px;
  background: rgba(0,0,0,0.5);
  border-radius: 50%;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-smooth);
}
.player-avatar--clickable:hover .avatar-zoom-hint {
  opacity: 1;
}
.headshot-preview-container {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 16px;
}
.headshot-preview-img {
  max-width: 500px;
  max-height: 600px;
  object-fit: contain;
  border-radius: var(--radius-lg);
}

/* 头像预览弹窗 - 全屏遮罩 */
.headshot-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.headshot-dialog {
  background: var(--bg-card);
  border: 1px solid var(--border-medium);
  border-radius: var(--radius-xl);
  box-shadow: 0 32px 100px rgba(0, 0, 0, 0.8), 0 0 2px rgba(0, 255, 136, 0.2);
  overflow: hidden;
  transform: scale(0.95);
  animation: headshotZoomIn 0.3s var(--ease-spring) forwards;
}

@keyframes headshotZoomIn {
  to {
    transform: scale(1);
  }
}

.headshot-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
  background: linear-gradient(135deg, var(--accent-lighter), transparent);
}

.headshot-dialog-title {
  font-family: var(--font-heading);
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.5px;
}

.headshot-dialog-close {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--border-light);
  background: var(--bg-hover);
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-fast) var(--ease-smooth);
}

.headshot-dialog-close:hover {
  background: var(--danger);
  border-color: var(--danger);
  color: #fff;
  transform: rotate(90deg);
}

.headshot-dialog-body {
  padding: 24px;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 弹窗过渡动画 */
.headshot-fade-enter-active {
  transition: opacity 0.25s var(--ease-smooth);
}
.headshot-fade-leave-active {
  transition: opacity 0.2s var(--ease-smooth);
}
.headshot-fade-enter-from,
.headshot-fade-leave-to {
  opacity: 0;
}
.player-name {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
}
.player-sub {
  margin: 4px 0 0;
  font-size: 14px;
  color: var(--text-muted);
}
.player-extra {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--text-dim);
}
.follow-btn { margin-top: 8px !important; }
.player-hero-stats {
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
.hero-stat-num--pts {
  color: var(--accent);
  text-shadow: 0 0 20px var(--accent-glow);
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

/* 关键数据卡片 */
.stat-row {
  margin-bottom: 0;
}
.key-stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 16px 20px;
  text-align: center;
  transition: all var(--duration-normal) var(--ease-smooth);
  margin-bottom: 16px;
  position: relative;
  overflow: hidden;
}
.key-stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  opacity: 0;
  transition: opacity var(--duration-normal) var(--ease-smooth);
}
.key-stat-card:hover::before {
  opacity: 1;
}
.key-stat-card:hover {
  border-color: var(--border-medium);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}
.key-stat-label {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
  font-weight: 600;
}
.key-stat-num {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: -0.5px;
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
  font-size: 20px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-weight: 700;
  letter-spacing: 0.3px;
}
.section-sub {
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 0.2px;
}

/* 图表卡片 */
.chart-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24) !important;
  transition: all var(--duration-normal) var(--ease-smooth);
  overflow: hidden;
  margin-bottom: 16px;
  position: relative;
}
.chart-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--purple), var(--accent));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  opacity: 0;
  transition: opacity var(--duration-normal) var(--ease-smooth);
}
.chart-card:hover::before {
  opacity: 1;
}
.chart-card:hover {
  border-color: var(--border-medium) !important;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3) !important;
}
.chart-body {
  padding: 12px 16px 16px;
}
.chart {
  height: 340px;
}

/* 比赛日志卡片 */
.gamelog-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  position: relative;
  overflow: hidden;
}
.gamelog-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), var(--cyan));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

/* 对手链接 */
.opponent-link {
  color: var(--text-primary);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s ease;
}
.opponent-link:hover {
  color: var(--accent);
  text-decoration: underline;
}

/* 比分显示 */
.score-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 14px;
}
.score-team {
  color: var(--text-secondary);
  min-width: 24px;
  text-align: right;
}
.score-team.score-win {
  color: var(--accent);
}
.score-sep {
  color: var(--text-dim);
  font-weight: 400;
}
.score-opp {
  color: var(--text-muted);
  min-width: 24px;
  text-align: left;
}

/* ===== Chart Tabs ===== */
.chart-tabs {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  overflow: hidden;
}
.chart-tabs-header {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--border-light);
  padding: 0 8px;
  background: var(--bg-hover);
}
.chart-tab-btn {
  padding: 12px 20px;
  font-size: 14px;
  font-weight: 600;
  font-family: var(--font-body);
  color: var(--text-muted);
  background: none;
  border: none;
  cursor: pointer;
  position: relative;
  transition: color var(--duration-fast) var(--ease-smooth);
  display: flex;
  align-items: center;
  gap: 6px;
}
.chart-tab-btn:hover {
  color: var(--text-secondary);
}
.chart-tab-btn--active {
  color: var(--accent);
}
.chart-tab-btn--active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 12px;
  right: 12px;
  height: 2px;
  background: var(--accent);
  border-radius: 2px 2px 0 0;
}
.chart-tab-icon {
  font-size: 10px;
}
.chart-tab-content {
  padding: 20px;
}

/* Shot chart layout */
.shotchart-body {
  padding: 16px 20px 20px;
}
.shotchart-layout {
  display: flex;
  gap: 28px;
  align-items: flex-start;
}
.shotchart-court {
  flex: 1;
  min-width: 0;
  max-width: 480px;
}
.shotchart-sidebar {
  width: 200px;
  flex-shrink: 0;
}
.shotchart-summary {
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 16px;
}
.shotchart-summary-title {
  margin: 0 0 14px;
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
}
.shotchart-stat-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
  margin-bottom: 16px;
}
.shotchart-stat-item {
  text-align: center;
}
.shotchart-stat-label {
  display: block;
  font-size: 11px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
  font-weight: 600;
}
.shotchart-stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
}
.shotchart-stat-value--made {
  color: #00E676;
}
.shotchart-zone-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
}
.shotchart-zone-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.zone-color {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}
.zone-color--paint { background: #6C5CE7; }
.zone-color--mid { background: var(--accent); }
.zone-color--three { background: #06B6D4; }
.zone-color--ft { background: var(--gold); }
.zone-name {
  color: var(--text-secondary);
  flex: 1;
  font-weight: 500;
}
.zone-count {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 12px;
}
.shotchart-note {
  margin: 12px 0 0;
  font-size: 11px;
  color: var(--text-dim);
  font-style: italic;
}

/* Responsive: stack shotchart on mobile */
@media (max-width: 768px) {
  .shotchart-layout {
    flex-direction: column;
  }
  .shotchart-sidebar {
    width: 100%;
  }
  .shotchart-stat-grid {
    grid-template-columns: 1fr 1fr 1fr;
  }
}
</style>
