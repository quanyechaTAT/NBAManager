<template>
  <div class="shot-panel" v-loading="loading">
    <!-- 加载中 -->
    <div v-if="loading" class="panel-loading">正在获取官方投篮坐标...</div>

    <!-- REAL_SHOT_CHART -->
    <template v-else-if="mode === 'REAL_SHOT_CHART'">
      <!-- 标题栏 -->
      <div class="panel-header">
        <div class="header-left">
          <span class="header-icon">🏀</span>
          <span class="header-title">投篮热图</span>
          <span class="header-sub">SHOT CHART</span>
        </div>
        <div class="header-filters">
          <el-segmented v-model="teamFilter" :options="teamOpts" size="small" />
          <el-segmented v-model="resultFilter" :options="resultOpts" size="small" />
        </div>
      </div>

      <!-- 统计横幅 -->
      <ShotChartSummaryBar :summary="summary" />

      <!-- 主体 -->
      <div class="panel-body">
        <div class="body-left">
          <AdvancedShotCourt :shots="filtered" :size="560" />
        </div>
        <div class="body-right">
          <ShotZoneStatsPanel :zones="zoneStats" />
          <ShotChartLegend :total="filtered.length" />
        </div>
      </div>

      <!-- 节次筛选 -->
      <div class="panel-period-filter">
        <span class="period-label">节次</span>
        <el-segmented v-model="periodFilter" :options="periodOpts" size="small" />
      </div>

      <!-- 数据源 -->
      <div class="panel-source">数据来源：{{ sourceLabel }}</div>
    </template>

    <!-- UNAVAILABLE -->
    <div v-else class="panel-unavailable">
      <div class="unavail-icon">🏀</div>
      <div class="unavail-title">官方投篮坐标暂不可用</div>
      <div class="unavail-sub">{{ message || '系统不会生成模拟投篮点，以保证数据真实性。' }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ShotData } from '@/api/types'
import { filterShots, calculateShotSummary, calculateZoneStats } from '@/utils/shotChart'
import ShotChartSummaryBar from './ShotChartSummaryBar.vue'
import AdvancedShotCourt from './AdvancedShotCourt.vue'
import ShotZoneStatsPanel from './ShotZoneStatsPanel.vue'
import ShotChartLegend from './ShotChartLegend.vue'

const props = defineProps<{
  shots: ShotData[]
  mode: string
  source: string
  message: string
  loading: boolean
  homeTeamId?: number | null
  awayTeamId?: number | null
  homeTeamName?: string
  awayTeamName?: string
}>()

const teamFilter = ref<'all' | 'home' | 'away'>('all')
const resultFilter = ref<'all' | 'made' | 'missed'>('all')
const periodFilter = ref<number | 'all'>('all')

const teamOpts = [
  { label: '全部', value: 'all' },
  { label: '主队', value: 'home' },
  { label: '客队', value: 'away' }
]
const resultOpts = [
  { label: '全部', value: 'all' },
  { label: '命中', value: 'made' },
  { label: '未中', value: 'missed' }
]
const periodOpts = computed(() => {
  const periods = new Set(props.shots.map(s => s.period).filter((p): p is number => p != null && p > 0))
  const opts: Array<{ label: string; value: number | 'all' }> = [{ label: '全部', value: 'all' }]
  for (let i = 1; i <= 4; i++) { if (periods.has(i)) opts.push({ label: `第${i}节`, value: i }) }
  if (periods.has(5) || [...periods].some(p => p > 4)) opts.push({ label: '加时', value: 5 })
  return opts
})

const filtered = computed(() => filterShots(props.shots, {
  team: teamFilter.value, result: resultFilter.value, period: periodFilter.value,
  homeTeamId: props.homeTeamId, awayTeamId: props.awayTeamId,
  homeTeamName: props.homeTeamName, awayTeamName: props.awayTeamName
}))
const summary = computed(() => calculateShotSummary(filtered.value))
const zoneStats = computed(() => calculateZoneStats(filtered.value))

const sourceLabel = computed(() => {
  const s = props.source
  if (s === 'NBA_SHOTCHARTDETAIL') return 'NBA ShotChartDetail · 全场'
  if (s === 'NBA_SHOTCHARTDETAIL_TEAM') return 'NBA ShotChartDetail · 球队维度'
  if (s === 'NBA_SHOTCHARTDETAIL_PLAYER') return 'NBA ShotChartDetail · 球员维度'
  return s || '未知'
})
</script>

<style scoped>
.shot-panel { display: flex; flex-direction: column; gap: 20px; padding: 20px; background: rgba(255,255,255,0.015); border: 1px solid rgba(139,92,246,0.1); border-radius: 14px; }
.panel-loading { text-align: center; padding: 80px 0; color: var(--text-secondary); font-size: 14px; }
.panel-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.header-left { display: flex; align-items: center; gap: 8px; }
.header-icon { font-size: 20px; }
.header-title { font-size: 16px; font-weight: 800; color: var(--text-primary); }
.header-sub { font-size: 10px; font-weight: 700; color: var(--text-dim); letter-spacing: 2px; text-transform: uppercase; }
.header-filters { display: flex; gap: 8px; }
.panel-body { display: grid; grid-template-columns: 1fr 280px; gap: 20px; }
.body-left { min-width: 0; }
.body-right { display: flex; flex-direction: column; gap: 12px; }
.panel-period-filter { display: flex; align-items: center; gap: 12px; justify-content: center; }
.period-label { font-size: 12px; color: var(--text-muted); }
.panel-source { text-align: center; font-size: 11px; color: var(--text-dim); }
.panel-unavailable { text-align: center; padding: 80px 24px; }
.unavail-icon { font-size: 40px; margin-bottom: 16px; opacity: 0.4; }
.unavail-title { font-size: 16px; font-weight: 600; color: var(--text-secondary); margin-bottom: 8px; }
.unavail-sub { font-size: 13px; color: var(--text-dim); }
@media (max-width: 900px) { .panel-body { grid-template-columns: 1fr; } }
</style>
