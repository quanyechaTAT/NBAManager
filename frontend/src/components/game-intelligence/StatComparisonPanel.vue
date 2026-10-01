<template>
  <div class="stat-comparison-panel">
    <div class="panel-header">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--gi-cyan, #06B6D4)" stroke-width="2">
        <line x1="18" y1="20" x2="18" y2="10" />
        <line x1="12" y1="20" x2="12" y2="4" />
        <line x1="6" y1="20" x2="6" y2="14" />
      </svg>
      <span class="panel-title">数据对比</span>
    </div>

    <div class="stat-rows">
      <div
        v-for="item in items"
        :key="item.label"
        class="stat-row"
      >
        <!-- Home value -->
        <span
          class="stat-value home"
          :class="{ highlighted: isHomeHigher(item) }"
        >
          {{ item.homeValue }}
        </span>

        <!-- Home bar -->
        <div class="bar-wrapper home-bar">
          <div
            class="bar-fill home-fill"
            :style="{ width: homeBarWidth(item) + '%' }"
          />
        </div>

        <!-- Label -->
        <span class="stat-label">{{ item.label }}</span>

        <!-- Away bar -->
        <div class="bar-wrapper away-bar">
          <div
            class="bar-fill away-fill"
            :style="{ width: awayBarWidth(item) + '%' }"
          />
        </div>

        <!-- Away value -->
        <span
          class="stat-value away"
          :class="{ highlighted: isAwayHigher(item) }"
        >
          {{ item.awayValue }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">

interface StatItem {
  label: string
  homeValue: string
  awayValue: string
}

defineProps<{
  items: StatItem[]
}>()

/** Labels where lower value is better */
const lowerIsBetter = new Set(['总失误', '失误'])

/** Parse a value to a number, handling percentage strings like "45.2%" */
function parseNum(val: string): number {
  const cleaned = val.replace('%', '').trim()
  const n = parseFloat(cleaned)
  return isNaN(n) ? 0 : n
}

/** Check if an item is "lower is better" */
function isLowerBetter(item: StatItem): boolean {
  return lowerIsBetter.has(item.label)
}

/** Determine if home value "wins" this row */
function isHomeHigher(item: StatItem): boolean {
  const hv = parseNum(item.homeValue)
  const av = parseNum(item.awayValue)
  if (hv === av) return false
  return isLowerBetter(item) ? hv < av : hv > av
}

/** Determine if away value "wins" this row */
function isAwayHigher(item: StatItem): boolean {
  const hv = parseNum(item.homeValue)
  const av = parseNum(item.awayValue)
  if (hv === av) return false
  return isLowerBetter(item) ? av < hv : av > hv
}

/** Calculate bar widths (proportional to value) */
function computeBarWidths(item: StatItem): { home: number; away: number } {
  const hv = parseNum(item.homeValue)
  const av = parseNum(item.awayValue)
  const max = Math.max(hv, av, 1) // avoid division by zero

  // Scale so the larger bar is always 100%
  return {
    home: (hv / max) * 100,
    away: (av / max) * 100,
  }
}

function homeBarWidth(item: StatItem): number {
  return computeBarWidths(item).home
}

function awayBarWidth(item: StatItem): number {
  return computeBarWidths(item).away
}
</script>

<style scoped>
.stat-comparison-panel {
  background: var(--gi-surface-strong, #111827);
  border: 1px solid var(--gi-border, rgba(255, 255, 255, 0.08));
  border-radius: 14px;
  padding: 20px;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--gi-text, #F8FAFC);
}

/* Stat rows */
.stat-rows {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.stat-row {
  display: grid;
  grid-template-columns: 60px 1fr auto 1fr 60px;
  align-items: center;
  gap: 10px;
}

/* Value labels */
.stat-value {
  font-size: 13px;
  font-weight: 500;
  color: var(--gi-muted, #94A3B8);
  transition: color 0.2s, font-weight 0.2s;
  text-align: center;
}

.stat-value.home {
  text-align: right;
}

.stat-value.away {
  text-align: left;
}

.stat-value.highlighted {
  font-weight: 700;
  color: var(--gi-text, #F8FAFC);
}

/* Center label */
.stat-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--gi-muted, #94A3B8);
  text-align: center;
  white-space: nowrap;
  min-width: 48px;
}

/* Bars */
.bar-wrapper {
  height: 8px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 4px;
  overflow: hidden;
  position: relative;
}

.home-bar {
  justify-self: end;
}

.away-bar {
  justify-self: start;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.4s ease;
  min-width: 2px;
}

.home-fill {
  background: var(--gi-home, #3B82F6);
}

.away-fill {
  background: var(--gi-away, #22D3EE);
}

@media (max-width: 600px) {
  .stat-row {
    grid-template-columns: 48px 1fr auto 1fr 48px;
    gap: 6px;
  }

  .stat-value {
    font-size: 12px;
  }

  .stat-label {
    font-size: 11px;
    min-width: 36px;
  }
}
</style>
