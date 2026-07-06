<template>
  <section class="momentum-card" v-if="data.length">
    <div class="momentum-header">
      <div>
        <h4 class="chart-title">比赛走势</h4>
        <p class="chart-subtitle">按比分差展示双方领先变化</p>
      </div>
      <div class="margin-pill" :class="latestMargin >= 0 ? 'home' : 'away'">
        {{ latestMargin >= 0 ? '主队' : '客队' }} {{ Math.abs(latestMargin) }} 分
      </div>
    </div>

    <svg :viewBox="`0 0 ${width} ${height}`" class="chart-svg" role="img" aria-label="比赛走势">
      <defs>
        <linearGradient id="momentumHome" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stop-color="#3BA2FF" stop-opacity="0.36" />
          <stop offset="100%" stop-color="#3BA2FF" stop-opacity="0.03" />
        </linearGradient>
        <linearGradient id="momentumAway" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stop-color="#FF5A6A" stop-opacity="0.03" />
          <stop offset="100%" stop-color="#FF5A6A" stop-opacity="0.32" />
        </linearGradient>
        <linearGradient id="momentumLine" x1="0" x2="1" y1="0" y2="0">
          <stop offset="0%" stop-color="#64B5FF" />
          <stop offset="55%" stop-color="#3BA2FF" />
          <stop offset="100%" stop-color="#FF5A6A" />
        </linearGradient>
      </defs>

      <g class="grid">
        <line :x1="padding" :y1="padding" :x2="width - padding" :y2="padding" />
        <line :x1="padding" :y1="centerY" :x2="width - padding" :y2="centerY" class="zero" />
        <line :x1="padding" :y1="height - padding" :x2="width - padding" :y2="height - padding" />
      </g>

      <text :x="padding" :y="padding - 10" class="axis-label">主队领先</text>
      <text :x="padding" :y="height - padding + 22" class="axis-label">客队领先</text>
      <text :x="width - padding" :y="centerY - 8" class="axis-label end" text-anchor="end">0</text>

      <path :d="homeAreaPath" fill="url(#momentumHome)" />
      <path :d="awayAreaPath" fill="url(#momentumAway)" />
      <path :d="linePath" fill="none" stroke="url(#momentumLine)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />

      <g v-for="(point, i) in points" :key="`${point.snapshot.id}-${i}`">
        <circle
          v-if="point.important"
          :cx="point.x"
          :cy="point.y"
          r="4.2"
          :class="point.snapshot.scoreMargin >= 0 ? 'point-home' : 'point-away'"
          @click="$emit('pointClick', point.snapshot)"
        >
          <title>Q{{ point.snapshot.period }} {{ point.snapshot.gameClock }} · {{ point.snapshot.homeScore }}-{{ point.snapshot.awayScore }}</title>
        </circle>
      </g>
    </svg>

    <div class="chart-legend">
      <span><i class="dot home"></i>主队领先</span>
      <span><i class="dot away"></i>客队领先</span>
      <span class="hint">点击高亮节点查看回合</span>
    </div>
  </section>
  <div v-else class="empty-state">暂无走势数据</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MomentumSnapshot } from '@/api/types'

const props = defineProps<{ data: MomentumSnapshot[] }>()
defineEmits<{ pointClick: [snapshot: MomentumSnapshot] }>()

const width = 960
const height = 280
const padding = 48
const centerY = height / 2

const chartData = computed(() => {
  if (props.data.length <= 2) return props.data
  const result: MomentumSnapshot[] = []
  let lastMargin: number | null = null
  props.data.forEach((item, index) => {
    const changed = lastMargin === null || item.scoreMargin !== lastMargin
    const crossesZero = lastMargin !== null && Math.sign(item.scoreMargin) !== Math.sign(lastMargin)
    const periodicAnchor = index % 12 === 0
    if (index === 0 || index === props.data.length - 1 || changed || crossesZero || periodicAnchor) {
      result.push(item)
      lastMargin = item.scoreMargin
    }
  })
  return result
})

const latestMargin = computed(() => chartData.value.at(-1)?.scoreMargin ?? 0)

const maxMargin = computed(() => {
  if (!chartData.value.length) return 10
  return Math.max(10, ...chartData.value.map(d => Math.abs(d.scoreMargin)))
})

const points = computed(() => {
  if (!chartData.value.length) return []
  const xStep = (width - 2 * padding) / Math.max(1, chartData.value.length - 1)
  return chartData.value.map((d, i) => {
    const y = centerY - (d.scoreMargin / maxMargin.value) * (centerY - padding)
    const previous = chartData.value[i - 1]
    const next = chartData.value[i + 1]
    const swing = previous ? Math.abs(d.scoreMargin - previous.scoreMargin) >= 5 : false
    const localExtreme = previous && next
      ? Math.abs(d.scoreMargin) >= Math.abs(previous.scoreMargin) && Math.abs(d.scoreMargin) >= Math.abs(next.scoreMargin)
      : false
    return {
      x: padding + i * xStep,
      y,
      snapshot: d,
      important: i === 0 || i === chartData.value.length - 1 || swing || localExtreme || Math.abs(d.scoreMargin) >= maxMargin.value * 0.75,
    }
  })
})

const linePath = computed(() => {
  if (!points.value.length) return ''
  if (points.value.length === 1) return `M ${points.value[0].x} ${points.value[0].y}`
  const parts = [`M ${points.value[0].x} ${points.value[0].y}`]
  for (let i = 1; i < points.value.length; i++) {
    const prev = points.value[i - 1]
    const curr = points.value[i]
    const midX = (prev.x + curr.x) / 2
    parts.push(`C ${midX} ${prev.y}, ${midX} ${curr.y}, ${curr.x} ${curr.y}`)
  }
  return parts.join(' ')
})

const homeAreaPath = computed(() => {
  const upper = points.value.map(p => ({ x: p.x, y: Math.min(p.y, centerY) }))
  if (!upper.length) return ''
  const line = upper.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
  return `${line} L ${upper[upper.length - 1].x} ${centerY} L ${upper[0].x} ${centerY} Z`
})

const awayAreaPath = computed(() => {
  const lower = points.value.map(p => ({ x: p.x, y: Math.max(p.y, centerY) }))
  if (!lower.length) return ''
  const line = lower.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
  return `${line} L ${lower[lower.length - 1].x} ${centerY} L ${lower[0].x} ${centerY} Z`
})
</script>

<style scoped>
.momentum-card {
  padding: 22px 24px 16px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background:
    radial-gradient(circle at 15% 0%, rgba(59, 162, 255, 0.12), transparent 35%),
    linear-gradient(180deg, rgba(15, 23, 42, 0.92), rgba(9, 15, 31, 0.86));
}
.momentum-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}
.chart-title {
  margin: 0;
  color: #eef4ff;
  font-size: 18px;
  font-weight: 700;
}
.chart-subtitle {
  margin: 4px 0 0;
  color: #7f8ca3;
  font-size: 12px;
}
.margin-pill {
  flex-shrink: 0;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid rgba(148, 163, 184, 0.16);
}
.margin-pill.home { color: #64b5ff; background: rgba(59, 162, 255, 0.12); }
.margin-pill.away { color: #ff7a85; background: rgba(255, 90, 106, 0.12); }
.chart-svg {
  display: block;
  width: 100%;
  height: clamp(220px, 26vw, 330px);
}
.grid line {
  stroke: rgba(148, 163, 184, 0.1);
  stroke-width: 1;
}
.grid .zero {
  stroke: rgba(226, 232, 240, 0.28);
  stroke-dasharray: 6 8;
}
.axis-label {
  fill: rgba(148, 163, 184, 0.72);
  font-size: 12px;
  font-weight: 600;
}
.axis-label.end { fill: rgba(226, 232, 240, 0.48); }
.point-home,
.point-away {
  cursor: pointer;
  stroke: #f8fbff;
  stroke-width: 2;
  transition: r 0.15s ease, opacity 0.15s ease;
}
.point-home { fill: #3ba2ff; }
.point-away { fill: #ff5a6a; }
.point-home:hover,
.point-away:hover { r: 6; }
.chart-legend {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-top: 6px;
  color: #8b98ad;
  font-size: 12px;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 50%;
}
.dot.home { background: #3ba2ff; }
.dot.away { background: #ff5a6a; }
.hint { color: #64748b; }
.empty-state {
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 14px;
  padding: 56px 24px;
  text-align: center;
  color: #7f8ca3;
  background: rgba(15, 23, 42, 0.56);
}
</style>
