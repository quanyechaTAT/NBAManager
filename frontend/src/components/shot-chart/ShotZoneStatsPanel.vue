<template>
  <div class="zone-panel">
    <div class="zone-title">区域命中率</div>
    <div v-for="z in zones" :key="z.zone" class="zone-row">
      <span class="zone-name">{{ z.label }}</span>
      <div class="zone-bar-wrap">
        <div class="zone-bar" :style="{ width: z.attempted ? Math.min(100, z.pctNum) + '%' : '0%' }" :class="barColor(z.pctNum, z.attempted)"></div>
      </div>
      <span class="zone-stat">{{ z.made }}/{{ z.attempted }}</span>
      <span class="zone-pct" :class="pctColor(z.pctNum, z.attempted)">{{ z.attempted ? z.pct + '%' : '-' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ZoneStat } from '@/utils/shotChart'

defineProps<{ zones: ZoneStat[] }>()

function barColor(pct: number, att: number) {
  if (!att) return 'bar-empty'
  if (pct >= 55) return 'bar-green'
  if (pct >= 40) return 'bar-teal'
  if (pct >= 33) return 'bar-yellow'
  return 'bar-red'
}
function pctColor(pct: number, att: number) {
  if (!att) return 'c-dim'
  if (pct >= 55) return 'c-green'
  if (pct >= 40) return 'c-teal'
  if (pct >= 33) return 'c-yellow'
  return 'c-red'
}
</script>

<style scoped>
.zone-panel { display: flex; flex-direction: column; gap: 8px; padding: 16px; background: rgba(255,255,255,0.02); border: 1px solid rgba(139,92,246,0.12); border-radius: 12px; }
.zone-title { font-size: 12px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1px; margin-bottom: 4px; }
.zone-row { display: grid; grid-template-columns: 100px 1fr 48px 48px; align-items: center; gap: 8px; }
.zone-name { font-size: 12px; color: var(--text-secondary); }
.zone-bar-wrap { height: 6px; background: rgba(255,255,255,0.04); border-radius: 3px; overflow: hidden; }
.zone-bar { height: 100%; border-radius: 3px; transition: width 0.3s ease; }
.bar-green { background: #00e676; }
.bar-teal { background: #26a69a; }
.bar-yellow { background: #ffc107; }
.bar-red { background: #ff5252; }
.bar-empty { background: rgba(255,255,255,0.06); }
.zone-stat { font-size: 11px; color: var(--text-muted); text-align: right; font-variant-numeric: tabular-nums; }
.zone-pct { font-size: 12px; font-weight: 700; text-align: right; }
.c-green { color: #00e676; }
.c-teal { color: #26a69a; }
.c-yellow { color: #ffc107; }
.c-red { color: #ff5252; }
.c-dim { color: var(--text-dim); }
</style>
