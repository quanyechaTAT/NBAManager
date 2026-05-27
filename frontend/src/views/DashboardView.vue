<template>
  <div class="page">
    <div class="overview">
      <el-card shadow="never" class="metric-card metric-card--blue">
        <div class="metric-icon">
          <span>🏀</span>
        </div>
        <div class="metric-body">
          <span>球队数量</span>
          <strong>{{ teamCount }}</strong>
        </div>
      </el-card>
      <el-card shadow="never" class="metric-card metric-card--green">
        <div class="metric-icon">
          <span>⭐</span>
        </div>
        <div class="metric-body">
          <span>球员数量</span>
          <strong>{{ playerCount }}</strong>
        </div>
      </el-card>
      <el-card shadow="never" class="metric-card metric-card--gold">
        <div class="metric-icon">
          <span>🏆</span>
        </div>
        <div class="metric-body">
          <span>最高胜场</span>
          <strong>{{ maxWins }}</strong>
        </div>
      </el-card>
    </div>

    <el-row :gutter="18">
      <el-col :span="14">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-title">
              <span>球队战绩概览</span>
              <small>按当前系统数据统计</small>
            </div>
          </template>
          <v-chart class="chart" :option="winsOption" autoresize v-if="loaded" />
          <el-skeleton v-else :rows="6" animated />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-title">
              <span>球员得分排行</span>
              <small>场均得分 Top 8</small>
            </div>
          </template>
          <v-chart class="chart chart-sm" :option="ppgOption" autoresize v-if="loaded" />
          <el-skeleton v-else :rows="6" animated />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { fetchDashboardStats } from '@/api/dashboard'
import type { DashboardStats } from '@/api/types'
import { ElMessage } from 'element-plus'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent, LegendComponent])

const stats = ref<DashboardStats | null>(null)
const loaded = ref(false)

const teamCount = computed(() => stats.value?.teamWinRows.length ?? 0)
const playerCount = computed(() => stats.value?.topScorers.length ?? 0)
const maxWins = computed(() => Math.max(0, ...(stats.value?.teamWinRows.map((r) => r.wins) ?? [])))

const winsOption = computed(() => {
  const rows = stats.value?.teamWinRows ?? []
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.name),
      axisLabel: { rotate: 30, color: '#6E7681' },
      axisLine: { lineStyle: { color: '#30363D' } },
    },
    yAxis: {
      type: 'value',
      name: '场次',
      nameTextStyle: { color: '#6E7681' },
      axisLabel: { color: '#6E7681' },
      splitLine: { lineStyle: { color: '#1C2333' } },
    },
    series: [
      {
        name: '胜场 (Wins)',
        type: 'bar',
        data: rows.map((r) => r.wins),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#69F0AE' },
            { offset: 1, color: '#00E676' },
          ]),
          borderRadius: [4, 4, 0, 0],
        },
        barWidth: '40%',
      },
      {
        name: '负场 (Losses)',
        type: 'bar',
        data: rows.map((r) => r.losses),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#3D444D' },
            { offset: 1, color: '#30363D' },
          ]),
          borderRadius: [4, 4, 0, 0],
        },
        barWidth: '40%',
      },
    ],
    legend: {
      data: ['胜场 (Wins)', '负场 (Losses)'],
      textStyle: { color: '#8B949E' },
      top: 0,
    },
  }
})

const ppgOption = computed(() => {
  const rows = stats.value?.topScorers ?? []
  return {
    tooltip: { trigger: 'axis', formatter: (p: any) => `${p[0].name}<br/>场均得分: ${p[0].value}` },
    grid: { left: '3%', right: '8%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'value',
      name: '场均得分 (PPG)',
      nameTextStyle: { color: '#6E7681' },
      axisLabel: { color: '#6E7681' },
      splitLine: { lineStyle: { color: '#1C2333' } },
    },
    yAxis: {
      type: 'category',
      data: rows.map((r) => r.playerName),
      inverse: true,
      axisLabel: { width: 90, overflow: 'truncate', color: '#8B949E' },
      axisLine: { lineStyle: { color: '#30363D' } },
    },
    series: [
      {
        name: '场均得分',
        type: 'bar',
        data: rows.map((r) => ({ value: r.ppg, itemStyle: { color: '#00E676' } })),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#00E676' },
            { offset: 1, color: '#69F0AE' },
          ]),
          borderRadius: [0, 4, 4, 0],
        },
        label: {
          show: true,
          position: 'right',
          color: '#8B949E',
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
}
.overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.metric-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-lg) !important;
  box-shadow: var(--shadow-sm) !important;
  transition: all var(--duration-normal) var(--ease-smooth);
  overflow: hidden;
  position: relative;
}
.metric-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  border-radius: 3px 0 0 3px;
}
.metric-card--blue::before {
  background: var(--accent);
}
.metric-card--green::before {
  background: #448AFF;
}
.metric-card--gold::before {
  background: var(--warning);
}
.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-hover) !important;
  border-color: var(--accent) !important;
}
.metric-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  position: relative;
  z-index: 1;
}
.metric-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--bg-hover);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  border: 1px solid var(--border-light);
}
.metric-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.metric-body span {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  font-family: var(--font-body);
}
.metric-body strong {
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.5px;
}
.chart-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-lg) !important;
  box-shadow: var(--shadow-sm) !important;
  transition: all var(--duration-normal) var(--ease-smooth);
}
.chart-card:hover {
  border-color: var(--border-medium) !important;
}
.card-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-left: 12px;
  border-left: 3px solid var(--accent);
}
.card-title span {
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 15px;
  letter-spacing: 0.3px;
}
.card-title small {
  color: var(--text-muted);
  font-size: 12px;
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
