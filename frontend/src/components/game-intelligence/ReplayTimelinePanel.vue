<script setup lang="ts">
import { ref, shallowRef, computed, watch, nextTick, onMounted, onUnmounted, type ComponentPublicInstance } from 'vue'
import type { TimelineEvent } from '@/api/types'
import { getTimelineDescription } from '@/utils/replayText'

const props = defineProps<{
  events: TimelineEvent[]
  selectedEvent: TimelineEvent | null
}>()

const emit = defineEmits<{
  select: [event: TimelineEvent]
}>()

// --- Filters ---
const periodFilter = ref<number | 'all'>('all')
const typeFilter = ref<string>('all')
const searchQuery = ref('')
const rowRefs = new Map<number, HTMLElement>()
const timelineScrollRef = ref<HTMLElement | null>(null)
const scrollTop = ref(0)
const viewportHeight = ref(600)
const rowHeights = shallowRef(new Map<number, number>())
const rowIds = new WeakMap<HTMLElement, number>()
let resizeObserver: ResizeObserver | null = null
const ESTIMATED_ROW_HEIGHT = 128
const OVERSCAN = 6
const virtual = computed(() => filteredEvents.value.length > 100)
const offsets = computed(() => {
  const result = [0]
  for (const event of filteredEvents.value) {
    result.push(result[result.length - 1] + (rowHeights.value.get(event.id) ?? ESTIMATED_ROW_HEIGHT))
  }
  return result
})
function rowAt(position: number): number {
  let low = 0, high = filteredEvents.value.length
  while (low < high) {
    const middle = (low + high) >>> 1
    if (offsets.value[middle + 1] <= position) low = middle + 1
    else high = middle
  }
  return low
}
const windowRange = computed(() => {
  if (!virtual.value) return { start: 0, end: filteredEvents.value.length }
  return {
    start: Math.max(0, rowAt(scrollTop.value) - OVERSCAN),
    end: Math.min(filteredEvents.value.length, rowAt(scrollTop.value + viewportHeight.value) + OVERSCAN + 1),
  }
})
const visibleEvents = computed(() => filteredEvents.value.slice(windowRange.value.start, windowRange.value.end))
const beforeHeight = computed(() => offsets.value[windowRange.value.start])
const afterHeight = computed(() => offsets.value[offsets.value.length - 1] - offsets.value[windowRange.value.end])

function onTimelineScroll() {
  const scroller = timelineScrollRef.value
  if (scroller) {
    scrollTop.value = scroller.scrollTop
    viewportHeight.value = scroller.clientHeight || 600
  }
}

onMounted(() => {
  if (typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(entries => {
    const updated = new Map(rowHeights.value)
    let changed = false
    for (const entry of entries) {
      if (entry.target === timelineScrollRef.value) { onTimelineScroll(); continue }
      const id = rowIds.get(entry.target as HTMLElement)
      const height = Math.ceil(entry.target.getBoundingClientRect().height)
      if (id != null && height > 0 && updated.get(id) !== height) {
        updated.set(id, height); changed = true
      }
    }
    if (changed) rowHeights.value = updated
  })
  if (timelineScrollRef.value) resizeObserver.observe(timelineScrollRef.value)
  for (const row of rowRefs.values()) resizeObserver.observe(row)
  onTimelineScroll()
})
onUnmounted(() => resizeObserver?.disconnect())

const periods = [
  { label: '全部', value: 'all' as const },
  { label: 'Q1', value: 1 },
  { label: 'Q2', value: 2 },
  { label: 'Q3', value: 3 },
  { label: 'Q4', value: 4 },
  { label: 'OT', value: 5 },
]

const typeOptions = [
  { label: '全部', value: 'all' },
  { label: '得分', value: 'scoring' },
  { label: '关键', value: 'highlight' },
  { label: '犯规', value: 'foul' },
  { label: '暂停', value: 'timeout' },
  { label: '失误', value: 'turnover' },
]

function isScoringEvent(event: TimelineEvent): boolean {
  if (event.isScoringPlay === true) return true
  if (event.isScoringPlay === false) return false
  const text = `${event.eventType || ''} ${event.actionType || ''} ${event.description || ''} ${event.descriptionEn || ''}`.toLowerCase()
  const missed = text.includes('miss') || text.includes('不中') || text.includes('未命中')
  const scoringKeyword =
    text.includes('made') || text.includes('makes') || text.includes('命中') ||
    text.includes('free throw') || text.includes('freethrow') || text.includes('罚球') ||
    text.includes('2pt') || text.includes('3pt') || text.includes('三分') ||
    text.includes('扣篮') || text.includes('上篮') || text.includes('dunk') ||
    text.includes('layup') || text.includes('jump shot') || text.includes('tip')
  return scoringKeyword && !missed
}

function mapEventType(event: TimelineEvent): string {
  const text = `${event.eventType || ''} ${event.actionType || ''} ${event.description || ''}`.toLowerCase()
  if (isScoringEvent(event)) return 'scoring'
  if (text.includes('turnover') || text.includes('失误')) return 'turnover'
  if (text.includes('timeout') || text.includes('暂停')) return 'timeout'
  if (text.includes('foul') || text.includes('犯规')) return 'foul'
  if (text.includes('block') || text.includes('盖帽')) return 'block'
  if (text.includes('steal') || text.includes('抢断')) return 'steal'
  if (text.includes('rebound') || text.includes('篮板')) return 'rebound'
  return 'other'
}

function matchTypeFilter(event: TimelineEvent): boolean {
  if (typeFilter.value === 'all') return true
  if (typeFilter.value === 'highlight') return event.isHighlightCandidate === true
  return mapEventType(event) === typeFilter.value
}

const filteredEvents = computed(() => {
  let result = props.events

  if (periodFilter.value !== 'all') {
    result = result.filter(e => e.period === periodFilter.value)
  }

  result = result.filter(matchTypeFilter)

  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    result = result.filter(e =>
      (e.playerName || '').toLowerCase().includes(q) ||
      (e.description || '').toLowerCase().includes(q) ||
      ((e as any).descriptionEn || '').toLowerCase().includes(q) ||
      ((e as any).descriptionCn || '').toLowerCase().includes(q) ||
      (e.teamName || '').toLowerCase().includes(q)
    )
  }

  return result
})

// --- Event type color mapping ---
const TYPE_COLORS: Record<string, string> = {
  scoring: '#10B981',
  turnover: '#EF4444',
  timeout: '#F59E0B',
  foul: '#F59E0B',
  substitution: '#3B82F6',
  block: '#8B5CF6',
  steal: '#06B6D4',
  other: '#64748B',
}

function typeColor(event: TimelineEvent): string {
  return TYPE_COLORS[mapEventType(event)] || TYPE_COLORS.other
}

function isScoring(event: TimelineEvent): boolean {
  return mapEventType(event) === 'scoring'
}

function periodLabel(period: number): string {
  if (period <= 4) return `Q${period}`
  return `OT${period - 4}`
}

function isSelected(event: TimelineEvent): boolean {
  return props.selectedEvent?.id === event.id
}

function onSelect(event: TimelineEvent) {
  emit('select', event)
}

function selectAdjacent(event: TimelineEvent, direction: number) {
  const index = filteredEvents.value.findIndex(item => item.id === event.id)
  const target = filteredEvents.value[Math.max(0, Math.min(filteredEvents.value.length - 1, index + direction))]
  if (target) onSelect(target)
}

function hasScore(event: TimelineEvent): boolean {
  return event.homeScore !== null && event.homeScore !== undefined
    && event.awayScore !== null && event.awayScore !== undefined
}

function setRowRef(event: TimelineEvent, el: Element | ComponentPublicInstance | null) {
  const target = el instanceof HTMLElement
    ? el
    : el && !(el instanceof Element) && el.$el instanceof HTMLElement
      ? el.$el
      : null
  const previous = rowRefs.get(event.id)
  if (previous && previous !== target) resizeObserver?.unobserve(previous)
  if (target) {
    rowRefs.set(event.id, target)
    rowIds.set(target, event.id)
    resizeObserver?.observe(target)
  } else {
    rowRefs.delete(event.id)
  }
}

function centerRowInScroller(id: number) {
  const scroller = timelineScrollRef.value
  const row = rowRefs.get(id)
  if (!scroller || !row) return

  // Both rectangles use viewport coordinates; offsetTop may belong to an outer positioned ancestor.
  const containerRect = scroller.getBoundingClientRect()
  const rowRect = row.getBoundingClientRect()
  const targetTop = scroller.scrollTop + rowRect.top - containerRect.top - scroller.clientTop
    - (scroller.clientHeight - rowRect.height) / 2
  const maxTop = Math.max(0, scroller.scrollHeight - scroller.clientHeight)
  scroller.scrollTop = Math.min(maxTop, Math.max(0, targetTop))
  onTimelineScroll()
}

watch(filteredEvents, events => {
  const retained = new Set(events.map(event => event.id))
  rowHeights.value = new Map([...rowHeights.value].filter(([id]) => retained.has(id)))
  if (timelineScrollRef.value) timelineScrollRef.value.scrollTop = 0
  scrollTop.value = 0
})

watch(
  () => [props.selectedEvent?.id, props.events] as const,
  async ([id]) => {
    if (id == null) return
    // A distant selection removes the focused row while replacing the render window.
    const moveFocus = !!timelineScrollRef.value?.contains(document.activeElement)
      && document.activeElement?.classList.contains('timeline-row')
    const visible = filteredEvents.value.some(event => event.id === id)
    if (!visible) {
      periodFilter.value = 'all'
      typeFilter.value = 'all'
      searchQuery.value = ''
    }
    await nextTick()
    if (props.selectedEvent?.id !== id) return
    if (virtual.value) {
      const index = filteredEvents.value.findIndex(event => event.id === id)
      const scroller = timelineScrollRef.value
      if (index < 0 || !scroller) return
      // Render an offscreen selection first, then refine with the real row rectangle.
      scroller.scrollTop = Math.max(0, offsets.value[index] - scroller.clientHeight / 2
        + (rowHeights.value.get(id) ?? ESTIMATED_ROW_HEIGHT) / 2)
      onTimelineScroll()
      await nextTick()
      if (props.selectedEvent?.id !== id) return
    }
    centerRowInScroller(id)
    if (moveFocus) rowRefs.get(id)?.focus({ preventScroll: true })
  },
  { immediate: true, flush: 'post' }
)
</script>

<template>
  <div class="replay-timeline-panel">
    <!-- Filter bar -->
    <div class="filter-bar">
      <div class="period-filters">
        <button
          v-for="p in periods"
          :key="p.value"
          class="filter-btn"
          :class="{ active: periodFilter === p.value }"
          @click="periodFilter = p.value"
        >
          {{ p.label }}
        </button>
      </div>

      <div class="type-filters">
        <button
          v-for="t in typeOptions"
          :key="t.value"
          class="filter-btn"
          :class="{ active: typeFilter === t.value }"
          @click="typeFilter = t.value"
        >
          {{ t.label }}
        </button>
      </div>

      <div class="search-wrapper">
        <svg class="search-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8" /><path d="m21 21-4.35-4.35" />
        </svg>
        <input
          v-model="searchQuery"
          type="text"
          class="search-input"
          placeholder="搜索球员或描述..."
        />
      </div>
    </div>

    <!-- Timeline -->
    <div ref="timelineScrollRef" class="timeline-scroll" @scroll.passive="onTimelineScroll">
      <div
        v-if="filteredEvents.length === 0"
        class="empty-state"
      >
        <span class="empty-icon">&#128203;</span>
        <span>没有匹配的事件</span>
      </div>

      <div v-if="beforeHeight" aria-hidden="true" :style="{ height: `${beforeHeight}px` }" />
      <div
        v-for="event in visibleEvents"
        :key="event.id"
        :ref="(el) => setRowRef(event, el)"
        class="timeline-row"
        :class="{ selected: isSelected(event) }"
        role="button"
        tabindex="0"
        :aria-pressed="isSelected(event)"
        :data-event-id="event.id"
        :aria-label="`${periodLabel(event.period)} ${event.gameClock} ${event.playerName || '事件'} ${getTimelineDescription(event)}`"
        @click="onSelect(event)"
        @keydown.enter.prevent="onSelect(event)"
        @keydown.space.prevent="onSelect(event)"
        @keydown.down.prevent="selectAdjacent(event, 1)"
        @keydown.up.prevent="selectAdjacent(event, -1)"
        @keydown.home.prevent="selectAdjacent(event, -filteredEvents.length)"
        @keydown.end.prevent="selectAdjacent(event, filteredEvents.length)"
      >
        <!-- Timeline line + dot -->
        <div class="timeline-track">
          <div class="timeline-dot" :style="{ background: typeColor(event), boxShadow: isSelected(event) ? `0 0 10px 3px ${typeColor(event)}` : 'none' }" />
          <div class="timeline-line" />
        </div>

        <!-- Event card -->
        <div
          class="event-card"
          :class="{ 'scoring-border': isScoring(event) }"
        >
          <div class="card-header">
            <span class="period-badge" :style="{ background: typeColor(event) }">
              {{ periodLabel(event.period) }}
            </span>
            <span class="game-clock">{{ event.gameClock }}</span>
            <span v-if="hasScore(event)" class="score-display">
              <span class="home-score">{{ event.homeScore }}</span>
              <span class="score-sep">-</span>
              <span class="away-score">{{ event.awayScore }}</span>
            </span>
          </div>

          <div class="card-body">
            <span class="player-name">{{ event.playerName }}</span>
            <span v-if="event.teamName" class="team-tag">{{ event.teamName }}</span>
          </div>

          <div class="card-desc">{{ getTimelineDescription(event) }}</div>
        </div>
      </div>
      <div v-if="afterHeight" aria-hidden="true" :style="{ height: `${afterHeight}px` }" />
    </div>
  </div>
</template>

<style scoped>
.replay-timeline-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
}

/* Filter bar */
.filter-bar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 16px;
  background: var(--gi-surface-strong, #111827);
  border: 1px solid var(--gi-border);
  border-radius: 14px;
}

.period-filters,
.type-filters {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.filter-btn {
  padding: 4px 12px;
  border: 1px solid var(--gi-border, rgba(255,255,255,0.08));
  border-radius: 8px;
  background: transparent;
  color: var(--gi-muted, #94A3B8);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.filter-btn:hover {
  color: var(--gi-text, #F8FAFC);
  border-color: rgba(255,255,255,0.2);
}

.filter-btn.active {
  background: var(--gi-glow, rgba(59,130,246,0.15));
  color: var(--gi-text, #F8FAFC);
  border-color: var(--gi-home, #3B82F6);
}

.search-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 10px;
  color: var(--gi-muted, #94A3B8);
  pointer-events: none;
}

.search-input {
  width: 100%;
  padding: 6px 10px 6px 32px;
  background: rgba(0,0,0,0.2);
  border: 1px solid var(--gi-border, rgba(255,255,255,0.08));
  border-radius: 8px;
  color: var(--gi-text, #F8FAFC);
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.search-input::placeholder {
  color: var(--gi-muted, #94A3B8);
  opacity: 0.7;
}

.search-input:focus {
  border-color: var(--gi-home, #3B82F6);
}

/* Timeline scroll area */
.timeline-scroll {
  max-height: 600px;
  overflow-y: auto;
  overflow-anchor: none;
  padding: 4px 0;
  scrollbar-width: thin;
  scrollbar-color: rgba(255,255,255,0.1) transparent;
}

.timeline-scroll::-webkit-scrollbar {
  width: 6px;
}

.timeline-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.timeline-scroll::-webkit-scrollbar-thumb {
  background: rgba(255,255,255,0.1);
  border-radius: 3px;
}

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 0;
  color: var(--gi-muted, #94A3B8);
  font-size: 14px;
}

.empty-icon {
  font-size: 28px;
  opacity: 0.5;
}

/* Timeline row */
.timeline-row {
  display: flex;
  gap: 12px;
  cursor: pointer;
  padding: 2px 0;
  transition: transform 0.15s ease;
}

.timeline-row:hover {
  transform: translateY(-1px);
}

.timeline-row:focus-visible {
  outline: 2px solid var(--gi-home, #3B82F6);
  outline-offset: 3px;
  border-radius: 14px;
}

.timeline-row:hover .event-card {
  border-color: rgba(255,255,255,0.18);
}

.timeline-row.selected .event-card {
  background: var(--gi-glow, rgba(59,130,246,0.15));
  border-color: var(--gi-home, #3B82F6);
}

/* Timeline track (left column) */
.timeline-track {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 20px;
  flex-shrink: 0;
  position: relative;
}

.timeline-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
  z-index: 2;
  transition: box-shadow 0.3s ease;
}

.timeline-row.selected .timeline-dot {
  box-shadow: 0 0 10px 3px var(--gi-home, rgba(59,130,246,0.4));
}

.timeline-line {
  width: 2px;
  flex: 1;
  background: var(--gi-border, rgba(255,255,255,0.08));
  min-height: 16px;
}

.timeline-row:last-child .timeline-line {
  display: none;
}

/* Event card */
.event-card {
  flex: 1;
  background: var(--gi-surface-strong, #111827);
  border: 1px solid var(--gi-border, rgba(255,255,255,0.08));
  border-radius: 14px;
  padding: 16px;
  transition: border-color 0.2s ease, background 0.2s ease;
  position: relative;
  overflow: hidden;
}

.event-card.scoring-border {
  border-left: 3px solid #10B981;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.period-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 0.02em;
}

.game-clock {
  font-size: 12px;
  color: var(--gi-muted, #94A3B8);
  font-variant-numeric: tabular-nums;
}

.score-display {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.home-score {
  color: var(--gi-home, #3B82F6);
}

.away-score {
  color: var(--gi-away, #22D3EE);
}

.score-sep {
  color: var(--gi-muted, #94A3B8);
  font-weight: 400;
}

.card-body {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.player-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--gi-text, #F8FAFC);
}

.team-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(255,255,255,0.06);
  color: var(--gi-muted, #94A3B8);
}

.card-desc {
  font-size: 13px;
  color: var(--gi-muted, #94A3B8);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
