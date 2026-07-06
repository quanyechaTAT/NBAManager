<template>
  <section class="turning-points" v-if="points.length">
    <div class="section-heading">
      <h4 class="section-title">关键转折</h4>
      <span class="section-count">{{ points.length }} 个节点</span>
    </div>
    <div class="points-list">
      <article v-for="tp in points" :key="tp.id" class="tp-card">
        <div class="tp-header">
          <span class="tp-type">{{ typeLabels[tp.type] || tp.type }}</span>
          <span class="tp-time">Q{{ tp.period }} {{ tp.gameClock }}</span>
          <span class="tp-confidence">置信度 {{ Math.round(tp.confidence * 100) }}%</span>
        </div>
        <p class="tp-reason">{{ tp.factualReason }}</p>
        <div v-if="tp.aiExplanation" class="tp-ai">
          <span class="ai-badge">AI 分析</span>
          <span>{{ tp.aiExplanation }}</span>
        </div>
      </article>
    </div>
  </section>
  <div v-else class="empty-state">暂无转折点数据</div>
</template>

<script setup lang="ts">
import type { TurningPoint } from '@/api/types'

defineProps<{ points: TurningPoint[] }>()

const typeLabels: Record<string, string> = {
  RUN: '攻击波',
  OVERTAKE: '反超',
  CLUTCH_SEPARATION: '关键拉开',
}
</script>

<style scoped>
.turning-points {
  min-width: 0;
}
.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.section-title {
  margin: 0;
  color: #eef4ff;
  font-size: 17px;
  font-weight: 700;
}
.section-count {
  color: #7f8ca3;
  font-size: 12px;
}
.points-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}
.tp-card {
  border: 1px solid rgba(148, 163, 184, 0.13);
  border-radius: 14px;
  padding: 16px;
  background:
    radial-gradient(circle at 0% 0%, rgba(245, 158, 11, 0.1), transparent 32%),
    linear-gradient(135deg, rgba(15, 23, 42, 0.94), rgba(17, 24, 39, 0.78));
}
.tp-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.tp-type {
  border-radius: 999px;
  padding: 4px 10px;
  background: rgba(245, 158, 11, 0.14);
  color: #ffd28a;
  font-size: 12px;
  font-weight: 800;
}
.tp-time {
  color: #98a6ba;
  font-size: 13px;
}
.tp-confidence {
  color: #8a97ac;
  font-size: 12px;
  margin-left: auto;
}
.tp-reason {
  margin: 0;
  color: #e2e8f0;
  font-size: 15px;
  line-height: 1.48;
}
.tp-ai {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  border-radius: 10px;
  padding: 10px 12px;
  color: #b9c6da;
  background: rgba(59, 162, 255, 0.08);
  font-size: 13px;
  line-height: 1.45;
}
.ai-badge {
  flex-shrink: 0;
  height: 20px;
  border-radius: 6px;
  padding: 2px 7px;
  color: #7ec3ff;
  background: rgba(59, 162, 255, 0.14);
  font-size: 11px;
  font-weight: 800;
}
.empty-state {
  border: 1px dashed rgba(148, 163, 184, 0.16);
  border-radius: 14px;
  padding: 42px 24px;
  text-align: center;
  color: #7f8ca3;
  background: rgba(15, 23, 42, 0.34);
}
</style>
