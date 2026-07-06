<template>
  <section class="game-trend-card">
    <div class="trend-header">
      <div>
        <h4 class="trend-title">比赛走势</h4>
        <p class="trend-subtitle">按比分差展示双方领先变化</p>
      </div>
      <div v-if="latestMargin !== null" class="margin-pill" :class="latestMargin >= 0 ? 'home' : 'away'">
        {{ latestMargin >= 0 ? homeTeam : awayTeam }} {{ Math.abs(latestMargin) }} 分
      </div>
    </div>

    <div v-if="data.length" ref="chartWrapperRef" class="chart-wrapper">
      <v-chart
        ref="chartRef"
        class="chart-instance"
        :option="chartOption"
        :autoresize="false"
        @click="handleChartClick"
      />
    </div>
    <div v-else class="empty-state">暂无走势数据</div>

    <div v-if="data.length" class="chart-legend">
      <span><i class="dot home"></i>{{ homeTeam }}领先</span>
      <span><i class="dot away"></i>{{ awayTeam }}领先</span>
      <span class="hint">点击高亮节点查看回合</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import type { MomentumSnapshot } from '@/api/types'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart, ScatterChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  MarkLineComponent,
  DataZoomComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([
  LineChart,
  ScatterChart,
  GridComponent,
  TooltipComponent,
  MarkLineComponent,
  DataZoomComponent,
  CanvasRenderer,
])

const props = defineProps<{
  data: MomentumSnapshot[]
  homeTeam: string
  awayTeam: string
}>()

const emit = defineEmits<{
  pointClick: [snapshot: MomentumSnapshot]
}>()

const chartRef = ref<InstanceType<typeof VChart> | null>(null)
const chartWrapperRef = ref<HTMLDivElement | null>(null)

// ---------------------------------------------------------------------------
// Theme resolution: read CSS variables or fall back to dark defaults
// ---------------------------------------------------------------------------
function resolveThemeVar(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback
  const val = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return val || fallback
}

const themeColors = computed(() => ({
  home: resolveThemeVar('--gi-home', '#3B82F6'),
  away: resolveThemeVar('--gi-away', '#F97316'),
  surface: resolveThemeVar('--gi-surface-strong', '#111827'),
  border: resolveThemeVar('--gi-border', 'rgba(255,255,255,0.08)'),
  text: resolveThemeVar('--gi-text', '#F8FAFC'),
  muted: resolveThemeVar('--gi-muted', '#94A3B8'),
}))

// ---------------------------------------------------------------------------
// Data processing – thin out unchanged segments, keep sign-change anchors
// ---------------------------------------------------------------------------
const processedData = computed(() => {
  if (props.data.length <= 2) return props.data
  const result: MomentumSnapshot[] = []
  let lastMargin: number | null = null
  props.data.forEach((item, index) => {
    const changed = lastMargin === null || item.scoreMargin !== lastMargin
    const crossesZero =
      lastMargin !== null && Math.sign(item.scoreMargin) !== Math.sign(lastMargin)
    const periodicAnchor = index % 12 === 0
    if (index === 0 || index === props.data.length - 1 || changed || crossesZero || periodicAnchor) {
      result.push(item)
      lastMargin = item.scoreMargin
    }
  })
  return result
})

// ---------------------------------------------------------------------------
// Period boundaries: compute which eventOrder values fall at period transitions
// (for X-axis category labels)
// ---------------------------------------------------------------------------
const periodLabels = computed(() => {
  const items = processedData.value
  if (!items.length) return [] as string[]
  const labels: string[] = []
  for (let i = 0; i < items.length; i++) {
    const prev = items[i - 1]
    if (!prev || prev.period !== items[i].period) {
      labels.push(`Q${items[i].period}`)
    } else {
      labels.push('')
    }
  }
  return labels
})

// ---------------------------------------------------------------------------
// Important points – sign changes, local extremes, high swing
// ---------------------------------------------------------------------------
const importantIndices = computed(() => {
  const items = processedData.value
  if (!items.length) return new Set<number>()
  const maxMargin = Math.max(10, ...items.map((d) => Math.abs(d.scoreMargin)))
  const set = new Set<number>()

  for (let i = 0; i < items.length; i++) {
    const prev = items[i - 1]
    const next = items[i + 1]
    const d = items[i]

    // endpoints
    if (i === 0 || i === items.length - 1) {
      set.add(i)
      continue
    }

    // sign change
    if (Math.sign(d.scoreMargin) !== Math.sign(prev.scoreMargin)) {
      set.add(i)
      continue
    }

    // large swing (>= 5 points between consecutive processed entries)
    if (Math.abs(d.scoreMargin - prev.scoreMargin) >= 5) {
      set.add(i)
      continue
    }

    // local extreme (peak or valley)
    if (
      Math.abs(d.scoreMargin) >= Math.abs(prev.scoreMargin) &&
      Math.abs(d.scoreMargin) >= Math.abs(next.scoreMargin) &&
      Math.abs(d.scoreMargin) >= maxMargin * 0.5
    ) {
      set.add(i)
    }
  }
  return set
})

// ---------------------------------------------------------------------------
// Latest margin for the badge
// ---------------------------------------------------------------------------
const latestMargin = computed(() => {
  const last = props.data.at(-1)
  return last ? last.scoreMargin : null
})

// ---------------------------------------------------------------------------
// ECharts option
// ---------------------------------------------------------------------------
const chartOption = computed(() => {
  const colors = themeColors.value
  const items = processedData.value
  if (!items.length) return {}

  const xLabels = periodLabels.value
  const maxMargin = Math.max(10, ...items.map((d) => Math.abs(d.scoreMargin)))

  // Scatter data for important points: [index, scoreMargin, snapshotIndex]
  const scatterData: [number, number, number][] = []
  importantIndices.value.forEach((idx) => {
    scatterData.push([idx, items[idx].scoreMargin, idx])
  })

  // Home / Away area visual pieces (above / below zero line)
  const isLight =
    typeof document !== 'undefined' &&
    document.documentElement.getAttribute('data-theme') === 'light'

  return {
    backgroundColor: 'transparent',
    grid: {
      top: 24,
      right: 20,
      bottom: 32,
      left: 52,
      containLabel: false,
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: isLight ? 'rgba(255,255,255,0.96)' : 'rgba(17,24,39,0.95)',
      borderColor: isLight ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.08)',
      borderWidth: 1,
      textStyle: {
        color: isLight ? '#1E293B' : colors.text,
        fontSize: 12,
      },
      formatter(params: any) {
        const point = params[0]
        if (!point) return ''
        const d = items[point.dataIndex]
        if (!d) return ''
        return `<div style="line-height:1.6">` +
          `<strong>Q${d.period} ${d.gameClock}</strong><br/>` +
          `${homeTeam} ${d.homeScore} - ${d.awayScore} ${awayTeam}<br/>` +
          `<span style="color:${d.scoreMargin >= 0 ? colors.home : colors.away}">` +
          `${d.scoreMargin >= 0 ? homeTeam : awayTeam} 领先 ${Math.abs(d.scoreMargin)} 分</span>` +
          `</div>`
      },
    },
    xAxis: {
      type: 'category',
      data: xLabels,
      boundaryGap: false,
      axisLine: {
        show: false,
      },
      axisTick: {
        show: false,
      },
      axisLabel: {
        color: colors.muted,
        fontSize: 11,
        fontWeight: 600,
        interval: (index: number) => xLabels[index] !== '',
        // Show non-empty labels, hide the rest but keep category positions
        formatter: (val: string) => val || '',
      },
      splitLine: {
        show: false,
      },
    },
    yAxis: {
      type: 'value',
      min: -maxMargin,
      max: maxMargin,
      axisLine: {
        show: false,
      },
      axisTick: {
        show: false,
      },
      splitLine: {
        lineStyle: {
          color: isLight ? 'rgba(0,0,0,0.06)' : 'rgba(255,255,255,0.06)',
        },
      },
      axisLabel: {
        color: colors.muted,
        fontSize: 11,
        formatter: (val: number) => (val === 0 ? '0' : `${val > 0 ? '+' : ''}${val}`),
      },
    },
    series: [
      // Main line
      {
        type: 'line',
        data: items.map((d) => d.scoreMargin),
        smooth: true,
        symbol: 'none',
        lineStyle: {
          width: 2.5,
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: colors.home },
              { offset: 0.5, color: colors.home },
              { offset: 0.5, color: colors.away },
              { offset: 1, color: colors.away },
            ],
          },
        },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: {
            color: isLight ? 'rgba(0,0,0,0.18)' : 'rgba(255,255,255,0.18)',
            type: 'dashed',
            width: 1.5,
          },
          label: {
            show: false,
          },
          data: [{ yAxis: 0 }],
        },
      },
      // Home area (above zero)
      {
        type: 'line',
        data: items.map((d) => Math.max(0, d.scoreMargin)),
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 0, opacity: 0 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: hexToRgba(colors.home, 0.32) },
              { offset: 1, color: hexToRgba(colors.home, 0.02) },
            ],
          },
        },
        z: 1,
      },
      // Away area (below zero, flipped)
      {
        type: 'line',
        data: items.map((d) => Math.min(0, d.scoreMargin)),
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 0, opacity: 0 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 1,
            x2: 0,
            y2: 0,
            colorStops: [
              { offset: 0, color: hexToRgba(colors.away, 0.28) },
              { offset: 1, color: hexToRgba(colors.away, 0.02) },
            ],
          },
        },
        z: 1,
      },
      // Important-point scatter
      {
        type: 'scatter',
        data: scatterData,
        symbolSize: (val: any) => {
          const margin = Math.abs(val[1])
          return margin >= maxMargin * 0.75 ? 12 : 8
        },
        itemStyle: {
          color: (params: any) => {
            const snap = items[params.dataIndex]
            return snap && snap.scoreMargin >= 0 ? colors.home : colors.away
          },
          borderColor: isLight ? '#FFFFFF' : colors.surface,
          borderWidth: 2,
          shadowBlur: 6,
          shadowColor: (params: any) => {
            const snap = items[params.dataIndex]
            return snap && snap.scoreMargin >= 0
              ? hexToRgba(colors.home, 0.4)
              : hexToRgba(colors.away, 0.4)
          },
        },
        z: 10,
        // Allow click events to propagate
        cursor: 'pointer',
      },
    ],
    // No data zoom for now, keep it clean
    animation: true,
    animationDuration: 600,
    animationEasing: 'cubicOut',
  }
})

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function hexToRgba(hex: string, alpha: number): string {
  const raw = hex.replace('#', '')
  const bigint = parseInt(raw, 16)
  const r = (bigint >> 16) & 255
  const g = (bigint >> 8) & 255
  const b = bigint & 255
  return `rgba(${r},${g},${b},${alpha})`
}

// ---------------------------------------------------------------------------
// Chart click handler – map ECharts scatter click back to MomentumSnapshot
// ---------------------------------------------------------------------------
function handleChartClick(params: any) {
  if (params.seriesType === 'scatter' && params.dataIndex != null) {
    const snap = processedData.value[params.dataIndex]
    if (snap) emit('pointClick', snap)
  }
}

// ---------------------------------------------------------------------------
// ResizeObserver for responsive chart
// ---------------------------------------------------------------------------
let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  nextTick(() => {
    const wrapper = chartWrapperRef.value
    if (!wrapper) return
    resizeObserver = new ResizeObserver(() => {
      chartRef.value?.resize()
    })
    resizeObserver.observe(wrapper)
  })
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  chartRef.value?.dispose()
})
</script>

<style scoped>
.game-trend-card {
  background: var(--gi-surface-strong, #111827);
  border: 1px solid var(--gi-border, rgba(255, 255, 255, 0.08));
  border-radius: 14px;
  padding: 20px;
}

.trend-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.trend-title {
  margin: 0;
  color: var(--gi-text, #F8FAFC);
  font-size: 18px;
  font-weight: 700;
}

.trend-subtitle {
  margin: 4px 0 0;
  color: var(--gi-muted, #94A3B8);
  font-size: 12px;
}

.margin-pill {
  flex-shrink: 0;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid var(--gi-border, rgba(255, 255, 255, 0.08));
}

.margin-pill.home {
  color: var(--gi-home, #3B82F6);
  background: rgba(59, 130, 246, 0.12);
}

.margin-pill.away {
  color: var(--gi-away, #F97316);
  background: rgba(249, 115, 22, 0.12);
}

.chart-wrapper {
  width: 100%;
  height: 340px;
}

.chart-instance {
  width: 100%;
  height: 100%;
}

.chart-legend {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-top: 6px;
  color: var(--gi-muted, #94A3B8);
  font-size: 12px;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 50%;
}

.dot.home {
  background: var(--gi-home, #3B82F6);
}

.dot.away {
  background: var(--gi-away, #F97316);
}

.hint {
  color: #64748B;
}

.empty-state {
  border: 1px solid var(--gi-border, rgba(255, 255, 255, 0.08));
  border-radius: 14px;
  padding: 56px 24px;
  text-align: center;
  color: var(--gi-muted, #94A3B8);
  background: rgba(17, 24, 39, 0.56);
}

/* Light theme overrides */
:global([data-theme='light']) .game-trend-card {
  background: var(--gi-surface-strong, #FFFFFF);
  border-color: rgba(0, 0, 0, 0.08);
}

:global([data-theme='light']) .trend-title {
  color: #1E293B;
}

:global([data-theme='light']) .trend-subtitle {
  color: #64748B;
}

:global([data-theme='light']) .chart-legend {
  color: #64748B;
}

:global([data-theme='light']) .hint {
  color: #94A3B8;
}

:global([data-theme='light']) .margin-pill {
  border-color: rgba(0, 0, 0, 0.08);
}

:global([data-theme='light']) .empty-state {
  background: rgba(248, 250, 252, 0.56);
  color: #64748B;
}
</style>
