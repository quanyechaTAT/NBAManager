<template>
  <div class="game-shot-chart" v-loading="loading">
    <!-- 加载中 -->
    <div v-if="loading" class="panel-loading">
      <span class="panel-loading__spinner" aria-hidden="true"></span>
      正在获取官方投篮坐标…
    </div>

    <!-- REAL_SHOT_CHART -->
    <template v-else-if="mode === 'REAL_SHOT_CHART'">
      <!-- 标题栏 -->
      <div class="shot-header">
        <div class="header-left">
          <span class="header-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="1em" height="1em"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg></span>
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
      <div class="unavail-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="1em" height="1em"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg></div>
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
  border: 1px solid var(--border-light);
  border-radius: 14px;
}
.panel-loading {
  display: flex; flex-direction: column; align-items: center; gap: 14px;
  padding: 80px 0; color: var(--text-muted); font-size: 13.5px;
}
.panel-loading__spinner {
  width: 34px; height: 34px;
  border: 3px solid var(--border-light);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: shotSpin 0.9s linear infinite;
}
@keyframes shotSpin { to { transform: rotate(360deg); } }

/* Header */
.shot-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.header-left { display: flex; align-items: center; gap: 8px; }
.header-icon { font-size: 18px; }
.header-title { font-size: 15px; font-weight: 800; color: var(--text-primary); }
.header-sub { font-size: 9px; font-weight: 700; color: var(--text-dim); letter-spacing: 2px; text-transform: uppercase; }
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
  border: 1px solid var(--border-light);
  border-radius: 14px;
  background: var(--bg-secondary);
}
.toolbar-label { font-size: 11px; color: var(--text-muted); font-weight: 600; }
.shot-source { text-align: center; font-size: 11px; color: var(--text-dim); }

/* Segmented filter buttons - 暗色主题 */
:deep(.el-segmented) {
  --el-segmented-bg-color: transparent;
  --el-segmented-border-color: transparent;
  --el-segmented-text-color: var(--text-muted);
  --el-segmented-active-bg-color: transparent;
  --el-segmented-active-text-color: #fff;
  padding: 3px !important;
  border: 1px solid var(--border-light) !important;
  border-radius: 12px !important;
  background: var(--bg-secondary) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04) !important;
  backdrop-filter: blur(10px);
}
:deep(.el-segmented__item) {
  min-height: 32px !important;
  border-radius: 9px !important;
  padding: 5px 13px !important;
  color: var(--text-muted) !important;
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
  background: linear-gradient(135deg, var(--accent-light), var(--accent)) !important;
  box-shadow:
    0 8px 18px rgba(34, 211, 238, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.22) !important;
}
:deep(.el-segmented__item:hover:not(.is-active)) {
  color: var(--text-primary) !important;
  background: var(--accent-lighter) !important;
}
:deep(.el-segmented__item.is-active) {
  color: #fff !important;
  transform: translateY(-1px);
}


/* ===== 亮色主题 ===== */
[data-theme="light"] .game-shot-chart {
  background: #ffffff !important;
  border-color: #E6E3DD !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06) !important;
}
[data-theme="light"] .header-title { color: var(--text-primary); }
[data-theme="light"] .header-sub { color: var(--text-dim); }
[data-theme="light"] .shot-toolbar {
  background: #FFFFFF !important;
  border: 1px solid #E6E3DD !important;
  box-shadow: none !important;
}
[data-theme="light"] .toolbar-label { color: var(--text-secondary); font-weight: 700; }
[data-theme="light"] .shot-source { color: var(--text-muted); }
[data-theme="light"] :deep(.el-segmented) {
  background: #FFFFFF !important;
  border: 1px solid #D2CEC5 !important;
  box-shadow: none !important;
  backdrop-filter: none !important;
  padding: 3px !important;
}
[data-theme="light"] :deep(.el-segmented__item) {
  color: var(--text-secondary) !important;
  font-weight: 600 !important;
}
[data-theme="light"] :deep(.el-segmented__item:hover:not(.is-active)) {
  color: var(--accent) !important;
  background: var(--accent-lighter) !important;
}
[data-theme="light"] :deep(.el-segmented__item-selected),
[data-theme="light"] :deep(.el-segmented__item.is-active) {
  background: var(--accent) !important;
  color: var(--accent-contrast, #04202A) !important;
  box-shadow: 0 1px 3px rgba(34, 211, 238, 0.3) !important;
}

:global(html[data-theme="light"]) .game-shot-chart,
:global(html[data-theme="light"]) .shot-toolbar {
  background: #ffffff !important;
}

:global(html[data-theme="light"]) .game-shot-chart :deep(.el-segmented) {
  --el-segmented-bg-color: #ffffff;
  --el-segmented-active-bg-color: transparent;
  background: #ffffff !important;
  border-color: #dbe7ff !important;
  box-shadow: none !important;
  backdrop-filter: none !important;
}

:global(html[data-theme="light"]) .game-shot-chart :deep(.el-segmented__group) {
  background: #ffffff !important;
}

:global(html[data-theme="light"]) .game-shot-chart :deep(.el-segmented__item) {
  color: var(--text-muted) !important;
  background: transparent !important;
}

:global(html[data-theme="light"]) .game-shot-chart :deep(.el-segmented__item:hover:not(.is-active)) {
  color: var(--accent) !important;
  background: var(--accent-lighter) !important;
}

:global(html[data-theme="light"]) .game-shot-chart :deep(.el-segmented__item-selected),
:global(html[data-theme="light"]) .game-shot-chart :deep(.el-segmented__item.is-active) {
  color: var(--accent-contrast, #04202A) !important;
  background: linear-gradient(135deg, var(--accent-light), var(--accent)) !important;
  box-shadow: 0 2px 8px rgba(34, 211, 238, 0.18) !important;
}

/* Unavailable */
.panel-unavailable {
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  max-width: 420px; margin: 40px auto 48px;
  padding: 44px 36px 40px;
  text-align: center;
  background: var(--bg-card);
  border: 1px dashed var(--border-medium);
  border-radius: var(--radius-xl);
}
.unavail-icon {
  display: flex; align-items: center; justify-content: center;
  width: 56px; height: 56px; margin-bottom: 8px;
  border-radius: 50%;
  background: var(--accent-lighter);
  color: var(--accent);
}
.unavail-title { font-family: var(--font-heading); font-size: 16px; font-weight: 700; color: var(--text-primary); }
.unavail-sub { font-size: 13px; line-height: 1.6; color: var(--text-muted); }

@media (max-width: 900px) {
  .shot-content { grid-template-columns: 1fr; }
  .shot-sidebar { width: 100%; }
  .header-filters { width: 100%; justify-content: center; }
}
</style>
