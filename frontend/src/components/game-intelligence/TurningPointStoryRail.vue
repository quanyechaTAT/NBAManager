<template>
  <section class="tp-rail" v-if="points.length">
    <div class="rail-header">
      <h4 class="rail-title">关键转折</h4>
      <span class="rail-count">{{ points.length }} 个节点</span>
    </div>
    <div class="rail-scroll" ref="scrollContainer">
      <article
        v-for="tp in points"
        :key="tp.id"
        class="tp-card"
        :class="typeClass(tp.type)"
        @click="$emit('select', tp)"
      >
        <div class="card-top">
          <span class="type-badge" :class="typeClass(tp.type)">
            <span class="type-icon">{{ typeIcon(tp.type) }}</span>
            {{ typeLabels[tp.type] || tp.type }}
          </span>
          <span class="confidence-badge" :class="confidenceClass(tp.confidence)">
            {{ Math.round(tp.confidence * 100) }}%
          </span>
        </div>

        <div class="card-mid">
          <span class="clock-label">Q{{ tp.period }} {{ tp.gameClock }}</span>
          <p class="factual-reason">{{ tp.factualReason }}</p>
        </div>

        <div class="card-bottom">
          <div v-if="tp.aiExplanation" class="ai-block">
            <span class="ai-tag">AI</span>
            <span class="ai-text">{{ tp.aiExplanation }}</span>
          </div>
          <div v-else class="ai-pending">
            <span class="ai-tag pending">AI</span>
            <span class="ai-text muted">等待 AI 分析</span>
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
    <div class="empty-title">暂无转折点数据</div>
    <div class="empty-desc">比赛进行中或数据尚未生成时，转折点将在此处展示。</div>
  </section>
</template>

<script setup lang="ts">
import type { TurningPoint } from '@/api/types'

defineProps<{ points: TurningPoint[] }>()
defineEmits<{ select: [point: TurningPoint] }>()

const typeLabels: Record<string, string> = {
  RUN: '攻击波',
  OVERTAKE: '反超',
  CLUTCH_SEPARATION: '关键拉开',
}

const typeIcons: Record<string, string> = {
  RUN: '⚡',
  OVERTAKE: '⇅',
  CLUTCH_SEPARATION: '⭐',
}

function typeClass(type: string): string {
  if (type === 'RUN') return 'tp-run'
  if (type === 'OVERTAKE') return 'tp-overtake'
  if (type === 'CLUTCH_SEPARATION') return 'tp-clutch'
  return ''
}

function typeIcon(type: string): string {
  return typeIcons[type] || '●'
}

function confidenceClass(confidence: number): string {
  if (confidence >= 0.8) return 'conf-high'
  if (confidence >= 0.5) return 'conf-mid'
  return 'conf-low'
}
</script>

<style scoped>
.tp-rail {
  min-width: 0;
}

.rail-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.rail-title {
  margin: 0;
  color: var(--gi-text, #f8fafc);
  font-size: 17px;
  font-weight: 700;
}

.rail-count {
  color: var(--gi-muted, #94a3b8);
  font-size: 12px;
}

.rail-scroll {
  display: flex;
  gap: 14px;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-behavior: smooth;
  padding-bottom: 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.2) transparent;
}

.rail-scroll::-webkit-scrollbar {
  height: 5px;
}

.rail-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.rail-scroll::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.2);
  border-radius: 10px;
}

.tp-card {
  min-width: 280px;
  max-width: 320px;
  flex-shrink: 0;
  background: var(--gi-surface-strong, #111827);
  border: 1px solid var(--gi-border, rgba(255, 255, 255, 0.08));
  border-radius: 14px;
  padding: 18px;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.tp-card.tp-run {
  border-left: 3px solid #10b981;
}

.tp-card.tp-overtake {
  border-left: 3px solid #f97316;
}

.tp-card.tp-clutch {
  border-left: 3px solid #8b5cf6;
}

.tp-card:hover {
  transform: translateY(-3px);
  border-color: rgba(255, 255, 255, 0.14);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
}

/* ---------- top row ---------- */
.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 14px;
}

.type-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.type-badge.tp-run {
  background: rgba(16, 185, 129, 0.14);
  color: #6ee7b7;
}

.type-badge.tp-overtake {
  background: rgba(249, 115, 22, 0.14);
  color: #fdba74;
}

.type-badge.tp-clutch {
  background: rgba(139, 92, 246, 0.14);
  color: #c4b5fd;
}

.type-icon {
  font-size: 11px;
}

.confidence-badge {
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  line-height: 1;
}

.confidence-badge.conf-high {
  background: rgba(16, 185, 129, 0.12);
  color: #6ee7b7;
}

.confidence-badge.conf-mid {
  background: rgba(245, 158, 11, 0.12);
  color: #fcd34d;
}

.confidence-badge.conf-low {
  background: rgba(239, 68, 68, 0.12);
  color: #fca5a5;
}

/* ---------- middle row ---------- */
.card-mid {
  margin-bottom: 14px;
}

.clock-label {
  display: block;
  margin-bottom: 8px;
  color: var(--gi-muted, #94a3b8);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.factual-reason {
  margin: 0;
  color: var(--gi-text, #f1f5f9);
  font-size: 14px;
  line-height: 1.5;
}

/* ---------- bottom row ---------- */
.card-bottom {
  padding-top: 12px;
  border-top: 1px solid var(--gi-border, rgba(255, 255, 255, 0.06));
}

.ai-block,
.ai-pending {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.ai-tag {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 18px;
  border-radius: 5px;
  background: rgba(59, 130, 246, 0.14);
  color: #93c5fd;
  font-size: 10px;
  font-weight: 800;
  line-height: 1;
  margin-top: 2px;
}

.ai-tag.pending {
  background: rgba(148, 163, 184, 0.1);
  color: #64748b;
}

.ai-text {
  color: #cbd5e1;
  font-size: 12px;
  line-height: 1.55;
}

.ai-text.muted {
  color: #64748b;
  font-style: italic;
}

/* ---------- empty state ---------- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  border: 1px dashed var(--gi-border, rgba(255, 255, 255, 0.08));
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.34);
  text-align: center;
}

.empty-icon {
  color: #475569;
  margin-bottom: 14px;
}

.empty-title {
  color: #e2e8f0;
  font-size: 15px;
  font-weight: 700;
}

.empty-desc {
  max-width: 320px;
  margin-top: 6px;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.6;
}
</style>
