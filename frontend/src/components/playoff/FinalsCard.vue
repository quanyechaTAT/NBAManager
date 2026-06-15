<template>
  <div class="finals-container">
    <div class="finals-badge">
      <div class="finals-title">NBA 总决赛</div>
      <div class="finals-season">{{ season }}</div>
    </div>

    <div class="finals-matchup">
      <div class="finals-team" :class="{ 'is-winner': finals.winner === finals.t1 }">
        <TeamLogo :team-name="finals.t1" />
        <div class="team-info">
          <span class="team-name">{{ finals.t1 }}</span>
        </div>
        <div class="team-wins">{{ formatScore(finals.w1) }}</div>
      </div>

      <div class="vs-divider"><span class="vs-text">VS</span></div>

      <div class="finals-team" :class="{ 'is-winner': finals.winner === finals.t2 }">
        <TeamLogo :team-name="finals.t2" />
        <div class="team-info">
          <span class="team-name">{{ finals.t2 }}</span>
        </div>
        <div class="team-wins">{{ formatScore(finals.w2) }}</div>
      </div>
    </div>

    <div v-if="finals.winner && finals.winner !== '待定'" class="champion-banner">
      <span class="trophy">🏆</span>
      <span class="champion-name">{{ finals.winner }}</span>
      <span class="champion-label">总冠军</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import TeamLogo from './TeamLogo.vue'

interface Finals {
  t1: string; t2: string; w1?: number; w2?: number; winner?: string
}
interface Props { finals: Finals; season: string }

defineProps<Props>()

const formatScore = (score?: number) => typeof score === 'number' ? score : '-'
</script>

<style scoped>
.finals-container {
  display: flex; flex-direction: column; align-items: center; gap: 16px;
  padding: 24px 16px;
  animation: fadeInScale 0.5s ease forwards; opacity: 0;
}
@keyframes fadeInScale { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }

.finals-badge {
  text-align: center; padding: 14px 28px;
  background: var(--playoff-gold-bg, linear-gradient(135deg, #D4A017 0%, #F5C842 100%));
  border-radius: 14px;
  box-shadow: 0 4px 16px var(--playoff-gold-shadow, rgba(212,160,23,0.35));
  position: relative; overflow: hidden;
}
.finals-badge::before {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(135deg, rgba(255,255,255,0.25) 0%, transparent 50%);
  pointer-events: none;
}
.finals-title {
  font-size: 18px; font-weight: 900; letter-spacing: 3px;
  color: #fff; text-shadow: 0 2px 8px rgba(0,0,0,0.2);
  margin-bottom: 2px; position: relative; z-index: 1;
}
.finals-season {
  font-size: 11px; font-weight: 600; letter-spacing: 1.5px;
  color: rgba(255,255,255,0.85); position: relative; z-index: 1;
}

.finals-matchup {
  width: 180px;
  background: var(--playoff-card-bg, #fff);
  border: 2px solid var(--playoff-gold, #D4A017);
  border-radius: 14px; padding: 12px;
  box-shadow: 0 8px 28px var(--playoff-gold-shadow, rgba(212,160,23,0.2));
}

.finals-team {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  background: var(--playoff-card-hover, rgba(0,0,0,0.02));
  border: 1px solid var(--playoff-card-border, rgba(0,0,0,0.06));
  border-radius: 10px; transition: all 0.3s ease;
}
.finals-team:first-child { margin-bottom: 8px; }
.finals-team:hover { background: var(--playoff-card-shadow-hover, rgba(0,0,0,0.04)); transform: translateX(-2px); }
.finals-team.is-winner {
  background: var(--playoff-win-bg, rgba(37,99,235,0.08));
  border-color: var(--playoff-accent-dim, rgba(37,99,235,0.2));
  box-shadow: 0 0 0 2px var(--playoff-accent-dim, rgba(37,99,235,0.1));
}
.finals-team.is-winner .team-name { color: var(--playoff-accent, #2563eb); font-weight: 900; }

.team-info { flex: 1; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.team-name {
  font-size: 15px; font-weight: 700; color: var(--text-primary, #1f2937);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.team-wins {
  display: flex; align-items: center; justify-content: center;
  width: 40px; height: 40px;
  background: var(--playoff-score-bg, #f1f5f9); border: 1px solid var(--playoff-card-border, #e5e7eb);
  border-radius: 10px;
  color: var(--text-primary, #1f2937);
  font-size: 18px; font-weight: 900; font-variant-numeric: tabular-nums;
}

.vs-divider { display: flex; align-items: center; justify-content: center; padding: 6px 0; }
.vs-text {
  font-size: 12px; font-weight: 900; letter-spacing: 3px;
  color: var(--text-dim, #9ca3af);
}

.champion-banner {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 24px;
  background: var(--playoff-gold-bg, linear-gradient(135deg, #fef3c7 0%, #fde68a 100%));
  border: 2px solid var(--playoff-gold, #D4A017);
  border-radius: 22px;
  box-shadow: 0 4px 12px var(--playoff-gold-shadow, rgba(212,160,23,0.25));
  animation: bounceIn 0.6s ease 0.3s forwards; opacity: 0;
}
@keyframes bounceIn { 0% { opacity: 0; transform: scale(0.3); } 50% { transform: scale(1.05); } 70% { transform: scale(0.9); } 100% { opacity: 1; transform: scale(1); } }

.trophy { font-size: 20px; }
.champion-name { font-size: 15px; font-weight: 900; color: var(--playoff-gold-text, #92400e); letter-spacing: 0.5px; }
.champion-label { font-size: 12px; font-weight: 700; color: var(--playoff-gold-text-dim, #b45309); }
</style>
