<template>
  <div class="summary-bar">
    <div class="summary-side">
      <div class="side-label">总投篮</div>
      <div class="side-row">
        <div class="side-ring">
          <svg viewBox="0 0 40 40" class="ring">
            <circle cx="20" cy="20" r="16" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="3.5" />
            <circle cx="20" cy="20" r="16" fill="none" stroke="#00E676" stroke-width="3.5"
              :stroke-dasharray="ringDash(summary.fgPct)" stroke-linecap="round" transform="rotate(-90 20 20)" />
          </svg>
          <span class="ring-text">{{ summary.fgPct }}%</span>
        </div>
        <div class="side-stats">
          <span class="stat-made">{{ summary.made }}</span>
          <span class="stat-sep">/</span>
          <span class="stat-total">{{ summary.total }}</span>
        </div>
      </div>
    </div>
    <div class="summary-vs">VS</div>
    <div class="summary-side">
      <div class="side-label">三分</div>
      <div class="side-row">
        <div class="side-ring">
          <svg viewBox="0 0 40 40" class="ring">
            <circle cx="20" cy="20" r="16" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="3.5" />
            <circle cx="20" cy="20" r="16" fill="none" stroke="#FF5252" stroke-width="3.5"
              :stroke-dasharray="ringDash(summary.threePct)" stroke-linecap="round" transform="rotate(-90 20 20)" />
          </svg>
          <span class="ring-text">{{ summary.threePct }}%</span>
        </div>
        <div class="side-stats">
          <span class="stat-made">{{ summary.threeMade }}</span>
          <span class="stat-sep">/</span>
          <span class="stat-total">{{ summary.threeAttempted }}</span>
        </div>
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
  const c = 2 * Math.PI * 16
  return `${(p / 100) * c} ${c}`
}
</script>

<style scoped>
.summary-bar {
  display: flex; align-items: center; justify-content: center; gap: 40px;
  padding: 18px 24px; max-height: 120px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: 14px;
}
.summary-side { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.side-label { font-size: 11px; font-weight: 600; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1.5px; }
.side-row { display: flex; align-items: center; gap: 14px; }
.side-ring { position: relative; width: 48px; height: 48px; flex-shrink: 0; }
.ring { width: 100%; height: 100%; }
.ring-text { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; color: var(--text-primary); }
.side-stats { display: flex; align-items: baseline; gap: 2px; }
.stat-made { font-size: 26px; font-weight: 800; color: var(--text-primary); font-variant-numeric: tabular-nums; }
.stat-sep { font-size: 16px; color: var(--text-dim); margin: 0 1px; }
.stat-total { font-size: 18px; font-weight: 600; color: var(--text-muted); font-variant-numeric: tabular-nums; }
.summary-vs { font-size: 13px; font-weight: 800; color: var(--text-dim); letter-spacing: 3px; }
@media (max-width: 600px) { .summary-bar { gap: 20px; padding: 14px 16px; } .stat-made { font-size: 20px; } .stat-total { font-size: 14px; } .side-ring { width: 40px; height: 40px; } }

/* ===== 亮色主题 ===== */
[data-theme="light"] .summary-bar {
  background: rgba(0, 0, 0, 0.02);
  border-color: rgba(0, 0, 0, 0.08);
}
[data-theme="light"] .side-label { color: var(--text-dim); }
[data-theme="light"] .ring-text { color: #1e293b; }
[data-theme="light"] .stat-made { color: #0f172a; }
[data-theme="light"] .stat-sep { color: var(--text-secondary); }
[data-theme="light"] .stat-total { color: var(--text-dim); }
[data-theme="light"] .summary-vs { color: var(--text-muted); }
</style>
