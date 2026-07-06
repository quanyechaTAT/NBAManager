<template>
  <div class="game-shot-chart" v-loading="loading">
    <!-- 加载中 -->
    <div v-if="loading" class="panel-loading">正在获取官方投篮坐标...</div>

    <!-- REAL_SHOT_CHART -->
    <template v-else-if="mode === 'REAL_SHOT_CHART'">
      <!-- 标题栏 -->
      <div class="shot-header">
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

      <!-- 主体：左球场 + 右统计 -->
      <div class="shot-content">
        <div class="shot-main">
          <AdvancedShotCourt :shots="filtered" />
          <div class="shot-toolbar">
            <span class="toolbar-label">节次</span>
            <el-segmented v-model="periodFilter" :options="periodOpts" size="small" />
          </div>
          <div class="shot-source">数据来源：{{ sourceLabel }}</div>
        </div>
        <aside class="shot-sidebar">
          <ShotZoneStatsPanel :zones="zoneStats" />
          <ShotChartLegend :total="filtered.length" />
        </aside>
      </div>
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
  for (let i = 1; i <= 4; i++) { if (periods.has(i)) opts.push({ label: `Q${i}`, value: i }) }
  if ([...periods].some(p => p > 4)) opts.push({ label: 'OT', value: 5 })
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
.game-shot-chart {
  display: flex; flex-direction: column; gap: 18px;
  padding: 20px; max-width: 1440px; margin: 0 auto;
  background: rgba(255,255,255,0.012);
  border: 1px solid rgba(139,92,246,0.08);
  border-radius: 14px;
}
.panel-loading { text-align: center; padding: 80px 0; color: var(--text-secondary); font-size: 14px; }

/* Header */
.shot-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.header-left { display: flex; align-items: center; gap: 8px; }
.header-icon { font-size: 18px; }
.header-title { font-size: 15px; font-weight: 800; color: var(--text-primary); }
.header-sub { font-size: 9px; font-weight: 700; color: #475569; letter-spacing: 2px; text-transform: uppercase; }
.header-filters { display: flex; gap: 8px; }

/* Content: left court + right sidebar */
.shot-content { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 20px; }
.shot-main { min-width: 0; display: flex; flex-direction: column; gap: 12px; }
.shot-sidebar { display: flex; flex-direction: column; gap: 14px; width: 340px; }

/* Toolbar */
.shot-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: center;
  padding: 8px 12px;
  border: 1px solid rgba(96, 165, 250, 0.12);
  border-radius: 14px;
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.5), rgba(15, 23, 42, 0.2)),
    rgba(59, 130, 246, 0.04);
}
.toolbar-label { font-size: 11px; color: #64748b; font-weight: 600; }
.shot-source { text-align: center; font-size: 11px; color: #334155; }

/* Segmented filter buttons - 暗色主题 */
:deep(.el-segmented) {
  --el-segmented-bg-color: transparent;
  --el-segmented-border-color: transparent;
  --el-segmented-text-color: #9fb0c7;
  --el-segmented-active-bg-color: transparent;
  --el-segmented-active-text-color: #fff;
  padding: 3px !important;
  border: 1px solid rgba(96, 165, 250, 0.18) !important;
  border-radius: 12px !important;
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.5), rgba(15, 23, 42, 0.22)),
    rgba(59, 130, 246, 0.05) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04) !important;
  backdrop-filter: blur(10px);
}
:deep(.el-segmented__item) {
  min-height: 32px !important;
  border-radius: 9px !important;
  padding: 5px 13px !important;
  color: #9fb0c7 !important;
  font-size: 12px !important;
  font-weight: 800 !important;
  transition: color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease !important;
}
:deep(.el-segmented__item-label) {
  position: relative;
  z-index: 1;
  line-height: 1 !important;
}
:deep(.el-segmented__item-selected) {
  border-radius: 9px !important;
  background:
    linear-gradient(135deg, rgba(59, 162, 255, 0.96), rgba(37, 99, 235, 0.92)) !important;
  box-shadow:
    0 8px 18px rgba(37, 99, 235, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.22) !important;
}
:deep(.el-segmented__item:hover:not(.is-active)) {
  color: #dbeafe !important;
  background: rgba(96, 165, 250, 0.1) !important;
}
:deep(.el-segmented__item.is-active) {
  color: #fff !important;
  transform: translateY(-1px);
}


/* ===== 亮色主题 ===== */
[data-theme="light"] .game-shot-chart {
  background: #ffffff !important;
  border-color: #e2e8f0 !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06) !important;
}
[data-theme="light"] .header-title { color: #0f172a; }
[data-theme="light"] .header-sub { color: #64748b; }
[data-theme="light"] .shot-toolbar {
  background: #ffffff !important;
  border: 1px solid #e2e8f0 !important;
  box-shadow: none !important;
}
[data-theme="light"] .toolbar-label { color: #334155; font-weight: 700; }
[data-theme="light"] .shot-source { color: #64748b; }
[data-theme="light"] :deep(.el-segmented) {
  background: #ffffff !important;
  border: 1px solid #d1d5db !important;
  box-shadow: none !important;
  backdrop-filter: none !important;
  padding: 3px !important;
}
[data-theme="light"] :deep(.el-segmented__item) {
  color: #374151 !important;
  font-weight: 600 !important;
}
[data-theme="light"] :deep(.el-segmented__item:hover:not(.is-active)) {
  color: #111827 !important;
  background: #f3f4f6 !important;
}
[data-theme="light"] :deep(.el-segmented__item-selected),
[data-theme="light"] :deep(.el-segmented__item.is-active) {
  background: var(--accent) !important;
  color: #fff !important;
  box-shadow: 0 1px 3px rgba(232, 93, 38, 0.3) !important;
}

/* Unavailable */
.panel-unavailable { text-align: center; padding: 80px 24px; }
.unavail-icon { font-size: 36px; margin-bottom: 14px; opacity: 0.35; }
.unavail-title { font-size: 15px; font-weight: 600; color: var(--text-secondary); margin-bottom: 6px; }
.unavail-sub { font-size: 12px; color: #475569; }

@media (max-width: 900px) {
  .shot-content { grid-template-columns: 1fr; }
  .shot-sidebar { width: 100%; }
  .header-filters { width: 100%; justify-content: center; }
}
</style>
