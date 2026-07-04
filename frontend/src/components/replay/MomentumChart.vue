<template>
  <div class="momentum-chart" v-if="data.length">
    <h4 class="chart-title">比赛走势</h4>
    <svg :viewBox="`0 0 ${width} ${height}`" class="chart-svg">
      <!-- Zero line -->
      <line :x1="padding" :y1="centerY" :x2="width - padding" :y2="centerY"
            stroke="var(--el-border-color)" stroke-width="1" stroke-dasharray="4" />
      <!-- Home leading area -->
      <path :d="homeAreaPath" fill="var(--el-color-primary-light-7)" opacity="0.3" />
      <!-- Away leading area -->
      <path :d="awayAreaPath" fill="var(--el-color-danger-light-7)" opacity="0.3" />
      <!-- Line -->
      <path :d="linePath" fill="none" stroke="var(--el-color-primary)" stroke-width="2" />
      <!-- Data points -->
      <circle v-for="(point, i) in points" :key="i"
              :cx="point.x" :cy="point.y" r="3"
              fill="var(--el-color-primary)" stroke="white" stroke-width="1"
              @click="$emit('pointClick', data[i])" class="data-point" />
    </svg>
    <div class="chart-legend">
      <span class="legend-home">● 主队领先</span>
      <span class="legend-away">● 客队领先</span>
    </div>
  </div>
  <div v-else class="empty-state">暂无走势数据</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MomentumSnapshot } from '@/api/types'

const props = defineProps<{ data: MomentumSnapshot[] }>()
defineEmits<{ pointClick: [snapshot: MomentumSnapshot] }>()

const width = 600
const height = 200
const padding = 40
const centerY = height / 2

const maxMargin = computed(() => {
  if (!props.data.length) return 10
  return Math.max(10, ...props.data.map(d => Math.abs(d.scoreMargin)))
})

const points = computed(() => {
  if (!props.data.length) return []
  const xStep = (width - 2 * padding) / Math.max(1, props.data.length - 1)
  return props.data.map((d, i) => ({
    x: padding + i * xStep,
    y: centerY - (d.scoreMargin / maxMargin.value) * (centerY - padding)
  }))
})

const linePath = computed(() => {
  if (!points.value.length) return ''
  return points.value.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
})

const homeAreaPath = computed(() => {
  if (!points.value.length) return ''
  const line = points.value.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${Math.min(p.y, centerY)}`).join(' ')
  return `${line} L ${points.value[points.value.length - 1].x} ${centerY} L ${points.value[0].x} ${centerY} Z`
})

const awayAreaPath = computed(() => {
  if (!points.value.length) return ''
  const line = points.value.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${Math.max(p.y, centerY)}`).join(' ')
  return `${line} L ${points.value[points.value.length - 1].x} ${centerY} L ${points.value[0].x} ${centerY} Z`
})
</script>

<style scoped>
.momentum-chart { padding: 16px; }
.chart-title { margin-bottom: 12px; color: var(--el-text-color-primary); font-size: 16px; font-weight: 600; }
.chart-svg { width: 100%; height: auto; }
.data-point { cursor: pointer; }
.data-point:hover { r: 5; }
.chart-legend { display: flex; gap: 16px; justify-content: center; margin-top: 8px; font-size: 12px; color: var(--el-text-color-secondary); }
.legend-home { color: var(--el-color-primary); }
.legend-away { color: var(--el-color-danger); }
.empty-state { text-align: center; padding: 40px; color: var(--el-text-color-secondary); }
</style>
