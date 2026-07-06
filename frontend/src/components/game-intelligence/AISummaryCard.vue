<template>
  <div class="ai-summary-card">
    <!-- Header -->
    <div class="ai-header">
      <span class="ai-badge">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
        </svg>
        AI 赛事分析
      </span>
      <span class="mimo-label">MIMO AI</span>
    </div>

    <!-- Body: summary content -->
    <div class="ai-body">
      <p v-if="summary?.content" class="summary-text">{{ summary.content }}</p>
      <p v-else class="summary-empty">AI 总结暂不可用</p>
    </div>

    <!-- Mini cards row -->
    <div class="mini-cards">
      <!-- Key Turning Point -->
      <div class="mini-card">
        <div class="mini-card-header">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--gi-warning, #F59E0B)" stroke-width="2">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
          </svg>
          <span class="mini-card-title">关键转折</span>
        </div>
        <p class="mini-card-value">
          <template v-if="firstTurningPoint">
            <span class="tp-type">{{ turningPointLabel(firstTurningPoint.type) }}</span>
            <span class="tp-reason">{{ firstTurningPoint.factualReason }}</span>
          </template>
          <span v-else class="mini-card-placeholder">暂无</span>
        </p>
      </div>

      <!-- MVP Candidate -->
      <div class="mini-card">
        <div class="mini-card-header">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--gi-purple, #8B5CF6)" stroke-width="2">
            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
          </svg>
          <span class="mini-card-title">MVP 候选</span>
        </div>
        <p class="mini-card-value" v-if="mvpCandidate">
          <span class="mvp-name">{{ mvpCandidate.playerName }}</span>
          <span class="mvp-stats">
            {{ mvpCandidate.points }}分 {{ mvpCandidate.rebounds }}板 {{ mvpCandidate.assists }}助
          </span>
        </p>
        <p v-else class="mini-card-placeholder">暂无</p>
      </div>

      <!-- Momentum Tendency -->
      <div class="mini-card">
        <div class="mini-card-header">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--gi-cyan, #06B6D4)" stroke-width="2">
            <polyline points="23 6 13.5 15.5 8.5 10.5 1 18" />
            <polyline points="17 6 23 6 23 12" />
          </svg>
          <span class="mini-card-title">走势倾向</span>
        </div>
        <p class="mini-card-value" v-if="momentumTendency">
          <span class="momentum-label">{{ momentumTendency.label }}</span>
          <span class="momentum-gap" :class="momentumTendency.positive ? 'positive' : 'negative'">
            {{ momentumTendency.positive ? '+' : '' }}{{ momentumTendency.gap }}
          </span>
        </p>
        <p v-else class="mini-card-placeholder">暂无</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { GameSummary, TurningPoint, Highlight } from '@/api/types'

interface ComparisonItem {
  label: string
  homeValue: string
  awayValue: string
}

const props = defineProps<{
  summary: GameSummary | null
  turningPoints: TurningPoint[]
  highlights: Highlight[]
  homePlayers: any[]
  awayPlayers: any[]
  homeTeam: string
  awayTeam: string
}>()

/** First turning point or null */
const firstTurningPoint = computed(() => {
  return props.turningPoints.length > 0 ? props.turningPoints[0] : null
})

/** Map turning point type to Chinese label */
function turningPointLabel(type: string): string {
  const map: Record<string, string> = {
    MOMENTUM_SHIFT: '势头转换',
    LEAD_CHANGE: '比分反超',
    BIG_RUN: '大比分攻势',
    CLOSE_OUT: '收官阶段',
    CLUTCH: '关键时刻',
    INJURY: '伤病影响',
    FOUL_TROUBLE: '犯规困扰',
  }
  return map[type] || type
}

/** Compute MVP candidate using weighted formula */
const mvpCandidate = computed(() => {
  const allPlayers = [...props.homePlayers, ...props.awayPlayers]
  if (allPlayers.length === 0) return null

  let best = allPlayers[0]
  let bestScore = -Infinity

  for (const p of allPlayers) {
    const score =
      (p.points ?? 0) +
      (p.rebounds ?? 0) * 1.2 +
      (p.assists ?? 0) * 1.5 +
      (p.steals ?? 0) * 3 +
      (p.blocks ?? 0) * 3 -
      (p.turnovers ?? 0) * 2 +
      (p.plusMinus ?? 0) * 0.4

    if (score > bestScore) {
      bestScore = score
      best = p
    }
  }

  return {
    playerName: best.playerName,
    points: best.points ?? 0,
    rebounds: best.rebounds ?? 0,
    assists: best.assists ?? 0,
    steals: best.steals ?? 0,
    blocks: best.blocks ?? 0,
    turnovers: best.turnovers ?? 0,
    plusMinus: best.plusMinus ?? 0,
    score: Math.round(bestScore * 10) / 10,
  }
})

/** Compute momentum tendency from comparison items - find biggest positive gap */
const momentumTendency = computed(() => {
  // Build comparison items from player stats (same logic as MatchDetailView)
  const h = props.homePlayers
  const a = props.awayPlayers
  const sum = (players: any[], field: string) =>
    players.reduce((s: number, p: any) => s + (p[field] ?? 0), 0)

  const items: ComparisonItem[] = [
    { label: '得分', homeValue: String(sum(h, 'points')), awayValue: String(sum(a, 'points')) },
    { label: '篮板', homeValue: String(sum(h, 'rebounds')), awayValue: String(sum(a, 'rebounds')) },
    { label: '助攻', homeValue: String(sum(h, 'assists')), awayValue: String(sum(a, 'assists')) },
    { label: '抢断', homeValue: String(sum(h, 'steals')), awayValue: String(sum(a, 'steals')) },
    { label: '盖帽', homeValue: String(sum(h, 'blocks')), awayValue: String(sum(a, 'blocks')) },
  ]

  let biggestGap = 0
  let result: { label: string; gap: number; positive: boolean } | null = null

  for (const item of items) {
    const hv = parseFloat(item.homeValue) || 0
    const av = parseFloat(item.awayValue) || 0
    const diff = hv - av
    const absDiff = Math.abs(diff)

    if (absDiff > Math.abs(biggestGap)) {
      biggestGap = diff
      result = {
        label: `${props.homeTeam} ${item.label}优势`,
        gap: Math.round(absDiff),
        positive: diff > 0,
      }
    }
  }

  return result
})
</script>

<style scoped>
.ai-summary-card {
  background: var(--gi-surface-strong, #111827);
  border: 1px solid var(--gi-border, rgba(255, 255, 255, 0.08));
  border-radius: 14px;
  padding: 20px;
}

/* Header */
.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.ai-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--gi-text, #F8FAFC);
}

.mimo-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--gi-purple, #8B5CF6);
  background: rgba(139, 92, 246, 0.12);
  padding: 3px 8px;
  border-radius: 6px;
  letter-spacing: 0.5px;
}

/* Body */
.ai-body {
  margin-bottom: 20px;
}

.summary-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--gi-muted, #94A3B8);
  margin: 0;
  white-space: pre-wrap;
}

.summary-empty {
  font-size: 14px;
  color: var(--gi-muted, #94A3B8);
  opacity: 0.5;
  font-style: italic;
  margin: 0;
}

/* Mini cards */
.mini-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.mini-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--gi-border, rgba(255, 255, 255, 0.08));
  border-radius: 10px;
  padding: 14px;
}

.mini-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.mini-card-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--gi-muted, #94A3B8);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.mini-card-value {
  font-size: 13px;
  line-height: 1.6;
  color: var(--gi-text, #F8FAFC);
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mini-card-placeholder {
  font-size: 13px;
  color: var(--gi-muted, #94A3B8);
  opacity: 0.5;
  font-style: italic;
  margin: 0;
}

/* Turning point styles */
.tp-type {
  font-weight: 600;
  color: var(--gi-warning, #F59E0B);
  font-size: 13px;
}

.tp-reason {
  font-size: 12px;
  color: var(--gi-muted, #94A3B8);
  line-height: 1.5;
}

/* MVP styles */
.mvp-name {
  font-weight: 700;
  font-size: 15px;
  color: var(--gi-text, #F8FAFC);
}

.mvp-stats {
  font-size: 12px;
  color: var(--gi-muted, #94A3B8);
}

/* Momentum styles */
.momentum-label {
  font-size: 13px;
  color: var(--gi-text, #F8FAFC);
}

.momentum-gap {
  font-size: 14px;
  font-weight: 700;
}

.momentum-gap.positive {
  color: var(--gi-success, #10B981);
}

.momentum-gap.negative {
  color: var(--gi-danger, #EF4444);
}

@media (max-width: 768px) {
  .mini-cards {
    grid-template-columns: 1fr;
  }
}
</style>
