<template>
  <section class="highlight-panel" v-if="highlights.length">
    <div class="panel-header">
      <div>
        <h4 class="section-title">精彩瞬间</h4>
        <p class="section-subtitle">由真实逐回合事件提取的高价值回合</p>
      </div>
      <span class="count-pill">{{ highlights.length }} 个</span>
    </div>

    <div class="cards-grid">
      <article
        v-for="h in highlights"
        :key="h.id"
        class="highlight-card"
        :class="h.highlightType.toLowerCase()"
      >
        <div class="card-header">
          <span class="type-badge">{{ typeLabels[h.highlightType] || h.highlightType }}</span>
          <span class="time-info">Q{{ h.period }} {{ h.gameClock }}</span>
        </div>
        <div class="card-title">{{ h.title }}</div>
        <div class="card-desc">{{ h.description }}</div>
        <div class="card-footer">
          <span class="score-label">事件后比分</span>
          <span class="card-score">{{ h.scoreAfter }}</span>
        </div>
      </article>
    </div>
  </section>

  <section v-else class="empty-state">
    <div class="empty-title">暂无精彩瞬间</div>
    <div class="empty-desc">当前比赛还没有可展示的扣篮、三分、盖帽、抢断或关键球事件。</div>
  </section>
</template>

<script setup lang="ts">
import type { Highlight } from '@/api/types'

defineProps<{ highlights: Highlight[] }>()

const typeLabels: Record<string, string> = {
  DUNK: '扣篮',
  BLOCK: '盖帽',
  THREE_PT: '三分',
  STEAL: '抢断',
  GAME_WINNER: '绝杀',
}
</script>

<style scoped>
.highlight-panel,
.empty-state {
  min-height: 100%;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 16px;
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.96), rgba(9, 14, 25, 0.98)),
    radial-gradient(circle at top right, rgba(59, 162, 255, 0.14), transparent 34%);
  box-shadow: 0 18px 50px rgba(2, 6, 23, 0.28);
}

.highlight-panel {
  padding: 18px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-title {
  margin: 0;
  color: #f8fafc;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
}

.section-subtitle {
  margin: 6px 0 0;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.5;
}

.count-pill,
.type-badge {
  flex: 0 0 auto;
  border: 1px solid rgba(96, 165, 250, 0.28);
  background: rgba(59, 130, 246, 0.12);
  color: #93c5fd;
  font-size: 12px;
  font-weight: 700;
}

.count-pill {
  padding: 5px 9px;
  border-radius: 999px;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.highlight-card {
  position: relative;
  overflow: hidden;
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.72);
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.highlight-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  content: '';
  background: #60a5fa;
}

.highlight-card:hover {
  transform: translateY(-2px);
  border-color: rgba(96, 165, 250, 0.36);
  background: rgba(20, 30, 52, 0.86);
}

.highlight-card.dunk::before,
.highlight-card.game_winner::before {
  background: #fb7185;
}

.highlight-card.three_pt::before {
  background: #34d399;
}

.highlight-card.block::before {
  background: #fbbf24;
}

.highlight-card.steal::before {
  background: #38bdf8;
}

.card-header,
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-header {
  margin-bottom: 12px;
}

.type-badge {
  padding: 4px 8px;
  border-radius: 999px;
}

.time-info,
.score-label {
  color: #94a3b8;
  font-size: 12px;
  white-space: nowrap;
}

.card-title {
  margin-bottom: 6px;
  color: #f8fafc;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
  text-wrap: pretty;
}

.card-desc {
  min-height: 38px;
  color: #cbd5e1;
  font-size: 13px;
  line-height: 1.5;
  text-wrap: pretty;
}

.card-footer {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.12);
}

.card-score {
  color: #f8fafc;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  font-weight: 800;
}

.empty-state {
  display: flex;
  min-height: 180px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 28px;
  border-style: dashed;
  text-align: center;
}

.empty-title {
  color: #e2e8f0;
  font-size: 15px;
  font-weight: 700;
}

.empty-desc {
  max-width: 360px;
  margin-top: 8px;
  color: #94a3b8;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 640px) {
  .highlight-panel {
    padding: 14px;
  }

  .panel-header {
    align-items: stretch;
    flex-direction: column;
  }

  .count-pill {
    width: max-content;
  }
}
</style>
