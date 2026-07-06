<template>
  <div class="court-outer">
    <div v-if="!shots.length" class="court-empty">暂无投篮数据</div>
    <div v-else class="court-inner">
      <svg :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="xMidYMid meet" class="court-svg"
           @mouseleave="hovered = null">
        <!-- Court background -->
        <rect x="0" y="0" :width="W" :height="H" fill="#090F1F" rx="1.5" />
        <!-- Boundary -->
        <rect x="0.3" y="0.3" :width="W-0.6" :height="H-0.6" fill="none" stroke="rgba(124,92,255,0.62)" stroke-width="0.45" rx="1" />
        <!-- Half line -->
        <line :x1="0.3" :y1="H-0.3" :x2="W-0.3" :y2="H-0.3" stroke="rgba(124,92,255,0.42)" stroke-width="0.35" />
        <!-- Center circle -->
        <path :d="centerCirclePath" fill="none" stroke="rgba(124,92,255,0.28)" stroke-width="0.28" />
        <!-- Key / Paint -->
        <rect :x="(W-16)/2" y="0.3" width="16" height="18.7" fill="none" stroke="rgba(124,92,255,0.55)" stroke-width="0.35" />
        <line :x1="(W-16)/2" :y1="19" :x2="(W+16)/2" :y2="19" stroke="rgba(124,92,255,0.55)" stroke-width="0.35" />
        <!-- Free throw circle -->
        <path :d="ftCircle" fill="none" stroke="rgba(124,92,255,0.18)" stroke-width="0.25" stroke-dasharray="1 0.7" />
        <!-- Restricted area -->
        <path :d="restricted" fill="none" stroke="rgba(124,92,255,0.5)" stroke-width="0.3" />
        <!-- 3-point line (single continuous path: left corner → arc → right corner) -->
        <path :d="threePointPath" fill="none" stroke="rgba(124,92,255,0.6)" stroke-width="0.35" />
        <!-- Basket -->
        <circle :cx="W/2" :cy="5.25" r="0.9" fill="none" stroke="#ff6b35" stroke-width="0.22" />
        <line :x1="W/2-1.8" y1="4" :x2="W/2+1.8" y2="4" stroke="rgba(124,92,255,0.5)" stroke-width="0.4" />
        <!-- Shot dots -->
        <g v-for="(s, i) in shots" :key="i"
           @mouseenter="hovered = i" @mouseleave="hovered = null" style="cursor:pointer">
          <circle :cx="s.x" :cy="s.y" :r="hovered === i ? 1.5 : 0.95"
            :fill="s.made ? 'rgba(0,230,118,0.2)' : 'rgba(255,82,82,0.18)'" />
          <circle :cx="s.x" :cy="s.y" :r="hovered === i ? 1.0 : 0.65"
            :fill="s.made ? '#00E676' : '#FF5252'"
            :stroke="s.made ? 'rgba(0,200,83,0.5)' : 'rgba(211,47,47,0.5)'" stroke-width="0.1" />
        </g>
      </svg>
      <!-- HTML Tooltip (positioned over SVG) -->
      <div v-if="hovered !== null && shots[hovered]" class="shot-tooltip" :style="tooltipStyle(shots[hovered])">
        <div class="tt-player">{{ shots[hovered].playerName || '未知' }}</div>
        <div class="tt-meta">{{ shots[hovered].teamName }} · Q{{ shots[hovered].period }} {{ shots[hovered].gameClock }}</div>
        <div class="tt-result" :class="shots[hovered].made ? 'tt-made' : 'tt-missed'">
          {{ shots[hovered].made ? '命中' : '未中' }} · {{ shots[hovered].shotType }} · {{ shots[hovered].distance }}ft
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ShotData } from '@/api/types'

defineProps<{ shots?: ShotData[] }>()

const W = 50; const H = 47
const hovered = ref<number | null>(null)

const ftCircle = computed(() => { const cx = W/2; const r = 6; return `M ${cx-r} 19 A ${r} ${r} 0 0 1 ${cx+r} 19` })
const restricted = computed(() => { const cx = W/2; return `M ${cx-4} 5.25 A 4 4 0 0 1 ${cx+4} 5.25` })
const centerCirclePath = computed(() => {
  const cx = W / 2
  const cy = H - 0.3
  const r = 6
  return `M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`
})
const threePointPath = computed(() => {
  const cx = W / 2
  const cy = 5.25  // basket Y
  const r = 23.75
  const cornerX = 3.5
  const cornerY = cy + Math.sqrt(Math.max(0, r * r - Math.pow(cx - cornerX, 2)))
  // arc intersection with cornerY
  const dy = cornerY - cy
  const dx = Math.sqrt(Math.max(0, r * r - dy * dy))
  const arcLeftX = cx - dx
  const arcRightX = cx + dx
  // Single path: left baseline → left corner end → arc → right corner end → right baseline
  return `M ${cornerX} 0.3 L ${cornerX} ${cornerY} A ${r} ${r} 0 0 0 ${W - cornerX} ${cornerY} L ${W - cornerX} 0.3`
})

function tooltipStyle(s: ShotData) {
  const xPct = (s.x / W) * 100
  const yPct = (s.y / H) * 100
  const left = Math.max(5, Math.min(xPct, 80))
  const top = yPct < 45 ? yPct + 3 : yPct - 18
  return { left: left + '%', top: top + '%' }
}
</script>

<style scoped>
.court-outer { width: 100%; }
.court-empty { text-align: center; padding: 48px; color: var(--text-muted); font-size: 13px; }
.court-inner { position: relative; width: 100%; aspect-ratio: 50 / 47; max-height: 640px; overflow: hidden; border-radius: 12px; background: #090F1F; box-shadow: inset 0 0 40px rgba(0,0,0,0.3); }
.court-svg { width: 100%; height: 100%; display: block; }

/* Tooltip */
.shot-tooltip {
  position: absolute; z-index: 10;
  width: 240px; padding: 10px 12px;
  background: rgba(15, 23, 42, 0.92);
  border: 1px solid rgba(139, 92, 246, 0.35);
  border-radius: 10px;
  pointer-events: none;
  backdrop-filter: blur(6px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}
.tt-player { font-size: 14px; font-weight: 600; color: #e8e8e8; margin-bottom: 3px; }
.tt-meta { font-size: 11px; color: #94a3b8; margin-bottom: 4px; }
.tt-result { font-size: 12px; font-weight: 600; }
.tt-made { color: #00E676; }
.tt-missed { color: #FF5252; }

/* ===== 亮色主题 ===== */
[data-theme="light"] .court-inner {
  background: #E8E4DF;
  box-shadow: inset 0 0 30px rgba(0,0,0,0.06), 0 2px 12px rgba(0,0,0,0.08);
}
[data-theme="light"] .court-svg > rect:first-child {
  fill: #E8E4DF;
}
[data-theme="light"] .court-svg rect[stroke*="124,92,255"] {
  stroke: rgba(80, 100, 130, 0.45);
}
[data-theme="light"] .court-svg line[stroke*="124,92,255"] {
  stroke: rgba(80, 100, 130, 0.45);
}
[data-theme="light"] .court-svg path[stroke*="124,92,255"] {
  stroke: rgba(80, 100, 130, 0.40);
}
[data-theme="light"] .shot-tooltip {
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(0, 0, 0, 0.12);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}
[data-theme="light"] .tt-player { color: #1e293b; }
[data-theme="light"] .tt-meta { color: #64748b; }
</style>
