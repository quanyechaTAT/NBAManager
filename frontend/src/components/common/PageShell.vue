<template>
  <div class="page" :class="{ 'page--animated': animated }">
    <div class="page-inner" :style="innerStyle">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  /** 页面内容最大宽度 */
  maxWidth?: string
  /** 是否启用入场动画 */
  animated?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  maxWidth: '1400px',
  animated: true
})

const innerStyle = computed(() => ({
  maxWidth: props.maxWidth,
  margin: '0 auto',
  padding: '0 24px'
}))
</script>

<style scoped>
.page {
  position: relative;
  min-height: calc(100vh - 60px);
}

.page--animated {
  animation: pageFadeIn 0.4s ease-out forwards;
}

@keyframes pageFadeIn {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.page-inner {
  position: relative;
  padding-top: 24px;
  padding-bottom: 32px;
}

/* 呼吸光效 */
.page-inner::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 300px;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent-glow), transparent);
  opacity: 0;
  animation: breatheLight 4s ease-in-out infinite;
  pointer-events: none;
  z-index: 10;
}

@keyframes breatheLight {
  0%, 100% { opacity: 0; width: 200px; }
  50% { opacity: 0.6; width: 400px; }
}

/* 角落装饰光点 */
.page-inner::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 100px;
  height: 100px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.1) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
  animation: cornerGlow 6s ease-in-out infinite alternate;
}

@keyframes cornerGlow {
  0% { opacity: 0.3; transform: scale(0.8); }
  100% { opacity: 0.6; transform: scale(1.2); }
}
</style>
