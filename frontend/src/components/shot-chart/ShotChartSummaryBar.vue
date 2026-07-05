<template>
  <div class="summary-bar">
    <div class="summary-side">
      <div class="summary-label">总投篮</div>
      <div class="summary-big">{{ summary.made }} / {{ summary.total }}</div>
      <div class="summary-pct" :class="pctColor(summary.fgPct)">{{ summary.fgPct }}%</div>
      <div class="ring-wrap">
        <svg viewBox="0 0 36 36" class="ring">
          <circle cx="18" cy="18" r="15.5" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="3" />
          <circle cx="18" cy="18" r="15.5" fill="none" stroke="var(--accent-green)" stroke-width="3"
            :stroke-dasharray="ringDash(summary.fgPct)" stroke-dashoffset="0"
            stroke-linecap="round" transform="rotate(-90 18 18)" />
        </svg>
      </div>
    </div>
    <div class="summary-vs">VS</div>
    <div class="summary-side">
      <div class="summary-label">三分</div>
      <div class="summary-big">{{ summary.threeMade }} / {{ summary.threeAttempted }}</div>
      <div class="summary-pct" :class="pctColor(summary.threePct)">{{ summary.threePct }}%</div>
      <div class="ring-wrap">
        <svg viewBox="0 0 36 36" class="ring">
          <circle cx="18" cy="18" r="15.5" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="3" />
          <circle cx="18" cy="18" r="15.5" fill="none" stroke="var(--accent-red, #ff5252)" stroke-width="3"
            :stroke-dasharray="ringDash(summary.threePct)" stroke-dashoffset="0"
            stroke-linecap="round" transform="rotate(-90 18 18)" />
        </svg>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  summary: { total: number; made: number; fgPct: string; threeAttempted: number; threeMade: number; threePct: string }
}>()

function ringDash(pct: string) {
  const p = parseFloat(pct) || 0
  const c = 2 * Math.PI * 15.5
  return `${(p / 100) * c} ${c}`
}
function pctColor(pct: string) {
  const p = parseFloat(pct) || 0
  if (p >= 50) return 'c-green'
  if (p >= 40) return 'c-teal'
  if (p >= 33) return 'c-yellow'
  return 'c-red'
}
</script>

<style scoped>
.summary-bar { display: flex; align-items: center; justify-content: center; gap: 32px; padding: 16px 24px; background: rgba(255,255,255,0.02); border: 1px solid rgba(139,92,246,0.15); border-radius: 12px; }
.summary-side { display: flex; flex-direction: column; align-items: center; gap: 2px; position: relative; }
.summary-label { font-size: 11px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1px; }
.summary-big { font-size: 18px; font-weight: 700; color: var(--text-primary); font-variant-numeric: tabular-nums; }
.summary-pct { font-size: 22px; font-weight: 800; }
.summary-vs { font-size: 14px; font-weight: 800; color: var(--text-dim); letter-spacing: 2px; }
.ring-wrap { position: absolute; top: -4px; right: -36px; width: 32px; height: 32px; opacity: 0.5; }
.ring { width: 100%; height: 100%; }
.c-green { color: #00e676; }
.c-teal { color: #26a69a; }
.c-yellow { color: #ffc107; }
.c-red { color: #ff5252; }
@media (max-width: 600px) { .summary-bar { gap: 16px; padding: 12px; } .summary-big { font-size: 15px; } .summary-pct { font-size: 18px; } }
</style>
