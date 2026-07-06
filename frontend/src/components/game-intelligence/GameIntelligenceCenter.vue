<template>
  <div class="game-intel">
    <!-- Trend + Summary row -->
    <div class="gi-row gi-row-trend">
      <GameTrendCard
        :data="momentumData"
        :home-team="homeTeam"
        :away-team="awayTeam"
        @point-click="handleMomentumClick"
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
    <TurningPointStoryRail :points="turningPoints" />

    <!-- Stat comparison -->
    <StatComparisonPanel :items="comparisonItems" />

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
import GameTrendCard from './GameTrendCard.vue'
import AISummaryCard from './AISummaryCard.vue'
import ReplayTimelinePanel from './ReplayTimelinePanel.vue'
import AIExplainPanel from './AIExplainPanel.vue'
import TurningPointStoryRail from './TurningPointStoryRail.vue'
import StatComparisonPanel from './StatComparisonPanel.vue'
import PlayerPerformanceRail from './PlayerPerformanceRail.vue'
import type { TimelineEvent, MomentumSnapshot, Highlight, TurningPoint, GameSummary, BoxScorePlayer } from '@/api/types'

defineProps<{
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

defineEmits<{ selectEvent: [event: TimelineEvent] }>()

function handleMomentumClick(snapshot: MomentumSnapshot) {
  // Parent handles finding the nearest timeline event
  const ev = (window as any).__gi_momentum_click?.(snapshot)
  if (ev) (window as any).__gi_select_event?.(ev)
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
@keyframes gi-fadeIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

.gi-row {
  display: grid;
  gap: 16px;
  align-items: start;
}
.gi-row-trend {
  grid-template-columns: 1.45fr 0.95fr;
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
