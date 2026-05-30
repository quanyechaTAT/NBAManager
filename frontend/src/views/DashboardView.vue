<template>
  <div class="page animated-bg">
    <!-- 浮动光晕粒子 -->
    <div class="bg-particles">
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
    </div>
    <!-- 科技网格线 -->
    <div class="bg-grid"></div>

    <div class="page-inner stagger-in">
      <!-- 欢迎横幅 -->
      <div class="welcome-banner">
        <h3>👋 欢迎回来，{{ auth.username }}！</h3>
        <p>NBA 数据管理仪表盘 — 查看球队战绩、球员数据和赛事资讯的最新概览。</p>
        <div class="welcome-tags">
          <span class="welcome-tag">📊 实时数据</span>
          <span class="welcome-tag">🏆 赛季追踪</span>
          <span class="welcome-tag">⚡ 快速分析</span>
        </div>
      </div>

      <!-- 统计卡片 -->
      <div class="overview">
        <el-card shadow="never" class="stat-card stat-card--purple">
          <div class="stat-card-inner">
            <div class="stat-card-body">
              <span class="stat-label">球队数量</span>
              <strong class="stat-number">{{ teamCount }}</strong>
              <span class="stat-sub">NBA 全联盟</span>
            </div>
            <div class="stat-card-icon">
              <span>🏀</span>
            </div>
          </div>
          <div class="stat-card-footer">
            <span class="stat-trend stat-trend--up">↑ 活跃</span>
            <div class="sparkline-wrap">
              <v-chart class="sparkline" :option="sparkTeam" autoresize />
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="stat-card stat-card--cyan">
          <div class="stat-card-inner">
            <div class="stat-card-body">
              <span class="stat-label">球员数量</span>
              <strong class="stat-number">{{ playerCount }}</strong>
              <span class="stat-sub">在册球员</span>
            </div>
            <div class="stat-card-icon">
              <span>⭐</span>
            </div>
          </div>
          <div class="stat-card-footer">
            <span class="stat-trend stat-trend--up">↑ 活跃</span>
            <div class="sparkline-wrap">
              <v-chart class="sparkline" :option="sparkPlayer" autoresize />
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="stat-card stat-card--gold">
          <div class="stat-card-inner">
            <div class="stat-card-body">
              <span class="stat-label">最高胜场</span>
              <strong class="stat-number">{{ maxWins }}</strong>
              <span class="stat-sub">赛季最佳</span>
            </div>
            <div class="stat-card-icon">
              <span>🏆</span>
            </div>
          </div>
          <div class="stat-card-footer">
            <span class="stat-trend stat-trend--up">↑ 冠军</span>
            <div class="sparkline-wrap">
              <v-chart class="sparkline" :option="sparkWins" autoresize />
            </div>
          </div>
        </el-card>
      </div>

      <!-- 图表区域 -->
      <el-row :gutter="18">
        <el-col :span="14">
          <el-card shadow="never" class="chart-card">
            <div class="card-head">
              <div class="card-head-title">
                <span>📊 球队战绩概览</span>
                <small>按当前系统数据统计</small>
              </div>
            </div>
            <div class="chart-body">
              <v-chart class="chart" :option="winsOption" autoresize v-if="loaded" />
              <el-skeleton v-else :rows="6" animated />
            </div>
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card shadow="never" class="chart-card">
            <div class="card-head">
              <div class="card-head-title">
                <span>🏅 球员得分排行</span>
                <small>场均得分 Top 8</small>
              </div>
            </div>
            <div class="chart-body">
              <v-chart class="chart chart-sm" :option="ppgOption" autoresize v-if="loaded" />
              <el-skeleton v-else :rows="6" animated />
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { fetchDashboardStats } from '@/api/dashboard'
import type { DashboardStats } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent])

const auth = useAuthStore()
const stats = ref<DashboardStats | null>(null)
const loaded = ref(false)

const teamCount = computed(() => stats.value?.teamWinRows.length ?? 0)
const playerCount = computed(() => stats.value?.topScorers.length ?? 0)
const maxWins = computed(() => Math.max(0, ...(stats.value?.teamWinRows.map((r) => r.wins) ?? [])))

/* ---- 迷你 sparkline ---- */
const sparkTeam = computed(() => ({
  grid: { left: 0, right: 0, top: 0, bottom: 0 },
  xAxis: { show: false, type: 'category', data: Array.from({ length: 7 }, (_, i) => i) },
  yAxis: { show: false, type: 'value' },
  series: [{
    type: 'line', smooth: true, symbol: 'none',
    lineStyle: { width: 2, color: '#6C5CE7' },
    areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
      { offset: 0, color: 'rgba(108,92,231,0.3)' }, { offset: 1, color: 'rgba(108,92,231,0.02)' }
    ]) },
    data: [3, 5, 4, 6, 5, 7, teamCount.value],
  }],
}))

const sparkPlayer = computed(() => ({
  grid: { left: 0, right: 0, top: 0, bottom: 0 },
  xAxis: { show: false, type: 'category', data: Array.from({ length: 7 }, (_, i) => i) },
  yAxis: { show: false, type: 'value' },
  series: [{
    type: 'line', smooth: true, symbol: 'none',
    lineStyle: { width: 2, color: '#00D2FF' },
    areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
      { offset: 0, color: 'rgba(0,210,255,0.3)' }, { offset: 1, color: 'rgba(0,210,255,0.02)' }
    ]) },
    data: [10, 14, 12, 16, 15, 18, playerCount.value],
  }],
}))

const sparkWins = computed(() => ({
  grid: { left: 0, right: 0, top: 0, bottom: 0 },
  xAxis: { show: false, type: 'category', data: Array.from({ length: 7 }, (_, i) => i) },
  yAxis: { show: false, type: 'value' },
  series: [{
    type: 'line', smooth: true, symbol: 'none',
    lineStyle: { width: 2, color: '#FFA726' },
    areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
      { offset: 0, color: 'rgba(255,167,38,0.3)' }, { offset: 1, color: 'rgba(255,167,38,0.02)' }
    ]) },
    data: [20, 28, 32, 35, 40, 45, maxWins.value],
  }],
}))

/* ---- 主图表 ---- */
const winsOption = computed(() => {
  const rows = stats.value?.teamWinRows ?? []
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#1C2333',
      borderColor: '#30363D',
      textStyle: { color: '#E6EDF3', fontSize: 12 },
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.name),
      axisLabel: { rotate: 30, color: '#6E7681', fontSize: 11 },
      axisLine: { lineStyle: { color: '#30363D' } },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      name: '场次',
      nameTextStyle: { color: '#6E7681', fontSize: 11 },
      axisLabel: { color: '#6E7681', fontSize: 11 },
      splitLine: { lineStyle: { color: '#1C2333', type: 'dashed' } },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: [
      {
        name: '胜场',
        type: 'bar',
        data: rows.map((r) => r.wins),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#6C5CE7' },
            { offset: 1, color: '#A29BFE' },
          ]),
          borderRadius: [6, 6, 0, 0],
        },
        barWidth: '35%',
      },
      {
        name: '负场',
        type: 'bar',
        data: rows.map((r) => r.losses),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#3D444D' },
            { offset: 1, color: '#2D333B' },
          ]),
          borderRadius: [6, 6, 0, 0],
        },
        barWidth: '35%',
      },
    ],
    legend: {
      data: ['胜场', '负场'],
      textStyle: { color: '#8B949E', fontSize: 12 },
      top: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
    },
  }
})

const ppgOption = computed(() => {
  const rows = stats.value?.topScorers ?? []
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#1C2333',
      borderColor: '#30363D',
      textStyle: { color: '#E6EDF3', fontSize: 12 },
      formatter: (p: any) => `${p[0].name}<br/>场均得分: ${p[0].value}`,
    },
    grid: { left: '3%', right: '10%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'value',
      name: 'PPG',
      nameTextStyle: { color: '#6E7681', fontSize: 11 },
      axisLabel: { color: '#6E7681', fontSize: 11 },
      splitLine: { lineStyle: { color: '#1C2333', type: 'dashed' } },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'category',
      data: rows.map((r) => r.playerName),
      inverse: true,
      axisLabel: { width: 90, overflow: 'truncate', color: '#8B949E', fontSize: 11 },
      axisLine: { lineStyle: { color: '#30363D' } },
      axisTick: { show: false },
    },
    series: [
      {
        name: '场均得分',
        type: 'bar',
        data: rows.map((r) => r.ppg),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#00D2FF' },
            { offset: 1, color: '#6C5CE7' },
          ]),
          borderRadius: [0, 6, 6, 0],
        },
        barWidth: '50%',
        label: {
          show: true,
          position: 'right',
          color: '#8B949E',
          fontSize: 11,
          formatter: '{c}',
        },
      },
    ],
  }
})

onMounted(async () => {
  try {
    const { data } = await fetchDashboardStats()
    stats.value = data
  } catch {
    ElMessage.error('加载看板数据失败')
  } finally {
    loaded.value = true
  }
})
</script>

<style scoped>
.page {
  max-width: 1200px;
  min-height: calc(100vh - 108px);
  position: relative;
  border-radius: var(--radius-lg);
}

/* 欢迎横幅 */
.welcome-banner .welcome-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.welcome-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  background: rgba(108, 92, 231, 0.1);
  border: 1px solid rgba(108, 92, 231, 0.2);
  border-radius: 20px;
  font-size: 12px;
  color: var(--purple-light);
  font-weight: 500;
}

/* 统计卡片 */
.overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-bottom: 20px;
}
.stat-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24) !important;
  transition: all var(--duration-normal) var(--ease-smooth);
  overflow: hidden;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.3), 0 3px 10px rgba(0, 0, 0, 0.22) !important;
}
.stat-card--purple:hover { border-color: var(--purple) !important; }
.stat-card--cyan:hover { border-color: var(--cyan) !important; }
.stat-card--gold:hover { border-color: var(--orange) !important; }

.stat-card :deep(.el-card__body) {
  padding: 0;
}
.stat-card-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 12px;
}
.stat-card-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stat-card-body .stat-number {
  font-size: 32px;
  margin: 4px 0;
}
.stat-sub {
  font-size: 12px;
  color: var(--text-dim);
}
.stat-card-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}
.stat-card--purple .stat-card-icon { background: var(--purple-dim); }
.stat-card--cyan .stat-card-icon { background: var(--cyan-dim); }
.stat-card--gold .stat-card-icon { background: var(--orange-dim); }

.stat-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 24px 16px;
  border-top: 1px solid var(--border-light);
}
.sparkline {
  width: 80px;
  height: 36px;
}

/* 图表卡片 */
.chart-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24) !important;
  transition: all var(--duration-normal) var(--ease-smooth);
  overflow: hidden;
}
.chart-card:hover {
  border-color: var(--border-medium) !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25) !important;
}
.chart-body {
  padding: 8px 16px 16px;
}
.chart {
  height: 360px;
}
.chart-sm {
  height: 360px;
}

@media (max-width: 960px) {
  .overview {
    grid-template-columns: 1fr;
  }
}
</style>
