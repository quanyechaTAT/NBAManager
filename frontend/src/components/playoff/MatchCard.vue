<template>
  <div class="match-card" :class="{ 'is-finals': isFinals, 'is-complete': isComplete }">
    <div class="team-row" :class="{ 'is-winner': matchup.winnerName === matchup.team1Name }">
      <TeamLogo :team-name="matchup.team1Name" />
      <span class="team-name">{{ matchup.team1Name }}</span>
      <div class="team-score" :class="{ 'score-win': matchup.winnerName === matchup.team1Name }">
        {{ formatScore(matchup.team1Wins) }}
      </div>
    </div>
    <div class="team-row" :class="{ 'is-winner': matchup.winnerName === matchup.team2Name }">
      <TeamLogo :team-name="matchup.team2Name" />
      <span class="team-name">{{ matchup.team2Name }}</span>
      <div class="team-score" :class="{ 'score-win': matchup.winnerName === matchup.team2Name }">
        {{ formatScore(matchup.team2Wins) }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import TeamLogo from './TeamLogo.vue'
import type { Matchup } from '@/types/playoff'

interface Props {
  matchup: Matchup
  isFinals?: boolean
}

const props = withDefaults(defineProps<Props>(), { isFinals: false })

const isComplete = computed(() => !!props.matchup.winnerName)

const formatScore = (score?: number) => {
  return typeof score === 'number' ? score : '-'
}
</script>

<style scoped>
.match-card {
  width: 100%;
  background: var(--playoff-card-bg, #ffffff);
  border: 1px solid var(--playoff-card-border, #e5e7eb);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px var(--playoff-card-shadow, rgba(0, 0, 0, 0.06));
  transition: all 0.25s ease;
  animation: slideIn 0.3s ease forwards;
  opacity: 0;
}
@keyframes slideIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }

.match-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px var(--playoff-card-shadow-hover, rgba(0, 0, 0, 0.1));
}
.match-card.is-complete {
  border-color: var(--playoff-accent-dim, rgba(37, 99, 235, 0.15));
}
.match-card.is-finals {
  border: 2px solid var(--playoff-gold, #D4A017);
}

.team-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
  min-height: 36px;
  border-bottom: 1px solid var(--playoff-card-border, #f0f0f0);
  transition: background 0.2s ease;
}
.team-row:last-child { border-bottom: none; }
.team-row:hover { background: var(--playoff-card-hover, rgba(0, 0, 0, 0.02)); }
.team-row.is-winner { background: var(--playoff-win-bg, rgba(37, 99, 235, 0.06)); }
.team-row.is-winner .team-name { font-weight: 800; color: var(--playoff-accent, #2563eb); }
.team-row.is-winner .team-score { background: var(--playoff-accent, #2563eb); color: #fff; font-weight: 900; }

.team-name {
  flex: 1; min-width: 0; font-size: 11px; font-weight: 600;
  color: var(--text-primary, #1f2937);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.team-score {
  display: flex; align-items: center; justify-content: center;
  width: 24px; height: 24px; flex-shrink: 0;
  background: var(--playoff-score-bg, #f1f5f9); border-radius: 5px;
  color: var(--text-secondary, #475569);
  font-size: 12px; font-weight: 700; font-variant-numeric: tabular-nums;
  transition: all 0.2s ease;
}
.team-score.score-win { background: var(--playoff-accent, #2563eb); color: #fff; }

.match-card.is-finals .team-row { padding: 8px 10px; min-height: 40px; }
.match-card.is-finals .team-name { font-size: 12px; font-weight: 700; }
.match-card.is-finals .team-score { width: 28px; height: 28px; font-size: 14px; }
</style>
