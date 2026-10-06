<template>
  <div class="momentum-area" :class="{ 'away-lead': isAwayLeading }" @mouseleave="handleMouseLeave">
    <div class="chart-head">
      <div class="chart-title-group">
        <span class="chart-label">比赛走势 · 领先分差</span>
        <span class="chart-sub">流畅贝塞尔动量曲线 / 0 轴基准线</span>
      </div>
      <div class="inline-legend">
        <span class="legend-item"><i class="home"></i>{{ homeTeam || '主队' }}</span>
        <span class="legend-tied"><i class="tied"></i>平局线</span>
        <span class="legend-item"><i class="away"></i>{{ awayTeam || '客队' }}</span>
      </div>
    </div>

    <div class="chart-viewport">
      <svg
        :viewBox="`0 0 ${width} ${height}`"
        role="img"
        aria-label="比赛领先分差走势"
        @mousemove="handleMouseMove"
        @mouseleave="handleMouseLeave"
      >
      <defs>
        <!-- Vertical line gradient: home color (top) → zero → away color (bottom) -->
        <linearGradient
          id="mainLineGrad"
          x1="0"
          x2="0"
          :y1="padding.top"
          :y2="height - padding.bottom"
          gradientUnits="userSpaceOnUse"
        >
          <stop offset="0%" stop-color="var(--trend-home)" />
          <stop :offset="`${Math.max(0, Math.min(100, zeroPercent - 2))}%`" stop-color="var(--trend-home)" />
          <stop :offset="`${Math.max(0, Math.min(100, zeroPercent + 2))}%`" stop-color="var(--trend-away)" />
          <stop offset="100%" stop-color="var(--trend-away)" />
        </linearGradient>

        <!-- Area fill gradient (Home lead: above 0 axis, fades upward) -->
        <linearGradient
          id="homeAreaGrad"
          x1="0"
          x2="0"
          :y1="padding.top"
          :y2="zeroY"
          gradientUnits="userSpaceOnUse"
        >
          <stop offset="0%" stop-color="var(--trend-home)" stop-opacity="0.32" />
          <stop offset="65%" stop-color="var(--trend-home)" stop-opacity="0.12" />
          <stop offset="100%" stop-color="var(--trend-home)" stop-opacity="0.01" />
        </linearGradient>

        <!-- Area fill gradient (Away lead: below 0 axis, fades downward) -->
        <linearGradient
          id="awayAreaGrad"
          x1="0"
          x2="0"
          :y1="zeroY"
          :y2="height - padding.bottom"
          gradientUnits="userSpaceOnUse"
        >
          <stop offset="0%" stop-color="var(--trend-away)" stop-opacity="0.01" />
          <stop offset="35%" stop-color="var(--trend-away)" stop-opacity="0.12" />
          <stop offset="100%" stop-color="var(--trend-away)" stop-opacity="0.32" />
        </linearGradient>

        <!-- Area clip paths for upper & lower regions separated by zero line -->
        <clipPath id="aboveZeroClip">
          <rect
            :x="padding.left"
            :y="padding.top"
            :width="plotWidth"
            :height="Math.max(0, zeroY - padding.top)"
          />
        </clipPath>
        <clipPath id="belowZeroClip">
          <rect
            :x="padding.left"
            :y="zeroY"
            :width="plotWidth"
            :height="Math.max(0, height - padding.bottom - zeroY)"
          />
        </clipPath>

        <!-- Glow filter for main curve stroke -->
        <filter id="mainLineGlow" x="-20%" y="-40%" width="140%" height="180%">
          <feGaussianBlur stdDeviation="4.5" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>

        <!-- Baseline glow filter -->
        <filter id="baselineGlow" x="-10%" y="-40%" width="120%" height="180%">
          <feGaussianBlur stdDeviation="2.5" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      <!-- Quarter backgrounds and vertical separators -->
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
          <text
            :x="band.x + band.width / 2"
            :y="padding.top + 20"
            text-anchor="middle"
            class="quarter-label"
          >
            {{ band.label }}
          </text>
        </g>
      </g>

      <!-- Horizontal grid lines & ticks -->
      <g class="grid">
        <g v-for="tick in nonZeroTicks" :key="tick.value">
          <line
            :x1="padding.left"
            :x2="width - padding.right"
            :y1="tick.y"
            :y2="tick.y"
            class="grid-line"
          />
          <text :x="padding.left - 10" :y="tick.y + 4" text-anchor="end" class="grid-text">{{ tick.label }}</text>
        </g>
      </g>

      <!-- 0-Axis Baseline (Tied line) with glow line and benchmark badge -->
      <g class="zero-baseline-group">
        <line
          :x1="padding.left"
          :x2="width - padding.right"
          :y1="zeroY"
          :y2="zeroY"
          class="zero-baseline-glow"
        />
        <line
          :x1="padding.left"
          :x2="width - padding.right"
          :y1="zeroY"
          :y2="zeroY"
          class="zero-baseline"
        />
        <g class="zero-badge-g" :transform="`translate(${padding.left + 8}, ${zeroY - 9})`">
          <rect width="40" height="18" rx="4" class="zero-badge-rect" />
          <text x="20" y="12" text-anchor="middle" class="zero-badge-text">TIED</text>
        </g>
        <text :x="padding.left - 10" :y="zeroY + 4" text-anchor="end" class="zero-tick-text">0</text>
      </g>

      <!-- Dual Gradient Area Fill: Home lead (above 0) and Away lead (below 0) -->
      <path
        v-if="mainAreaPath"
        class="home-area-fill area-fill"
        :d="mainAreaPath"
        clip-path="url(#aboveZeroClip)"
        fill="url(#homeAreaGrad)"
      />
      <path
        v-if="mainAreaPath"
        class="away-area-fill area-fill"
        :d="mainAreaPath"
        clip-path="url(#belowZeroClip)"
        fill="url(#awayAreaGrad)"
      />

      <!-- Main Spline Curve: Glow underlayer + Crisp gradient stroke -->
      <path v-if="mainLinePath" class="main-line-glow smooth-line" :d="mainLinePath" />
      <path v-if="mainLinePath" class="main-line smooth-line" :d="mainLinePath" />

      <!-- Breathing/pulsing endpoint at the latest point -->
      <g v-if="lastPoint" class="latest-endpoint">
        <circle :cx="lastPoint.x" :cy="lastPoint.y" r="9" class="endpoint-pulse" :class="lastPoint.side" />
        <circle :cx="lastPoint.x" :cy="lastPoint.y" r="4.5" class="endpoint-core" :class="lastPoint.side" />
        <circle :cx="lastPoint.x" :cy="lastPoint.y" r="1.6" class="endpoint-dot" />
      </g>

      <!-- Crosshair (hover) -->
      <line
        v-if="hoveredPoint"
        :x1="hoveredPoint.x"
        :y1="padding.top"
        :x2="hoveredPoint.x"
        :y2="height - padding.bottom"
        class="crosshair-line"
      />

      <!-- Hover dot with animated ring -->
      <g v-if="hoveredPoint" class="hover-point-group">
        <circle
          :cx="hoveredPoint.x"
          :cy="hoveredPoint.y"
          r="8"
          class="hover-dot-outer"
          :class="hoveredPoint.side"
        />
        <circle
          :cx="hoveredPoint.x"
          :cy="hoveredPoint.y"
          r="4.5"
          class="hover-dot"
          :class="hoveredPoint.side"
        />
      </g>

      <!-- Hit area for mouse detection -->
      <rect
        :x="padding.left"
        :y="padding.top"
        :width="plotWidth"
        :height="plotHeight"
        fill="transparent"
        class="hit-area"
      />
    </svg>

    <!-- High-density interactive tooltip (only visible on hover) -->
      <div
        v-if="hoveredPoint"
        class="hover-tooltip"
        :class="tooltipPositionClass"
        :style="{ left: `${tooltipLeftPercent}%`, top: `${tooltipTopPercent}%` }"
      >
        <div class="tooltip-header">
          <span class="tooltip-time-badge" :class="hoveredPoint.side">
            {{ periodLabel(hoveredPoint.period) }} {{ hoveredPoint.gameClock }}
          </span>
          <span v-if="hoveredPoint.keyLabel" class="tooltip-key-pill">{{ hoveredPoint.keyLabel }}</span>
        </div>

        <div class="tooltip-scoreboard">
          <div class="tooltip-team tooltip-team--home" :class="{ 'is-leader': hoveredPoint.signedMargin > 0 }">
            <span class="team-name">{{ homeTeam || '主队' }}</span>
            <span class="team-score">{{ hoveredPoint.homeScore }}</span>
          </div>
          <div class="tooltip-vs">:</div>
          <div class="tooltip-team tooltip-team--away" :class="{ 'is-leader': hoveredPoint.signedMargin < 0 }">
            <span class="team-score">{{ hoveredPoint.awayScore }}</span>
            <span class="team-name">{{ awayTeam || '客队' }}</span>
          </div>
        </div>

        <div class="tooltip-lead-row">
          <span class="lead-badge" :class="hoveredPoint.side">
            {{ leaderText(homeTeam, awayTeam, hoveredPoint.signedMargin) }}
          </span>
        </div>

        <div class="tooltip-desc-row">
          <span class="event-icon" aria-hidden="true">{{ eventIcon(eventKind(hoveredPoint.event)) }}</span>
          <span class="event-desc">{{ displayDescription(hoveredPoint.event) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { TrendDisplayMode, TrendModel, TrendPoint } from './types'
import { areaPath, findNearestHoverPoint, smoothPath } from './trendGeometry'
import { displayDescription, eventIcon, eventKind, leaderText, periodLabel } from './trendUtils'

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

const plotWidth = computed(() => Math.max(1, props.width - props.padding.left - props.padding.right))
const plotHeight = computed(() => Math.max(1, props.height - props.padding.top - props.padding.bottom))
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

const lastPoint = computed(() => props.model.points.at(-1) || null)

const yTicks = computed(() => {
  const ticks: number[] = []
  for (let value = props.model.yMax; value >= -props.model.yMax; value -= 5) ticks.push(value)
  return ticks.map((value) => ({
    value,
    y: scaleMargin(value),
    label: value > 0 ? `+${value}` : `${value}`,
  }))
})

const nonZeroTicks = computed(() => yTicks.value.filter((t) => t.value !== 0))

// Precise percentage-based tooltip positioning within chart-viewport
const tooltipLeftPercent = computed(() => {
  if (!hoveredPoint.value) return 0
  return (hoveredPoint.value.x / props.width) * 100
})

const tooltipTopPercent = computed(() => {
  if (!hoveredPoint.value) return 0
  return (hoveredPoint.value.y / props.height) * 100
})

const tooltipPositionClass = computed(() => {
  if (!hoveredPoint.value) return ''
  const xRatio = hoveredPoint.value.x / props.width
  const yRatio = hoveredPoint.value.y / props.height
  const horizontal = xRatio < 0.16 ? 'align-left' : xRatio > 0.84 ? 'align-right' : 'align-center'
  const vertical = yRatio < 0.38 ? 'valign-bottom' : 'valign-top'
  return `${horizontal} ${vertical}`
})

// Single continuous smooth bezier spline through ALL points
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
  const svgX = ((event.clientX - rect.left) / rect.width) * props.width
  const svgY = ((event.clientY - rect.top) / rect.height) * props.height

  hoveredPoint.value = findNearestHoverPoint(props.model.points, svgX, svgY, 32)
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
  --crosshair-color: rgba(255, 255, 255, 0.15);
}

.momentum-area.away-lead {
  --glow-color: var(--trend-away-soft);
}

.momentum-area::before {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background:
    radial-gradient(ellipse 65% 55% at 50% 50%, var(--glow-color), transparent 70%),
    radial-gradient(circle at 18% 12%, var(--trend-home-soft), transparent 30%),
    radial-gradient(circle at 82% 16%, var(--trend-away-soft), transparent 28%);
  opacity: 0.3;
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
  min-height: 40px;
  padding: 0 16px;
  color: var(--trend-text);
  border-bottom: 1px solid var(--trend-border);
}

.chart-viewport {
  position: relative;
  width: 100%;
}

.chart-viewport svg {
  display: block;
  width: 100%;
  height: auto;
}

.chart-title-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chart-label {
  color: var(--trend-text);
  font-size: 13px;
  font-weight: 850;
  letter-spacing: 0.02em;
}

.chart-sub {
  color: var(--trend-muted);
  font-size: 11px;
  font-weight: 600;
  opacity: 0.75;
}

.inline-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--trend-muted);
  font-size: 11px;
  font-weight: 700;
}

.inline-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.inline-legend i {
  width: 7px;
  height: 7px;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

.inline-legend .home {
  background: var(--trend-home);
  box-shadow: 0 0 8px var(--trend-home);
}

.inline-legend .away {
  background: var(--trend-away);
  box-shadow: 0 0 8px var(--trend-away);
}

.inline-legend .tied {
  background: rgba(255, 255, 255, 0.45);
  box-shadow: 0 0 6px rgba(255, 255, 255, 0.3);
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
  border-radius: 12px;
  box-shadow: inset 0 1px 0 var(--border-subtle);
  transition: background 0.8s ease;
  cursor: crosshair;
}

/* Quarter bands */
.quarters rect.quarter-a {
  fill: rgba(var(--bg-panel-rgb, 18, 24, 38), 0.22);
}

.quarters rect.quarter-b {
  fill: rgba(var(--border-light-rgb, 255, 255, 255), 0.03);
}

.quarter-divider {
  stroke: var(--trend-border-strong);
  stroke-dasharray: 4 6;
  stroke-width: 1;
  opacity: 0.6;
}

.quarter-label {
  fill: var(--trend-muted);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.12em;
  opacity: 0.8;
}

/* Grid */
.grid-line {
  stroke: var(--trend-grid);
  stroke-width: 0.5;
  opacity: 0.4;
}

.grid-text {
  fill: var(--trend-muted);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  opacity: 0.7;
}

/* 0 Axis Baseline with glow & badge */
.zero-baseline-glow {
  stroke: rgba(255, 255, 255, 0.2);
  stroke-width: 3.5;
  filter: url(#baselineGlow);
  pointer-events: none;
}

.zero-baseline {
  stroke: rgba(255, 255, 255, 0.55);
  stroke-dasharray: 5 4;
  stroke-width: 1.2;
  pointer-events: none;
}

.zero-badge-rect {
  fill: rgba(18, 24, 38, 0.85);
  stroke: rgba(255, 255, 255, 0.22);
  stroke-width: 0.8;
  rx: 4px;
}

.zero-badge-text {
  fill: rgba(255, 255, 255, 0.85);
  font-size: 9px;
  font-weight: 850;
  letter-spacing: 0.1em;
}

.zero-tick-text {
  fill: rgba(255, 255, 255, 0.7);
  font-size: 11px;
  font-weight: 850;
}

/* Area fill */
.area-fill {
  animation: area-in 1.2s ease 0.3s both;
  pointer-events: none;
}

@keyframes area-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* Main smooth curve stroke */
.main-line,
.main-line-glow {
  fill: none;
  stroke: url(#mainLineGrad);
  stroke-linecap: round;
  stroke-linejoin: round;
  pointer-events: none;
}

.main-line {
  stroke-width: 2.8;
}

.main-line-glow {
  stroke-width: 8;
  opacity: 0.35;
  filter: url(#mainLineGlow);
}

.smooth-line {
  stroke-dasharray: 2800;
  stroke-dashoffset: 2800;
  animation: draw-line 1.6s cubic-bezier(0.25, 1, 0.5, 1) forwards;
}

@keyframes draw-line {
  to {
    stroke-dashoffset: 0;
  }
}

/* Breathing / Pulsing Endpoint */
.latest-endpoint {
  pointer-events: none;
}

.endpoint-pulse {
  fill: none;
  stroke-width: 1.5;
  animation: pulse-ring 2.2s cubic-bezier(0.2, 0.8, 0.4, 1) infinite;
}

.endpoint-pulse.home {
  stroke: var(--trend-home);
}

.endpoint-pulse.away {
  stroke: var(--trend-away);
}

.endpoint-core {
  stroke: var(--bg-card);
  stroke-width: 2;
}

.endpoint-core.home {
  fill: var(--trend-home);
  filter: drop-shadow(0 0 6px var(--trend-home));
}

.endpoint-core.away {
  fill: var(--trend-away);
  filter: drop-shadow(0 0 6px var(--trend-away));
}

.endpoint-dot {
  fill: #ffffff;
}

@keyframes pulse-ring {
  0% {
    r: 4.5px;
    opacity: 0.9;
  }
  70% {
    r: 13px;
    opacity: 0;
  }
  100% {
    r: 13px;
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
.hover-point-group {
  pointer-events: none;
}

.hover-dot-outer {
  fill: none;
  stroke-width: 1.5;
  opacity: 0.6;
}

.hover-dot-outer.home {
  stroke: var(--trend-home);
}

.hover-dot-outer.away {
  stroke: var(--trend-away);
}

.hover-dot {
  stroke: var(--bg-card);
  stroke-width: 2.5;
  fill: var(--trend-home);
  filter: drop-shadow(0 0 10px var(--trend-home));
}

.hover-dot.away {
  fill: var(--trend-away);
  filter: drop-shadow(0 0 10px var(--trend-away));
}

/* Hit area */
.hit-area {
  cursor: crosshair;
}

/* High-density interactive tooltip */
.hover-tooltip {
  position: absolute;
  z-index: 25;
  min-width: 200px;
  max-width: 280px;
  padding: 12px 14px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 14px;
  color: var(--trend-text);
  background:
    linear-gradient(180deg, rgba(18, 24, 38, 0.94), rgba(12, 16, 26, 0.96));
  box-shadow:
    0 16px 36px -6px rgba(0, 0, 0, 0.55),
    inset 0 1px 0 rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(20px);
  pointer-events: none;
  animation: tooltip-in 0.16s cubic-bezier(0.16, 1, 0.3, 1);
  transition: transform 0.12s cubic-bezier(0.22, 1, 0.36, 1);
}

.hover-tooltip.align-center.valign-top { transform: translate(-50%, calc(-100% - 14px)); }
.hover-tooltip.align-left.valign-top { transform: translate(-8%, calc(-100% - 14px)); }
.hover-tooltip.align-right.valign-top { transform: translate(-92%, calc(-100% - 14px)); }

.hover-tooltip.align-center.valign-bottom { transform: translate(-50%, 14px); }
.hover-tooltip.align-left.valign-bottom { transform: translate(-8%, 14px); }
.hover-tooltip.align-right.valign-bottom { transform: translate(-92%, 14px); }

.tooltip-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.tooltip-time-badge {
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.04em;
  color: var(--trend-home);
}

.tooltip-time-badge.away {
  color: var(--trend-away);
}

.tooltip-key-pill {
  font-size: 10px;
  font-weight: 850;
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.1);
  color: #fbbf24;
}

.tooltip-scoreboard {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
}

.tooltip-team {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.tooltip-team--home {
  justify-content: flex-start;
}

.tooltip-team--away {
  justify-content: flex-end;
}

.tooltip-team .team-name {
  font-size: 12px;
  font-weight: 750;
  color: var(--trend-muted);
}

.tooltip-team .team-score {
  font-size: 18px;
  font-weight: 950;
  font-variant-numeric: tabular-nums;
  color: var(--trend-text);
}

.tooltip-team.is-leader .team-score {
  color: var(--trend-home);
  text-shadow: 0 0 10px rgba(var(--gi-home-rgb, 34, 211, 238), 0.4);
}

.tooltip-team--away.is-leader .team-score {
  color: var(--trend-away);
  text-shadow: 0 0 10px rgba(var(--gi-away-rgb, 251, 146, 60), 0.4);
}

.tooltip-vs {
  font-size: 14px;
  font-weight: 900;
  color: var(--trend-muted);
  opacity: 0.5;
}

.tooltip-lead-row {
  margin-top: 8px;
}

.lead-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 850;
  background: rgba(var(--gi-home-rgb, 34, 211, 238), 0.12);
  color: var(--trend-home);
}

.lead-badge.away {
  background: rgba(var(--gi-away-rgb, 251, 146, 60), 0.12);
  color: var(--trend-away);
}

.lead-badge.tie {
  background: rgba(255, 255, 255, 0.08);
  color: var(--trend-text);
}

.tooltip-desc-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 8px;
  font-size: 11px;
  line-height: 1.45;
  color: var(--trend-muted);
}

.event-icon {
  flex-shrink: 0;
  font-weight: 900;
  color: var(--trend-text);
}

.event-desc {
  flex: 1;
}

@keyframes tooltip-in {
  from {
    opacity: 0;
    transform: translate(-50%, calc(-100% - 10px));
  }
  to {
    opacity: 1;
    transform: translate(-50%, calc(-100% - 16px));
  }
}

@media (max-width: 720px) {
  .chart-head {
    flex-direction: column;
    align-items: flex-start;
    padding: 8px 12px;
    gap: 6px;
  }

  .inline-legend {
    align-self: flex-start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .smooth-line {
    stroke-dasharray: none !important;
    stroke-dashoffset: 0 !important;
    animation: none !important;
  }

  .area-fill {
    animation: none !important;
    opacity: 1 !important;
  }

  .endpoint-pulse {
    animation: none !important;
    opacity: 0.35 !important;
  }

  .hover-tooltip {
    animation: none !important;
    transition: none !important;
  }
}
</style>
