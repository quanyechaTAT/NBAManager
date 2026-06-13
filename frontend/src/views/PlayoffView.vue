<template>
  <div class="playoff-view">
    <!-- Header -->
    <div class="playoff-header">
      <div class="header-content">
        <div class="header-titles">
          <h1 class="main-title">NBA 季后赛对阵图</h1>
          <p class="sub-title">{{ currentSeason }} 赛季</p>
        </div>
        <button class="sync-button" @click="syncData" :disabled="syncing">
          <span class="sync-icon" :class="{ spinning: syncing }">⟳</span>
          <span>{{ syncing ? '同步中...' : '同步数据' }}</span>
        </button>
      </div>
    </div>

    <!-- Main Content -->
    <div class="playoff-content">
      <!-- Loading State -->
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>加载季后赛数据中...</p>
      </div>

      <!-- Error State -->
      <div v-else-if="loadError" class="error-state">
        <div class="error-icon">⚠</div>
        <p>加载季后赛数据失败</p>
        <button class="retry-button" @click="load">重试</button>
      </div>

      <!-- Playoff Bracket -->
      <div v-else class="bracket-container">
        <!-- No Data State -->
        <div v-if="!bracket" class="empty-state">
          <div class="empty-icon">📊</div>
          <p>本赛季暂无季后赛数据</p>
          <button class="sync-button-alt" @click="syncData">立即同步</button>
        </div>

        <!-- Main Bracket -->
        <div v-else class="bracket-layout">
          <!-- Western Conference -->
          <ConferenceBracket
            title="西部联盟"
            :rounds="westRounds"
            :is-reverse="false"
          />

          <!-- Finals -->
          <div class="finals-section">
            <FinalsCard :finals="finalsMatch" :season="currentSeason" />
          </div>

          <!-- Eastern Conference -->
          <ConferenceBracket
            title="东部联盟"
            :rounds="eastRounds"
            :is-reverse="true"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import ConferenceBracket from '@/components/playoff/ConferenceBracket.vue'
import FinalsCard from '@/components/playoff/FinalsCard.vue'
import type { PlayoffBracket, Matchup, RoundData } from '@/types/playoff'

const currentSeason = (() => {
  const d = new Date()
  const y = d.getFullYear()
  const m = d.getMonth() + 1
  return m >= 10 ? `${y}-${(y + 1).toString().slice(2)}` : `${y - 1}-${y.toString().slice(2)}`
})()

const loading = ref(false)
const syncing = ref(false)
const bracket = ref<PlayoffBracket | null>(null)
const loadError = ref(false)

interface EmptyMatchup extends Matchup {
  team1Name: string
  team2Name: string
}

function emptyMatch(id: string): EmptyMatchup {
  return {
    id,
    round: 1,
    team1Name: '待定',
    team2Name: '待定',
    winnerName: ''
  }
}

function fillRound(items: Matchup[], count: number, prefix: string): Matchup[] {
  return Array.from({ length: count }, (_, index) => items[index] || emptyMatch(`${prefix}-${index}`))
}

function rounds(side: 'eastern' | 'western'): RoundData {
  const source = bracket.value?.[side] || []
  return {
    1: fillRound(
      source.filter((m: Matchup) => m.round === 1),
      4,
      `${side}-r1`
    ),
    2: fillRound(
      source.filter((m: Matchup) => m.round === 2),
      2,
      `${side}-r2`
    ),
    3: fillRound(
      source.filter((m: Matchup) => m.round === 3),
      1,
      `${side}-r3`
    )
  }
}

const eastRounds = computed(() => rounds('eastern'))
const westRounds = computed(() => rounds('western'))

const finalsMatch = computed(() => {
  const f = bracket.value?.finals
  if (!f) return { t1: '待定', t2: '待定', w1: 0, w2: 0, winner: '' }
  return {
    t1: f.team1Name || f.t1 || '待定',
    t2: f.team2Name || f.t2 || '待定',
    w1: f.team1Wins ?? f.w1 ?? 0,
    w2: f.team2Wins ?? f.w2 ?? 0,
    winner: f.winnerName || f.winner || ''
  }
})

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const { data } = await request.get('/nba/playoff', {
      params: { season: currentSeason }
    })
    bracket.value =
      data.eastern?.length > 0 || data.western?.length > 0 || data.finals
        ? data
        : null
  } catch {
    bracket.value = null
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function syncData() {
  syncing.value = true
  try {
    await request.post('/nba/playoff/sync', null, {
      params: { season: currentSeason }
    })
    ElMessage.success('Playoff data synced successfully')
    await load()
  } catch {
    ElMessage.error('Failed to sync playoff data')
  } finally {
    syncing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.playoff-view {
  min-height: 100vh;
  background: var(--bg-page, #f8f9fa);
  position: relative;
}

.playoff-view::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 400px;
  background: radial-gradient(ellipse at top, var(--playoff-accent-dim, rgba(37, 99, 235, 0.08)) 0%, transparent 70%);
  pointer-events: none;
}

/* Header */
.playoff-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--bg-card, #ffffff);
  border-bottom: 1px solid var(--border-light, #e5e7eb);
  backdrop-filter: blur(10px);
}

.header-content {
  max-width: 1600px;
  margin: 0 auto;
  padding: 20px 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.header-titles {
  flex: 1;
  text-align: center;
}

.main-title {
  margin: 0 0 6px;
  font-size: 32px;
  font-weight: 900;
  letter-spacing: 3px;
  color: var(--text-primary, #1f2937);
  animation: titleSlideIn 0.6s ease forwards;
  opacity: 0;
}

@keyframes titleSlideIn {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.sub-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1.5px;
  color: var(--text-muted, #6b7280);
  text-transform: uppercase;
  animation: titleSlideIn 0.6s ease 0.1s forwards;
  opacity: 0;
}

.sync-button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--playoff-accent-dim, rgba(37, 99, 235, 0.1));
  border: 1px solid var(--playoff-accent-border, rgba(37, 99, 235, 0.2));
  border-radius: 8px;
  color: var(--playoff-accent, #2563eb);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s ease;
}

.sync-button:hover:not(:disabled) {
  background: var(--playoff-accent-dim-hover, rgba(37, 99, 235, 0.15));
  border-color: var(--playoff-accent, #2563eb);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px var(--playoff-accent-shadow, rgba(37, 99, 235, 0.2));
}

.sync-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.sync-icon {
  font-size: 18px;
  display: inline-block;
  transition: transform 0.3s ease;
}

.sync-icon.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* Content */
.playoff-content {
  max-width: 100%;
  margin: 0 auto;
  padding: 40px 20px 80px;
  position: relative;
  z-index: 1;
  overflow-x: auto;
}

/* Loading State */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  min-height: 400px;
  color: var(--text-muted, #6b7280);
  font-size: 16px;
  font-weight: 600;
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 4px solid var(--playoff-accent-dim, rgba(37, 99, 235, 0.2));
  border-top-color: var(--playoff-accent, #2563eb);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

/* Error State */
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  min-height: 400px;
  color: var(--text-muted, #6b7280);
}

.error-icon {
  font-size: 48px;
  opacity: 0.5;
}

.retry-button {
  padding: 10px 24px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 8px;
  color: #ef4444;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s ease;
}

.retry-button:hover {
  background: rgba(239, 68, 68, 0.15);
  transform: translateY(-2px);
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  min-height: 400px;
  color: var(--text-muted, #6b7280);
}

.empty-icon {
  font-size: 64px;
  opacity: 0.3;
}

.sync-button-alt {
  padding: 12px 28px;
  background: var(--playoff-accent, #2563eb);
  border: none;
  border-radius: 8px;
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px var(--playoff-accent-shadow, rgba(37, 99, 235, 0.3));
}

.sync-button-alt:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px var(--playoff-accent-shadow, rgba(37, 99, 235, 0.4));
}

/* Bracket Layout */
.bracket-container {
  width: 100%;
  min-width: fit-content;
}

.bracket-layout {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) minmax(240px, 280px) minmax(360px, 1fr);
  gap: 16px;
  align-items: start;
  justify-items: center;
  animation: fadeInUp 0.6s ease forwards;
  opacity: 0;
  margin: 0 auto;
  max-width: 1600px;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.finals-section {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 240px;
}

/* Responsive */
@media (max-width: 1400px) {
  .bracket-layout {
    gap: 12px;
    grid-template-columns: minmax(320px, 1fr) minmax(220px, 260px) minmax(320px, 1fr);
  }

  .finals-section {
    min-width: 220px;
  }
}

@media (max-width: 1200px) {
  .header-content {
    padding: 16px 24px;
  }

  .main-title {
    font-size: 26px;
  }

  .playoff-content {
    padding: 32px 16px 60px;
  }

  .bracket-layout {
    grid-template-columns: minmax(300px, 1fr) minmax(200px, 240px) minmax(300px, 1fr);
    gap: 10px;
  }
}

@media (max-width: 1100px) {
  .bracket-layout {
    grid-template-columns: 1fr;
    gap: 48px;
  }

  .finals-section {
    order: 0;
    width: 100%;
    max-width: 500px;
  }
}

@media (max-width: 768px) {
  .header-content {
    flex-direction: column;
    padding: 16px 20px;
    gap: 16px;
  }

  .main-title {
    font-size: 24px;
    letter-spacing: 2px;
  }

  .sub-title {
    font-size: 11px;
  }

  .playoff-content {
    padding: 24px 16px 48px;
  }

  .bracket-layout {
    gap: 32px;
  }
}
</style>
