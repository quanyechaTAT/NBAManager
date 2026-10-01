<template>
  <section class="replay-availability" :data-state="state" role="status" aria-live="polite">
    <div class="status-copy">
      <span class="status-kicker">{{ statusKicker }}</span>
      <h3>{{ title }}</h3>
      <p>{{ description }}</p>
      <div class="status-meta">
        <span v-if="quality">质量：{{ quality.level }}</span>
        <span v-if="quality?.eventCount">事件：{{ quality.eventCount }}</span>
        <span v-if="quality?.sourceLabel">{{ quality.sourceLabel }}</span>
        <span v-if="operationId">任务：{{ operationId }}</span>
        <span v-if="retryAfterSeconds">约 {{ retryAfterSeconds }} 秒后刷新</span>
      </div>
    </div>
    <div class="status-actions">
      <button v-if="showPrepare" type="button" :disabled="busy" @click="$emit('prepare')">
        {{ busy ? '准备中' : '准备回放' }}
      </button>
      <button v-if="showRetry" type="button" :disabled="busy" @click="$emit('retry')">
        {{ busy ? '重试中' : '重试' }}
      </button>
      <button type="button" :disabled="busy" @click="$emit('refresh')">刷新</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ReplayError, ReplayQuality } from '@/api/types'
import type { GameReplayUiState } from '@/composables/useGameReplay'

const props = defineProps<{
  state: GameReplayUiState
  quality: ReplayQuality | null
  error: ReplayError | { code: string; message: string; retryable: boolean } | null
  operationId: string | null
  retryAfterSeconds: number | null
  busy: boolean
}>()

defineEmits<{
  prepare: []
  retry: []
  refresh: []
}>()

const statusKicker = computed(() => {
  if (props.quality?.level === 'SUMMARY_ONLY') return '节末摘要'
  if (props.quality?.level === 'PARTIAL') return '部分回放'
  if (props.quality?.level === 'FULL') return '完整回放'
  return '回放状态'
})

const title = computed(() => {
  switch (props.state) {
    case 'loading': return '正在读取比赛回放'
    case 'preparing':
    case 'polling': return '正在准备回放数据'
    case 'not-started': return '比赛尚未开始'
    case 'ready': return '回放已就绪'
    case 'partial': return props.quality?.level === 'SUMMARY_ONLY' ? '仅有节末比分摘要' : '部分回放可用'
    case 'unavailable': return '回放暂不可用'
    case 'error': return '回放加载失败'
    case 'retrying': return '正在重试'
    default: return '等待加载回放'
  }
})

const description = computed(() => {
  if (props.error?.message) return props.error.message
  if (props.quality?.warnings?.length) return props.quality.warnings[0]
  switch (props.state) {
    case 'ready': return '事件、走势、关键回合和 AI 引用均来自同一份事实快照。'
    case 'partial': return props.quality?.level === 'SUMMARY_ONLY'
      ? '当前来源只提供节末比分，不会伪装成逐回合回放。'
      : '当前来源缺少部分事件，页面仅展示已验证事实。'
    case 'preparing':
    case 'polling': return '系统正在后台获取并校验逐回合数据。'
    case 'not-started': return '开赛前不会请求或生成回放。'
    case 'unavailable': return '可以稍后重试准备回放。'
    default: return '回放状态会随比赛事实快照自动更新。'
  }
})

const showPrepare = computed(() => props.state === 'unavailable' && props.error?.retryable !== false)
const showRetry = computed(() => (props.state === 'error' || props.state === 'retrying') && props.error?.retryable !== false)
</script>

<style scoped>
.replay-availability {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  margin-bottom: 16px;
  border: 1px solid var(--border-light);
  border-radius: 12px;
  background: var(--bg-card);
}

.status-copy {
  min-width: 0;
}

.status-kicker {
  display: inline-flex;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--accent);
  font-weight: 700;
}

h3 {
  margin: 0 0 4px;
  font-size: 16px;
  color: var(--text-primary);
}

p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.status-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 12px;
}

.status-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

button {
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  color: var(--text-primary);
  background: var(--bg-hover);
  cursor: pointer;
}

button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

button:hover:not(:disabled) {
  border-color: var(--accent);
}

@media (max-width: 720px) {
  .replay-availability {
    align-items: stretch;
    flex-direction: column;
  }

  .status-actions {
    justify-content: flex-start;
  }
}
</style>
