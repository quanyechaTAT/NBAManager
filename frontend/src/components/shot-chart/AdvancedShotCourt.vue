<template>
  <div class="court-wrapper" :style="{ maxWidth: size + 'px' }">
    <div v-if="!shots.length" class="court-empty">暂无投篮数据</div>
    <svg v-else :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="xMidYMid meet" class="court-svg"
         @mouseleave="hovered = null">
      <!-- Court background -->
      <rect x="0" y="0" :width="W" :height="H" fill="#0a0e1a" rx="2" />
      <!-- Court lines -->
      <rect x="0" y="0" :width="W" :height="H" fill="none" stroke="rgba(139,92,246,0.35)" stroke-width="0.4" rx="2" />
      <line :x1="0" :y1="H/2" :x2="W" :y2="H/2" stroke="rgba(139,92,246,0.2)" stroke-width="0.3" />
      <circle :cx="W/2" :cy="H/2" r="3" fill="none" stroke="rgba(139,92,246,0.25)" stroke-width="0.3" />
      <circle :cx="W/2" :cy="H/2" r="0.5" fill="rgba(139,92,246,0.3)" />
      <!-- Key / Paint -->
      <rect :x="(W-16)/2" y="0" width="16" height="19" fill="none" stroke="rgba(139,92,246,0.3)" stroke-width="0.3" />
      <line :x1="(W-16)/2" :y1="19" :x2="(W+16)/2" :y2="19" stroke="rgba(139,92,246,0.3)" stroke-width="0.3" />
      <path :d="ftCircle" fill="none" stroke="rgba(139,92,246,0.2)" stroke-width="0.3" stroke-dasharray="1.2 0.8" />
      <!-- Restricted area -->
      <path :d="restricted" fill="none" stroke="rgba(139,92,246,0.3)" stroke-width="0.3" />
      <!-- 3-point arc -->
      <path :d="threeArc" fill="none" stroke="rgba(139,92,246,0.35)" stroke-width="0.3" />
      <line x1="3.5" y1="0" x2="3.5" :y2="threeCornerY" stroke="rgba(139,92,246,0.35)" stroke-width="0.3" />
      <line :x1="W-3.5" y1="0" :x2="W-3.5" :y2="threeCornerY" stroke="rgba(139,92,246,0.35)" stroke-width="0.3" />
      <!-- Basket -->
      <circle :cx="W/2" :cy="5.25" r="0.9" fill="none" stroke="#ff6b35" stroke-width="0.25" />
      <line :x1="W/2-1.8" y1="4" :x2="W/2+1.8" y2="4" stroke="rgba(139,92,246,0.4)" stroke-width="0.5" />
      <!-- Shot dots -->
      <g v-for="(s, i) in shots" :key="i"
         @mouseenter="hovered = i" @mouseleave="hovered = null" style="cursor:pointer">
        <circle :cx="s.x" :cy="s.y" :r="hovered === i ? 1.8 : 1.1"
          :fill="s.made ? 'rgba(0,230,118,0.15)' : 'rgba(255,82,82,0.12)'" />
        <circle :cx="s.x" :cy="s.y" :r="hovered === i ? 1.3 : 0.85"
          :fill="s.made ? '#00e676' : '#ff5252'"
          :stroke="s.made ? '#00c853' : '#d32f2f'" stroke-width="0.12" />
      </g>
      <!-- Tooltip -->
      <g v-if="hovered !== null && shots[hovered]" :transform="tooltipPos(shots[hovered])">
        <rect x="0" y="-32" width="110" height="30" rx="4" fill="rgba(15,18,35,0.95)" stroke="rgba(139,92,246,0.4)" stroke-width="0.3" />
        <text x="6" y="-18" fill="#e0e0e0" font-size="3" font-weight="600">{{ shots[hovered].playerName || '未知' }}</text>
        <text x="6" y="-12" fill="#aaa" font-size="2.5">{{ shots[hovered].teamName }} · Q{{ shots[hovered].period }} {{ shots[hovered].gameClock }}</text>
        <text x="6" y="-6" :fill="shots[hovered].made ? '#00e676' : '#ff5252'" font-size="2.5">{{ shots[hovered].made ? '命中' : '未中' }} · {{ shots[hovered].shotType }} · {{ shots[hovered].distance }}ft</text>
      </g>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ShotData } from '@/api/types'

const props = withDefaults(defineProps<{ shots?: ShotData[]; size?: number }>(), { shots: () => [], size: 560 })

const W = 50; const H = 47
const threeCornerY = 14
const hovered = ref<number | null>(null)

const ftCircle = computed(() => { const cx = W/2; const r = 6; return `M ${cx-r} 19 A ${r} ${r} 0 0 1 ${cx+r} 19` })
const restricted = computed(() => { const cx = W/2; return `M ${cx-4} 5.25 A 4 4 0 0 1 ${cx+4} 5.25` })
const threeArc = computed(() => {
  const cx = W/2; const cy = 5.25; const r = 23.75
  const dy = threeCornerY - cy
  const dx = Math.sqrt(Math.max(0, r*r - dy*dy))
  return `M ${cx-dx} ${threeCornerY} A ${r} ${r} 0 0 1 ${cx+dx} ${threeCornerY}`
})

function tooltipPos(s: ShotData) {
  const tx = Math.min(Math.max(s.x, 5), W - 5)
  const ty = s.y < 20 ? s.y + 5 : s.y - 5
  return `translate(${tx}, ${ty})`
}
</script>

<style scoped>
.court-wrapper { width: 100%; margin: 0 auto; }
.court-empty { text-align: center; padding: 48px; color: var(--text-muted); font-size: 13px; }
.court-svg { width: 100%; height: auto; border-radius: 10px; overflow: hidden; }
</style>
