<template>
  <div class="replay-timeline">
    <div class="timeline-filters">
      <select v-model="selectedPeriod" class="filter-select">
        <option :value="undefined">全部节次</option>
        <option v-for="p in 4" :key="p" :value="p">第{{ p }}节</option>
      </select>
    </div>
    <div class="timeline-events" v-if="events.length">
      <div v-for="event in filteredEvents" :key="event.id"
           class="event-card"
           :class="{ 'scoring-event': event.isScoringPlay, 'selected': selectedEvent?.id === event.id }"
           @click="$emit('select', event)">
        <div class="event-meta">
          <span class="period-badge">Q{{ event.period }}</span>
          <span class="game-clock">{{ event.gameClock }}</span>
          <span class="score-display">{{ event.homeScore }}-{{ event.awayScore }}</span>
        </div>
        <div class="event-description">{{ event.description }}</div>
        <div class="event-tags">
          <span v-if="event.isScoringPlay" class="tag scoring">得分</span>
          <span v-if="event.isHighlightCandidate" class="tag highlight">精彩</span>
          <span class="tag source">{{ event.source }}</span>
        </div>
      </div>
    </div>
    <div v-else class="empty-state">暂无比赛事件数据</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { TimelineEvent } from '@/api/types'

const props = defineProps<{ events: TimelineEvent[], selectedEvent?: TimelineEvent | null }>()
defineEmits<{ select: [event: TimelineEvent] }>()

const selectedPeriod = ref<number | undefined>(undefined)

const filteredEvents = computed(() => {
  if (!selectedPeriod.value) return props.events
  return props.events.filter(e => e.period === selectedPeriod.value)
})
</script>

<style scoped>
.replay-timeline { display: flex; flex-direction: column; gap: 12px; }
.timeline-filters { display: flex; gap: 8px; margin-bottom: 8px; }
.filter-select { padding: 6px 12px; border-radius: 6px; background: var(--el-bg-color); border: 1px solid var(--el-border-color); color: var(--el-text-color-primary); }
.timeline-events { display: flex; flex-direction: column; gap: 8px; max-height: 600px; overflow-y: auto; }
.event-card { padding: 12px; border-radius: 8px; background: var(--el-bg-color-page); border: 1px solid var(--el-border-color-lighter); cursor: pointer; transition: all 0.2s; }
.event-card:hover { border-color: var(--el-color-primary); }
.event-card.selected { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.event-card.scoring-event { border-left: 3px solid var(--el-color-success); }
.event-meta { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; }
.period-badge { padding: 2px 8px; border-radius: 4px; background: var(--el-color-primary-light-8); color: var(--el-color-primary); font-size: 12px; font-weight: 600; }
.game-clock { color: var(--el-text-color-secondary); font-size: 13px; font-family: monospace; }
.score-display { font-weight: 600; color: var(--el-text-color-primary); }
.event-description { color: var(--el-text-color-primary); line-height: 1.5; }
.event-tags { display: flex; gap: 6px; margin-top: 8px; }
.tag { padding: 2px 8px; border-radius: 4px; font-size: 11px; }
.tag.scoring { background: var(--el-color-success-light-8); color: var(--el-color-success); }
.tag.highlight { background: var(--el-color-warning-light-8); color: var(--el-color-warning); }
.tag.source { background: var(--el-info-light-8); color: var(--el-info); }
.empty-state { text-align: center; padding: 40px; color: var(--el-text-color-secondary); }
</style>
