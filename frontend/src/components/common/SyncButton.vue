<template>
  <button
    class="sync-button"
    :class="{ 'is-syncing': syncing, 'is-compact': compact }"
    :disabled="syncing || disabled"
    @click="handleSync"
  >
    <span class="sync-icon" :class="{ 'spinning': syncing }">⟳</span>
    <span v-if="!compact" class="sync-text">
      {{ syncing ? '同步中...' : label }}
    </span>
  </button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

interface Props {
  /** 同步模块名称，对应后端接口 */
  module: string
  /** 按钮文本 */
  label?: string
  /** 是否紧凑模式（只显示图标） */
  compact?: boolean
  /** 是否禁用 */
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  label: '同步数据',
  compact: false,
  disabled: false
})

const emit = defineEmits<{
  (e: 'sync-start'): void
  (e: 'sync-success', message: string): void
  (e: 'sync-error', message: string): void
}>()

const syncing = ref(false)

async function handleSync() {
  if (syncing.value) return

  syncing.value = true
  emit('sync-start')

  try {
    const { data } = await request.post(`/admin/sync/${props.module}`)

    if (data.status === 'success' || data.status === 'started') {
      ElMessage.success(data.message)
      emit('sync-success', data.message)
    } else {
      ElMessage.warning(data.message)
      emit('sync-error', data.message)
    }
  } catch (error: any) {
    const msg = error.response?.data?.message || '同步失败'
    ElMessage.error(msg)
    emit('sync-error', msg)
  } finally {
    syncing.value = false
  }
}
</script>

<style scoped>
.sync-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.sync-button:hover:not(:disabled) {
  background: var(--accent-dim);
  border-color: var(--accent);
  color: var(--accent);
}

.sync-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.sync-button.is-syncing {
  border-color: var(--accent);
  background: var(--accent-dim);
}

/* 紧凑模式 */
.sync-button.is-compact {
  padding: 6px 10px;
}

.sync-button.is-compact .sync-icon {
  font-size: 16px;
}

/* 同步图标 */
.sync-icon {
  display: inline-block;
  font-size: 14px;
  transition: transform 0.2s ease;
}

.sync-icon.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 文本 */
.sync-text {
  line-height: 1;
}
</style>
