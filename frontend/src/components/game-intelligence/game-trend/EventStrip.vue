<template>
  <div class="event-strip-wrap">
    <div class="strip-row">
      <span>攻击波</span>
      <div class="run-strip" aria-label="比赛攻击波时间轴">
        <button
          v-for="run in runBlocks"
          :key="run.id"
          class="run-block"
          :class="[run.side, run.strength]"
          :style="{ left: `${run.xPercent}%`, width: `${run.widthPercent}%` }"
          type="button"
          :title="`${run.teamName} ${run.label}`"
          @click="$emit('runClick', run.startOrder, run.endOrder)"
        >
          <strong>{{ run.label }}</strong>
          <small>{{ run.teamName }}</small>
        </button>
      </div>
    </div>

    <div class="strip-row">
      <span>事件时间轴</span>
      <div class="event-strip" aria-label="关键事件时间轴">
        <button
          v-for="marker in markers"
          :key="marker.event.id"
          class="strip-event"
          :class="[marker.kind, { selected: marker.isSelected }]"
          :style="{ left: `${marker.xPercent}%`, backgroundColor: marker.color }"
          :title="marker.title"
          type="button"
          @click="$emit('select', marker.event.eventOrder)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MomentumRun, TrendEventMarker } from './types'
import { displayDescription, periodLabel } from './trendUtils'

const props = defineProps<{
  markers: TrendEventMarker[]
  runs: MomentumRun[]
  chartLeft: number
  chartWidth: number
}>()

defineEmits<{
  select: [eventOrder: number]
  runClick: [startOrder: number, endOrder: number]
}>()

const safeWidth = computed(() => Math.max(1, props.chartWidth))

const markers = computed(() =>
  props.markers.map((marker) => ({
    ...marker,
    xPercent: ((marker.x - props.chartLeft) / safeWidth.value) * 100,
    title: `${periodLabel(marker.event.period)} ${marker.event.gameClock} ${displayDescription(marker.event)}`,
  }))
)

const runBlocks = computed(() =>
  props.runs.map((run) => ({
    ...run,
    xPercent: ((run.startX - props.chartLeft) / safeWidth.value) * 100,
    widthPercent: Math.max(4, (run.width / safeWidth.value) * 100),
  }))
)
</script>

<style scoped>
.event-strip-wrap {
  display: grid;
  gap: 5px;
  margin-top: 8px;
  padding: 4px 2px 0;
  border-top: 1px solid var(--trend-border);
}

.strip-row {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.strip-row > span {
  color: var(--trend-home);
  font-size: 10px;
  font-weight: 950;
  letter-spacing: 0.1em;
}

.run-strip,
.event-strip {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--trend-border);
  border-radius: 5px;
  background: rgba(var(--bg-panel-rgb), 0.56);
  box-shadow: inset 0 0 0 1px var(--border-subtle);
}

.run-strip {
  height: 18px;
}

.event-strip {
  height: 18px;
}

.run-strip::before,
.event-strip::before {
  position: absolute;
  inset: 50% 10px auto;
  height: 1px;
  background: var(--trend-grid);
  content: '';
}

.run-block {
  position: absolute;
  top: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 36px;
  height: 10px;
  gap: 3px;
  padding: 0 6px;
  overflow: hidden;
  border: 1px solid var(--trend-border);
  border-radius: var(--radius-full);
  color: var(--trend-text);
  background: var(--trend-home-soft);
  cursor: pointer;
  transform: translateX(-1px);
  transition: transform var(--duration-normal) var(--ease-smooth), box-shadow var(--duration-normal) var(--ease-smooth), border-color var(--duration-normal) var(--ease-smooth);
}

.run-block.away {
  background: var(--trend-away-soft);
}

.run-block.strong {
  border-color: var(--trend-border-strong);
  box-shadow: 0 0 0 1px var(--border-subtle);
}

.run-block:hover {
  z-index: 2;
  transform: translateX(-1px) translateY(-1px) scale(1.02);
  box-shadow: var(--shadow-md);
}

.run-block strong {
  font-size: 8px;
  font-weight: 950;
  line-height: 1;
}

.run-block small {
  display: none;
}

.strip-event {
  position: absolute;
  top: 4px;
  width: 3px;
  height: 10px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-full);
  opacity: 0.78;
  cursor: pointer;
  transform: translateX(-50%);
  transition: width var(--duration-normal) var(--ease-smooth), height var(--duration-normal) var(--ease-smooth), opacity var(--duration-normal) var(--ease-smooth), top var(--duration-normal) var(--ease-smooth), box-shadow var(--duration-normal) var(--ease-smooth);
}

.strip-event:hover,
.strip-event.selected {
  top: 1px;
  width: 5px;
  height: 16px;
  opacity: 1;
  box-shadow: 0 0 0 3px var(--border-subtle);
}

@media (max-width: 680px) {
  .strip-row {
    grid-template-columns: 1fr;
  }
}
</style>
