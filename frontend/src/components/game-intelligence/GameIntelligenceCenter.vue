<template>
  <div class="game-intel">
    <!-- Trend + Summary row -->
    <div class="gi-row gi-row-trend">
      <GameTrendCard
        :data="momentumData"
        :timeline-events="timelineEvents"
        :home-team="homeTeam"
        :away-team="awayTeam"
        :selected-event="selectedEvent"
        @point-click="handleMomentumClick"
        @run-click="handleRunClick"
      />
      <AISummaryCard
        :summary="summary"
        :turning-points="turningPoints"
        :highlights="highlights"
        :home-players="homePlayers"
        :away-players="awayPlayers"
        :home-team="homeTeam"
        :away-team="awayTeam"
      />
    </div>

    <!-- Timeline + AI Explain row -->
    <div class="gi-row gi-row-main">
      <ReplayTimelinePanel
        :events="timelineEvents"
        :selected-event="selectedEvent"
        @select="$emit('selectEvent', $event)"
      />
      <AIExplainPanel
        :game-id="gameId"
        :selected-event="selectedEvent"
        :momentum-data="momentumData"
      />
    </div>

    <!-- Turning Points rail -->
    <TurningPointStoryRail ref="tpRailRef" :points="turningPoints" />

    <!-- Stat comparison -->
    <StatComparisonPanel v-if="comparisonItems.length" :items="comparisonItems" />
    <p v-else class="gi-stats-unavailable">双方统计尚未齐全，暂无法生成数据对比。</p>

    <!-- Player performance rail -->
    <PlayerPerformanceRail
      :home-players="homePlayers"
      :away-players="awayPlayers"
      :home-team="homeTeam"
      :away-team="awayTeam"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import GameTrendCard from './GameTrendCard.vue'
import AISummaryCard from './AISummaryCard.vue'
import ReplayTimelinePanel from './ReplayTimelinePanel.vue'
import AIExplainPanel from './AIExplainPanel.vue'
import TurningPointStoryRail from './TurningPointStoryRail.vue'
import StatComparisonPanel from './StatComparisonPanel.vue'
import PlayerPerformanceRail from './PlayerPerformanceRail.vue'
import type { TimelineEvent, MomentumSnapshot, Highlight, TurningPoint, GameSummary, BoxScorePlayer } from '@/api/types'

const props = defineProps<{
  gameId: string
  homeTeam: string
  awayTeam: string
  timelineEvents: TimelineEvent[]
  momentumData: MomentumSnapshot[]
  highlights: Highlight[]
  turningPoints: TurningPoint[]
  summary: GameSummary | null
  comparisonItems: Array<{ label: string; homeValue: string; awayValue: string }>
  homePlayers: BoxScorePlayer[]
  awayPlayers: BoxScorePlayer[]
  selectedEvent: TimelineEvent | null
}>()

const emit = defineEmits<{ selectEvent: [event: TimelineEvent] }>()
const tpRailRef = ref<InstanceType<typeof TurningPointStoryRail> | null>(null)

function scrollWindowToElement(target: HTMLElement, offset = 96) {
  const rect = target.getBoundingClientRect()
  const top = Math.max(0, window.scrollY + rect.top - offset)
  window.scrollTo({ top, behavior: 'smooth' })
}

function handleRunClick(startOrder: number, endOrder: number) {
  // Find matching turning point by event order range
  const match = props.turningPoints.find(tp =>
    tp.type === 'RUN' && tp.startEventOrder != null && tp.endEventOrder != null
      && Math.abs(tp.startEventOrder - startOrder) <= 3
      && Math.abs(tp.endEventOrder - endOrder) <= 3
  ) || props.turningPoints.find(tp =>
    tp.startEventOrder != null && Math.abs(tp.startEventOrder - startOrder) <= 5
  )

  const railEl = tpRailRef.value?.$el as HTMLElement | undefined
  if (!railEl) return

  if (match) {
    const card = railEl.querySelector(`[data-tp-id="${match.id}"]`) as HTMLElement | null
    if (card) {
      scrollWindowToElement(card, 140)
      card.classList.add('tp-highlight')
      setTimeout(() => card.classList.remove('tp-highlight'), 1500)
      return
    }
  }

  // Fallback: scroll to the turning points section
  scrollWindowToElement(railEl)
}

function handleMomentumClick(snapshot: MomentumSnapshot) {
  const exact = props.timelineEvents.find(e => e.eventOrder === snapshot.eventOrder)
  if (exact) {
    emit('selectEvent', exact)
    return
  }

  const nearest = props.timelineEvents.reduce<TimelineEvent | null>((best, event) => {
    if (!best) return event
    return Math.abs(event.eventOrder - snapshot.eventOrder) < Math.abs(best.eventOrder - snapshot.eventOrder)
      ? event
      : best
  }, null)

  if (nearest) emit('selectEvent', nearest)
}
</script>

<style scoped>
.game-intel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 0;
  animation: gi-fadeIn 0.35s ease;
}
.gi-stats-unavailable {
  margin: 0;
  padding: 18px 20px;
  border: 1px solid var(--gi-border);
  border-radius: 12px;
  background: var(--gi-surface);
  color: var(--gi-muted);
  font-size: 13px;
}
@keyframes gi-fadeIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

.gi-row {
  display: grid;
  gap: 16px;
  align-items: start;
}
.gi-row-trend {
  grid-template-columns: minmax(0, 1fr);
}
.gi-row-main {
  grid-template-columns: minmax(320px, 0.9fr) minmax(340px, 0.95fr);
}

@media (max-width: 1100px) {
  .gi-row-trend { grid-template-columns: 1fr; }
  .gi-row-main { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .game-intel { gap: 12px; }
}
</style>
