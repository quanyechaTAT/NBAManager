<template>
  <article class="win-card">
    <span>Win Probability</span>
    <div class="win-row">
      <strong class="home">{{ Math.round(home) }}%</strong>
      <strong class="away">{{ Math.round(away) }}%</strong>
    </div>
    <div class="track">
      <i :style="{ width: `${home}%` }"></i>
    </div>
  </article>
</template>

<script setup lang="ts">
defineProps<{
  home: number
  away: number
}>()
</script>

<style scoped>
.win-card {
  display: grid;
  gap: 10px;
}

span {
  color: var(--trend-muted);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.win-row {
  display: flex;
  justify-content: space-between;
}

strong {
  font-size: 22px;
  font-weight: 950;
}

.home {
  color: var(--trend-home);
}

.away {
  color: var(--trend-away);
}

.track {
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--trend-away-soft);
}

.track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--trend-home);
  animation: win-grow 0.7s ease both;
}

@keyframes win-grow {
  from { width: 0; }
}
</style>
