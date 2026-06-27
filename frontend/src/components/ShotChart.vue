<template>
  <div class="shot-chart-wrapper" :style="{ maxWidth: size + 'px' }">
    <!-- 空数据提示 -->
    <div v-if="!shots || shots.length === 0" class="shot-chart-empty">
      <svg viewBox="0 0 24 24" fill="none" width="32" height="32"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.5" opacity=".3"/><path d="M12 8v4M12 16h.01" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
      <span>暂无投篮数据</span>
      <span class="shot-chart-empty-sub">等待比赛数据同步</span>
    </div>
    <svg
      v-else
      ref="svgRef"
      class="shot-chart-svg"
      :viewBox="`0 0 ${courtW} ${courtH}`"
      preserveAspectRatio="xMidYMid meet"
    >
      <!-- Court floor -->
      <rect
        x="0" y="0"
        :width="courtW" :height="courtH"
        class="court-floor"
      />

      <!-- Outer boundary -->
      <rect
        x="0" y="0"
        :width="courtW" :height="courtH"
        class="court-line court-boundary"
        fill="none"
      />

      <!-- Half-court line (horizontal, at y=0 is the baseline, center is at y=courtH) -->
      <line
        :x1="0" :y1="courtH / 2"
        :x2="courtW" :y2="courtH / 2"
        class="court-line"
      />

      <!-- Center circle -->
      <circle
        :cx="courtW / 2" :cy="courtH / 2"
        r="3"
        class="court-line"
        fill="none"
      />
      <!-- Center dot -->
      <circle
        :cx="courtW / 2" :cy="courtH / 2"
        r="0.6"
        class="court-line"
      />

      <!-- Key / Paint (rectangle from baseline) -->
      <rect
        :x="(courtW - keyW) / 2"
        y="0"
        :width="keyW"
        :height="keyH"
        class="court-line"
        fill="none"
      />

      <!-- Free throw line -->
      <line
        :x1="(courtW - keyW) / 2"
        :y1="keyH"
        :x2="(courtW + keyW) / 2"
        :y2="keyH"
        class="court-line"
      />

      <!-- Free throw circle (top half, dashed) -->
      <path
        :d="ftCircleTopPath"
        class="court-line court-line-dashed"
        fill="none"
      />

      <!-- Restricted area arc (small semicircle under basket) -->
      <path
        :d="restrictedAreaPath"
        class="court-line"
        fill="none"
      />

      <!-- 3-point arc -->
      <path
        :d="threePointPath"
        class="court-line"
        fill="none"
      />

      <!-- 3-point straight sections (side lines from baseline) -->
      <line
        :x1="threePointCornerX" y1="0"
        :x2="threePointCornerX" :y2="threePointCornerY"
        class="court-line"
      />
      <line
        :x1="courtW - threePointCornerX" y1="0"
        :x2="courtW - threePointCornerX" :y2="threePointCornerY"
        class="court-line"
      />

      <!-- Basket (hoop) -->
      <circle
        :cx="courtW / 2" :cy="basketY"
        r="0.9"
        class="court-hoop"
        fill="none"
      />
      <!-- Backboard -->
      <line
        :x1="courtW / 2 - 1.8" :y1="backboardY"
        :x2="courtW / 2 + 1.8" :y2="backboardY"
        class="court-line court-backboard"
      />

      <!-- Shot dots -->
      <g v-for="(shot, i) in shots" :key="i">
        <circle
          :cx="shot.x"
          :cy="shot.y"
          r="0.9"
          :class="shot.made ? 'shot-made' : 'shot-missed'"
          class="shot-dot"
        />
        <!-- Glow effect -->
        <circle
          :cx="shot.x"
          :cy="shot.y"
          r="1.6"
          :class="shot.made ? 'shot-glow-made' : 'shot-glow-missed'"
          class="shot-glow"
        />
      </g>
    </svg>

    <!-- Legend -->
    <div class="shot-chart-legend">
      <div class="legend-item">
        <span class="legend-dot legend-dot--made"></span>
        <span class="legend-label">命中</span>
      </div>
      <div class="legend-item">
        <span class="legend-dot legend-dot--missed"></span>
        <span class="legend-label">未中</span>
      </div>
      <div class="legend-stats" v-if="shots.length">
        <span class="legend-separator">|</span>
        <span class="legend-label">
          {{ madeCount }}/{{ shots.length }} ({{ shotPct }}%)
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import type { ShotData } from '@/api/types'

const props = withDefaults(defineProps<{
  shots?: ShotData[]
  size?: number
}>(), {
  shots: () => [],
  size: 400,
})

// 调试：监听shots变化
watch(() => props.shots, (newShots) => {
  console.log('ShotChart: shots changed, count =', newShots.length)
  if (newShots.length > 0) {
    console.log('ShotChart: first shot =', newShots[0])
  }
}, { immediate: true })

// SVG coordinate system: x 0-50, y 0-47 (half court)
const courtW = 50
const courtH = 47

// Key / Paint dimensions
const keyW = 16   // width of the paint
const keyH = 19   // distance from baseline to free throw line

// 3-point line
const threePointRadius = 23.75  // NBA 3-point distance (scaled)
const threePointCornerX = 3.5   // corner 3 distance from sideline
const threePointCornerY = 14    // how far up the corner 3 goes before arc

// Basket position
const basketY = 5.25
const backboardY = 4

// Restricted area
const restrictedRadius = 4

// Free throw circle
const ftCircleRadius = 6

const madeCount = computed(() => props.shots.filter(s => s.made).length)
const shotPct = computed(() => {
  if (!props.shots.length) return '0'
  return ((madeCount.value / props.shots.length) * 100).toFixed(1)
})

// Free throw circle top half arc path
const ftCircleTopPath = computed(() => {
  const cx = courtW / 2
  const cy = keyH
  const r = ftCircleRadius
  // Top half semicircle (from left to right)
  return `M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`
})

// Restricted area arc path
const restrictedAreaPath = computed(() => {
  const cx = courtW / 2
  const cy = basketY
  const r = restrictedRadius
  return `M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`
})

// 3-point arc path
const threePointPath = computed(() => {
  const cx = courtW / 2
  const cy = basketY
  const r = threePointRadius

  // Calculate where the arc meets the side lines (corner 3)
  // Arc goes from one corner 3 point to the other
  const cornerTopY = threePointCornerY
  // Find the x position on the arc at y = cornerTopY
  // (x - cx)^2 + (y - cy)^2 = r^2
  // x = cx + sqrt(r^2 - (y - cy)^2)
  const dx = Math.sqrt(Math.max(0, r * r - (cornerTopY - cy) * (cornerTopY - cy)))
  const arcLeftX = cx - dx
  const arcRightX = cx + dx

  return `M ${arcLeftX} ${cornerTopY} A ${r} ${r} 0 0 1 ${arcRightX} ${cornerTopY}`
})
</script>

<style scoped>
.shot-chart-wrapper {
  width: 100%;
  margin: 0 auto;
}

.shot-chart-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 24px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  color: var(--text-muted);
  font-size: 14px;
}
.shot-chart-empty svg {
  opacity: 0.4;
}
.shot-chart-empty-sub {
  font-size: 12px;
  color: var(--text-dim);
}

.shot-chart-svg {
  width: 100%;
  height: auto;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

/* Court floor */
.court-floor {
  fill: var(--shotchart-floor, #0D1117);
}

/* All court lines share this class */
.court-line {
  stroke: var(--shotchart-line, #2D333B);
  stroke-width: 0.3;
  fill: none;
}

.court-line-dashed {
  stroke-dasharray: 1.2 0.8;
}

.court-boundary {
  stroke-width: 0.4;
}

.court-backboard {
  stroke-width: 0.5;
}

.court-hoop {
  stroke: var(--shotchart-hoop, #FF6B35);
  stroke-width: 0.25;
}

/* Shot dots */
.shot-dot {
  transition: r 0.15s ease;
}

.shot-dot:hover {
  r: 1.3;
}

.shot-made {
  fill: #00E676;
  stroke: #00C853;
  stroke-width: 0.15;
}

.shot-missed {
  fill: #FF5252;
  stroke: #D32F2F;
  stroke-width: 0.15;
}

.shot-glow {
  pointer-events: none;
}

.shot-glow-made {
  fill: rgba(0, 230, 118, 0.15);
}

.shot-glow-missed {
  fill: rgba(255, 82, 82, 0.15);
}

/* Legend */
.shot-chart-legend {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 10px 0 0;
  font-family: var(--font-body);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}

.legend-dot--made {
  background: #00E676;
  box-shadow: 0 0 6px rgba(0, 230, 118, 0.4);
}

.legend-dot--missed {
  background: #FF5252;
  box-shadow: 0 0 6px rgba(255, 82, 82, 0.4);
}

.legend-label {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.legend-separator {
  color: var(--border-medium);
  font-size: 14px;
  margin: 0 2px;
}

.legend-stats {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ===== Light theme overrides ===== */
[data-theme="light"] .court-floor {
  fill: #F5F0E8;
}

[data-theme="light"] .court-line {
  stroke: #C4B8A8;
}

[data-theme="light"] .court-hoop {
  stroke: #D4520A;
}

[data-theme="light"] .shot-made {
  fill: #00C853;
  stroke: #009624;
}

[data-theme="light"] .shot-missed {
  fill: #D32F2F;
  stroke: #B71C1C;
}

[data-theme="light"] .shot-glow-made {
  fill: rgba(0, 200, 83, 0.15);
}

[data-theme="light"] .shot-glow-missed {
  fill: rgba(211, 47, 47, 0.12);
}
</style>
