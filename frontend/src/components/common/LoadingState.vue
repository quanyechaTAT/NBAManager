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
  border: 3px solid var(--border-light);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: loadingRotate 1s linear infinite;
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
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
}
</style>
