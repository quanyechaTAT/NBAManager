<template>
  <div class="highlight-cards" v-if="highlights.length">
    <h4 class="section-title">精彩瞬间</h4>
    <div class="cards-grid">
      <div v-for="h in highlights" :key="h.id" class="highlight-card" :class="h.highlightType.toLowerCase()">
        <div class="card-header">
          <span class="type-badge">{{ typeLabels[h.highlightType] || h.highlightType }}</span>
          <span class="time-info">Q{{ h.period }} {{ h.gameClock }}</span>
        </div>
        <div class="card-title">{{ h.title }}</div>
        <div class="card-desc">{{ h.description }}</div>
        <div class="card-score">{{ h.scoreAfter }}</div>
      </div>
    </div>
  </div>
  <div v-else class="empty-state">暂无精彩瞬间</div>
</template>

<script setup lang="ts">
import type { Highlight } from '@/api/types'

defineProps<{ highlights: Highlight[] }>()

const typeLabels: Record<string, string> = {
  'DUNK': '扣篮',
  'BLOCK': '盖帽',
  'THREE_PT': '三分',
  'STEAL': '抢断',
  'GAME_WINNER': '绝杀'
}
</script>

<style scoped>
.section-title { margin-bottom: 12px; color: var(--el-text-color-primary); font-size: 16px; font-weight: 600; }
.cards-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 12px; }
.highlight-card { padding: 16px; border-radius: 10px; background: var(--el-bg-color-page); border: 1px solid var(--el-border-color-lighter); }
.highlight-card.dunk { border-left: 3px solid var(--el-color-danger); }
.highlight-card.three_pt { border-left: 3px solid var(--el-color-success); }
.highlight-card.block { border-left: 3px solid var(--el-color-warning); }
.highlight-card.steal { border-left: 3px solid var(--el-color-info); }
.highlight-card.game_winner { border-left: 3px solid gold; }
.card-header { display: flex; justify-content: space-between; margin-bottom: 8px; }
.type-badge { padding: 2px 8px; border-radius: 4px; background: var(--el-color-primary-light-8); color: var(--el-color-primary); font-size: 12px; font-weight: 600; }
.time-info { color: var(--el-text-color-secondary); font-size: 12px; }
.card-title { font-weight: 600; color: var(--el-text-color-primary); margin-bottom: 4px; }
.card-desc { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.4; }
.card-score { margin-top: 8px; font-weight: 600; color: var(--el-text-color-primary); font-family: monospace; }
.empty-state { text-align: center; padding: 40px; color: var(--el-text-color-secondary); }
</style>
