<template>
  <section class="replay-timeline">
    <div class="timeline-toolbar">
      <div>
        <h4 class="timeline-title">逐回合事件</h4>
        <p class="timeline-subtitle">按比赛时间浏览官方 Play-by-Play</p>
      </div>
      <div class="period-tabs" role="tablist" aria-label="节次筛选">
        <button
          type="button"
          class="period-tab"
          :class="{ active: selectedPeriod === undefined }"
          @click="selectedPeriod = undefined"
        >
          全部
        </button>
        <button
          v-for="p in availablePeriods"
          :key="p"
          type="button"
          class="period-tab"
          :class="{ active: selectedPeriod === p }"
          @click="selectedPeriod = p"
        >
          {{ periodLabel(p) }}
        </button>
      </div>
    </div>

    <div class="timeline-events" v-if="filteredEvents.length">
      <button
        v-for="event in filteredEvents"
        :key="event.id"
        type="button"
        class="event-card"
        :class="{
          selected: selectedEvent?.id === event.id,
          scoring: event.isScoringPlay,
          highlight: event.isHighlightCandidate,
        }"
        @click="$emit('select', event)"
      >
        <span class="event-rail"></span>
        <div class="event-topline">
          <span class="period-badge">Q{{ event.period }}</span>
          <span class="game-clock">{{ event.gameClock }}</span>
          <span class="score-display">{{ event.homeScore }}-{{ event.awayScore }}</span>
        </div>
        <div class="event-description">{{ event.description }}</div>
        <div class="event-footer">
          <span v-if="event.playerName" class="player-name">{{ event.playerName }}</span>
          <span v-if="event.isScoringPlay" class="tag scoring">得分</span>
          <span v-if="event.isHighlightCandidate" class="tag highlight">精彩</span>
          <span class="tag source">{{ event.source || 'PBP' }}</span>
        </div>
      </button>
    </div>
    <div v-else class="empty-state">暂无比赛事件数据</div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { TimelineEvent } from '@/api/types'

const props = defineProps<{ events: TimelineEvent[], selectedEvent?: TimelineEvent | null }>()
defineEmits<{ select: [event: TimelineEvent] }>()

const selectedPeriod = ref<number | undefined>(undefined)

const availablePeriods = computed(() => {
  return [...new Set(props.events.map(e => e.period).filter(Boolean))].sort((a, b) => a - b)
})

const filteredEvents = computed(() => {
  if (!selectedPeriod.value) return props.events
  return props.events.filter(e => e.period === selectedPeriod.value)
})

function periodLabel(period: number) {
  return period <= 4 ? `第${period}节` : `OT${period - 4}`
}
</script>

<style scoped>
.replay-timeline {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}
.timeline-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.68);
}
.timeline-title {
  margin: 0;
  color: #eef4ff;
  font-size: 17px;
  font-weight: 700;
}
.timeline-subtitle {
  margin: 4px 0 0;
  color: #7f8ca3;
  font-size: 12px;
}
.period-tabs {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 3px;
  padding: 3px;
  border: 1px solid rgba(96, 165, 250, 0.18);
  border-radius: 12px;
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.5), rgba(15, 23, 42, 0.24)),
    rgba(59, 130, 246, 0.05);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(10px);
}
.period-tab {
  height: 32px;
  border: 0;
  border-radius: 9px;
  padding: 0 13px;
  color: #9fb0c7;
  background: transparent;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  transition: color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}
.period-tab:hover:not(.active) {
  color: #dbeafe;
  background: rgba(96, 165, 250, 0.1);
}
.period-tab.active {
  color: #ffffff;
  background:
    linear-gradient(135deg, rgba(59, 162, 255, 0.96), rgba(37, 99, 235, 0.92));
  box-shadow:
    0 8px 18px rgba(37, 99, 235, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.22);
  transform: translateY(-1px);
}
.timeline-events {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 640px;
  overflow-y: auto;
  padding-right: 6px;
}
.timeline-events::-webkit-scrollbar { width: 8px; }
.timeline-events::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(100, 116, 139, 0.42);
}
.event-card {
  position: relative;
  width: 100%;
  min-height: 92px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 14px;
  padding: 14px 16px 14px 18px;
  overflow: hidden;
  text-align: left;
  color: #e8eef9;
  background:
    linear-gradient(135deg, rgba(15, 23, 42, 0.94), rgba(17, 24, 39, 0.78));
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}
.event-card:hover {
  transform: translateY(-1px);
  border-color: rgba(59, 162, 255, 0.45);
  background:
    radial-gradient(circle at 0% 20%, rgba(59, 162, 255, 0.14), transparent 28%),
    linear-gradient(135deg, rgba(15, 23, 42, 0.98), rgba(17, 24, 39, 0.84));
}
.event-card.selected {
  border-color: rgba(59, 162, 255, 0.8);
  box-shadow: 0 0 0 1px rgba(59, 162, 255, 0.2), 0 14px 30px rgba(2, 6, 23, 0.26);
}
.event-rail {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: rgba(100, 116, 139, 0.45);
}
.event-card.scoring .event-rail { background: #22c55e; }
.event-card.highlight .event-rail { background: #f59e0b; }
.event-topline {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 10px;
}
.period-badge {
  border-radius: 7px;
  padding: 3px 8px;
  background: rgba(59, 162, 255, 0.16);
  color: #7ec3ff;
  font-size: 12px;
  font-weight: 800;
}
.game-clock {
  color: #8996ab;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}
.score-display {
  color: #f8fbff;
  font-size: 15px;
  font-weight: 800;
  margin-left: 2px;
}
.event-description {
  color: #d9e2f2;
  font-size: 15px;
  line-height: 1.45;
}
.event-footer {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.player-name {
  color: #9fb0c7;
  font-size: 12px;
  margin-right: auto;
}
.tag {
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 700;
}
.tag.scoring { color: #7ef0a5; background: rgba(34, 197, 94, 0.12); }
.tag.highlight { color: #ffd28a; background: rgba(245, 158, 11, 0.14); }
.tag.source { color: #9aa7bb; background: rgba(148, 163, 184, 0.1); }
.empty-state {
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 14px;
  padding: 56px 24px;
  text-align: center;
  color: #7f8ca3;
  background: rgba(15, 23, 42, 0.56);
}
@media (max-width: 900px) {
  .timeline-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}

[data-theme="light"] .period-tabs {
  border-color: rgba(37, 99, 235, 0.16);
  background: #ffffff;
  box-shadow:
    0 6px 16px rgba(15, 23, 42, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.96);
}

[data-theme="light"] .period-tab {
  color: #64748b;
}

[data-theme="light"] .period-tab:hover:not(.active) {
  color: #1e3a8a;
  background: rgba(37, 99, 235, 0.08);
}

[data-theme="light"] .period-tab.active {
  color: #fff;
  background:
    linear-gradient(135deg, #4aa3ff, #2563eb);
  box-shadow:
    0 8px 18px rgba(37, 99, 235, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.25);
}

:global(html[data-theme="light"]) .period-tabs {
  border-color: #dbe7ff;
  background: #ffffff !important;
  box-shadow: none;
  backdrop-filter: none;
}

:global(html[data-theme="light"]) .period-tab {
  color: #94a3b8;
}

:global(html[data-theme="light"]) .period-tab:hover:not(.active) {
  color: #2563eb;
  background: #f8fbff;
}

:global(html[data-theme="light"]) .period-tab.active {
  color: #ffffff;
  background: linear-gradient(135deg, #4aa3ff, #2563eb);
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.18);
}
</style>
