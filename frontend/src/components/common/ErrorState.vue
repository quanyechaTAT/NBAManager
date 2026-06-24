<template>
  <div class="error-state">
    <div class="error-state__icon">
      <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="10" />
        <line x1="12" y1="8" x2="12" y2="12" />
        <line x1="12" y1="16" x2="12.01" y2="16" />
      </svg>
    </div>
    <h3 class="error-state__title">出错了</h3>
    <p class="error-state__message">{{ message }}</p>
    <div v-if="$slots.actions || showRetry" class="error-state__actions">
      <slot name="actions">
        <button
          v-if="showRetry"
          class="error-state__retry-btn"
          @click="$emit('retry')"
        >
          {{ retryText }}
        </button>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  /** 错误消息 */
  message: string
  /** 重试按钮文本 */
  retryText?: string
  /** 是否显示重试按钮 */
  showRetry?: boolean
}

withDefaults(defineProps<Props>(), {
  retryText: '重试',
  showRetry: true
})

defineEmits<{
  (e: 'retry'): void
}>()
</script>

<style scoped>
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  text-align: center;
}

.error-state__icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(239, 68, 68, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  color: var(--danger);
}

.error-state__icon svg {
  width: 32px;
  height: 32px;
}

.error-state__title {
  font-family: var(--font-heading);
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.error-state__message {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0 0 20px;
  max-width: 400px;
  line-height: 1.5;
}

.error-state__actions {
  display: flex;
  gap: 8px;
}

.error-state__retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: var(--danger);
  border: none;
  border-radius: var(--radius-md);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
}

.error-state__retry-btn:hover {
  background: #DC2626;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px var(--danger-glow);
}

.error-state__retry-btn:active {
  transform: translateY(0);
}
</style>
