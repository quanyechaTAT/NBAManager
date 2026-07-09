<template>
  <div class="momentum-area" :class="{ 'away-lead': isAwayLeading }" @mouseleave="handleMouseLeave">
    <div class="chart-head">
      <span>领先分差</span>
      <strong>峰值 {{ peakLead }} · 平局 {{ tieCount }} · 关键球 {{ keyMomentCount }}</strong>
      <div class="inline-legend">
        <span><i class="home"></i>{{ homeTeam || '主队' }}领先</span>
        <span><i class="away"></i>{{ awayTeam || '客队' }}领先</span>
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
        <linearGradient id="homeLeadFill" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stop-color="var(--trend-home)" stop-opacity="0.25" />
          <stop offset="50%" stop-color="var(--trend-home)" stop-opacity="0.1" />
          <stop offset="100%" stop-color="var(--trend-home)" stop-opacity="0" />
        </linearGradient>
        <linearGradient id="awayLeadFill" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stop-color="var(--trend-away)" stop-opacity="0" />
          <stop offset="50%" stop-color="var(--trend-away)" stop-opacity="0.1" />
          <stop offset="100%" stop-color="var(--trend-away)" stop-opacity="0.25" />
        </linearGradient>
        <filter id="homeLineGlow" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="4" result="blur" />
          <feFlood flood-color="var(--trend-home)" flood-opacity="0.6" result="color" />
          <feComposite in="color" in2="blur" operator="in" result="glow" />
          <feMerge>
            <feMergeNode in="glow" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        <filter id="awayLineGlow" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="4" result="blur" />
          <feFlood flood-color="var(--trend-away)" flood-opacity="0.6" result="color" />
          <feComposite in="color" in2="blur" operator="in" result="glow" />
          <feMerge>
            <feMergeNode in="glow" />
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

      <!-- Team-colored lead areas -->
      <path class="home-area area-fill" :d="homeAreaPath" />
      <path class="away-area area-fill" :d="awayAreaPath" />

      <!-- Team-colored lead lines — glow layer (thin) -->
      <path class="home-lead-line home-lead-glow smooth-line" :d="homeLinePath" />
      <path class="away-lead-line away-lead-glow smooth-line" :d="awayLinePath" />
      <!-- Team-colored lead lines — solid layer (thin) -->
      <path class="home-lead-line smooth-line" :d="homeLinePath" />
      <path class="away-lead-line smooth-line" :d="awayLinePath" />

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
      :style="{ left: `${(hoveredPoint.x / width) * 100}%`, top: `${(hoveredPoint.y / height) * 100}%` }"
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

const plotHeight = computed(() => props.height - props.padding.top - props.padding.bottom)
const zeroY = computed(() => scaleMargin(0))
const peakLead = computed(() => Math.max(props.model.metrics.maxHomeLead, props.model.metrics.maxAwayLead))
const tieCount = computed(() => props.model.metrics.tieCount)
const keyMomentCount = computed(() => props.model.markers.filter((marker) => marker.isKeyMoment).length)

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

// Split points into home-lead and away-lead segments
const homeLeadPoints = computed(() =>
  props.model.points.filter((p) => p.signedMargin >= 0).map((p) => ({ x: p.x, y: Math.min(p.y, zeroY.value) }))
)
const awayLeadPoints = computed(() =>
  props.model.points.filter((p) => p.signedMargin <= 0).map((p) => ({ x: p.x, y: Math.max(p.y, zeroY.value) }))
)

// Smooth curve paths using cubic bezier
const homeLinePath = computed(() => smoothPath(homeLeadPoints.value))
const awayLinePath = computed(() => smoothPath(awayLeadPoints.value))

// Area fills for each team's lead region
const homeAreaPath = computed(() => areaPath(homeLeadPoints.value, zeroY.value))
const awayAreaPath = computed(() => areaPath(awayLeadPoints.value, zeroY.value))

function scaleMargin(value: number): number {
  return props.padding.top + ((props.model.yMax - value) / (props.model.yMax * 2)) * plotHeight.value
}

function handleMouseMove(event: MouseEvent) {
  const svg = event.currentTarget as SVGSVGElement
  const rect = svg.getBoundingClientRect()
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
  display: grid;
  grid-template-columns: minmax(120px, 1fr) auto minmax(120px, 1fr);
  align-items: center;
  gap: 12px;
  min-height: 44px;
  padding: 0 16px;
  color: var(--trend-text);
  font-size: 14px;
  font-weight: 800;
  border-bottom: 1px solid var(--trend-border);
}

.chart-head > span {
  justify-self: start;
}

.chart-head > strong {
  justify-self: center;
  color: var(--trend-home);
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.inline-legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  color: var(--trend-muted);
  font-size: 11px;
  font-weight: 700;
}

.inline-legend span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.inline-legend i {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-full);
  background: var(--trend-border);
  flex-shrink: 0;
}

.inline-legend .home {
  background: var(--trend-home);
  box-shadow: 0 0 8px var(--trend-home-soft);
}

.inline-legend .away {
  background: var(--trend-away);
  box-shadow: 0 0 8px var(--trend-away-soft);
}

svg {
  position: relative;
  z-index: 1;
  display: block;
  width: calc(100% - 28px);
  margin: 0 14px;
  aspect-ratio: 1180 / 380;
  background:
    linear-gradient(var(--border-subtle) 1px, transparent 1px),
    linear-gradient(90deg, var(--border-subtle) 1px, transparent 1px),
    radial-gradient(ellipse 50% 40% at 50% 50%, var(--glow-color), transparent),
    var(--bg-panel);
  background-size: 48px 48px, 48px 48px, auto, auto;
  border: 1px solid var(--trend-border);
  border-radius: 12px;
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

/* Team-colored lead areas */
.home-area {
  fill: url(#homeLeadFill);
}

.away-area {
  fill: url(#awayLeadFill);
}

/* Team-colored lead lines — thin and smooth */
.home-lead-line,
.away-lead-line {
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.home-lead-line {
  stroke: var(--trend-home);
  stroke-width: 3;
  filter: drop-shadow(0 0 8px var(--trend-home-soft));
}

.home-lead-glow {
  stroke-width: 8;
  filter: url(#homeLineGlow);
  opacity: 0.4;
}

.away-lead-line {
  stroke: var(--trend-away);
  stroke-width: 3;
  filter: drop-shadow(0 0 8px var(--trend-away-soft));
}

.away-lead-glow {
  stroke-width: 8;
  filter: url(#awayLineGlow);
  opacity: 0.4;
}

/* Smooth line draw animation */
.smooth-line {
  stroke-dasharray: 2400;
  stroke-dashoffset: 2400;
  animation: draw-line 2s ease forwards;
}

.away-lead-line.smooth-line,
.away-lead-glow.smooth-line {
  animation-delay: 0.7s;
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
  stroke: rgba(255, 255, 255, 0.1);
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
