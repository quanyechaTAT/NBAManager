<template>
  <section class="tp-rail" v-if="displayPoints.length">
    <div class="rail-header">
      <h4 class="rail-title">关键转折</h4>
      <span class="rail-count">{{ displayPoints.length }} 个核心节点</span>
    </div>
    <div class="rail-scroll">
      <article
        v-for="tp in displayPoints"
        :key="tp.id"
        :data-tp-id="tp.id"
        class="tp-card"
        :class="[typeClass(tp.type), impactClass(tp.impactLevel)]"
      >
        <div class="card-top">
          <span class="type-badge" :class="typeClass(tp.type)">
            <span class="type-icon">{{ typeIcon(tp.type) }}</span>
            {{ typeLabel(tp.type) }}
          </span>
          <span class="confidence-badge" :class="confidenceClass(tp.confidence)">
            {{ Math.round(tp.confidence * 100) }}%
          </span>
        </div>

        <div class="card-mid">
          <span class="clock-label">Q{{ tp.period }} {{ tp.gameClock }}</span>
          <p class="factual-reason">{{ tp.factualReason }}</p>
          <div v-if="hasScoreRange(tp)" class="score-change">
            <span class="score-from">{{ tp.startHomeScore }}-{{ tp.startAwayScore }}</span>
            <span class="score-arrow">→</span>
            <span class="score-to">{{ tp.endHomeScore }}-{{ tp.endAwayScore }}</span>
          </div>
        </div>

        <div class="card-bottom">
          <div class="ai-block">
            <span class="ai-tag">分析</span>
            <span class="ai-text">{{ tp.aiExplanation || fallbackExplanation(tp) }}</span>
          </div>
        </div>
      </article>
    </div>
  </section>

  <section v-else class="empty-state">
    <div class="empty-icon">
      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
      </svg>
    </div>
    <div class="empty-title">暂无明显比赛转折</div>
    <div class="empty-desc">本场比赛走势较平稳，未检测到显著攻击波、反超或关键时刻节点。</div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TurningPoint } from '@/api/types'

const props = defineProps<{ points: TurningPoint[] }>()
defineEmits<{ select: [point: TurningPoint] }>()

// Show top 6 by impactScore
const displayPoints = computed(() =>
  [...props.points]
    .sort((a, b) => (b.impactScore ?? 0) - (a.impactScore ?? 0))
    .slice(0, 6)
)

function hasScoreRange(tp: TurningPoint): boolean {
  return tp.startHomeScore != null && tp.endHomeScore != null
}

function typeLabel(type: string): string {
  switch (type) {
    case 'RUN': return '攻击波'
    case 'LEAD_CHANGE': return '反超'
    case 'TIE': return '追平'
    case 'CLUTCH_SCORE': return '关键得分'
    case 'CLUTCH_STOP': return '关键防守'
    case 'MARGIN_BREAK': return '拉开分差'
    default: return '转折点'
  }
}

function typeIcon(type: string): string {
  switch (type) {
    case 'RUN': return '⚡'
    case 'LEAD_CHANGE': return '⇅'
    case 'TIE': return '⚖'
    case 'CLUTCH_SCORE': return '🎯'
    case 'CLUTCH_STOP': return '🛡'
    case 'MARGIN_BREAK': return '📈'
    default: return '●'
  }
}

function typeClass(type: string): string {
  switch (type) {
    case 'RUN': return 'tp-run'
    case 'LEAD_CHANGE': case 'TIE': return 'tp-overtake'
    case 'CLUTCH_SCORE': case 'CLUTCH_STOP': return 'tp-clutch'
    case 'MARGIN_BREAK': return 'tp-margin'
    default: return ''
  }
}

function impactClass(level?: string): string {
  if (level === 'HIGH') return 'impact-high'
  if (level === 'MEDIUM') return 'impact-mid'
  return ''
}

function confidenceClass(confidence: number): string {
  if (confidence >= 0.8) return 'conf-high'
  if (confidence >= 0.5) return 'conf-mid'
  return 'conf-low'
}

function fallbackExplanation(tp: TurningPoint): string {
  const margin = tp.scoreSwing ?? 0
  if (tp.type === 'RUN') return `这段攻击波净胜 ${margin} 分，改变了比赛走势。`
  if (tp.type === 'LEAD_CHANGE') return `比分完成反超，比赛形势发生变化。`
  if (tp.type === 'TIE') return `比分追平，双方回到同一起跑线。`
  if (tp.type === 'CLUTCH_SCORE') return `关键时刻得分，对比赛结果有重要影响。`
  if (tp.type === 'MARGIN_BREAK') return `分差扩大到 ${margin} 分，建立显著优势。`
  return `比赛关键转折点。`
}
</script>

<style scoped>
.tp-rail { min-width: 0; }

.rail-header {
  display: flex; align-items: baseline; justify-content: space-between;
  gap: 12px; margin-bottom: 14px;
}
.rail-title { margin: 0; color: var(--gi-text, #f8fafc); font-size: 17px; font-weight: 700; }
.rail-count { color: var(--gi-muted, #94a3b8); font-size: 12px; }

.rail-scroll {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.tp-card {
  background: var(--gi-surface-strong, #111827);
  border: 1px solid var(--gi-border, rgba(255, 255, 255, 0.08));
  border-radius: 14px; padding: 18px;
  transition: transform 0.18s ease, border-color 0.18s ease;
}
.tp-card:hover { transform: translateY(-2px); border-color: rgba(255, 255, 255, 0.14); }
.tp-card.tp-highlight {
  border-color: var(--gi-home, #3b82f6);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.3), 0 0 20px rgba(59, 130, 246, 0.15);
  transition: border-color 0.3s, box-shadow 0.3s;
}

.tp-card.tp-run { border-left: 3px solid #10b981; }
.tp-card.tp-overtake { border-left: 3px solid #f97316; }
.tp-card.tp-clutch { border-left: 3px solid #8b5cf6; }
.tp-card.tp-margin { border-left: 3px solid #3b82f6; }
.tp-card.impact-high { box-shadow: 0 0 12px rgba(139, 92, 246, 0.15); }

.card-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 12px; }
.type-badge {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 4px 10px; border-radius: 999px;
  font-size: 12px; font-weight: 700;
}
.type-badge.tp-run { background: rgba(16, 185, 129, 0.14); color: #6ee7b7; }
.type-badge.tp-overtake { background: rgba(249, 115, 22, 0.14); color: #fdba74; }
.type-badge.tp-clutch { background: rgba(139, 92, 246, 0.14); color: #c4b5fd; }
.type-badge.tp-margin { background: rgba(59, 130, 246, 0.14); color: #93c5fd; }
.type-icon { font-size: 11px; }

.confidence-badge {
  padding: 3px 8px; border-radius: 999px;
  font-size: 11px; font-weight: 700;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.conf-high { background: rgba(16, 185, 129, 0.12); color: #6ee7b7; }
.conf-mid { background: rgba(245, 158, 11, 0.12); color: #fcd34d; }
.conf-low { background: rgba(239, 68, 68, 0.12); color: #fca5a5; }

.card-mid { margin-bottom: 12px; }
.clock-label { display: block; margin-bottom: 6px; color: var(--gi-muted, #94a3b8); font-size: 12px; font-weight: 600; }
.factual-reason { margin: 0; color: var(--gi-text, #f1f5f9); font-size: 14px; line-height: 1.5; }

.score-change {
  display: flex; align-items: center; gap: 6px; margin-top: 8px;
  font-size: 13px; font-weight: 600; font-family: ui-monospace, monospace;
}
.score-from { color: #94a3b8; }
.score-arrow { color: #64748b; }
.score-to { color: var(--gi-text, #f1f5f9); }

.card-bottom { padding-top: 10px; border-top: 1px solid var(--gi-border, rgba(255, 255, 255, 0.06)); }
.ai-block { display: flex; align-items: flex-start; gap: 8px; }
.ai-tag {
  flex-shrink: 0; display: inline-flex; align-items: center; justify-content: center;
  width: 28px; height: 18px; border-radius: 5px;
  background: rgba(59, 130, 246, 0.14); color: #93c5fd;
  font-size: 10px; font-weight: 800; margin-top: 2px;
}
.ai-text { color: #cbd5e1; font-size: 12px; line-height: 1.55; }

.empty-state {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 40px 24px; border: 1px dashed var(--gi-border, rgba(255, 255, 255, 0.08));
  border-radius: 14px; background: rgba(15, 23, 42, 0.34); text-align: center;
}
.empty-icon { color: #475569; margin-bottom: 14px; }
.empty-title { color: #e2e8f0; font-size: 15px; font-weight: 700; }
.empty-desc { max-width: 320px; margin-top: 6px; color: #94a3b8; font-size: 12px; line-height: 1.6; }
</style>
