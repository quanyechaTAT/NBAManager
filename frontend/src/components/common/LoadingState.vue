<template>
  <div class="loading-state">
    <div class="loading-state__spinner">
      <div class="loading-state__circle"></div>
    </div>
    <p v-if="text" class="loading-state__text">{{ text }}</p>
  </div>
</template>

<script setup lang="ts">
interface Props {
  /** 加载提示文本 */
  text?: string
}

withDefaults(defineProps<Props>(), {
  text: '加载中...'
})
</script>

<style scoped>
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
}

.loading-state__spinner {
  width: 48px;
  height: 48px;
  position: relative;
  margin-bottom: 16px;
}

.loading-state__circle {
  width: 100%;
  height: 100%;
  border: 2.5px solid rgba(148, 163, 184, 0.15);
  border-top-color: var(--accent);
  border-right-color: rgba(34, 211, 238, 0.4);
  border-radius: 50%;
  animation: loadingRotate 0.9s cubic-bezier(0.55, 0.15, 0.45, 0.85) infinite;
  box-shadow: 0 0 16px -2px rgba(34, 211, 238, 0.25);
}

@keyframes loadingRotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.loading-state__text {
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.04em;
  color: var(--text-tertiary);
  margin: 0;
}
</style>
