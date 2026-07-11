<template>
  <div class="momentum-area" :class="{ 'away-lead': isAwayLeading }" @mouseleave="handleMouseLeave">
    <div class="chart-head">
      <span class="chart-label">领先分差</span>
      <div class="inline-legend">
        <span><i class="home"></i>{{ homeTeam || '主队' }}</span>
        <span><i class="away"></i>{{ awayTeam || '客队' }}</span>
      </div>
    </div>

    <svg
      :viewBox="`0 0 ${width} ${height}`"
      role="img"
      aria-label="比赛领先分差走势"
      @mousemove="handleMouseMove"
      @mouseleave="handleMouseLeave"
    >
      <defs>
        <!-- Vertical gradient: home color (top) → zero → away color (bottom) -->
        <linearGradient id="mainLineGrad" x1="0" x2="0" :y1="padding.top" :y2="height - padding.bottom" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="var(--trend-home)" />
          <stop :offset="`${zeroPercent}%`" stop-color="var(--trend-home)" />
          <stop :offset="`${zeroPercent}%`" stop-color="var(--trend-away)" />
          <stop offset="100%" stop-color="var(--trend-away)" />
        </linearGradient>
        <!-- Area fill gradient: home color (top) → transparent (zero) → away color (bottom) -->
        <linearGradient id="areaFillGrad" x1="0" x2="0" :y1="padding.top" :y2="height - padding.bottom" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="var(--trend-home)" stop-opacity="0.18" />
          <stop :offset="`${zeroPercent}%`" stop-color="var(--trend-home)" stop-opacity="0.04" />
          <stop :offset="`${zeroPercent}%`" stop-color="var(--trend-away)" stop-opacity="0.04" />
          <stop offset="100%" stop-color="var(--trend-away)" stop-opacity="0.18" />
        </linearGradient>
        <!-- Single glow filter -->
        <filter id="mainLineGlow" x="-10%" y="-20%" width="120%" height="140%">
          <feGaussianBlur stdDeviation="5" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      <g class="quarters">
        <g v-for="(band, index) in model.quarterBands" :key="band.period">
          <rect
            :x="band.x"
            :y="padding.top"
            :width="band.width"
            :height="plotHeight"
            :class="index % 2 === 0 ? 'quarter-a' : 'quarter-b'"
          />
          <line
            v-if="index > 0"
            :x1="band.x"
            :x2="band.x"
            :y1="padding.top"
            :y2="height - padding.bottom"
            class="quarter-divider"
          />
          <text :x="band.x + band.width / 2" :y="padding.top + 22" text-anchor="middle" class="quarter-label">
            {{ band.label }}
          </text>
        </g>
      </g>

      <g class="grid">
        <g v-for="tick in yTicks" :key="tick.value">
          <line
            :x1="padding.left"
            :x2="width - padding.right"
            :y1="tick.y"
            :y2="tick.y"
            :class="{ zero: tick.value === 0 }"
          />
          <text :x="padding.left - 12" :y="tick.y + 4" text-anchor="end">{{ tick.label }}</text>
        </g>
      </g>

      <!-- Single area fill with gradient (home color top → away color bottom) -->
      <path v-if="mainAreaPath" class="main-area area-fill" :d="mainAreaPath" />

      <!-- Single continuous glow layer -->
      <path v-if="mainLinePath" class="main-line-glow smooth-line" :d="mainLinePath" />
      <!-- Single continuous main line with gradient stroke -->
      <path v-if="mainLinePath" class="main-line smooth-line" :d="mainLinePath" />

      <!-- Crosshair (hover) -->
      <line
        v-if="hoveredPoint"
        :x1="hoveredPoint.x"
        :y1="padding.top"
        :x2="hoveredPoint.x"
        :y2="height - padding.bottom"
        class="crosshair-line"
      />

      <!-- Hover dot -->
      <circle
        v-if="hoveredPoint"
        :cx="hoveredPoint.x"
        :cy="hoveredPoint.y"
        r="5"
        class="hover-dot"
        :class="hoveredPoint.side"
      />

      <!-- Hit area for mouse detection -->
      <rect
        :x="padding.left"
        :y="padding.top"
        :width="width - padding.left - padding.right"
        :height="plotHeight"
        fill="transparent"
        class="hit-area"
      />
    </svg>

    <!-- Hover tooltip (only visible on hover) -->
    <div
      v-if="hoveredPoint"
      class="hover-tooltip"
      :style="{ left: `${tooltipPixelX}px`, top: `${tooltipPixelY}px` }"
    >
      <strong :class="hoveredPoint.side">{{ periodLabel(hoveredPoint.period) }} {{ hoveredPoint.gameClock }}</strong>
      <span>{{ displayDescription(hoveredPoint.event) }}</span>
      <b>{{ hoveredPoint.homeScore }} - {{ hoveredPoint.awayScore }}</b>
      <em :class="hoveredPoint.side">{{ leaderText(homeTeam, awayTeam, hoveredPoint.signedMargin) }}</em>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { TrendDisplayMode, TrendModel, TrendPoint } from './types'
import { areaPath, findNearestHoverPoint, smoothPath } from './trendGeometry'
import { displayDescription, leaderText, periodLabel } from './trendUtils'

const props = defineProps<{
  model: TrendModel
  homeTeam: string
  awayTeam: string
  displayMode: TrendDisplayMode
  width: number
  height: number
  padding: { top: number; right: number; bottom: number; left: number }
}>()

defineEmits<{
  select: [eventOrder: number]
}>()

const hoveredPoint = ref<TrendPoint | null>(null)
const svgRect = ref<DOMRect | null>(null)

const plotHeight = computed(() => props.height - props.padding.top - props.padding.bottom)
const zeroY = computed(() => scaleMargin(0))

// Gradient zero-line position (percentage from top)
const zeroPercent = computed(() => {
  const p = props.padding.top
  const h = plotHeight.value
  return ((zeroY.value - p) / h) * 100
})

const isAwayLeading = computed(() => {
  const last = props.model.points.at(-1)
  return last ? last.signedMargin < 0 : false
})

const yTicks = computed(() => {
  const ticks: number[] = []
  for (let value = props.model.yMax; value >= -props.model.yMax; value -= 5) ticks.push(value)
  return ticks.map((value) => ({
    value,
    y: scaleMargin(value),
    label: value > 0 ? `+${value}` : `${value}`,
  }))
})

// Tooltip pixel position relative to the parent .momentum-area div
const tooltipPixelX = computed(() => {
  if (!hoveredPoint.value || !svgRect.value) return 0
  // SVG margin-left is 10px, so offset from parent left edge
  return 10 + (hoveredPoint.value.x / props.width) * svgRect.value.width
})
const tooltipPixelY = computed(() => {
  if (!hoveredPoint.value || !svgRect.value) return 0
  return (hoveredPoint.value.y / props.height) * svgRect.value.height
})

// Single continuous path through ALL points
const mainLinePath = computed(() => {
  if (props.model.points.length === 0) return ''
  return smoothPath(props.model.points)
})

// Single area fill from line down to baseline
const mainAreaPath = computed(() => {
  if (props.model.points.length < 2) return ''
  return areaPath(props.model.points, zeroY.value)
})

function scaleMargin(value: number): number {
  return props.padding.top + ((props.model.yMax - value) / (props.model.yMax * 2)) * plotHeight.value
}

function handleMouseMove(event: MouseEvent) {
  const svg = event.currentTarget as SVGSVGElement
  const rect = svg.getBoundingClientRect()
  svgRect.value = rect
  const svgX = (event.clientX - rect.left) / rect.width * props.width
  const svgY = (event.clientY - rect.top) / rect.height * props.height

  hoveredPoint.value = findNearestHoverPoint(props.model.points, svgX, svgY, 30)
}

function handleMouseLeave() {
  hoveredPoint.value = null
}

</script>

<style scoped>
.momentum-area {
  position: relative;
  overflow: visible;
  padding: 0;
  background: transparent;
  border: 1px solid var(--trend-border);
  border-radius: 18px;
  --glow-color: var(--trend-home-soft);
  --crosshair-color: rgba(255, 255, 255, 0.1);
}

.momentum-area.away-lead {
  --glow-color: var(--trend-away-soft);
}

.momentum-area::before {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background:
    radial-gradient(ellipse 60% 50% at 50% 50%, var(--glow-color), transparent),
    radial-gradient(circle at 18% 12%, var(--trend-home-soft), transparent 30%),
    radial-gradient(circle at 82% 16%, var(--trend-away-soft), transparent 28%);
  opacity: 0.25;
  pointer-events: none;
  content: '';
  transition: background 0.8s ease;
}

.chart-head {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 36px;
  padding: 0 14px;
  color: var(--trend-text);
  font-size: 13px;
  font-weight: 800;
  border-bottom: 1px solid var(--trend-border);
}

.chart-label {
  color: var(--trend-muted);
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0.04em;
}

.inline-legend {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--trend-muted);
  font-size: 11px;
  font-weight: 700;
}

.inline-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}

.inline-legend i {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

.inline-legend .home {
  background: var(--trend-home);
  box-shadow: 0 0 6px var(--trend-home-soft);
}

.inline-legend .away {
  background: var(--trend-away);
  box-shadow: 0 0 6px var(--trend-away-soft);
}

svg {
  position: relative;
  z-index: 1;
  display: block;
  width: calc(100% - 20px);
  margin: 0 10px;
  aspect-ratio: 1180 / 380;
  background:
    linear-gradient(var(--border-subtle) 1px, transparent 1px),
    linear-gradient(90deg, var(--border-subtle) 1px, transparent 1px),
    radial-gradient(ellipse 50% 40% at 50% 50%, var(--glow-color), transparent),
    var(--bg-panel);
  background-size: 56px 56px, 56px 56px, auto, auto;
  border: 1px solid var(--trend-border);
  border-radius: 10px;
  box-shadow: inset 0 1px 0 var(--border-subtle);
  transition: background 0.8s ease;
  cursor: crosshair;
}

.quarters rect {
  fill: var(--quarter-fill-a);
}

.quarters rect.quarter-b {
  fill: var(--quarter-fill-b);
}

.quarter-divider {
  stroke: var(--trend-border-strong);
  stroke-dasharray: 5 6;
  stroke-width: 1;
}

.quarter-label,
.grid text {
  fill: var(--trend-muted);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.1em;
}

.grid line {
  stroke: var(--trend-grid);
  stroke-width: 0.5;
  opacity: 0.5;
}

.grid line.zero {
  stroke: var(--trend-border-strong);
  stroke-dasharray: 8 4;
  stroke-width: 1;
  opacity: 0.8;
}

/* Area fill */
.main-area {
  fill: url(#areaFillGrad);
}

/* Main line — gradient stroke */
.main-line,
.main-line-glow {
  fill: none;
  stroke: url(#mainLineGrad);
  stroke-linecap: round;
  stroke-linejoin: round;
}

.main-line {
  stroke-width: 2.5;
}

.main-line-glow {
  stroke-width: 7;
  opacity: 0.25;
  filter: url(#mainLineGlow);
}

/* Smooth line draw animation */
.smooth-line {
  stroke-dasharray: 2400;
  stroke-dashoffset: 2400;
  animation: draw-line 1.8s ease forwards;
}

@keyframes draw-line {
  to {
    stroke-dashoffset: 0;
  }
}

/* Area fade-in */
.area-fill {
  animation: area-in 1.2s ease 0.5s both;
}

@keyframes area-in {
  from {
    opacity: 0;
  }
}

/* Crosshair */
.crosshair-line {
  stroke: var(--crosshair-color);
  stroke-width: 1;
  stroke-dasharray: 4 4;
  pointer-events: none;
}

/* Hover dot */
.hover-dot {
  stroke: var(--bg-card);
  stroke-width: 2.5;
  pointer-events: none;
  fill: var(--trend-home);
  filter: drop-shadow(0 0 8px var(--trend-home-soft));
}

.hover-dot.away {
  fill: var(--trend-away);
  filter: drop-shadow(0 0 8px var(--trend-away-soft));
}

/* Hit area */
.hit-area {
  cursor: crosshair;
}

/* Hover tooltip */
.hover-tooltip {
  position: absolute;
  z-index: 20;
  min-width: 160px;
  max-width: 240px;
  padding: 10px 13px;
  border: 1px solid var(--trend-border-strong);
  border-radius: 12px;
  color: var(--trend-text);
  background:
    linear-gradient(180deg, rgba(var(--bg-card-rgb), 0.95), rgba(var(--bg-panel-rgb), 0.9));
  box-shadow:
    var(--shadow-lg),
    inset 0 1px 0 var(--border-subtle);
  backdrop-filter: blur(16px);
  pointer-events: none;
  transform: translate(-50%, calc(-100% - 14px));
  animation: tooltip-in 0.15s ease;
}

.hover-tooltip strong,
.hover-tooltip span,
.hover-tooltip b,
.hover-tooltip em {
  display: block;
}

.hover-tooltip strong {
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 0.06em;
  color: var(--trend-home);
}

.hover-tooltip strong.away {
  color: var(--trend-away);
}

.hover-tooltip span {
  margin-top: 4px;
  color: var(--trend-muted);
  font-size: 12px;
  line-height: 1.4;
}

.hover-tooltip b {
  margin-top: 6px;
  font-size: 17px;
  font-weight: 950;
  letter-spacing: -0.02em;
}

.hover-tooltip em {
  margin-top: 2px;
  font-style: normal;
  font-size: 11px;
  font-weight: 850;
  color: var(--trend-home);
}

.hover-tooltip em.away {
  color: var(--trend-away);
}

.hover-tooltip em.tie {
  color: var(--trend-text);
}

@keyframes tooltip-in {
  from {
    opacity: 0;
    transform: translate(-50%, calc(-100% - 8px));
  }
  to {
    opacity: 1;
    transform: translate(-50%, calc(-100% - 14px));
  }
}

@media (max-width: 720px) {
  .chart-head {
    grid-template-columns: 1fr;
    align-items: start;
    padding: 10px 12px;
  }

  .chart-head > strong,
  .inline-legend {
    justify-self: start;
  }
}
</style>
