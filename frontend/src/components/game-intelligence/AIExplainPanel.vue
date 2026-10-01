<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { TimelineEvent, MomentumSnapshot, EventExplain } from '@/api/types'
import { explainEvent } from '@/api/aiReplay'
import { getTimelineDescription, displayAiText } from '@/utils/replayText'

const props = defineProps<{
  gameId: string
  selectedEvent: TimelineEvent | null
  momentumData: MomentumSnapshot[]
}>()

// Cache: eventId -> EventExplain
const cache = ref<Map<number, EventExplain>>(new Map())
const loading = ref(false)
const explainError = ref<string | null>(null)
let requestGeneration = 0
let controller: AbortController | null = null

const currentExplain = computed<EventExplain | null>(() => {
  if (!props.selectedEvent) return null
  return cache.value.get(props.selectedEvent.id) ?? null
})

const canGenerate = computed(() => Boolean(props.selectedEvent && props.gameId && !currentExplain.value && !loading.value))

function isAbortError(error: unknown): boolean {
  if (error instanceof DOMException && error.name === 'AbortError') return true
  if (!error || typeof error !== 'object') return false
  const candidate = error as { code?: unknown; name?: unknown }
  return candidate.code === 'ERR_CANCELED' || candidate.name === 'CanceledError' || candidate.name === 'AbortError'
}

function explainErrorMessage(error: unknown): string {
  if (error && typeof error === 'object') {
    const candidate = error as {
      response?: { status?: number; data?: { message?: unknown } }
      message?: unknown
    }
    if (candidate.response?.status === 401) return '请登录后生成 AI 解说'
    if (candidate.response?.status === 403) return '当前账号无权生成 AI 解说'
    if (typeof candidate.response?.data?.message === 'string') return candidate.response.data.message
    if (typeof candidate.message === 'string') return candidate.message
  }
  return 'AI 解说生成失败，请稍后重试'
}

watch(
  () => props.selectedEvent?.id,
  () => {
    requestGeneration += 1
    controller?.abort()
    controller = null
    loading.value = false
    explainError.value = null
  },
)

watch(
  () => props.gameId,
  () => {
    requestGeneration += 1
    controller?.abort()
    controller = null
    loading.value = false
    explainError.value = null
    cache.value = new Map()
  },
)

async function generateExplanation() {
  const event = props.selectedEvent
  const gameId = props.gameId
  if (!event || !gameId || currentExplain.value) return

  requestGeneration += 1
  const generation = requestGeneration
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  explainError.value = null

  try {
    const result = await explainEvent(gameId, event.id, controller.signal)
    if (generation !== requestGeneration || props.selectedEvent?.id !== event.id || props.gameId !== gameId) return
    cache.value.set(event.id, result)
  } catch (error) {
    if (generation !== requestGeneration || isAbortError(error)) return
    explainError.value = explainErrorMessage(error)
  } finally {
    if (generation === requestGeneration) {
      loading.value = false
      controller = null
    }
  }
}

// Linked events from momentum data
const prevEvent = computed<MomentumSnapshot | null>(() => {
  if (!props.selectedEvent) return null
  const idx = props.momentumData.findIndex(m => m.eventOrder === props.selectedEvent!.eventOrder)
  if (idx <= 0) return null
  return props.momentumData[idx - 1]
})

const nextEvent = computed<MomentumSnapshot | null>(() => {
  if (!props.selectedEvent) return null
  const idx = props.momentumData.findIndex(m => m.eventOrder === props.selectedEvent!.eventOrder)
  if (idx === -1 || idx >= props.momentumData.length - 1) return null
  return props.momentumData[idx + 1]
})

function periodLabel(period: number): string {
  if (period <= 4) return `Q${period}`
  return `OT${period - 4}`
}

function formatScore(home: number, away: number): string {
  return `${home} - ${away}`
}
</script>

<template>
  <div class="ai-explain-panel">
    <!-- No event selected -->
    <div v-if="!selectedEvent" class="empty-state">
      <svg class="empty-icon" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" opacity="0.4">
        <circle cx="12" cy="12" r="10" /><path d="M12 16v-4M12 8h.01" />
      </svg>
      <span class="empty-text">点击左侧时间线事件查看 AI 解说</span>
    </div>

    <!-- Event details + AI explanation -->
    <template v-else-if="selectedEvent">
      <!-- Event facts -->
      <div class="event-facts">
        <div class="facts-row">
          <span class="fact-badge" :style="{ background: 'var(--gi-home, #3B82F6)' }">
            {{ periodLabel(selectedEvent.period) }}
          </span>
          <span class="fact-clock">{{ selectedEvent.gameClock }}</span>
          <span class="fact-score">
            {{ formatScore(selectedEvent.homeScore, selectedEvent.awayScore) }}
          </span>
        </div>

        <div class="facts-player">{{ selectedEvent.playerName }}</div>
        <div v-if="selectedEvent.teamName" class="facts-team">{{ selectedEvent.teamName }}</div>
        <div class="facts-desc">{{ getTimelineDescription(selectedEvent) }}</div>
      </div>

      <div class="explain-divider" />

      <!-- AI Explanation -->
      <div class="ai-section">
        <div class="ai-header">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color: var(--gi-home, #3B82F6)">
            <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2Z" /><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" /><circle cx="12" cy="17" r=".5" />
          </svg>
          <span class="ai-label">AI 解说</span>
        </div>

        <p v-if="currentExplain?.explanation" class="ai-text">
          {{ displayAiText(currentExplain?.explanation) }}
        </p>
        <div v-else-if="loading" class="skeleton-wrapper" aria-live="polite">
          <div class="skeleton-line skeleton-title" />
          <div class="skeleton-line skeleton-desc" />
          <div class="skeleton-line skeleton-desc short" />
        </div>
        <div v-else class="ai-generate">
          <p class="ai-unavailable">基于当前事件事实生成解释。此操作会调用 AI，请按需触发。</p>
          <button
            data-test="generate-explain"
            class="generate-btn"
            type="button"
            :disabled="!canGenerate"
            @click="generateExplanation"
          >
            生成 AI 解说
          </button>
          <p v-if="explainError" class="ai-error" role="alert">{{ explainError }}</p>
        </div>

        <!-- Model info -->
        <div v-if="currentExplain?.modelProvider" class="model-info">
          {{ currentExplain.modelProvider }} / {{ currentExplain.modelName }}
        </div>
      </div>

      <!-- Linked events -->
      <div v-if="prevEvent || nextEvent" class="linked-events">
        <div class="linked-title">关联事件</div>

        <div v-if="prevEvent" class="linked-item prev">
          <span class="linked-label">前一事件</span>
          <span class="linked-time">{{ periodLabel(prevEvent.period) }} {{ prevEvent.gameClock }}</span>
          <span class="linked-score">{{ formatScore(prevEvent.homeScore, prevEvent.awayScore) }}</span>
        </div>

        <div v-if="nextEvent" class="linked-item next">
          <span class="linked-label">后一事件</span>
          <span class="linked-time">{{ periodLabel(nextEvent.period) }} {{ nextEvent.gameClock }}</span>
          <span class="linked-score">{{ formatScore(nextEvent.homeScore, nextEvent.awayScore) }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ai-explain-panel {
  background: var(--gi-surface-strong, #111827);
  border: 1px solid var(--gi-border, rgba(255,255,255,0.08));
  border-radius: 14px;
  padding: 20px;
  position: sticky;
  top: 16px;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 16px;
  text-align: center;
}

.empty-text {
  font-size: 13px;
  color: var(--gi-muted, #94A3B8);
  line-height: 1.5;
}

/* Skeleton loading */
.skeleton-wrapper {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.skeleton-line {
  border-radius: 6px;
  background: linear-gradient(90deg, rgba(255,255,255,0.04) 25%, rgba(255,255,255,0.08) 50%, rgba(255,255,255,0.04) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.skeleton-badge {
  width: 40px;
  height: 22px;
}

.skeleton-clock {
  width: 60px;
  height: 16px;
}

.skeleton-name {
  width: 140px;
  height: 18px;
}

.skeleton-desc {
  width: 100%;
  height: 14px;
}

.skeleton-desc.short {
  width: 65%;
}

.skeleton-divider {
  height: 1px;
  background: var(--gi-border, rgba(255,255,255,0.08));
  margin: 4px 0;
}

.skeleton-title {
  width: 80px;
  height: 16px;
}

/* Event facts */
.event-facts {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.facts-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fact-badge {
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

.fact-clock {
  font-size: 12px;
  color: var(--gi-muted, #94A3B8);
  font-variant-numeric: tabular-nums;
}

.fact-score {
  margin-left: auto;
  font-size: 13px;
  font-weight: 600;
  color: var(--gi-text, #F8FAFC);
  font-variant-numeric: tabular-nums;
}

.facts-player {
  font-size: 16px;
  font-weight: 700;
  color: var(--gi-text, #F8FAFC);
}

.facts-team {
  font-size: 12px;
  color: var(--gi-muted, #94A3B8);
}

.facts-desc {
  font-size: 13px;
  color: var(--gi-muted, #94A3B8);
  line-height: 1.5;
}

/* Divider */
.explain-divider {
  height: 1px;
  background: var(--gi-border, rgba(255,255,255,0.08));
  margin: 2px 0;
}

/* AI section */
.ai-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-header {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ai-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--gi-text, #F8FAFC);
}

.ai-text {
  font-size: 13px;
  line-height: 1.7;
  color: var(--gi-text, #F8FAFC);
  margin: 0;
  opacity: 0.9;
}

.ai-unavailable {
  font-size: 13px;
  color: var(--gi-muted, #94A3B8);
  font-style: italic;
  margin: 0;
}

.ai-generate {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.generate-btn {
  align-self: flex-start;
  border: 1px solid rgba(59, 130, 246, 0.35);
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.22), rgba(139, 92, 246, 0.14));
  color: var(--gi-text, #F8FAFC);
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  padding: 8px 12px;
  transition: transform 0.16s ease, border-color 0.16s ease, background 0.16s ease;
}

.generate-btn:hover:not(:disabled) {
  border-color: rgba(59, 130, 246, 0.7);
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.32), rgba(139, 92, 246, 0.22));
  transform: translateY(-1px);
}

.generate-btn:focus-visible {
  outline: 2px solid var(--gi-home, #3B82F6);
  outline-offset: 2px;
}

.generate-btn:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.ai-error {
  border: 1px solid rgba(239, 68, 68, 0.22);
  border-radius: 10px;
  background: rgba(239, 68, 68, 0.1);
  color: var(--gi-danger, #EF4444);
  font-size: 12px;
  line-height: 1.5;
  margin: 0;
  padding: 8px 10px;
}

.model-info {
  font-size: 11px;
  color: var(--gi-muted, #94A3B8);
  opacity: 0.6;
}

/* Linked events */
.linked-events {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.linked-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--gi-muted, #94A3B8);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.linked-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255,255,255,0.03);
  border: 1px solid var(--gi-border, rgba(255,255,255,0.06));
}

.linked-item.prev {
  border-left: 2px solid var(--gi-muted, #94A3B8);
}

.linked-item.next {
  border-left: 2px solid var(--gi-home, #3B82F6);
}

.linked-label {
  font-size: 11px;
  color: var(--gi-muted, #94A3B8);
  min-width: 48px;
}

.linked-time {
  font-size: 12px;
  color: var(--gi-text, #F8FAFC);
  font-variant-numeric: tabular-nums;
}

.linked-score {
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
  color: var(--gi-text, #F8FAFC);
  font-variant-numeric: tabular-nums;
}
</style>
