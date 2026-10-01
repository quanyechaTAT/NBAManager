<template>
  <div class="app-empty" :class="`app-empty--${size}`">
    <div class="app-empty__illustration" aria-hidden="true">
      <svg class="app-empty__svg" viewBox="0 0 120 120" fill="none">
        <!-- 柔和背景光圈 -->
        <circle cx="60" cy="60" r="48" class="app-empty__ambient" />
        <!-- 半场球场弧线与三分线 -->
        <path d="M24 95 Q60 62 96 95" class="app-empty__court-line" />
        <path d="M42 95 V68 H78 V95" class="app-empty__key" />
        <circle cx="60" cy="68" r="16" class="app-empty__court-circle" />
        <!-- 悬浮的篮球图标 -->
        <g class="app-empty__ball">
          <circle cx="60" cy="46" r="20" class="app-empty__ball-base" />
          <path d="M60 26 V66" class="app-empty__ball-seam" />
          <path d="M40 46 H80" class="app-empty__ball-seam" />
          <path d="M45 32 Q60 46 45 60" class="app-empty__ball-seam" />
          <path d="M75 32 Q60 46 75 60" class="app-empty__ball-seam" />
        </g>
      </svg>
    </div>
    <p v-if="title" class="app-empty__title">{{ title }}</p>
    <p class="app-empty__description">{{ displayDescription }}</p>
    <div v-if="$slots.actions" class="app-empty__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  title?: string
  description?: string
  size?: 'sm' | 'md' | 'lg'
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  description: '',
  size: 'md',
})

const displayDescription = computed(() => {
  if (props.description) return props.description
  return '球场暂时归于平静，新的对决正蓄势待发'
})
</script>

<style scoped>
.app-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  position: relative;
  width: 100%;
}

.app-empty--sm {
  padding: 24px 16px;
}

.app-empty--md {
  padding: 44px 24px;
}

.app-empty--lg {
  padding: 64px 24px;
}

.app-empty__illustration {
  margin-bottom: 16px;
  position: relative;
}

.app-empty--sm .app-empty__illustration {
  transform: scale(0.75);
  margin-bottom: 8px;
}

.app-empty--lg .app-empty__illustration {
  transform: scale(1.15);
  margin-bottom: 24px;
}

.app-empty__svg {
  width: 100px;
  height: 100px;
}

.app-empty__ambient {
  fill: rgba(34, 211, 238, 0.05);
  stroke: rgba(34, 211, 238, 0.12);
  stroke-width: 1;
}

[data-theme="light"] .app-empty__ambient {
  fill: rgba(8, 145, 178, 0.04);
  stroke: rgba(8, 145, 178, 0.12);
}

.app-empty__court-line,
.app-empty__key,
.app-empty__court-circle {
  stroke: var(--border-medium);
  stroke-width: 1.2;
  stroke-dasharray: 3 3;
  opacity: 0.6;
}

.app-empty__ball-base {
  fill: var(--bg-elevated);
  stroke: var(--accent);
  stroke-width: 1.5;
  transition: transform 0.3s ease;
}

.app-empty__ball-seam {
  stroke: var(--accent);
  stroke-width: 1.2;
  opacity: 0.7;
}

.app-empty:hover .app-empty__ball {
  transform: translateY(-2px);
  transition: transform 0.3s var(--ease-spring);
}

.app-empty__title {
  font-family: var(--font-heading);
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 6px;
  letter-spacing: -0.2px;
}

.app-empty--sm .app-empty__title {
  font-size: 14px;
}

.app-empty--md .app-empty__title {
  font-size: 16px;
}

.app-empty--lg .app-empty__title {
  font-size: 18px;
}

.app-empty__description {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
  max-width: 360px;
  line-height: 1.55;
}

.app-empty--sm .app-empty__description {
  font-size: 13px;
}

.app-empty__actions {
  margin-top: 18px;
  display: flex;
  gap: 10px;
}
</style>
