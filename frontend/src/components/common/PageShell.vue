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
  maxWidth: '1440px',
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
  min-height: calc(100vh - var(--nav-height, 60px));
}

.page--animated {
  animation: courtside-rise var(--duration-reveal, 0.45s) var(--ease-out-expo, ease) both;
}

.page-inner {
  position: relative;
  padding-top: 28px;
  padding-bottom: 40px;
  z-index: 1;
}

/* 顶部细橙光边 — Courtside signature */
.page-inner::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: min(420px, 56vw);
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(34, 211, 238, 0.7), transparent);
  opacity: 0.75;
  pointer-events: none;
  z-index: 2;
}

/* 角落轻氛围，避免霓虹过载 */
.page-inner::after {
  content: '';
  position: absolute;
  top: -40px;
  right: -20px;
  width: 180px;
  height: 180px;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.08) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

@media (max-width: 768px) {
  .page-inner {
    padding-top: 20px;
    padding-bottom: 28px;
    padding-left: 0;
    padding-right: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .page--animated {
    animation: none;
  }
}
</style>
