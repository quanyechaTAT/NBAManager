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
        <el-button class="back-btn" link @click="router.push('/players')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="m15 18-6-6 6-6"/></svg>
          返回球员数据
        </el-button>
      </div>

      <!-- 球员信息卡片 -->
      <div class="player-hero" v-loading="detailLoading">
        <div class="player-hero-inner">
          <div class="player-hero-left">
            <div class="player-avatar">{{ player?.name?.charAt(0) || '?' }}</div>
            <div>
              <h1 class="player-name">{{ player?.name }}</h1>
              <p class="player-sub">{{ player?.teamName }} · {{ player?.position }} · #{{ player?.jerseyNumber }}</p>
              <p class="player-extra">{{ player?.height }} · {{ player?.weight }}磅 · {{ player?.country }}</p>
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

      <!-- 图表区域 -->
      <el-row :gutter="16" style="margin-top: 20px;">
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

      <!-- 近期比赛日志 -->
      <div class="section-header" style="margin-top: 28px;">
        <div class="section-header-left">
          <h2>近期比赛</h2>
          <span class="section-sub">最近 10 场比赛数据</span>
        </div>
      </div>
      <el-card shadow="never" class="gamelog-card">
        <el-table :data="gameLog" stripe v-loading="gameLogLoading" empty-text="暂无比赛日志" size="small">
          <el-table-column label="日期" width="120">
            <template #default="{ row }">{{ formatDate(row.matchDate) }}</template>
          </el-table-column>
          <el-table-column label="对手" min-width="100" prop="opponent" />
          <el-table-column label="分钟" width="65" align="center" prop="minutes" />
          <el-table-column label="得分" width="65" align="center" prop="points" sortable />
          <el-table-column label="篮板" width="65" align="center" prop="rebounds" />
          <el-table-column label="助攻" width="65" align="center" prop="assists" />
          <el-table-column label="抢断" width="65" align="center" prop="steals" />
          <el-table-column label="结果" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.result === 'W' ? 'success' : 'danger'" size="small" effect="light">
                {{ row.result === 'W' ? '胜' : '负' }}
              </el-tag>
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
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { RadarChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { fetchPlayerDetail, fetchPlayerCareer, fetchPlayerGameLog } from '@/api/playerDetail'
import type { PlayerDetail, PlayerCareerStat, PlayerGameLogItem } from '@/api/playerDetail'

use([RadarChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent, CanvasRenderer])

const route = useRoute()
const router = useRouter()

const playerId = computed(() => Number(route.query.id) || 0)

const player = ref<PlayerDetail | null>(null)
const career = ref<PlayerCareerStat[]>([])
const gameLog = ref<PlayerGameLogItem[]>([])
const gameLogTotal = ref(0)
const gameLogPage = ref(1)

const detailLoading = ref(false)
const detailLoaded = ref(false)
const careerLoaded = ref(false)
const gameLogLoading = ref(false)

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

/* 加载数据 */
async function loadDetail() {
  if (!playerId.value) return
  detailLoading.value = true
  try {
    const { data } = await fetchPlayerDetail(playerId.value)
    player.value = data
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
  gameLogLoading.value = true
  try {
    const { data } = await fetchPlayerGameLog(playerId.value, { page: gameLogPage.value - 1, size: 10 })
    gameLog.value = data.content
    gameLogTotal.value = data.totalElements
  } catch {
    ElMessage.error('加载比赛日志失败')
  } finally {
    gameLogLoading.value = false
  }
}

onMounted(() => {
  loadDetail()
  loadCareer()
  loadGameLog()
})
</script>

<style scoped>
.page {
  max-width: 1200px;
  min-height: calc(100vh - 108px);
  position: relative;
  border-radius: var(--radius-lg);
}

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
}
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
  width: 64px;
  height: 64px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--purple) 0%, #5A4BD1 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
  font-family: var(--font-heading);
}
.player-name {
  margin: 0;
  font-size: 28px;
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
  font-size: 32px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  line-height: 1.1;
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
  margin-top: 4px;
  display: block;
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
}
.key-stat-card:hover {
  border-color: var(--border-medium);
  box-shadow: var(--shadow-md);
}
.key-stat-label {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}
.key-stat-num {
  font-size: 24px;
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

/* 图表卡片 */
.chart-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24) !important;
  transition: all var(--duration-normal) var(--ease-smooth);
  overflow: hidden;
  margin-bottom: 16px;
}
.chart-card:hover {
  border-color: var(--border-medium) !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25) !important;
}
.chart-body {
  padding: 8px 16px 16px;
}
.chart {
  height: 320px;
}

/* 比赛日志卡片 */
.gamelog-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
