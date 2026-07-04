<template>
  <div class="turning-points" v-if="points.length">
    <h4 class="section-title">关键转折</h4>
    <div class="points-list">
      <div v-for="tp in points" :key="tp.id" class="tp-card">
        <div class="tp-header">
          <span class="tp-type">{{ typeLabels[tp.type] || tp.type }}</span>
          <span class="tp-time">Q{{ tp.period }} {{ tp.gameClock }}</span>
          <span class="tp-confidence">置信度 {{ Math.round(tp.confidence * 100) }}%</span>
        </div>
        <div class="tp-reason">{{ tp.factualReason }}</div>
        <div v-if="tp.aiExplanation" class="tp-ai">
          <span class="ai-badge">AI 分析</span>
          {{ tp.aiExplanation }}
        </div>
      </div>
    </div>
  </div>
  <div v-else class="empty-state">暂无转折点数据</div>
</template>

<script setup lang="ts">
import type { TurningPoint } from '@/api/types'

defineProps<{ points: TurningPoint[] }>()

const typeLabels: Record<string, string> = {
  'RUN': '攻击波',
  'OVERTAKE': '反超',
  'CLUTCH_SEPARATION': '关键时刻拉开'
}
</script>

<style scoped>
.section-title { margin-bottom: 12px; color: var(--el-text-color-primary); font-size: 16px; font-weight: 600; }
.points-list { display: flex; flex-direction: column; gap: 12px; }
.tp-card { padding: 16px; border-radius: 10px; background: var(--el-bg-color-page); border: 1px solid var(--el-border-color-lighter); }
.tp-header { display: flex; gap: 12px; align-items: center; margin-bottom: 8px; }
.tp-type { padding: 2px 10px; border-radius: 4px; background: var(--el-color-warning-light-8); color: var(--el-color-warning); font-size: 12px; font-weight: 600; }
.tp-time { color: var(--el-text-color-secondary); font-size: 13px; }
.tp-confidence { color: var(--el-text-color-secondary); font-size: 12px; margin-left: auto; }
.tp-reason { color: var(--el-text-color-primary); line-height: 1.5; }
.tp-ai { margin-top: 8px; padding: 8px 12px; border-radius: 6px; background: var(--el-color-info-light-9); color: var(--el-text-color-regular); font-size: 13px; }
.ai-badge { display: inline-block; padding: 1px 6px; border-radius: 3px; background: var(--el-color-info); color: white; font-size: 11px; margin-right: 6px; }
.empty-state { text-align: center; padding: 40px; color: var(--el-text-color-secondary); }
</style>
