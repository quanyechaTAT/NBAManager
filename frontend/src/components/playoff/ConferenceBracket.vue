<template>
  <div class="conference-bracket">
    <div class="conference-header">
      <h3 class="conference-title">{{ title }}</h3>
    </div>

    <div class="rounds-container" :class="{ 'reverse': isReverse }">
      <div class="round-column">
        <div class="round-label">首轮</div>
        <div class="matches-list round-1">
          <MatchCard v-for="(match, index) in rounds[1]" :key="match.id" :matchup="match"
            :style="{ animationDelay: `${index * 100}ms` }" />
        </div>
      </div>

      <BracketConnector :count="4" :direction="isReverse ? 'left' : 'right'" :height="240" />

      <div class="round-column">
        <div class="round-label">半决赛</div>
        <div class="matches-list round-2">
          <MatchCard v-for="(match, index) in rounds[2]" :key="match.id" :matchup="match"
            :style="{ animationDelay: `${400 + index * 100}ms` }" />
        </div>
      </div>

      <BracketConnector :count="2" :direction="isReverse ? 'left' : 'right'" :height="120" />

      <div class="round-column">
        <div class="round-label">分区决赛</div>
        <div class="matches-list round-3">
          <MatchCard v-for="(match, index) in rounds[3]" :key="match.id" :matchup="match"
            :style="{ animationDelay: `${600 + index * 100}ms` }" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import MatchCard from './MatchCard.vue'
import BracketConnector from './BracketConnector.vue'
import type { RoundData } from '@/types/playoff'

interface Props {
  title: string
  rounds: RoundData
  isReverse?: boolean
}

withDefaults(defineProps<Props>(), { isReverse: false })
</script>

<style scoped>
.conference-bracket { display: flex; flex-direction: column; gap: 12px; flex: 1; min-width: 0; }
.conference-header { text-align: center; }

.conference-title {
  display: inline-block; margin: 0; padding: 6px 16px;
  background: var(--playoff-accent, #2563eb);
  border-radius: 16px; color: #fff;
  font-size: 13px; font-weight: 800; letter-spacing: 0.5px;
  box-shadow: 0 4px 12px var(--playoff-accent-shadow, rgba(37,99,235,0.3));
  animation: slideDown 0.4s ease forwards; opacity: 0;
}
@keyframes slideDown { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }

.rounds-container { display: flex; align-items: center; gap: 0; }
.rounds-container.reverse { flex-direction: row-reverse; }

.round-column { display: flex; flex-direction: column; gap: 8px; flex: 1; min-width: 0; }

.round-label {
  height: 24px; display: flex; align-items: center; justify-content: center;
  padding: 0 8px; font-size: 10px; font-weight: 700; letter-spacing: 0.5px;
  color: var(--text-dim, #94a3b8);
  background: var(--bg-hover, rgba(148,163,184,0.1));
  border-radius: 6px; animation: fadeIn 0.3s ease forwards; opacity: 0;
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

.matches-list { display: flex; flex-direction: column; gap: 6px; width: 100%; }
.matches-list.round-1 { gap: 6px; }
.matches-list.round-2 { gap: 40px; margin-top: 24px; }
.matches-list.round-3 { margin-top: 40px; }

@media (max-width: 1100px) {
  .rounds-container, .rounds-container.reverse { flex-direction: column; align-items: center; gap: 16px; }
  .round-column { width: 100%; max-width: 350px; }
  .matches-list.round-2, .matches-list.round-3 { margin-top: 0; gap: 8px; }
  .rounds-container :deep(.bracket-connector) { display: none; }
}
</style>
